package com.studentpay.manager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studentpay.manager.data.AppSettings
import com.studentpay.manager.data.PinCheck
import com.studentpay.manager.data.PinRepository
import com.studentpay.manager.data.SettingsRepository
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.pdf.ReportService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Gate { LOADING, SETUP, LOCKED, UNLOCKED }

data class LockUiState(
    val busy: Boolean = false,
    val wrongPin: Boolean = false,
    val attemptsLeft: Int = 0,
    /** Wall-clock millis until which entry is disabled (0 = not waiting). */
    val lockedUntilMillis: Long = 0L,
)

/**
 * App-wide gate: PIN setup -> lock screen -> app. "Unlocked" lives only in memory, so a killed
 * process always starts locked.
 */
class AppViewModel(
    private val pin: PinRepository,
    settingsRepository: SettingsRepository,
    private val repository: StudentPayRepository,
    private val reports: ReportService,
) : ViewModel() {

    private val unlocked = MutableStateFlow(false)

    val gate: StateFlow<Gate> = combine(pin.hasPin, unlocked) { hasPin, isUnlocked ->
        when {
            !hasPin -> Gate.SETUP
            isUnlocked -> Gate.UNLOCKED
            else -> Gate.LOCKED
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Gate.LOADING)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _lock = MutableStateFlow(LockUiState())
    val lock: StateFlow<LockUiState> = _lock.asStateFlow()

    private var stoppedAt: Long? = null

    init {
        viewModelScope.launch {
            val remaining = pin.lockRemainingMs()
            if (remaining > 0) _lock.value = LockUiState(lockedUntilMillis = System.currentTimeMillis() + remaining)
        }
    }

    // ---- background auto-lock ----

    fun onAppStopped(elapsedRealtime: Long) {
        stoppedAt = elapsedRealtime
    }

    fun onAppStarted(elapsedRealtime: Long) {
        val stopped = stoppedAt ?: return
        stoppedAt = null
        if (unlocked.value && elapsedRealtime - stopped >= settings.value.lockTimeout.millis) {
            unlocked.value = false
        }
    }

    fun lockNow() {
        unlocked.value = false
    }

    // ---- PIN flows ----

    fun submitPin(entered: String) {
        if (_lock.value.busy) return
        viewModelScope.launch {
            _lock.value = _lock.value.copy(busy = true)
            val now = System.currentTimeMillis()
            _lock.value = when (val r = pin.verify(entered)) {
                PinCheck.Success -> {
                    unlocked.value = true
                    LockUiState()
                }
                is PinCheck.Wrong -> LockUiState(
                    wrongPin = true,
                    attemptsLeft = r.attemptsLeft,
                    lockedUntilMillis = if (r.lockedForMs > 0) now + r.lockedForMs else 0L,
                )
                is PinCheck.Locked -> LockUiState(lockedUntilMillis = now + r.remainingMs)
                PinCheck.NotSet -> LockUiState()
            }
        }
    }

    fun createPin(newPin: String) {
        viewModelScope.launch {
            pin.setPin(newPin)
            unlocked.value = true
        }
    }

    /** Checks the current PIN without changing any state (first step of "Change PIN"). */
    fun verifyCurrentPin(entered: String, onResult: (PinCheck) -> Unit) {
        viewModelScope.launch { onResult(pin.verify(entered)) }
    }

    fun setNewPin(newPin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            pin.setPin(newPin)
            onDone()
        }
    }

    /** "Forgot PIN": erase all data and the PIN, then the user sets up a new PIN (and may restore a backup). */
    fun resetEverything() {
        viewModelScope.launch {
            try {
                repository.eraseAll()
                reports.clearCache()
                pin.clear()
                unlocked.value = false
                _lock.value = LockUiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave the app locked; nothing was half-applied that matters (PIN is cleared last).
            }
        }
    }
}
