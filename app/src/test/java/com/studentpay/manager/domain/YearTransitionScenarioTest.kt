package com.studentpay.manager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The December -> January case, followed through every screen's calculation. */
class YearTransitionScenarioTest {
    private val typeB = Student(1, "Mina", PaymentType.B, 700)
    private val typeA = Student(2, "Rafi", PaymentType.A, 500)

    /** What the repository would store when each student is ticked in January 2027. */
    private fun storedForJanuary2027(s: Student): Payment {
        val t = PaymentRules.targetPeriod(s.type, collectionMonth = 0, collectionYear = 2027)
        return Payment(0, s.id, t.year, t.month, 0, 1_000L)
    }

    @Test fun typeBTickedInJanuaryIsStoredAsPreviousDecember() {
        val p = storedForJanuary2027(typeB)
        assertEquals(Period(2026, 11), p.period)
        assertEquals(0, p.paidOnMonth)
    }

    @Test fun typeATickedInJanuaryIsStoredAsThatJanuary() {
        assertEquals(Period(2027, 0), storedForJanuary2027(typeA).period)
    }

    @Test fun everyCalculationAgreesAboutIt() {
        val payments = listOf(storedForJanuary2027(typeB), storedForJanuary2027(typeA))
        val students = listOf(typeB, typeA)

        // Home / Payments for January 2027: both are paid (B via last year's December).
        val jan = Dashboard.compute(students, payments, 0, 2027)
        assertEquals(2, jan.stats.paidCount)
        assertEquals(1200L, jan.stats.collectedAmount)

        // Collection month December 2026: B would be paying for November (not paid), A for December (not paid).
        val dec = Dashboard.compute(students, payments, 11, 2026)
        assertEquals(0, dec.stats.paidCount)

        // Student report for 2026 shows B's December as paid; for 2027 it shows nothing for B.
        val b2026 = Reports.student(typeB, payments, 2026, Period(2027, 0))
        assertEquals(MonthState.PAID, b2026.months[11].state)
        assertEquals(1, b2026.paidMonths)
        assertEquals(0, Reports.student(typeB, payments, 2027, Period(2027, 0)).paidMonths)

        // Yearly 2026 contains B's December; yearly 2027 contains only A's January.
        assertEquals(700L, Reports.yearly(students, payments, 2026).months[11].amount)
        assertEquals(500L, Reports.yearly(students, payments, 2027).months[0].amount)

        // Monthly report keyed by month paid FOR.
        assertTrue(Reports.monthly(students, payments, 2026, 11).rows[0].isPaid)
        assertFalse(Reports.monthly(students, payments, 2027, 0).rows[0].isPaid)
    }
}
