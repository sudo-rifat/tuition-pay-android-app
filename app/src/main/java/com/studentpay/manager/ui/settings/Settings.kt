package com.studentpay.manager.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.studentpay.manager.R
import com.studentpay.manager.data.AppSettings
import com.studentpay.manager.data.BackupService
import com.studentpay.manager.data.LockTimeout
import com.studentpay.manager.data.SettingsRepository
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.data.ThemeMode
import com.studentpay.manager.domain.BackupFormatError
import com.studentpay.manager.domain.BackupFormatException
import com.studentpay.manager.domain.ImportPlan
import com.studentpay.manager.ui.AppViewModel
import com.studentpay.manager.ui.appViewModel
import com.studentpay.manager.ui.components.AppTopBar
import com.studentpay.manager.ui.components.SectionHeader
import com.studentpay.manager.ui.components.groupedShape
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RestoreState {
    data object Idle : RestoreState
    data object Working : RestoreState
    data class Confirm(val plan: ImportPlan, val currentStudents: Int, val currentPayments: Int) : RestoreState
}

sealed interface Notice {
    data class Exported(val students: Int, val payments: Int) : Notice
    data object ExportFailed : Notice
    data class Restored(val students: Int, val payments: Int) : Notice
    data class RestoreFailed(val reason: BackupFormatError?) : Notice
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val restore: RestoreState = RestoreState.Idle,
    val notice: Notice? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backup: BackupService,
    private val repository: StudentPayRepository,
) : ViewModel() {
    private val restore = MutableStateFlow<RestoreState>(RestoreState.Idle)
    private val notice = MutableStateFlow<Notice?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(settingsRepository.settings, restore, notice) { s, r, n ->
        SettingsUiState(s, r, n)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(m: ThemeMode) { viewModelScope.launch { settingsRepository.setTheme(m) } }
    fun setLockTimeout(t: LockTimeout) { viewModelScope.launch { settingsRepository.setLockTimeout(t) } }
    fun setHideInRecents(v: Boolean) { viewModelScope.launch { settingsRepository.setHideInRecents(v) } }

    fun exportTo(uri: android.net.Uri) {
        viewModelScope.launch {
            notice.value = try {
                val (s, p) = backup.export(uri)
                Notice.Exported(s, p)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Notice.ExportFailed
            }
        }
    }

    /** Step 1 of restore: read + validate only. Nothing is changed until the user confirms. */
    fun startRestore(uri: android.net.Uri) {
        restore.value = RestoreState.Working
        viewModelScope.launch {
            try {
                val plan = backup.readPlan(uri)
                restore.value = RestoreState.Confirm(plan, repository.allStudents().size, repository.allPayments().size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupFormatException) {
                restore.value = RestoreState.Idle
                notice.value = Notice.RestoreFailed(e.error)
            } catch (e: Exception) {
                restore.value = RestoreState.Idle
                notice.value = Notice.RestoreFailed(null)
            }
        }
    }

    fun cancelRestore() { restore.value = RestoreState.Idle }

    /** Step 2: replace everything in one transaction. */
    fun confirmRestore() {
        val c = restore.value as? RestoreState.Confirm ?: return
        restore.value = RestoreState.Working
        viewModelScope.launch {
            try {
                backup.apply(c.plan)
                notice.value = Notice.Restored(c.plan.students.size, c.plan.payments.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice.value = Notice.RestoreFailed(null)
            } finally {
                restore.value = RestoreState.Idle
            }
        }
    }

    fun noticeShown() { notice.update { null } }
}

@Composable
fun SettingsScreen(appVm: AppViewModel, onChangePin: () -> Unit, onHelp: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it.settings, it.backup, it.repository) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var themeDialog by remember { mutableStateOf(false) }
    var timeoutDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.startRestore(uri)
    }

    val noticeText = state.notice?.let { noticeMessage(it) }
    LaunchedEffect(state.notice) {
        if (noticeText != null) { snackbar.showSnackbar(noticeText); vm.noticeShown() }
    }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_settings)) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            SectionHeader(stringResource(R.string.settings_security))
            Group {
                Row2(0, 4, stringResource(R.string.settings_change_pin), null, onClick = onChangePin)
                Row2(1, 4, stringResource(R.string.settings_lock_after), timeoutLabel(state.settings.lockTimeout),
                    onClick = { timeoutDialog = true })
                Row2(2, 4, stringResource(R.string.settings_hide_recents), stringResource(R.string.settings_hide_recents_desc),
                    trailing = { Switch(checked = state.settings.hideInRecents, onCheckedChange = null) },
                    onClick = { vm.setHideInRecents(!state.settings.hideInRecents) })
                Row2(3, 4, stringResource(R.string.settings_lock_now), null, onClick = { appVm.lockNow() })
            }

            SectionHeader(stringResource(R.string.settings_appearance), Modifier.padding(top = 8.dp))
            Group {
                Row2(0, 1, stringResource(R.string.settings_theme), themeLabel(state.settings.theme),
                    onClick = { themeDialog = true })
            }

            SectionHeader(stringResource(R.string.settings_data), Modifier.padding(top = 8.dp))
            Group {
                Row2(0, 2, stringResource(R.string.settings_export), stringResource(R.string.settings_export_desc),
                    onClick = { exportLauncher.launch("student-pay-backup-${LocalDate.now()}.json") })
                Row2(1, 2, stringResource(R.string.settings_restore), stringResource(R.string.settings_restore_desc),
                    onClick = { importLauncher.launch(arrayOf("*/*")) })
            }

            SectionHeader(stringResource(R.string.settings_help_section), Modifier.padding(top = 8.dp))
            Group {
                Row2(0, 2, stringResource(R.string.settings_help), null, onClick = onHelp)
                Row2(1, 2, stringResource(R.string.settings_about), stringResource(R.string.settings_about_desc), onClick = null)
            }
        }
    }

    if (themeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = state.settings.theme,
            onSelect = { vm.setTheme(it); themeDialog = false },
            onDismiss = { themeDialog = false },
        )
    }
    if (timeoutDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_lock_after),
            options = LockTimeout.entries.map { it to timeoutLabel(it) },
            selected = state.settings.lockTimeout,
            onSelect = { vm.setLockTimeout(it); timeoutDialog = false },
            onDismiss = { timeoutDialog = false },
        )
    }
    when (val r = state.restore) {
        RestoreState.Idle -> Unit
        RestoreState.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(); Text(stringResource(R.string.restore_working))
            } },
        )
        is RestoreState.Confirm -> RestoreConfirmDialog(r, onConfirm = vm::confirmRestore, onDismiss = vm::cancelRestore)
    }
}

@Composable
private fun noticeMessage(n: Notice): String = when (n) {
    is Notice.Exported -> stringResource(R.string.notice_exported, n.students, n.payments)
    Notice.ExportFailed -> stringResource(R.string.notice_export_failed)
    is Notice.Restored -> stringResource(R.string.notice_restored, n.students, n.payments)
    is Notice.RestoreFailed -> stringResource(
        when (n.reason) {
            BackupFormatError.NOT_JSON, BackupFormatError.NOT_A_BACKUP -> R.string.restore_err_not_backup
            BackupFormatError.UNSUPPORTED_VERSION -> R.string.restore_err_version
            BackupFormatError.TOO_LARGE, BackupFormatError.TOO_MANY_RECORDS -> R.string.restore_err_large
            BackupFormatError.NO_STUDENTS -> R.string.restore_err_empty
            null -> R.string.restore_err_generic
        },
    )
}

@Composable
private fun RestoreConfirmDialog(c: RestoreState.Confirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val s = c.plan.summary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_confirm_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.restore_file_has, s.studentsImported, s.paymentsImported))
                if (s.hasProblems) {
                    Text(stringResource(R.string.restore_problems_title), style = MaterialTheme.typography.titleSmall)
                    if (s.studentsSkipped > 0) Text(stringResource(R.string.restore_p_students, s.studentsSkipped))
                    if (s.paymentsSkippedOrphan > 0) Text(stringResource(R.string.restore_p_orphan, s.paymentsSkippedOrphan))
                    if (s.paymentsSkippedInvalid > 0) Text(stringResource(R.string.restore_p_invalid, s.paymentsSkippedInvalid))
                    if (s.duplicatePaymentsMerged > 0) Text(stringResource(R.string.restore_p_dupes, s.duplicatePaymentsMerged))
                    if (s.datesDefaulted > 0) Text(stringResource(R.string.restore_p_dates, s.datesDefaulted))
                    if (s.feesDefaulted > 0) Text(stringResource(R.string.restore_p_fees, s.feesDefaulted))
                }
                Text(
                    stringResource(R.string.restore_replaces, c.currentStudents, c.currentPayments),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.restore_confirm_action), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun themeLabel(m: ThemeMode): String = stringResource(
    when (m) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun timeoutLabel(t: LockTimeout): String = stringResource(
    when (t) {
        LockTimeout.IMMEDIATE -> R.string.lock_immediately
        LockTimeout.ONE_MINUTE -> R.string.lock_1_min
        LockTimeout.FIVE_MINUTES -> R.string.lock_5_min
    },
)

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

@Composable
private fun Row2(
    index: Int,
    count: Int,
    title: String,
    supporting: String?,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing?.invoke()
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = groupedShape(index, count), color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(), content = content)
    } else {
        Surface(shape = groupedShape(index, count), color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
