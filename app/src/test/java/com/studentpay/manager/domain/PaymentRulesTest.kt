package com.studentpay.manager.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Golden values worked out by hand from legacy app.js getTargetMonthIndex / getTargetYear. */
class PaymentRulesTest {

    @Test fun typeA_paysForTheSameMonthAndYear() {
        for (m in 0..11) {
            assertEquals(m, PaymentRules.targetMonth(PaymentType.A, m))
            assertEquals(2026, PaymentRules.targetYear(PaymentType.A, m, 2026))
        }
    }

    @Test fun typeB_paysForThePreviousMonth() {
        assertEquals(0, PaymentRules.targetMonth(PaymentType.B, 1))   // collected Feb -> Jan
        assertEquals(10, PaymentRules.targetMonth(PaymentType.B, 11)) // collected Dec -> Nov
        assertEquals(2026, PaymentRules.targetYear(PaymentType.B, 1, 2026))
        assertEquals(2026, PaymentRules.targetYear(PaymentType.B, 11, 2026))
    }

    @Test fun typeB_januaryWrapsToPreviousYearDecember() {
        assertEquals(Period(2025, 11), PaymentRules.targetPeriod(PaymentType.B, 0, 2026))
        assertEquals(Period(2099, 11), PaymentRules.targetPeriod(PaymentType.B, 0, 2100))
    }

    @Test fun typeA_januaryStaysInThatYear() {
        assertEquals(Period(2026, 0), PaymentRules.targetPeriod(PaymentType.A, 0, 2026))
    }

    @Test fun periodRejectsInvalidMonth() {
        var thrown = false
        try { Period(2026, 12) } catch (e: IllegalArgumentException) { thrown = true }
        assertEquals(true, thrown)
    }
}

class PeriodTest {
    @Test fun plusMonthsCarriesAcrossYears() {
        assertEquals(Period(2027, 0), Period(2026, 11).plusMonths(1))
        assertEquals(Period(2025, 11), Period(2026, 0).plusMonths(-1))
        assertEquals(Period(2027, 5), Period(2026, 5).plusMonths(12))
        assertEquals(Period(2024, 11), Period(2026, 11).plusMonths(-24))
    }

    @Test fun ordering() {
        assertEquals(true, Period(2025, 11) < Period(2026, 0))
        assertEquals(true, Period(2026, 3) > Period(2026, 2))
    }

    @Test fun boundsMatchLegacyYearLists() {
        val c = SelectionBounds.collection(Period(2026, 6))
        assertEquals(Period(2025, 0), c.start)
        assertEquals(Period(2031, 11), c.endInclusive)
        val r = SelectionBounds.reports(Period(2026, 6))
        assertEquals(Period(2024, 0), r.start)
        assertEquals(Period(2027, 11), r.endInclusive)
    }
}
