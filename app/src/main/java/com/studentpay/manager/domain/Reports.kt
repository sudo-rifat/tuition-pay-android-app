package com.studentpay.manager.domain

/**
 * Which "amount" a report shows.
 *  - COLLECTED (default): money RECEIVED during the selected period, whatever month the fee was for
 *    (a Type B fee collected in March is for February but belongs to March's collection).
 *  - ACTUAL: fees that BELONG to the selected period, whenever they were received
 *    (that Type B fee belongs to February).
 */
enum class ReportBasis { COLLECTED, ACTUAL }

/**
 * The month a payment was RECEIVED in. Uses the stored `paid_on_month` (exact, even if the student's type changed
 * later); old records without it fall back to the student's type.
 */
fun Payment.collectionPeriod(fallbackType: PaymentType): Period {
    val on = paidOnMonth
    if (on != null) {
        if (on == monthPaidFor) return Period(year, on)                                        // Type A
        if (on == (monthPaidFor + 1) % 12) return Period(if (monthPaidFor == 11) year + 1 else year, on)  // Type B
    }
    return if (fallbackType == PaymentType.B) period.plusMonths(1) else period
}

data class StudentReportMonth(val month: Int, val state: MonthState, val paidAtMillis: Long?, val amount: Int?)

data class StudentReport(
    val student: Student,
    val year: Int,
    val months: List<StudentReportMonth>,
    val paidMonths: Int,
    val dueMonths: Int,
    /** Sum of the amounts recorded on the paid months. */
    val totalAmount: Long,
)

data class MonthlyReportRow(val student: Student, val paidAtMillis: Long?, val amount: Int?) {
    val isPaid: Boolean get() = paidAtMillis != null
}

data class MonthlyReport(
    val year: Int,
    val month: Int,
    val rows: List<MonthlyReportRow>,
    val paidCount: Int,
    val dueCount: Int,
    val collectedAmount: Long,
)

/** One line of the "collected" monthly report: a student and what (if anything) they paid in that collection month. */
data class CollectedRow(val student: Student, val forPeriod: Period, val paidAtMillis: Long?, val amount: Int?) {
    val isPaid: Boolean get() = paidAtMillis != null
}

data class MonthlyCollectedReport(
    val year: Int,
    val month: Int,
    val rows: List<CollectedRow>,
    val paidCount: Int,
    val dueCount: Int,
    val collectedAmount: Long,
)

data class StudentCollectedRow(val collectedIn: Period, val forPeriod: Period, val paidAtMillis: Long, val amount: Int)

data class StudentCollectedReport(
    val student: Student,
    val year: Int,
    val rows: List<StudentCollectedRow>,
    val totalAmount: Long,
)

data class YearlyReportMonth(val month: Int, val paymentCount: Int, val amount: Long)

data class YearlyReport(
    val year: Int,
    val months: List<YearlyReportMonth>,
    val grandTotal: Long,
    val recordCount: Int,
)

/**
 * Report maths. As in the legacy app, reports are keyed by the month the fee is FOR
 * (month_paid_for + year). Amounts are the ones RECORDED on each payment, so a later fee change never
 * alters an earlier month.
 */
object Reports {

    fun student(student: Student, payments: Collection<Payment>, year: Int, today: Period): StudentReport {
        val mine = payments.filter { it.studentId == student.id && it.year == year }
        val byMonth = mine.associateBy { it.monthPaidFor }
        val states = Dashboard.monthStates(student.id, year, mine, today)
        val months = (0..11).map { m ->
            StudentReportMonth(m, states[m], byMonth[m]?.paidAtMillis, byMonth[m]?.amount)
        }
        val paid = months.count { it.state == MonthState.PAID }
        val due = months.count { it.state == MonthState.DUE }
        return StudentReport(student, year, months, paid, due, months.sumOf { (it.amount ?: 0).toLong() })
    }

    fun monthly(students: List<Student>, payments: Collection<Payment>, year: Int, month: Int): MonthlyReport {
        val forMonth = payments.filter { it.year == year && it.monthPaidFor == month }
            .associateBy { it.studentId }
        var collected = 0L
        // Deleted students only appear when they actually paid in this month (so the income is accounted for);
        // they are never listed as "due".
        val rows = students.filter { !it.isDeleted || forMonth.containsKey(it.id) }.map { s ->
            val p = forMonth[s.id]
            if (p != null) collected += p.amount
            MonthlyReportRow(s, p?.paidAtMillis, p?.amount)
        }
        val paid = rows.count { it.isPaid }
        return MonthlyReport(year, month, rows, paid, rows.size - paid, collected)
    }

    // ---------------- COLLECTED basis ----------------

    /**
     * Same logic as the Home dashboard: every active student with Paid/Due for the selected COLLECTION month,
     * plus deleted students only if they paid in it. "For" shows the month each fee belongs to.
     */
    fun monthlyCollected(students: List<Student>, payments: Collection<Payment>, year: Int, month: Int): MonthlyCollectedReport {
        val selected = Period(year, month)
        val typeById = students.associate { it.id to it.type }
        val paid = HashMap<Long, Payment>()
        for (p in payments) {
            if (p.collectionPeriod(typeById[p.studentId] ?: PaymentType.A) == selected) paid.putIfAbsent(p.studentId, p)
        }
        var collected = 0L
        val rows = students.filter { !it.isDeleted || paid.containsKey(it.id) }.map { s ->
            val p = paid[s.id]
            if (p != null) collected += p.amount
            CollectedRow(
                student = s,
                forPeriod = p?.period ?: PaymentRules.targetPeriod(s.type, month, year),
                paidAtMillis = p?.paidAtMillis,
                amount = p?.amount,
            )
        }
        val paidCount = rows.count { it.isPaid }
        return MonthlyCollectedReport(year, month, rows, paidCount, rows.size - paidCount, collected)
    }

    /** Money received in each month of [year] (by collection month). */
    fun yearlyCollected(students: List<Student>, payments: Collection<Payment>, year: Int): YearlyReport {
        val typeById = students.associate { it.id to it.type }
        val byMonth = List(12) { ArrayList<Payment>() }
        for (p in payments) {
            val c = p.collectionPeriod(typeById[p.studentId] ?: PaymentType.A)
            if (c.year == year) byMonth[c.month].add(p)
        }
        val months = (0..11).map { m -> YearlyReportMonth(m, byMonth[m].size, byMonth[m].sumOf { it.amount.toLong() }) }
        return YearlyReport(year, months, months.sumOf { it.amount }, months.sumOf { it.paymentCount })
    }

    /** Everything this student paid during [year], in the order it was received. */
    fun studentCollected(student: Student, payments: Collection<Payment>, year: Int): StudentCollectedReport {
        val rows = payments.asSequence()
            .filter { it.studentId == student.id }
            .map { it to it.collectionPeriod(student.type) }
            .filter { (_, c) -> c.year == year }
            .sortedBy { (_, c) -> c }
            .map { (p, c) -> StudentCollectedRow(c, p.period, p.paidAtMillis, p.amount) }
            .toList()
        return StudentCollectedReport(student, year, rows, rows.sumOf { it.amount.toLong() })
    }

    // ---------------- ACTUAL basis (fees belonging to the period) ----------------

    fun yearly(students: List<Student>, payments: Collection<Payment>, year: Int): YearlyReport {
        val inYear = payments.filter { it.year == year }
        val months = (0..11).map { m ->
            val ps = inYear.filter { it.monthPaidFor == m }
            YearlyReportMonth(m, ps.size, ps.sumOf { it.amount.toLong() })
        }
        return YearlyReport(year, months, months.sumOf { it.amount }, inYear.size)
    }
}
