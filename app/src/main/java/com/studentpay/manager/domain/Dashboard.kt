package com.studentpay.manager.domain

/** Fast "is this student paid for this period" lookup. Built once per recomputation. */
class PaidIndex(payments: Collection<Payment>) {
    private val keys: HashSet<Triple<Long, Int, Int>> =
        payments.mapTo(HashSet(payments.size * 2)) { Triple(it.studentId, it.year, it.monthPaidFor) }

    fun isPaid(studentId: Long, period: Period): Boolean =
        keys.contains(Triple(studentId, period.year, period.month))
}

/** One student's row on Home / Payments for a selected collection month. */
data class StudentMonthStatus(
    val student: Student,
    /** The month the fee is FOR, after applying the student's payment type. */
    val target: Period,
    val isPaid: Boolean,
)

data class DashboardStats(
    val totalStudents: Int,
    val paidCount: Int,
    val dueCount: Int,
    /** Sum of the CURRENT monthly fee of every paid student (legacy behaviour, app.js renderViewSection). */
    val collectedAmount: Long,
)

data class DashboardResult(val rows: List<StudentMonthStatus>, val stats: DashboardStats)

enum class MonthState { PAID, DUE, FUTURE }

object Dashboard {

    /**
     * Status of every student for the selected COLLECTION month/year
     * (legacy renderViewSection / renderUpdateSection). Order of [students] is kept.
     */
    fun compute(
        students: List<Student>,
        payments: Collection<Payment>,
        collectionMonth: Int,
        collectionYear: Int,
    ): DashboardResult {
        val index = PaidIndex(payments)
        var paid = 0
        var collected = 0L
        val rows = students.map { s ->
            val target = PaymentRules.targetPeriod(s.type, collectionMonth, collectionYear)
            val isPaid = index.isPaid(s.id, target)
            if (isPaid) {
                paid++
                collected += s.monthlyFee
            }
            StudentMonthStatus(s, target, isPaid)
        }
        return DashboardResult(
            rows = rows,
            stats = DashboardStats(
                totalStudents = students.size,
                paidCount = paid,
                dueCount = students.size - paid,
                collectedAmount = collected,
            ),
        )
    }

    /**
     * The 12-month strip of one student for [year], keyed by the month the fee is FOR
     * (legacy status grid). Rule for unpaid months (relative to "today"):
     *  - past year            -> DUE
     *  - current year         -> DUE up to and including the current month, FUTURE after
     *  - future year          -> FUTURE
     */
    fun monthStates(
        studentId: Long,
        year: Int,
        payments: Collection<Payment>,
        today: Period,
    ): List<MonthState> {
        val index = PaidIndex(payments)
        return (0..11).map { m ->
            when {
                index.isPaid(studentId, Period(year, m)) -> MonthState.PAID
                year < today.year -> MonthState.DUE
                year == today.year && m <= today.month -> MonthState.DUE
                else -> MonthState.FUTURE
            }
        }
    }
}
