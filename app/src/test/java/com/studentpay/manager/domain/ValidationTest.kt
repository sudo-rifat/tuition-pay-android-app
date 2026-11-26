package com.studentpay.manager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test fun validStudentIsTrimmed() {
        val r = StudentValidator.validate("  Rafi Ahmed ", " 1500 ")
        assertTrue(r.isValid)
        assertEquals("Rafi Ahmed", r.name)
        assertEquals(1500, r.fee)
    }

    @Test fun blankNameRejected() {
        assertEquals(NameError.EMPTY, StudentValidator.validate("   ", "10").nameError)
    }

    @Test fun overlongNameRejected() {
        assertEquals(NameError.TOO_LONG, StudentValidator.validate("x".repeat(81), "10").nameError)
        assertNull(StudentValidator.validate("x".repeat(80), "10").nameError)
    }

    @Test fun bengaliNameAccepted() {
        assertTrue(StudentValidator.validate("রাফি আহমেদ", "500").isValid)
    }

    @Test fun feeRules() {
        assertEquals(FeeError.EMPTY, StudentValidator.validate("A", "").feeError)
        assertEquals(FeeError.NOT_A_NUMBER, StudentValidator.validate("A", "12.5").feeError)
        assertEquals(FeeError.NOT_A_NUMBER, StudentValidator.validate("A", "-5").feeError)
        assertEquals(FeeError.NOT_A_NUMBER, StudentValidator.validate("A", "৫০০").feeError) // Bengali digits are not accepted
        assertEquals(FeeError.TOO_LARGE, StudentValidator.validate("A", "10000001").feeError)
        assertEquals(FeeError.TOO_LARGE, StudentValidator.validate("A", "99999999999999999999").feeError)
        assertEquals(0, StudentValidator.validate("A", "0").fee)
        assertEquals(10_000_000, StudentValidator.validate("A", "10000000").fee)
        assertEquals(7, StudentValidator.validate("A", "000007").fee)
    }
}
