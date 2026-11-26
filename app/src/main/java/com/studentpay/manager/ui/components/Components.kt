package com.studentpay.manager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studentpay.manager.R
import com.studentpay.manager.domain.Months
import com.studentpay.manager.domain.Period
import com.studentpay.manager.ui.fullLabel
import com.studentpay.manager.ui.theme.StatusTheme

/** Grouped-list corner shape: big outer corners, small inner corners (Material 3 Expressive grouping). */
fun groupedShape(index: Int, count: Int): Shape {
    val outer = 16.dp
    val inner = 4.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = actions,
    )
}

/** Paid/Due indicator. Never colour alone: icon + word. */
@Composable
fun StatusChip(isPaid: Boolean, modifier: Modifier = Modifier) {
    val status = StatusTheme.colors
    val bg = if (isPaid) status.paidContainer else MaterialTheme.colorScheme.errorContainer
    val fg = if (isPaid) status.onPaidContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(shape = CircleShape, color = bg, contentColor = fg, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Icon(
                if (isPaid) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                stringResource(if (isPaid) R.string.status_paid else R.string.status_due),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ---------- filter ----------

enum class StatusFilter { ALL, DUE, PAID }

@Composable
fun StatusFilterRow(
    selected: StatusFilter,
    paid: Int,
    due: Int,
    total: Int,
    onSelect: (StatusFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == StatusFilter.ALL,
            onClick = { onSelect(StatusFilter.ALL) },
            label = { Text(stringResource(R.string.filter_all, total)) },
        )
        FilterChip(
            selected = selected == StatusFilter.DUE,
            onClick = { onSelect(StatusFilter.DUE) },
            label = { Text(stringResource(R.string.filter_due, due)) },
        )
        FilterChip(
            selected = selected == StatusFilter.PAID,
            onClick = { onSelect(StatusFilter.PAID) },
            label = { Text(stringResource(R.string.filter_paid, paid)) },
        )
    }
}

// ---------- month / year selection ----------

/** "‹  March 2026  ›" - one tap to step a month (December wraps to January), tap the label to jump. */
@Composable
fun MonthYearStepper(
    selected: Period,
    range: ClosedRange<Period>,
    onChange: (Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onChange(selected.plusMonths(-1)) }, enabled = selected > range.start) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.stepper_previous_month))
        }
        TextButton(onClick = { picking = true }) {
            Text(selected.fullLabel(), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = { onChange(selected.plusMonths(1)) }, enabled = selected < range.endInclusive) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.stepper_next_month))
        }
    }
    if (picking) {
        MonthPickerDialog(
            selected = selected,
            range = range,
            onDismiss = { picking = false },
            onPick = { picking = false; onChange(it) },
        )
    }
}

@Composable
fun YearStepper(
    year: Int,
    minYear: Int,
    maxYear: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onChange(year - 1) }, enabled = year > minYear) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.stepper_previous_year))
        }
        Text(year.toString(), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(year + 1) }, enabled = year < maxYear) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.stepper_next_year))
        }
    }
}

@Composable
private fun MonthPickerDialog(
    selected: Period,
    range: ClosedRange<Period>,
    onDismiss: () -> Unit,
    onPick: (Period) -> Unit,
) {
    var year by remember { mutableIntStateOf(selected.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            Column {
                YearStepper(year, range.start.year, range.endInclusive.year, { year = it })
                Spacer(Modifier.height(8.dp))
                for (row in 0 until 4) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 3) {
                            val m = row * 3 + col
                            val candidate = Period(year, m)
                            val allowed = candidate in range
                            val isSelected = candidate == selected
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .padding(vertical = 2.dp)
                                    .let { if (allowed) it.clickable { onPick(candidate) } else it },
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        Months.SHORT[m],
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (allowed) androidx.compose.ui.graphics.Color.Unspecified
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

val ScreenPadding = PaddingValues(horizontal = 16.dp)
