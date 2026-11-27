package com.studentpay.manager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.studentpay.manager.R
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.domain.Dashboard
import com.studentpay.manager.domain.DashboardStats
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val isLoading: Boolean = true,
    val selected: Period = todayPeriod(),
    val range: ClosedRange<Period> = SelectionBounds.collection(todayPeriod()),
    val filter: StatusFilter = StatusFilter.ALL,
    val stats: DashboardStats = DashboardStats(0, 0, 0, 0L),
    val rows: List<StudentMonthStatus> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(repository: StudentPayRepository) : ViewModel() {
    private val range = SelectionBounds.collection(todayPeriod())
    private val selected = MutableStateFlow(todayPeriod())
    private val filter = MutableStateFlow(StatusFilter.ALL)

    val uiState: StateFlow<HomeUiState> = selected.flatMapLatest { sel ->
        // Type B in January reads the previous year's December, hence year-1..year.
        combine(
            repository.observeStudents(),
            repository.observePayments(sel.year - 1, sel.year),
            filter,
        ) { students, payments, f ->
            val result = Dashboard.compute(students, payments, sel.month, sel.year)
            HomeUiState(
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun select(period: Period) {
        selected.value = when {
            period < range.start -> range.start
            period > range.endInclusive -> range.endInclusive
            else -> period
        }
    }

    fun setFilter(f: StatusFilter) {
        filter.value = f
    }
}

@Composable
fun HomeScreen(onOpenStudent: (Long) -> Unit, onAddStudent: () -> Unit) {
    val vm = appViewModel { HomeViewModel(it.repository) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar(stringResource(R.string.nav_home)) }) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "stepper") {
                MonthYearStepper(state.selected, state.range, vm::select, Modifier.padding(bottom = 4.dp))
            }
            item(key = "hero") { HeroCard(state.selected, state.stats, Modifier.padding(bottom = 12.dp)) }

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
                    StudentStatusRow(row, index, state.rows.size, onClick = { onOpenStudent(row.student.id) })
                }
            }
        }
    }
}

/** The one "hero" of the screen: what has been collected for the selected month. */
@Composable
private fun HeroCard(selected: Period, stats: DashboardStats, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.home_collected_in, selected.fullLabel()),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                formatTaka(stats.collectedAmount),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Stat(stats.paidCount.toString(), stringResource(R.string.stat_paid))
                Stat(stats.dueCount.toString(), stringResource(R.string.stat_due))
                Stat(stats.totalStudents.toString(), stringResource(R.string.stat_students))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StudentStatusRow(row: StudentMonthStatus, index: Int, count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
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
                Text(row.student.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    stringResource(
                        R.string.row_support,
                        stringResource(if (row.student.type == PaymentType.A) R.string.type_a_short else R.string.type_b_short),
                        formatTaka(row.amount.toLong()),
                        row.target.shortLabel(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            StatusChip(row.isPaid)
        }
    }
}
