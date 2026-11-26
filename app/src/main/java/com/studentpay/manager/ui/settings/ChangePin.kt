package com.studentpay.manager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studentpay.manager.R
import com.studentpay.manager.data.PinCheck
import com.studentpay.manager.ui.AppViewModel
import com.studentpay.manager.ui.components.AppTopBar
import com.studentpay.manager.ui.components.PinEntry

private enum class Step { CURRENT, NEW, CONFIRM, DONE }

/** Change PIN: verify the current PIN first, then enter and confirm the new one. */
@Composable
fun ChangePinScreen(vm: AppViewModel, onBack: () -> Unit) {
    var step by remember { mutableStateOf(Step.CURRENT) }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val waitText = stringResource(R.string.change_pin_wait)
    val mismatchText = stringResource(R.string.setup_mismatch)
    // Precompute (composable calls cannot happen inside callbacks).
    val wrongTexts = (0..5).map { stringResource(R.string.lock_wrong, it) }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.settings_change_pin), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(
                    when (step) {
                        Step.CURRENT -> R.string.change_pin_current
                        Step.NEW -> R.string.change_pin_new
                        Step.CONFIRM -> R.string.change_pin_confirm
                        Step.DONE -> R.string.change_pin_done
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            if (step == Step.DONE) {
                Text(stringResource(R.string.change_pin_done_body), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.action_done))
                }
            } else {
                PinEntry(
                    enabled = !busy,
                    onComplete = { entered ->
                        error = null
                        when (step) {
                            Step.CURRENT -> {
                                busy = true
                                vm.verifyCurrentPin(entered) { result ->
                                    busy = false
                                    when (result) {
                                        PinCheck.Success -> step = Step.NEW
                                        is PinCheck.Wrong -> error =
                                            if (result.lockedForMs > 0) waitText else wrongTexts[result.attemptsLeft.coerceIn(0, 5)]
                                        is PinCheck.Locked -> error = waitText
                                        PinCheck.NotSet -> onBack()
                                    }
                                }
                            }
                            Step.NEW -> { newPin = entered; step = Step.CONFIRM }
                            Step.CONFIRM -> {
                                if (entered == newPin) {
                                    busy = true
                                    vm.setNewPin(entered) { busy = false; newPin = ""; step = Step.DONE }
                                } else {
                                    newPin = ""
                                    step = Step.NEW
                                    error = mismatchText
                                }
                            }
                            Step.DONE -> Unit
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.height(44.dp),
                )
            }
        }
    }
}
