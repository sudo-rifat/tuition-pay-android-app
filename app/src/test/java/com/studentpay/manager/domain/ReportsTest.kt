package com.studentpay.manager.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsTest {
    private val utc = ZoneId.of("UTC")
    private val s1 = Student(1, "Rafi", PaymentType.A, 500)
    private val s2 = Student(2, "Mina", PaymentType.B, 700)
    // 2026-03-05T00:00:00Z
    private val march5 = 1_772_668_800_000L
    private fun pay(sid: Long, y: Int, m: Int, t: Long = march5) =
        Payment(0, sid, y, m, null, t, if (sid == 1L) 500 else 700)

    @Test fun studentReportCountsPaidAndDueForTheYear() {
        val payments = listOf(pay(1, 2026, 0), pay(1, 2026, 1), pay(1, 2025, 5), pay(2, 2026, 0))
        val r = Reports.student(s1, payments, 2026, Period(2026, 3)) // today = April 2026
        assertEquals(2, r.paidMonths)
        assertEquals(2, r.dueMonths)         // Mar, Apr unpaid; May.. are future
        assertEquals(1000L, r.totalAmount)
        assertEquals(MonthState.FUTURE, r.months[11].state)
    }

    @Test fun studentReportIgnoresOtherYearsAndStudents() {
        val payments = listOf(pay(1, 2025, 5), pay(2, 2026, 0))
        val r = Reports.student(s1, payments, 2026, Period(2026, 0))
        assertEquals(0, r.paidMonths)
    }

    @Test fun monthlyReportListsEveryStudentWithPaidAndDue() {
        val payments = listOf(pay(1, 2026, 2), pay(2, 2026, 3))
        val r = Reports.monthly(listOf(s1, s2), payments, 2026, 2)
        assertEquals(1, r.paidCount)
        assertEquals(1, r.dueCount)
        assertEquals(500L, r.collectedAmount)
        assertTrue(r.rows[0].isPaid)
        assertTrue(!r.rows[1].isPaid)
    }

    @Test fun yearlyReportBreaksDownByMonthAndTotals() {
        val payments = listOf(pay(1, 2026, 0), pay(2, 2026, 0), pay(1, 2026, 4), pay(1, 2025, 4))
        val r = Reports.yearly(listOf(s1, s2), payments, 2026)
        assertEquals(2, r.months[0].paymentCount)
        assertEquals(1200L, r.months[0].amount)
        assertEquals(500L, r.months[4].amount)
        assertEquals(1700L, r.grandTotal)
        assertEquals(3, r.recordCount)
    }

    @Test fun specsContainExpectedText() {
        val payments = listOf(pay(1, 2026, 2))
        val m = ReportSpecs.monthly(Reports.monthly(listOf(s1, s2), payments, 2026, 2), utc)
        assertEquals("March 2026", m.subtitle)
        assertEquals(listOf("Rafi", "Paid", "05/03/2026", "Tk 500"), m.rows[0])
        assertEquals(listOf("Mina", "Due", "-", "-"), m.rows[1])
        assertEquals("Total Collected: Tk 500", m.summaryLines.last())
        assertEquals("report_monthly_2026_03", m.fileStem)
        val y = ReportSpecs.yearly(Reports.yearly(listOf(s1), listOf(pay(1, 2026, 0)), 2026))
        assertEquals(12, y.rows.size)
        assertEquals("Grand Total for 2026: Tk 500", y.summaryLines[0])
    }

    @Test fun typeLabelsUseCurrentAndPreviousMonthWording() {
        assertEquals("Current Month", ReportFormat.typeLabel(PaymentType.A))
        assertEquals("Previous Month", ReportFormat.typeLabel(PaymentType.B))
        val spec = ReportSpecs.student(Reports.student(s2, emptyList(), 2026, Period(2026, 0)), "01/01/2026", utc)
        assertEquals("Payment Type: Previous Month", spec.infoLines[2])
    }

    @Test fun moneyFormatsWithThousandsSeparator() {
        assertEquals("Tk 1,234,567", ReportFormat.money(1_234_567))
        assertEquals("Tk 0", ReportFormat.money(0))
    }
}
