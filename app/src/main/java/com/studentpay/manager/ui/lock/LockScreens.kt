package com.studentpay.manager.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studentpay.manager.R
import com.studentpay.manager.ui.AppViewModel
import com.studentpay.manager.ui.components.PinEntry
import kotlinx.coroutines.delay

@Composable
private fun LockLayout(
    title: String,
    subtitle: String,
    message: String?,
    messageIsError: Boolean,
    footnote: String?,
    pinEnabled: Boolean,
    onPin: (String) -> Unit,
    bottom: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        PinEntry(enabled = pinEnabled, onComplete = onPin)
        Spacer(Modifier.height(12.dp))
        // Reserve the line so the keypad does not jump when a message appears.
        Text(
            message.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(44.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        bottom()
        if (footnote != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** First run: create a 4-digit PIN (enter, then confirm). */
@Composable
fun PinSetupScreen(onPinCreated: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    LockLayout(
        title = stringResource(if (first == null) R.string.setup_title_create else R.string.setup_title_confirm),
        subtitle = stringResource(if (first == null) R.string.setup_subtitle_create else R.string.setup_subtitle_confirm),
        message = if (mismatch) stringResource(R.string.setup_mismatch) else null,
        messageIsError = true,
        footnote = stringResource(R.string.setup_footnote),
        pinEnabled = true,
        onPin = { entered ->
            val f = first
            when {
                f == null -> { first = entered; mismatch = false }
                f == entered -> onPinCreated(entered)
                else -> { first = null; mismatch = true }
            }
        },
    )
}

/** Lock screen shown when opening the app. */
@Composable
fun UnlockScreen(vm: AppViewModel) {
    val lock by vm.lock.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var forgot by remember { mutableStateOf(false) }

    LaunchedEffect(lock.lockedUntilMillis) {
        while (System.currentTimeMillis() < lock.lockedUntilMillis) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val remainingSec = ((lock.lockedUntilMillis - now + 999) / 1000).coerceAtLeast(0)
    val waiting = remainingSec > 0

    val message = when {
        waiting -> stringResource(R.string.lock_wait, remainingSec / 60, (remainingSec % 60).toString().padStart(2, '0'))
        lock.wrongPin -> stringResource(R.string.lock_wrong, lock.attemptsLeft)
        else -> null
    }
    LockLayout(
        title = stringResource(R.string.lock_title),
        subtitle = stringResource(R.string.lock_subtitle),
        message = message,
        messageIsError = true,
        footnote = null,
        pinEnabled = !lock.busy && !waiting,
        onPin = vm::submitPin,
        bottom = { TextButton(onClick = { forgot = true }) { Text(stringResource(R.string.lock_forgot)) } },
    )
    if (forgot) {
        ForgotPinDialog(onDismiss = { forgot = false }, onReset = { forgot = false; vm.resetEverything() })
    }
}

@Composable
private fun ForgotPinDialog(onDismiss: () -> Unit, onReset: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val word = stringResource(R.string.forgot_word)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forgot_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.forgot_body))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.forgot_type, word)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onReset, enabled = typed.trim() == word) {
                Text(stringResource(R.string.forgot_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
