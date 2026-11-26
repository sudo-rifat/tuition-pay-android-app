package com.studentpay.manager.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.studentpay.manager.R
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.domain.Dashboard
import com.studentpay.manager.domain.MonthState
import com.studentpay.manager.domain.Months
import com.studentpay.manager.domain.Payment
import com.studentpay.manager.domain.PaymentType
import com.studentpay.manager.domain.Student
import com.studentpay.manager.pdf.ReportService
import com.studentpay.manager.ui.appViewModel
import com.studentpay.manager.ui.components.AppTopBar
import com.studentpay.manager.ui.components.ConfirmDialog
import com.studentpay.manager.ui.components.EmptyState
import com.studentpay.manager.ui.components.SectionHeader
import com.studentpay.manager.ui.components.YearStepper
import com.studentpay.manager.ui.components.groupedShape
import com.studentpay.manager.ui.formatDate
import com.studentpay.manager.ui.formatTaka
import com.studentpay.manager.ui.theme.StatusTheme
import com.studentpay.manager.ui.todayPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val student: Student? = null,
    val year: Int = todayPeriod().year,
    val states: List<MonthState> = emptyList(),
    /** Paid months of [year], in month order. */
    val history: List<Payment> = emptyList(),
    val generating: Boolean = false,
    val deleteCount: Int? = null,
    val error: Boolean = false,
)

class StudentDetailViewModel(
    private val repository: StudentPayRepository,
    private val reports: ReportService,
    private val studentId: Long,
) : ViewModel() {
    private val today = todayPeriod()
    private val year = MutableStateFlow(today.year)
    private val generating = MutableStateFlow(false)
    private val deleteCount = MutableStateFlow<Int?>(null)
    private val error = MutableStateFlow(false)

    val uiState: StateFlow<DetailUiState> = combine(
        repository.observeStudent(studentId),
        repository.observeStudentPayments(studentId),
        year,
        combine(generating, deleteCount, error) { g, d, e -> Triple(g, d, e) },
    ) { student, payments, y, flags ->
        DetailUiState(
            isLoading = false,
            student = student,
            year = y,
            states = if (student == null) emptyList() else Dashboard.monthStates(student.id, y, payments, today),
            history = payments.filter { it.year == y }.sortedBy { it.monthPaidFor },
            generating = flags.first,
            deleteCount = flags.second,
            error = flags.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun setYear(y: Int) { year.value = y }

    fun createReport(onReady: (String) -> Unit) {
        if (generating.value) return
        generating.value = true
        viewModelScope.launch {
            try {
                onReady(reports.studentReport(studentId, year.value).name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = true
            } finally {
                generating.value = false
            }
        }
    }

    fun requestDelete() {
        viewModelScope.launch { deleteCount.value = repository.paymentCountFor(studentId) }
    }

    fun cancelDelete() { deleteCount.value = null }

    fun confirmDelete(onDone: () -> Unit) {
        deleteCount.value = null
        viewModelScope.launch {
            try {
                repository.deleteStudent(studentId)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = true
            }
        }
    }

    fun errorShown() { error.value = false }
}

@Composable
fun StudentDetailScreen(studentId: Long, onBack: () -> Unit, onEdit: () -> Unit, onPreview: (String) -> Unit) {
    val vm = appViewModel(key = "detail-$studentId") { StudentDetailViewModel(it.repository, it.reports, studentId) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val errorText = stringResource(R.string.error_generic)
    LaunchedEffect(state.error) {
        if (state.error) { snackbar.showSnackbar(errorText); vm.errorShown() }
    }
    var menu by remember { mutableStateOf(false) }
    val student = state.student

    Scaffold(
        topBar = {
            AppTopBar(
                title = student?.name ?: stringResource(R.string.student_details),
                onBack = onBack,
                actions = {
                    if (student != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_generic))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; vm.requestDelete() },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            student == null -> EmptyState(
                title = stringResource(R.string.student_not_found),
                body = stringResource(R.string.student_not_found_body),
                modifier = Modifier.padding(padding),
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "summary") { SummaryCard(student) }
                item(key = "grid-header") {
                    SectionHeader(stringResource(R.string.detail_status_header), Modifier.padding(top = 8.dp))
                }
                item(key = "year") {
                    val thisYear = todayPeriod().year
                    YearStepper(state.year, thisYear - 5, thisYear + 5, vm::setYear)
                }
                item(key = "grid") { MonthGrid(state.states) }
                item(key = "history-header") {
                    SectionHeader(stringResource(R.string.detail_history_header, state.year), Modifier.padding(top = 8.dp))
                }
                if (state.history.isEmpty()) {
                    item(key = "history-empty") {
                        Text(
                            stringResource(R.string.detail_history_empty, state.year),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                itemsIndexed(state.history, key = { _, p -> p.id }) { index, p ->
                    HistoryRow(p, student, index, state.history.size)
                }
                item(key = "report") {
                    Button(
                        onClick = { vm.createReport(onPreview) },
                        enabled = !state.generating,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                    ) {
                        if (state.generating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.detail_report, state.year))
                    }
                }
            }
        }
    }

    state.deleteCount?.let { count ->
        ConfirmDialog(
            title = stringResource(R.string.delete_title, student?.name.orEmpty()),
            text = stringResource(R.string.delete_body, count),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { vm.confirmDelete(onBack) },
            onDismiss = vm::cancelDelete,
        )
    }
}

@Composable
private fun SummaryCard(student: Student) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatTaka(student.monthlyFee.toLong()) + stringResource(R.string.detail_per_month),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(if (student.type == PaymentType.A) R.string.type_a_title else R.string.type_b_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(if (student.type == PaymentType.A) R.string.type_a_desc else R.string.type_b_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The legacy 12-month status grid, keyed by the month the fee is FOR. Icon + word, never colour alone. */
@Composable
private fun MonthGrid(states: List<MonthState>) {
    if (states.size != 12) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        for (row in 0 until 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0 until 3) {
                    val m = row * 3 + col
                    MonthTile(Months.SHORT[m], states[m], Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MonthTile(label: String, state: MonthState, modifier: Modifier) {
    val status = StatusTheme.colors
    val (bg, fg) = when (state) {
        MonthState.PAID -> status.paidContainer to status.onPaidContainer
        MonthState.DUE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        MonthState.FUTURE -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(12.dp), color = bg, contentColor = fg, modifier = modifier.height(64.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                when (state) {
                    MonthState.PAID -> Icon(Icons.Default.Check, null, Modifier.size(12.dp))
                    MonthState.DUE -> Icon(Icons.Default.Warning, null, Modifier.size(12.dp))
                    MonthState.FUTURE -> Unit
                }
                Text(
                    stringResource(
                        when (state) {
                            MonthState.PAID -> R.string.status_paid
                            MonthState.DUE -> R.string.status_due
                            MonthState.FUTURE -> R.string.status_upcoming
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(p: Payment, student: Student, index: Int, count: Int) {
    Surface(
        shape = groupedShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(Months.FULL[p.monthPaidFor] + " " + p.year, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.detail_paid_on, formatDate(p.paidAtMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatTaka(student.monthlyFee.toLong()), style = MaterialTheme.typography.titleSmall)
        }
    }
}
