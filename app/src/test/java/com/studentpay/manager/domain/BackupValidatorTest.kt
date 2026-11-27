package com.studentpay.manager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {
    private val now = 9_000_000_000_000L
    private fun st(id: Long?, name: String? = "Rafi", type: String? = "A", fee: Long? = 500) = RawStudent(id, name, type, fee)
    private fun pay(sid: Long?, y: Int? = 2026, m: Int? = 2, on: Int? = 2, d: Long? = 1000L, amt: Long? = 500L) =
        RawPayment(sid, y, m, on, d, amt)

    @Test fun cleanLegacyDataImportsEverythingAndKeepsIds() {
        val p = BackupValidator.validate(listOf(st(7), st(9, "Mina", "B", 700)), listOf(pay(7), pay(9, m = 1)), now)
        assertEquals(listOf(7L, 9L), p.students.map { it.id })
        assertEquals(2, p.payments.size)
        assertFalse(p.summary.hasProblems)
    }

    @Test fun duplicatePaymentsAreMergedKeepingLatestDate() {
        val p = BackupValidator.validate(listOf(st(1)), listOf(pay(1, d = 100), pay(1, d = 300), pay(1, d = 200)), now)
        assertEquals(1, p.payments.size)
        assertEquals(300L, p.payments[0].paidAtMillis)
        assertEquals(2, p.summary.duplicatePaymentsMerged)
        assertTrue(p.summary.hasProblems)
    }

    @Test fun orphanPaymentsAreSkipped() {
        val p = BackupValidator.validate(listOf(st(1)), listOf(pay(1), pay(99)), now)
        assertEquals(1, p.payments.size)
        assertEquals(1, p.summary.paymentsSkippedOrphan)
    }

    @Test fun invalidMonthYearAndNullsAreSkipped() {
        val p = BackupValidator.validate(
            listOf(st(1)),
            listOf(pay(1, m = 12), pay(1, m = -1), pay(1, y = 1999), pay(1, y = 2101), pay(1, y = null), pay(null)),
            now,
        )
        assertEquals(0, p.payments.size)
        assertEquals(6, p.summary.paymentsSkippedInvalid)
    }

    @Test fun missingDateGetsImportTimeLikeLegacyMigration() {
        val p = BackupValidator.validate(listOf(st(1)), listOf(pay(1, d = null)), now)
        assertEquals(now, p.payments[0].paidAtMillis)
        assertEquals(1, p.summary.datesDefaulted)
    }

    @Test fun missingFeeDefaultsToZeroButNegativeFeeSkipsStudent() {
        val p = BackupValidator.validate(listOf(st(1, fee = null), st(2, fee = -5)), emptyList(), now)
        assertEquals(1, p.students.size)
        assertEquals(0, p.students[0].monthlyFee)
        assertEquals(1, p.summary.feesDefaulted)
        assertEquals(1, p.summary.studentsSkipped)
    }

    @Test fun badStudentsAreSkippedAndTheirPaymentsBecomeOrphans() {
        val p = BackupValidator.validate(
            listOf(st(null), st(0), st(1, name = "  "), st(2, type = "C"), st(3), st(3, "dup")),
            listOf(pay(1), pay(2), pay(3)),
            now,
        )
        assertEquals(listOf(3L), p.students.map { it.id })
        assertEquals(5, p.summary.studentsSkipped)
        assertEquals(2, p.summary.paymentsSkippedOrphan)
        assertEquals(1, p.payments.size)
    }

    @Test fun nameIsTrimmedAndPaidOnMonthRangeChecked() {
        val p = BackupValidator.validate(listOf(st(1, "  Rafi ")), listOf(pay(1, on = 15)), now)
        assertEquals("Rafi", p.students[0].name)
        assertEquals(null, p.payments[0].paidOnMonth)
    }

    @Test fun recordedAmountIsKeptEvenWhenItDiffersFromTheCurrentFee() {
        val p = BackupValidator.validate(listOf(st(1, fee = 1500)), listOf(pay(1, amt = 1000)), now)
        assertEquals(1000, p.payments[0].amount)
        assertEquals(0, p.summary.amountsDefaulted)
    }

    @Test fun missingOrInvalidAmountUsesStudentFeeAndIsReported() {
        val p = BackupValidator.validate(
            listOf(st(1, fee = 700)),
            listOf(pay(1, m = 0, amt = null), pay(1, m = 1, amt = -5), pay(1, m = 2, amt = 99_999_999_999L), pay(1, m = 3, amt = 0)),
            now,
        )
        assertEquals(listOf(700, 700, 700, 0), p.payments.map { it.amount })
        assertEquals(3, p.summary.amountsDefaulted)
        assertTrue(p.summary.hasProblems)
    }

    @Test fun duplicateMergeKeepsTheAmountOfTheKeptRecord() {
        val p = BackupValidator.validate(
            listOf(st(1, fee = 900)),
            listOf(pay(1, d = 100, amt = 400), pay(1, d = 300, amt = null)),
            now,
        )
        assertEquals(900, p.payments[0].amount)          // the later record (no amount) won -> defaulted
        assertEquals(1, p.summary.amountsDefaulted)
        val q = BackupValidator.validate(
            listOf(st(1, fee = 900)),
            listOf(pay(1, d = 100, amt = null), pay(1, d = 300, amt = 400)),
            now,
        )
        assertEquals(400, q.payments[0].amount)
        assertEquals(0, q.summary.amountsDefaulted)
    }

    @Test fun notesAreCapped() {
        val bad = (1..50).map { st(null) }
        val p = BackupValidator.validate(bad, emptyList(), now)
        assertTrue(p.summary.notes.size <= 8)
        assertEquals(50, p.summary.studentsSkipped)
    }
}
