package com.studentpay.manager.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.studentpay.manager.R
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.domain.Dashboard
import com.studentpay.manager.domain.DashboardStats
import com.studentpay.manager.domain.Payment
import com.studentpay.manager.domain.PaymentType
import com.studentpay.manager.domain.Period
import com.studentpay.manager.domain.SelectionBounds
import com.studentpay.manager.domain.StudentMonthStatus
import com.studentpay.manager.ui.appViewModel
import com.studentpay.manager.ui.components.AppTopBar
import com.studentpay.manager.ui.components.EmptyState
import com.studentpay.manager.ui.components.MonthYearStepper
import com.studentpay.manager.ui.components.StatusFilter
import com.studentpay.manager.ui.components.StatusFilterRow
import com.studentpay.manager.ui.components.groupedShape
import com.studentpay.manager.ui.formatTaka
import com.studentpay.manager.ui.fullLabel
import com.studentpay.manager.ui.shortLabel
import com.studentpay.manager.ui.todayPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UndoPrompt(val studentName: String, val payment: Payment, val token: Long)

data class PaymentsUiState(
    val isLoading: Boolean = true,
    val selected: Period = todayPeriod(),
    val range: ClosedRange<Period> = SelectionBounds.collection(todayPeriod()),
    val filter: StatusFilter = StatusFilter.ALL,
    val stats: DashboardStats = DashboardStats(0, 0, 0, 0L),
    val rows: List<StudentMonthStatus> = emptyList(),
    val undo: UndoPrompt? = null,
    val error: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsViewModel(private val repository: StudentPayRepository) : ViewModel() {
    private val range = SelectionBounds.collection(todayPeriod())
    private val selected = MutableStateFlow(todayPeriod())
    private val filter = MutableStateFlow(StatusFilter.ALL)
    private val undo = MutableStateFlow<UndoPrompt?>(null)
    private val error = MutableStateFlow(false)
    private var token = 0L

    private val base = selected.flatMapLatest { sel ->
        combine(
            repository.observeStudents(),
            repository.observePayments(sel.year - 1, sel.year),
            filter,
            repository.observeDeletedStudents(),
        ) { students, payments, f, deleted ->
            val result = Dashboard.compute(students, payments, sel.month, sel.year, deleted)
            PaymentsUiState(
                isLoading = false,
                selected = sel,
                range = range,
                filter = f,
                stats = result.stats,
                rows = when (f) {
                    StatusFilter.ALL -> result.rows
                    StatusFilter.DUE -> result.rows.filter { !it.isPaid }
                    StatusFilter.PAID -> result.rows.filter { it.isPaid }
                },
            )
        }
    }

    val uiState: StateFlow<PaymentsUiState> = combine(base, undo, error) { s, u, e -> s.copy(undo = u, error = e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentsUiState())

    fun select(period: Period) {
        selected.value = when {
            period < range.start -> range.start
            period > range.endInclusive -> range.endInclusive
            else -> period
        }
    }

    fun setFilter(f: StatusFilter) { filter.value = f }

    /** Tick = record payment (now); untick = reverse it, with Undo. */
    fun toggle(row: StudentMonthStatus) {
        val sel = selected.value
        viewModelScope.launch {
            try {
                if (row.isPaid) {
                    val removed = repository.removePayment(row.student, sel.month, sel.year)
                    if (removed != null) undo.value = UndoPrompt(row.student.name, removed, ++token)
                } else {
                    repository.markPaid(row.student, sel.month, sel.year)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = true
            }
        }
    }

    fun undoRemoval() {
        val prompt = undo.value ?: return
        undo.value = null
        viewModelScope.launch {
            try { repository.restorePayment(prompt.payment) } catch (e: CancellationException) { throw e } catch (e: Exception) { error.value = true }
        }
    }

    fun undoDismissed() { undo.value = null }
    fun errorShown() { error.value = false }
}

@Composable
fun PaymentsScreen(onAddStudent: () -> Unit) {
    val vm = appViewModel { PaymentsViewModel(it.repository) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val undoPrompt = state.undo
    val removedText = undoPrompt?.let { stringResource(R.string.payments_removed, it.studentName) }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(undoPrompt?.token) {
        if (undoPrompt != null && removedText != null) {
            val result = snackbar.showSnackbar(removedText, actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) vm.undoRemoval() else vm.undoDismissed()
        }
    }
    val errorText = stringResource(R.string.error_generic)
    LaunchedEffect(state.error) {
        if (state.error) { snackbar.showSnackbar(errorText); vm.errorShown() }
    }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_payments)) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "stepper") { MonthYearStepper(state.selected, state.range, vm::select) }
            item(key = "hint") {
                Text(
                    stringResource(R.string.payments_hint, state.selected.fullLabel()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (state.stats.totalStudents == 0) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        actionLabel = stringResource(R.string.students_add),
                        onAction = onAddStudent,
                    )
                }
            } else {
                item(key = "summary") {
                    Text(
                        stringResource(
                            R.string.payments_summary,
                            state.stats.paidCount,
                            state.stats.totalStudents,
                            formatTaka(state.stats.collectedAmount),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                item(key = "filters") {
                    StatusFilterRow(
                        selected = state.filter,
                        paid = state.stats.paidCount,
                        due = state.stats.dueCount,
                        total = state.stats.totalStudents,
                        onSelect = vm::setFilter,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (state.rows.isEmpty()) {
                    item(key = "filter-empty") {
                        EmptyState(
                            title = stringResource(
                                if (state.filter == StatusFilter.DUE) R.string.home_none_due_title
                                else R.string.home_none_paid_title,
                            ),
                            body = stringResource(R.string.home_filter_empty_body, state.selected.fullLabel()),
                        )
                    }
                }
                itemsIndexed(state.rows, key = { _, r -> r.student.id }) { index, row ->
                    PaymentRow(row, index, state.rows.size, onToggle = { vm.toggle(row) })
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(row: StudentMonthStatus, index: Int, count: Int, onToggle: () -> Unit) {
    Surface(
        shape = groupedShape(index, count),
        color = if (row.isPaid) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(value = row.isPaid, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = row.isPaid, onCheckedChange = null)
            Column(Modifier.weight(1f)) {
                Text(row.student.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    stringResource(
                        R.string.payments_row_support,
                        row.target.shortLabel(),
                        formatTaka(row.amount.toLong()),
                        stringResource(if (row.student.type == PaymentType.A) R.string.type_a_short else R.string.type_b_short),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
