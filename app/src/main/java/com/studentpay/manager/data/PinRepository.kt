package com.studentpay.manager.data

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.studentpay.manager.domain.LockoutPolicy
import com.studentpay.manager.domain.PinHasher
import com.studentpay.manager.domain.PinPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface PinCheck {
    data object Success : PinCheck
    /** Wrong PIN. [lockedForMs] > 0 when this failure started a temporary wait. */
    data class Wrong(val attemptsLeft: Int, val lockedForMs: Long) : PinCheck
    /** Temporarily locked after repeated failures. Never permanent. */
    data class Locked(val remainingMs: Long) : PinCheck
    /** No usable PIN is stored (first run, or the Keystore key is gone). */
    data object NotSet : PinCheck
}

/**
 * Stores only a salted PBKDF2 hash of the PIN, wrapped with a Keystore key. The PIN is never stored or logged.
 * The store lives in noBackupFilesDir, so it is excluded from Auto Backup / device transfer (the Keystore key
 * would not exist on a new device anyway).
 *
 * Threat model: this protects against casual access by someone holding the unlocked phone. It is not
 * server-grade security and does not try to resist a rooted device or a debugger.
 */
class PinRepository(
    private val store: DataStore<Preferences>,
    private val crypto: KeystoreCrypto,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private object Keys {
        val SALT = stringPreferencesKey("salt")
        val ITERATIONS = intPreferencesKey("iterations")
        val WRAPPED = stringPreferencesKey("wrapped_hash")
        val FAILURES = intPreferencesKey("failures")
        val LOCK_UNTIL = longPreferencesKey("lock_until_ms")
    }

    private val mutex = Mutex()

    val hasPin: Flow<Boolean> = store.data.map { it[Keys.WRAPPED] != null }

    suspend fun lockRemainingMs(): Long {
        val until = store.data.first()[Keys.LOCK_UNTIL] ?: 0L
        return (until - clock()).coerceIn(0L, MAX_WAIT_MS)
    }

    /** Sets (or replaces) the PIN. The caller must already have verified the old one when changing. */
    suspend fun setPin(pin: String) {
        require(PinPolicy.isValid(pin)) { "PIN must be exactly 4 digits" }
        mutex.withLock {
            val salt = PinHasher.newSalt()
            val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin.toCharArray(), salt, ITERATIONS) }
            val wrapped = withContext(Dispatchers.Default) { crypto.encrypt(hash) }
            store.edit {
                it[Keys.SALT] = salt.b64()
                it[Keys.ITERATIONS] = ITERATIONS
                it[Keys.WRAPPED] = wrapped.b64()
                it[Keys.FAILURES] = 0
                it[Keys.LOCK_UNTIL] = 0L
            }
        }
    }

    suspend fun verify(pin: String): PinCheck = mutex.withLock {
        val prefs = store.data.first()
        val wrapped = prefs[Keys.WRAPPED] ?: return@withLock PinCheck.NotSet
        val now = clock()
        val lockUntil = prefs[Keys.LOCK_UNTIL] ?: 0L
        if (now < lockUntil) return@withLock PinCheck.Locked((lockUntil - now).coerceAtMost(MAX_WAIT_MS))

        if (!PinPolicy.isValid(pin)) return@withLock PinCheck.Wrong(LockoutPolicy.attemptsLeftInBlock(prefs[Keys.FAILURES] ?: 0), 0L)

        val expected = try {
            withContext(Dispatchers.Default) { crypto.decrypt(wrapped.unb64()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keystore key lost/invalidated or data corrupted: the verifier is unusable. Start over.
            store.edit { it.clear() }
            return@withLock PinCheck.NotSet
        }
        val salt = (prefs[Keys.SALT] ?: return@withLock PinCheck.NotSet).unb64()
        val iterations = prefs[Keys.ITERATIONS] ?: ITERATIONS
        val actual = withContext(Dispatchers.Default) { PinHasher.hash(pin.toCharArray(), salt, iterations) }

        if (PinHasher.matches(expected, actual)) {
            store.edit { it[Keys.FAILURES] = 0; it[Keys.LOCK_UNTIL] = 0L }
            PinCheck.Success
        } else {
            val failures = (prefs[Keys.FAILURES] ?: 0) + 1
            val delay = LockoutPolicy.delayAfterFailure(failures)
            store.edit {
                it[Keys.FAILURES] = failures
                it[Keys.LOCK_UNTIL] = if (delay > 0) now + delay else 0L
            }
            PinCheck.Wrong(LockoutPolicy.attemptsLeftInBlock(failures), delay)
        }
    }

    /** Verifies [old]; on success installs [new]. */
    suspend fun changePin(old: String, new: String): PinCheck {
        val check = verify(old)
        if (check is PinCheck.Success) setPin(new)
        return check
    }

    /** Forgets the PIN and deletes the Keystore key ("Forgot PIN" reset, after the data was erased). */
    suspend fun clear() {
        mutex.withLock {
            store.edit { it.clear() }
            crypto.deleteKey()
        }
    }

    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.unb64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        /**
         * Stored with the hash so it can be raised later. 200k keeps verification well under a second on
         * low-end phones; the Keystore wrapping (not the KDF) is what protects a 4-digit PIN offline.
         */
        const val ITERATIONS = 200_000
        const val MAX_WAIT_MS = 10 * 60_000L
    }
}
