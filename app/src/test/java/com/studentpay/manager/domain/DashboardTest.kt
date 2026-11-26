package com.studentpay.manager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTest {
    private val a = Student(1, "Rafi", PaymentType.A, 500)
    private val b = Student(2, "Mina", PaymentType.B, 700)
    private val c = Student(3, "Tanvir", PaymentType.A, 0)

    private fun pay(sid: Long, y: Int, m: Int) = Payment(0, sid, y, m, null, 1_000L)

    @Test fun statsCountPaidDueAndSumCurrentFees() {
        // Collection month = March 2026: A pays for Mar-2026, B pays for Feb-2026.
        val payments = listOf(pay(1, 2026, 2), pay(2, 2026, 1))
        val r = Dashboard.compute(listOf(a, b, c), payments, 2, 2026)
        assertEquals(3, r.stats.totalStudents)
        assertEquals(2, r.stats.paidCount)
        assertEquals(1, r.stats.dueCount)
        assertEquals(1200L, r.stats.collectedAmount)
        assertEquals(listOf(true, true, false), r.rows.map { it.isPaid })
    }

    @Test fun typeBPaymentForMarchDoesNotCountAsPaidInMarchCollection() {
        val payments = listOf(pay(2, 2026, 2)) // Mina paid FOR March
        val r = Dashboard.compute(listOf(b), payments, 2, 2026)  // collection = March -> target Feb
        assertFalse(r.rows[0].isPaid)
        val r2 = Dashboard.compute(listOf(b), payments, 3, 2026) // collection = April -> target March
        assertTrue(r2.rows[0].isPaid)
    }

    @Test fun januaryCollectionForTypeBLooksAtPreviousYearDecember() {
        val payments = listOf(pay(2, 2025, 11))
        val r = Dashboard.compute(listOf(b), payments, 0, 2026)
        assertTrue(r.rows[0].isPaid)
        assertEquals(Period(2025, 11), r.rows[0].target)
    }

    @Test fun zeroFeeStudentCanBePaidAndAddsNothing() {
        val r = Dashboard.compute(listOf(c), listOf(pay(3, 2026, 5)), 5, 2026)
        assertEquals(1, r.stats.paidCount)
        assertEquals(0L, r.stats.collectedAmount)
    }

    @Test fun emptyStudentListGivesZeroStats() {
        val r = Dashboard.compute(emptyList(), emptyList(), 0, 2026)
        assertEquals(0, r.stats.totalStudents)
        assertEquals(0L, r.stats.collectedAmount)
    }

    @Test fun monthStatesFollowLegacyDueRule() {
        val today = Period(2026, 5) // June 2026
        val payments = listOf(pay(1, 2026, 0), pay(1, 2025, 3))
        val cur = Dashboard.monthStates(1, 2026, payments, today)
        assertEquals(MonthState.PAID, cur[0])
        assertEquals(MonthState.DUE, cur[1])
        assertEquals(MonthState.DUE, cur[5])      // current month, unpaid -> due
        assertEquals(MonthState.FUTURE, cur[6])
        val past = Dashboard.monthStates(1, 2025, payments, today)
        assertEquals(MonthState.PAID, past[3])
        assertEquals(MonthState.DUE, past[4])     // every unpaid month of a past year is due
        val future = Dashboard.monthStates(1, 2027, payments, today)
        assertTrue(future.all { it == MonthState.FUTURE })
    }
}
