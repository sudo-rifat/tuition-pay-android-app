package com.studentpay.manager.domain

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN format policy: exactly four ASCII digits. */
object PinPolicy {
    const val LENGTH = 4

    fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }
}

/**
 * Temporary lock-out after repeated wrong PINs. Never permanent: the wait is capped, and the
 * "Forgot PIN" path always exists.
 *
 * After every [ATTEMPTS_PER_BLOCK]-th consecutive failure the user must wait:
 * 30 s, 60 s, 120 s, ... capped at 10 min. A correct PIN resets the counter.
 */
object LockoutPolicy {
    const val ATTEMPTS_PER_BLOCK = 5
    private const val BASE_DELAY_MS = 30_000L
    private const val MAX_DELAY_MS = 10 * 60_000L

    /** Wait imposed right after the [totalFailures]-th consecutive failure (0 if none). */
    fun delayAfterFailure(totalFailures: Int): Long {
        if (totalFailures <= 0 || totalFailures % ATTEMPTS_PER_BLOCK != 0) return 0L
        val block = totalFailures / ATTEMPTS_PER_BLOCK // 1, 2, 3 ...
        val shift = (block - 1).coerceAtMost(10)
        return (BASE_DELAY_MS shl shift).coerceAtMost(MAX_DELAY_MS)
    }

    /** Tries left before the next wait begins. */
    fun attemptsLeftInBlock(totalFailures: Int): Int =
        ATTEMPTS_PER_BLOCK - (totalFailures % ATTEMPTS_PER_BLOCK)
}

/**
 * PBKDF2-HMAC-SHA256 hashing of the PIN. Pure JVM (javax.crypto) so it is unit-testable.
 * The result is additionally wrapped with an Android Keystore key by the data layer (device binding),
 * because a 4-digit PIN has far too little entropy to be protected by a KDF alone.
 */
object PinHasher {
    private const val KEY_BITS = 256
    const val SALT_BYTES = 16

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison. */
    fun matches(expected: ByteArray, actual: ByteArray): Boolean = MessageDigest.isEqual(expected, actual)
}
