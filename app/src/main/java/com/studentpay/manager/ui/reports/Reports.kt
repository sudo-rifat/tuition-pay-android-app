package com.studentpay.manager.ui.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.studentpay.manager.R
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.domain.Period
import com.studentpay.manager.domain.ReportBasis
import com.studentpay.manager.domain.SelectionBounds
import com.studentpay.manager.domain.Student
import com.studentpay.manager.pdf.ReportService
import com.studentpay.manager.ui.appViewModel
import com.studentpay.manager.ui.components.AppTopBar
import com.studentpay.manager.ui.components.EmptyState
import com.studentpay.manager.ui.components.MonthYearStepper
import com.studentpay.manager.ui.components.SectionHeader
import com.studentpay.manager.ui.components.YearStepper
import com.studentpay.manager.ui.todayPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReportType { STUDENT, MONTHLY, YEARLY }

/** One-shot result of "generate": open the preview, or ask where to save. */
sealed interface ReportOutput {
    data class Preview(val fileName: String) : ReportOutput
    data class Save(val fileName: String) : ReportOutput
}

private data class Local(
    val type: ReportType = ReportType.MONTHLY,
    /** Default: money collected (received) in the selected period. */
    val basis: ReportBasis = ReportBasis.COLLECTED,
    val studentId: Long? = null,
    val selected: Period = todayPeriod(),
    val generating: Boolean = false,
    val output: ReportOutput? = null,
    val saved: Boolean = false,
    val error: Boolean = false,
)

data class ReportsUiState(
    val isLoading: Boolean = true,
    val students: List<Student> = emptyList(),
    val type: ReportType = ReportType.MONTHLY,
    val basis: ReportBasis = ReportBasis.COLLECTED,
    val student: Student? = null,
    val selected: Period = todayPeriod(),
    val range: ClosedRange<Period> = SelectionBounds.reports(todayPeriod()),
    val generating: Boolean = false,
    val output: ReportOutput? = null,
    val saved: Boolean = false,
    val error: Boolean = false,
)

class ReportsViewModel(
    repository: StudentPayRepository,
    private val reports: ReportService,
) : ViewModel() {
    private val range = SelectionBounds.reports(todayPeriod())
    private val local = MutableStateFlow(Local())
    private var pendingSaveName: String? = null

    val uiState: StateFlow<ReportsUiState> = combine(repository.observeStudents(), local) { students, l ->
        ReportsUiState(
            isLoading = false,
            students = students,
            type = l.type,
            basis = l.basis,
            student = students.firstOrNull { it.id == l.studentId } ?: students.firstOrNull(),
            selected = l.selected,
            range = range,
            generating = l.generating,
            output = l.output,
            saved = l.saved,
            error = l.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun setType(t: ReportType) = local.update { it.copy(type = t) }
    fun setBasis(b: ReportBasis) = local.update { it.copy(basis = b) }
    fun setStudent(id: Long) = local.update { it.copy(studentId = id) }
    fun setPeriod(p: Period) = local.update {
        it.copy(selected = if (p < range.start) range.start else if (p > range.endInclusive) range.endInclusive else p)
    }

    fun generate(save: Boolean) {
        val s = uiState.value
        if (s.generating) return
        local.update { it.copy(generating = true, error = false) }
        viewModelScope.launch {
            try {
                val file = when (s.type) {
                    ReportType.STUDENT -> reports.studentReport(s.student?.id ?: error("no student"), s.selected.year, s.basis)
                    ReportType.MONTHLY -> reports.monthlyReport(s.selected.year, s.selected.month, s.basis)
                    ReportType.YEARLY -> reports.yearlyReport(s.selected.year, s.basis)
                }
                if (save) pendingSaveName = file.name
                local.update {
                    it.copy(output = if (save) ReportOutput.Save(file.name) else ReportOutput.Preview(file.name))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = true) }
            } finally {
                local.update { it.copy(generating = false) }
            }
        }
    }

    fun outputHandled() = local.update { it.copy(output = null) }

    fun saveTo(uri: android.net.Uri) {
        val name = pendingSaveName ?: return
        viewModelScope.launch {
            try {
                reports.saveTo(reports.fileFor(name), uri)
                local.update { it.copy(saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = true) }
            }
        }
    }

    fun noticeShown() = local.update { it.copy(saved = false, error = false) }
}

@Composable
fun ReportsScreen(onOpenPreview: (String) -> Unit) {
    val vm = appViewModel { ReportsViewModel(it.repository, it.reports) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.saveTo(uri)
    }
    LaunchedEffect(state.output) {
        when (val o = state.output) {
            is ReportOutput.Preview -> { vm.outputHandled(); onOpenPreview(o.fileName) }
            is ReportOutput.Save -> { vm.outputHandled(); saveLauncher.launch(o.fileName) }
            null -> Unit
        }
    }
    val savedText = stringResource(R.string.reports_saved)
    val errorText = stringResource(R.string.reports_error)
    LaunchedEffect(state.saved, state.error) {
        if (state.saved) { snackbar.showSnackbar(savedText); vm.noticeShown() }
        else if (state.error) { snackbar.showSnackbar(errorText); vm.noticeShown() }
    }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_reports)) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.students.isEmpty() -> EmptyState(
                title = stringResource(R.string.reports_empty_title),
                body = stringResource(R.string.reports_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionHeader(stringResource(R.string.reports_type), Modifier.padding(0.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.type == ReportType.MONTHLY, { vm.setType(ReportType.MONTHLY) },
                        { Text(stringResource(R.string.report_monthly)) })
                    FilterChip(state.type == ReportType.YEARLY, { vm.setType(ReportType.YEARLY) },
                        { Text(stringResource(R.string.report_yearly)) })
                    FilterChip(state.type == ReportType.STUDENT, { vm.setType(ReportType.STUDENT) },
                        { Text(stringResource(R.string.report_student)) })
                }
                Text(
                    stringResource(
                        when (state.type) {
                            ReportType.MONTHLY -> R.string.report_monthly_desc
                            ReportType.YEARLY -> R.string.report_yearly_desc
                            ReportType.STUDENT -> R.string.report_student_desc
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionHeader(stringResource(R.string.reports_basis), Modifier.padding(0.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.basis == ReportBasis.COLLECTED, { vm.setBasis(ReportBasis.COLLECTED) },
                        { Text(stringResource(R.string.basis_collected)) })
                    FilterChip(state.basis == ReportBasis.ACTUAL, { vm.setBasis(ReportBasis.ACTUAL) },
                        { Text(stringResource(R.string.basis_actual)) })
                }
                Text(
                    stringResource(
                        if (state.basis == ReportBasis.COLLECTED) R.string.basis_collected_desc else R.string.basis_actual_desc,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.type == ReportType.STUDENT) {
                    StudentPicker(state.students, state.student, vm::setStudent)
                }
                if (state.type == ReportType.MONTHLY) {
                    MonthYearStepper(state.selected, state.range, vm::setPeriod)
                } else {
                    YearStepper(
                        year = state.selected.year,
                        minYear = state.range.start.year,
                        maxYear = state.range.endInclusive.year,
                        onChange = { vm.setPeriod(Period(it, state.selected.month)) },
                    )
                }

                Button(
                    onClick = { vm.generate(save = false) },
                    enabled = !state.generating,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (state.generating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.reports_preview))
                }
                OutlinedButton(
                    onClick = { vm.generate(save = true) },
                    enabled = !state.generating,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(stringResource(R.string.reports_save)) }
            }
        }
    }
}

@Composable
private fun StudentPicker(students: List<Student>, selected: Student?, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(selected?.name ?: stringResource(R.string.reports_pick_student), maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            students.forEach { s ->
                DropdownMenuItem(text = { Text(s.name) }, onClick = { open = false; onSelect(s.id) })
            }
        }
    }
}
