package com.studentpay.manager.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Collected" = money received in the period. "Actual" = fees that belong to the period. */
class CollectedReportsTest {
    private val utc = ZoneId.of("UTC")
    private val a = Student(1, "Rafi", PaymentType.A, 500)
    private val b = Student(2, "Mina", PaymentType.B, 700)
    private val gone = Student(3, "Sara", PaymentType.B, 400, deletedAtMillis = 9L)
    private val t = 1_772_668_800_000L // 2026-03-05

    /** What the repository stores when [s] is ticked in collection month [m] of [y]. */
    private fun record(s: Student, m: Int, y: Int, amount: Int = s.monthlyFee): Payment {
        val target = PaymentRules.targetPeriod(s.type, m, y)
        return Payment(0, s.id, target.year, target.month, m, t, amount)
    }

    // ---- collection period ----
    @Test fun collectionPeriodIsRecoveredFromTheStoredPayment() {
        assertEquals(Period(2026, 2), record(a, 2, 2026).collectionPeriod(PaymentType.A))
        assertEquals(Period(2026, 2), record(b, 2, 2026).collectionPeriod(PaymentType.B))   // for Feb, received March
        assertEquals(Period(2027, 0), record(b, 0, 2027).collectionPeriod(PaymentType.B))   // for Dec 2026, received Jan 2027
        assertEquals(Period(2026, 11), record(b, 11, 2026).collectionPeriod(PaymentType.B)) // for Nov, received Dec
        // an old Type B payment stays correct even if the student is switched to Type A afterwards
        assertEquals(Period(2027, 0), record(b, 0, 2027).collectionPeriod(PaymentType.A))
    }

    @Test fun collectionPeriodFallsBackToTheStudentTypeWhenNotStored() {
        val old = Payment(0, 2, 2026, 11, null, t, 700)
        assertEquals(Period(2027, 0), old.collectionPeriod(PaymentType.B))
        assertEquals(Period(2026, 11), old.collectionPeriod(PaymentType.A))
        val odd = Payment(0, 2, 2026, 2, 7, t, 700)   // impossible combination -> fallback
        assertEquals(Period(2026, 3), odd.collectionPeriod(PaymentType.B))
    }

    // ---- the scenario where the two bases differ ----
    @Test fun typeBDecemberFeeReceivedInJanuaryBelongsToDifferentYearsInEachBasis() {
        val p = listOf(record(b, 0, 2027, 700))
        // Collected: January 2027 / year 2027
        assertEquals(700L, Reports.monthlyCollected(listOf(b), p, 2027, 0).collectedAmount)
        assertEquals(700L, Reports.yearlyCollected(listOf(b), p, 2027).months[0].amount)
        assertEquals(0L, Reports.yearlyCollected(listOf(b), p, 2026).grandTotal)
        // Actual: it is December 2026's fee
        assertEquals(0L, Reports.monthly(listOf(b), p, 2027, 0).collectedAmount)
        assertEquals(700L, Reports.monthly(listOf(b), p, 2026, 11).collectedAmount)
        assertEquals(700L, Reports.yearly(listOf(b), p, 2026).months[11].amount)
        assertEquals(0L, Reports.yearly(listOf(b), p, 2027).grandTotal)
    }

    // ---- monthly collected ----
    @Test fun monthlyCollectedMatchesTheHomeDashboardRules() {
        // March 2026: A pays for March, B pays for February, deleted B (Sara) paid too; nobody else.
        val payments = listOf(record(a, 2, 2026), record(b, 2, 2026, amount = 650), record(gone, 2, 2026))
        val r = Reports.monthlyCollected(listOf(a, b, gone), payments, 2026, 2)
        assertEquals(listOf("Rafi", "Mina", "Sara"), r.rows.map { it.student.name })
        assertEquals(3, r.paidCount); assertEquals(0, r.dueCount)
        assertEquals(500L + 650L + 400L, r.collectedAmount)
        assertEquals(Period(2026, 1), r.rows[1].forPeriod)      // Mina's fee is for February
        val dash = Dashboard.compute(listOf(a, b), payments, 2, 2026, listOf(gone))
        assertEquals(dash.stats.collectedAmount, r.collectedAmount)   // report == Home
    }

    @Test fun monthlyCollectedListsUnpaidActiveStudentsAsDueAndHidesUnpaidDeletedOnes() {
        val r = Reports.monthlyCollected(listOf(a, b, gone), listOf(record(a, 2, 2026)), 2026, 2)
        assertEquals(listOf("Rafi", "Mina"), r.rows.map { it.student.name })
        assertEquals(1, r.paidCount); assertEquals(1, r.dueCount)
        assertEquals(Period(2026, 1), r.rows[1].forPeriod)      // what Mina would be paying for
        assertNull(r.rows[1].amount)
    }

    @Test fun monthlyCollectedSpec() {
        val r = Reports.monthlyCollected(listOf(a, b, gone), listOf(record(a, 2, 2026), record(gone, 2, 2026)), 2026, 2)
        val s = ReportSpecs.monthlyCollected(r, utc)
        assertEquals("Collected in March 2026", s.subtitle)
        assertEquals(listOf("Rafi", "Mar 2026", "Paid", "05/03/2026", "Tk 500"), s.rows[0])
        assertEquals(listOf("Mina", "Feb 2026", "Due", "-", "-"), s.rows[1])
        assertEquals("Sara (deleted)", s.rows[2][0])
        assertEquals("Total Collected: Tk 900", s.summaryLines.last())
        assertEquals("report_monthly_collected_2026_03", s.fileStem)
        assertEquals(ReportBasisText.COLLECTED, s.infoLines[0])
    }

    // ---- yearly collected ----
    @Test fun yearlyCollectedGroupsByMonthReceived() {
        val payments = listOf(record(a, 0, 2026), record(b, 0, 2026, 700), record(a, 4, 2026), record(a, 4, 2025))
        val y = Reports.yearlyCollected(listOf(a, b), payments, 2026)
        assertEquals(2, y.months[0].paymentCount)          // Jan 2026: A (for Jan) and B (for Dec 2025)
        assertEquals(1200L, y.months[0].amount)
        assertEquals(500L, y.months[4].amount)
        assertEquals(1700L, y.grandTotal)
        assertEquals(3, y.recordCount)
        val spec = ReportSpecs.yearlyCollected(y)
        assertEquals("Grand Total Collected in 2026: Tk 1,700", spec.summaryLines[0])
        assertEquals("report_yearly_collected_2026", spec.fileStem)
    }

    // ---- student collected ----
    @Test fun studentCollectedListsWhatWasReceivedInTheYearInOrder() {
        val payments = listOf(record(b, 3, 2026, 650), record(b, 0, 2026), record(b, 0, 2027), record(a, 1, 2026))
        val r = Reports.studentCollected(b, payments, 2026)
        assertEquals(listOf(Period(2026, 0), Period(2026, 3)), r.rows.map { it.collectedIn })
        assertEquals(listOf(Period(2025, 11), Period(2026, 2)), r.rows.map { it.forPeriod })
        assertEquals(1350L, r.totalAmount)
        val spec = ReportSpecs.studentCollected(r, "01/04/2026", utc)
        assertEquals(listOf("January", "Dec 2025", "05/03/2026", "Tk 700"), spec.rows[0])
        assertEquals("Total Received: Tk 1,350", spec.summaryLines[1])
        assertEquals("report_student_collected_2_2026", spec.fileStem)
    }

    @Test fun actualReportsNowStateTheirBasis() {
        val spec = ReportSpecs.monthly(Reports.monthly(listOf(a), emptyList(), 2026, 2), utc)
        assertEquals(ReportBasisText.ACTUAL, spec.infoLines[0])
        assertEquals(ReportBasisText.ACTUAL, ReportSpecs.yearly(Reports.yearly(listOf(a), emptyList(), 2026)).infoLines[0])
    }

    // ---- amount parsing for "change the amount when recording" ----
    @Test fun paymentAmountParsing() {
        assertEquals(1500, PaymentAmount.parse("1500"))
        assertEquals(0, PaymentAmount.parse("0"))
        assertEquals(250, PaymentAmount.parse(" 250 "))
        assertNull(PaymentAmount.parse(""))
        assertNull(PaymentAmount.parse("12.5"))
        assertNull(PaymentAmount.parse("-5"))
        assertNull(PaymentAmount.parse("৫০০"))
        assertNull(PaymentAmount.parse("10000001"))
        assertFalse(PaymentAmount.parse("abc") != null)
        assertTrue(PaymentAmount.parse("10000000") == 10_000_000)
    }
}
