package com.studentpay.manager.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.studentpay.manager.R
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.domain.Dashboard
import com.studentpay.manager.domain.DashboardStats
import com.studentpay.manager.domain.Payment
import com.studentpay.manager.domain.PaymentAmount
import com.studentpay.manager.domain.PaymentEntry
import com.studentpay.manager.domain.PaymentType
import com.studentpay.manager.domain.Period
import com.studentpay.manager.domain.SelectionBounds
import com.studentpay.manager.domain.StudentMonthStatus
import com.studentpay.manager.ui.appViewModel
import com.studentpay.manager.ui.components.AppTopBar
import com.studentpay.manager.ui.components.EmptyState
import com.studentpay.manager.ui.components.MonthYearStepper
import com.studentpay.manager.ui.components.StatusChip
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UndoPrompt(val payments: List<Payment>, val token: Long)
data class RecordedNotice(val count: Int, val total: Long, val token: Long)

enum class PaymentDialog { RECORD, REMOVE }

private data class Local(
    val selectedIds: Set<Long> = emptySet(),
    val dialog: PaymentDialog? = null,
    val working: Boolean = false,
    val undo: UndoPrompt? = null,
    val recorded: RecordedNotice? = null,
    val error: Boolean = false,
)

data class PaymentsUiState(
    val isLoading: Boolean = true,
    val selected: Period = todayPeriod(),
    val range: ClosedRange<Period> = SelectionBounds.collection(todayPeriod()),
    val filter: StatusFilter = StatusFilter.ALL,
    val stats: DashboardStats = DashboardStats(0, 0, 0, 0L),
    /** Rows shown (after the Paid/Due filter). */
    val rows: List<StudentMonthStatus> = emptyList(),
    /** Every active student for the month, ignoring the filter. */
    val allRows: List<StudentMonthStatus> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val dialog: PaymentDialog? = null,
    val working: Boolean = false,
    val undo: UndoPrompt? = null,
    val recorded: RecordedNotice? = null,
    val error: Boolean = false,
) {
    /** Selected students who are NOT paid yet: what "Record" would act on. */
    val toRecord: List<StudentMonthStatus> get() = allRows.filter { it.student.id in selectedIds && !it.isPaid }

    /** Selected students who ARE paid: what "Remove" would act on. */
    val toRemove: List<StudentMonthStatus> get() = allRows.filter { it.student.id in selectedIds && it.isPaid }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsViewModel(private val repository: StudentPayRepository) : ViewModel() {
    private val range = SelectionBounds.collection(todayPeriod())
    private val selected = MutableStateFlow(todayPeriod())
    private val filter = MutableStateFlow(StatusFilter.ALL)
    private val local = MutableStateFlow(Local())
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
                allRows = result.rows,
                rows = when (f) {
                    StatusFilter.ALL -> result.rows
                    StatusFilter.DUE -> result.rows.filter { !it.isPaid }
                    StatusFilter.PAID -> result.rows.filter { it.isPaid }
                },
            )
        }
    }

    val uiState: StateFlow<PaymentsUiState> = combine(base, local) { b, l ->
        b.copy(
            selectedIds = l.selectedIds, dialog = l.dialog, working = l.working,
            undo = l.undo, recorded = l.recorded, error = l.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentsUiState())

    fun select(period: Period) {
        selected.value = when {
            period < range.start -> range.start
            period > range.endInclusive -> range.endInclusive
            else -> period
        }
        // A selection belongs to one month: never carry it over.
        local.update { it.copy(selectedIds = emptySet(), dialog = null) }
    }

    fun setFilter(f: StatusFilter) { filter.value = f }

    // ---- selection (nothing is saved by selecting) ----

    fun toggle(studentId: Long) = local.update {
        it.copy(selectedIds = if (studentId in it.selectedIds) it.selectedIds - studentId else it.selectedIds + studentId)
    }

    fun selectAllShown() {
        val ids = uiState.value.rows.map { it.student.id }
        local.update { it.copy(selectedIds = it.selectedIds + ids) }
    }

    fun clearSelection() = local.update { it.copy(selectedIds = emptySet()) }

    fun openDialog(d: PaymentDialog) = local.update { it.copy(dialog = d) }
    fun dismissDialog() = local.update { if (it.working) it else it.copy(dialog = null) }

    // ---- confirmed actions ----

    /** [amounts] = what the user typed per student (may differ from the monthly fee). */
    fun confirmRecord(amounts: Map<Long, Int>) {
        val state = uiState.value
        val sel = state.selected
        val entries = state.toRecord.map { PaymentEntry(it.student, amounts[it.student.id] ?: it.amount) }
        if (entries.isEmpty() || local.value.working) return
        local.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                val created = repository.recordPayments(entries, sel.month, sel.year)
                val doneIds = entries.map { it.student.id }.toSet()
                local.update {
                    it.copy(
                        selectedIds = it.selectedIds - doneIds,
                        dialog = null,
                        recorded = RecordedNotice(created.size, created.sumOf { p -> p.amount.toLong() }, ++token),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = true, dialog = null) }
            } finally {
                local.update { it.copy(working = false) }
            }
        }
    }

    fun confirmRemove() {
        val state = uiState.value
        val sel = state.selected
        val students = state.toRemove.map { it.student }
        if (students.isEmpty() || local.value.working) return
        local.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                val removed = repository.removePayments(students, sel.month, sel.year)
                val doneIds = students.map { it.id }.toSet()
                local.update {
                    it.copy(
                        selectedIds = it.selectedIds - doneIds,
                        dialog = null,
                        undo = if (removed.isEmpty()) null else UndoPrompt(removed, ++token),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = true, dialog = null) }
            } finally {
                local.update { it.copy(working = false) }
            }
        }
    }

    fun undoRemoval() {
        val prompt = local.value.undo ?: return
        local.update { it.copy(undo = null) }
        viewModelScope.launch {
            try {
                repository.restorePayments(prompt.payments)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = true) }
            }
        }
    }

    fun undoDismissed() = local.update { it.copy(undo = null) }
    fun recordedShown() = local.update { it.copy(recorded = null) }
    fun errorShown() = local.update { it.copy(error = false) }
}

@Composable
fun PaymentsScreen(onAddStudent: () -> Unit) {
    val vm = appViewModel { PaymentsViewModel(it.repository) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Undo after a removal.
    val undoPrompt = state.undo
    val removedText = undoPrompt?.let { stringResource(R.string.payments_removed_n, it.payments.size) }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(undoPrompt?.token) {
        if (undoPrompt != null && removedText != null) {
            val result = snackbar.showSnackbar(removedText, actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) vm.undoRemoval() else vm.undoDismissed()
        }
    }
    // Confirmation of a recording.
    val recorded = state.recorded
    val recordedText = recorded?.let { stringResource(R.string.payments_recorded, it.count, formatTaka(it.total)) }
    LaunchedEffect(recorded?.token) {
        if (recordedText != null) { snackbar.showSnackbar(recordedText); vm.recordedShown() }
    }
    val errorText = stringResource(R.string.error_generic)
    LaunchedEffect(state.error) {
        if (state.error) { snackbar.showSnackbar(errorText); vm.errorShown() }
    }

    val recordRows = state.toRecord
    val removeRows = state.toRemove

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_payments)) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.selectedIds.isNotEmpty()) {
                SelectionBar(
                    selected = state.selectedIds.size,
                    recordCount = recordRows.size,
                    removeCount = removeRows.size,
                    onClear = vm::clearSelection,
                    onRecord = { vm.openDialog(PaymentDialog.RECORD) },
                    onRemove = { vm.openDialog(PaymentDialog.REMOVE) },
                )
            }
        },
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
                    stringResource(R.string.payments_hint),
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
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(
                                R.string.payments_summary,
                                state.stats.paidCount,
                                state.stats.totalStudents,
                                formatTaka(state.stats.collectedAmount),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = vm::selectAllShown, enabled = state.rows.isNotEmpty()) {
                            Text(stringResource(R.string.payments_select_all))
                        }
                    }
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
                    PaymentRow(
                        row = row,
                        index = index,
                        count = state.rows.size,
                        isSelected = row.student.id in state.selectedIds,
                        onToggle = { vm.toggle(row.student.id) },
                    )
                }
            }
        }
    }

    when (state.dialog) {
        PaymentDialog.RECORD -> RecordPaymentsDialog(
            rows = recordRows,
            working = state.working,
            onConfirm = vm::confirmRecord,
            onDismiss = vm::dismissDialog,
        )
        PaymentDialog.REMOVE -> RemovePaymentsDialog(
            rows = removeRows,
            working = state.working,
            onConfirm = vm::confirmRemove,
            onDismiss = vm::dismissDialog,
        )
        null -> Unit
    }
}

@Composable
private fun SelectionBar(
    selected: Int,
    recordCount: Int,
    removeCount: Int,
    onClear: () -> Unit,
    onRecord: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.payments_selected, selected),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.payments_clear)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recordCount > 0) {
                    Button(onClick = onRecord, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.payments_record_btn, recordCount))
                    }
                }
                if (removeCount > 0) {
                    OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.payments_remove_btn, removeCount), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(row: StudentMonthStatus, index: Int, count: Int, isSelected: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = groupedShape(index, count),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = isSelected, onCheckedChange = null)
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
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            StatusChip(row.isPaid)
        }
    }
}

/**
 * Confirmation before recording. Each payment has its own editable amount (prefilled with the student's monthly
 * fee); what is typed here is exactly what is saved. Nothing is written until "Record" is pressed.
 */
@Composable
private fun RecordPaymentsDialog(
    rows: List<StudentMonthStatus>,
    working: Boolean,
    onConfirm: (Map<Long, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val ids = rows.map { it.student.id }
    val texts = remember(ids) {
        mutableStateMapOf<Long, String>().also { m -> rows.forEach { m[it.student.id] = it.amount.toString() } }
    }
    val parsed = rows.associate { it.student.id to PaymentAmount.parse(texts[it.student.id].orEmpty()) }
    val valid = rows.isNotEmpty() && parsed.values.all { it != null }
    val total = parsed.values.sumOf { (it ?: 0).toLong() }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.record_title, rows.size)) },
        text = {
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.record_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rows.forEachIndexed { i, row ->
                    val id = row.student.id
                    val text = texts[id].orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(row.student.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                stringResource(R.string.record_for, row.target.shortLabel()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { v -> texts[id] = v.filter { it in '0'..'9' }.take(9) },
                            prefix = { Text("\u09F3") },
                            singleLine = true,
                            isError = PaymentAmount.parse(text) == null,
                            label = { Text(stringResource(R.string.record_amount)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = if (i == rows.lastIndex) ImeAction.Done else ImeAction.Next,
                            ),
                            modifier = Modifier.width(140.dp),
                        )
                    }
                }
                Text(
                    stringResource(R.string.record_total, formatTaka(total)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parsed.mapValues { it.value ?: 0 }) }, enabled = valid && !working) {
                if (working) CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.record_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Confirmation before removing: lists exactly which payments will be removed. */
@Composable
private fun RemovePaymentsDialog(
    rows: List<StudentMonthStatus>,
    working: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shown = rows.take(8)
    val total = rows.sumOf { it.amount.toLong() }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.remove_title, rows.size)) },
        text = {
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.remove_body))
                shown.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(row.student.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                stringResource(R.string.record_for, row.target.shortLabel()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(formatTaka(row.amount.toLong()), style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (rows.size > shown.size) {
                    Text(stringResource(R.string.remove_more, rows.size - shown.size), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(R.string.record_total, formatTaka(total)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !working && rows.isNotEmpty()) {
                Text(stringResource(R.string.remove_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
