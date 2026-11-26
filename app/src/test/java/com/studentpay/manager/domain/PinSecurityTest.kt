package com.studentpay.manager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSecurityTest {
    @Test fun pinPolicyIsExactlyFourAsciiDigits() {
        assertTrue(PinPolicy.isValid("0000"))
        assertTrue(PinPolicy.isValid("1234"))
        assertFalse(PinPolicy.isValid("123"))
        assertFalse(PinPolicy.isValid("12345"))
        assertFalse(PinPolicy.isValid("12a4"))
        assertFalse(PinPolicy.isValid(""))
        assertFalse(PinPolicy.isValid("১২৩৪")) // Bengali digits
        assertFalse(PinPolicy.isValid("12 4"))
    }

    @Test fun hashIsDeterministicForSameSaltAndDifferentOtherwise() {
        val salt = ByteArray(16) { it.toByte() }
        val h1 = PinHasher.hash("1234".toCharArray(), salt, 1000)
        val h2 = PinHasher.hash("1234".toCharArray(), salt, 1000)
        val h3 = PinHasher.hash("1235".toCharArray(), salt, 1000)
        val h4 = PinHasher.hash("1234".toCharArray(), ByteArray(16) { 9 }, 1000)
        assertEquals(32, h1.size)
        assertTrue(PinHasher.matches(h1, h2))
        assertFalse(PinHasher.matches(h1, h3))
        assertFalse(PinHasher.matches(h1, h4))
    }

    @Test fun saltsAreRandom() {
        assertFalse(PinHasher.newSalt().contentEquals(PinHasher.newSalt()))
    }

    @Test fun lockoutIsTemporaryAndCapped() {
        assertEquals(0L, LockoutPolicy.delayAfterFailure(0))
        assertEquals(0L, LockoutPolicy.delayAfterFailure(4))
        assertEquals(30_000L, LockoutPolicy.delayAfterFailure(5))
        assertEquals(0L, LockoutPolicy.delayAfterFailure(6))
        assertEquals(60_000L, LockoutPolicy.delayAfterFailure(10))
        assertEquals(120_000L, LockoutPolicy.delayAfterFailure(15))
        assertEquals(600_000L, LockoutPolicy.delayAfterFailure(500)) // cap: 10 minutes, never permanent
        assertEquals(600_000L, LockoutPolicy.delayAfterFailure(5 * 1000))
    }

    @Test fun attemptsLeft() {
        assertEquals(5, LockoutPolicy.attemptsLeftInBlock(0))
        assertEquals(1, LockoutPolicy.attemptsLeftInBlock(4))
        assertEquals(5, LockoutPolicy.attemptsLeftInBlock(5))
        assertEquals(4, LockoutPolicy.attemptsLeftInBlock(6))
    }
}
