package com.studentpay.manager.domain

/** Fast "is this student paid for this period" lookup. Built once per recomputation. */
class PaidIndex(payments: Collection<Payment>) {
    private val byKey: HashMap<Triple<Long, Int, Int>, Payment> = HashMap(payments.size * 2)

    init {
        for (p in payments) byKey[Triple(p.studentId, p.year, p.monthPaidFor)] = p
    }

    fun isPaid(studentId: Long, period: Period): Boolean = paymentFor(studentId, period) != null

    fun paymentFor(studentId: Long, period: Period): Payment? =
        byKey[Triple(studentId, period.year, period.month)]
}

/** One student's row on Home / Payments for a selected collection month. */
data class StudentMonthStatus(
    val student: Student,
    /** The month the fee is FOR, after applying the student's payment type. */
    val target: Period,
    val isPaid: Boolean,
    /** Recorded amount if paid; otherwise the student's current fee (what ticking would record). */
    val amount: Int,
)

data class DashboardStats(
    val totalStudents: Int,
    val paidCount: Int,
    val dueCount: Int,
    /**
     * Sum of the amounts RECORDED on the paid payments (not the students' current fees). Includes income from
     * students who were deleted since: that money was really received.
     */
    val collectedAmount: Long,
    /** The part of [collectedAmount] that came from deleted students. */
    val fromDeletedStudents: Long = 0L,
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
        deletedStudents: List<Student> = emptyList(),
    ): DashboardResult {
        val index = PaidIndex(payments)
        var paid = 0
        var collected = 0L
        val rows = students.map { s ->
            val target = PaymentRules.targetPeriod(s.type, collectionMonth, collectionYear)
            val payment = index.paymentFor(s.id, target)
            if (payment != null) {
                paid++
                collected += payment.amount
            }
            StudentMonthStatus(s, target, payment != null, payment?.amount ?: s.monthlyFee)
        }
        // Deleted students are not listed or counted, but what they paid still counts as income.
        var fromDeleted = 0L
        for (s in deletedStudents) {
            val target = PaymentRules.targetPeriod(s.type, collectionMonth, collectionYear)
            val payment = index.paymentFor(s.id, target) ?: continue
            collected += payment.amount
            fromDeleted += payment.amount
        }
        return DashboardResult(
            rows = rows,
            stats = DashboardStats(
                totalStudents = students.size,
                paidCount = paid,
                dueCount = students.size - paid,
                collectedAmount = collected,
                fromDeletedStudents = fromDeleted,
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
