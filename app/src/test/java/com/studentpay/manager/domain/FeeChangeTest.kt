package com.studentpay.manager.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fee changes must never rewrite history: January was paid at 1,000; in March the fee becomes 1,500.
 */
class FeeChangeTest {
    private val before = Student(1, "Rafi", PaymentType.A, 1000)
    private val after = before.copy(monthlyFee = 1500)
    private val utc = ZoneId.of("UTC")

    // What the repository records: the fee at the moment of ticking.
    private val payments = listOf(
        Payment(0, 1, 2026, 0, 0, 1_769_904_000_000L, before.monthlyFee), // Jan, fee was 1,000
        Payment(0, 1, 2026, 2, 2, 1_772_668_800_000L, after.monthlyFee),  // Mar, fee is now 1,500
    )

    @Test fun studentReportKeepsJanuaryAt1000() {
        val r = Reports.student(after, payments, 2026, Period(2026, 3))
        assertEquals(1000, r.months[0].amount)
        assertEquals(1500, r.months[2].amount)
        assertEquals(2500L, r.totalAmount)
        val spec = ReportSpecs.student(r, "01/04/2026", utc)
        assertEquals("Tk 1,000", spec.rows[0][3])
        assertEquals("Tk 1,500", spec.rows[2][3])
        assertEquals("Current Monthly Fee: Tk 1,500", spec.infoLines[1])
    }

    @Test fun monthlyAndYearlyReportsUseRecordedAmounts() {
        assertEquals(1000L, Reports.monthly(listOf(after), payments, 2026, 0).collectedAmount)
        assertEquals(1500L, Reports.monthly(listOf(after), payments, 2026, 2).collectedAmount)
        val y = Reports.yearly(listOf(after), payments, 2026)
        assertEquals(1000L, y.months[0].amount)
        assertEquals(1500L, y.months[2].amount)
        assertEquals(2500L, y.grandTotal)
    }

    @Test fun dashboardCollectedIsRecordedNotCurrent() {
        val jan = Dashboard.compute(listOf(after), payments, 0, 2026)
        assertEquals(1000L, jan.stats.collectedAmount)
        assertEquals(1000, jan.rows[0].amount)
    }

    @Test fun unpaidMonthShowsWhatTickingWouldRecord() {
        val feb = Dashboard.compute(listOf(after), payments, 1, 2026)
        assertEquals(0L, feb.stats.collectedAmount)
        assertEquals(1500, feb.rows[0].amount) // the current fee: what will be recorded if ticked now
    }

    @Test fun changingTheFeeBackAndForthNeverMovesHistory() {
        val raised = Reports.yearly(listOf(after.copy(monthlyFee = 9999)), payments, 2026)
        assertEquals(2500L, raised.grandTotal)
        val cut = Reports.yearly(listOf(after.copy(monthlyFee = 1)), payments, 2026)
        assertEquals(2500L, cut.grandTotal)
    }
}
