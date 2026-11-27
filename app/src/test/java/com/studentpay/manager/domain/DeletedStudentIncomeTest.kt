package com.studentpay.manager.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deleting a student hides them, but the income already received from them must stay everywhere. */
class DeletedStudentIncomeTest {
    private val utc = ZoneId.of("UTC")
    private val active = Student(1, "Rafi", PaymentType.A, 500)
    private val gonePaid = Student(2, "Mina", PaymentType.A, 700, deletedAtMillis = 5_000L)
    private val goneNever = Student(3, "Tanvir", PaymentType.A, 900, deletedAtMillis = 6_000L)
    private val goneB = Student(4, "Sara", PaymentType.B, 400, deletedAtMillis = 7_000L)
    private val march5 = 1_772_668_800_000L

    private fun pay(s: Student, y: Int, m: Int, amount: Int = s.monthlyFee) = Payment(0, s.id, y, m, null, march5, amount)

    private val payments = listOf(pay(active, 2026, 2), pay(gonePaid, 2026, 2, amount = 650), pay(goneB, 2026, 1))

    @Test fun deletedFlagIsDerivedFromTimestamp() {
        assertFalse(active.isDeleted)
        assertTrue(gonePaid.isDeleted)
    }

    @Test fun dashboardIncomeIncludesDeletedStudentsButCountsDoNot() {
        // Collection month March 2026: active A pays for Mar, deleted A for Mar, deleted B for Feb.
        val r = Dashboard.compute(listOf(active), payments, 2, 2026, deletedStudents = listOf(gonePaid, goneNever, goneB))
        assertEquals(1, r.stats.totalStudents)           // deleted students are not listed or counted
        assertEquals(1, r.stats.paidCount)
        assertEquals(0, r.stats.dueCount)
        assertEquals(1, r.rows.size)
        assertEquals(500L + 650L + 400L, r.stats.collectedAmount)   // recorded amounts, incl. deleted students
        assertEquals(650L + 400L, r.stats.fromDeletedStudents)
    }

    @Test fun dashboardWithoutDeletedListIsUnchanged() {
        val r = Dashboard.compute(listOf(active), payments, 2, 2026)
        assertEquals(500L, r.stats.collectedAmount)
        assertEquals(0L, r.stats.fromDeletedStudents)
    }

    @Test fun monthlyReportKeepsDeletedPayersAndNeverMarksDeletedAsDue() {
        val r = Reports.monthly(listOf(active, gonePaid, goneNever), payments, 2026, 2)
        assertEquals(listOf("Rafi", "Mina"), r.rows.map { it.student.name })   // Tanvir (deleted, unpaid) is omitted
        assertEquals(2, r.paidCount)
        assertEquals(0, r.dueCount)
        assertEquals(1150L, r.collectedAmount)
        val spec = ReportSpecs.monthly(r, utc)
        assertEquals("Mina (deleted)", spec.rows[1][0])
        assertEquals("Tk 650", spec.rows[1][3])
        assertEquals("Total Collected: Tk 1,150", spec.summaryLines.last())
    }

    @Test fun activeUnpaidStudentIsStillDueNextToDeletedOnes() {
        val r = Reports.monthly(listOf(active, gonePaid), listOf(pay(gonePaid, 2026, 2)), 2026, 2)
        assertEquals(1, r.dueCount)
        assertEquals(1, r.paidCount)
    }

    @Test fun yearlyReportKeepsIncomeOfDeletedStudents() {
        // The yearly report only needs payments, so deleting a student cannot change it.
        val y = Reports.yearly(listOf(active), payments, 2026)
        assertEquals(500L + 650L, y.months[2].amount)
        assertEquals(400L, y.months[1].amount)
        assertEquals(1550L, y.grandTotal)
        assertEquals(3, y.recordCount)
    }
}
