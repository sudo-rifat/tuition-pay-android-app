package com.studentpay.manager.domain

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
        val rows = students.map { s ->
            val p = forMonth[s.id]
            if (p != null) collected += p.amount
            MonthlyReportRow(s, p?.paidAtMillis, p?.amount)
        }
        val paid = rows.count { it.isPaid }
        return MonthlyReport(year, month, rows, paid, rows.size - paid, collected)
    }

    fun yearly(students: List<Student>, payments: Collection<Payment>, year: Int): YearlyReport {
        val inYear = payments.filter { it.year == year }
        val months = (0..11).map { m ->
            val ps = inYear.filter { it.monthPaidFor == m }
            YearlyReportMonth(m, ps.size, ps.sumOf { it.amount.toLong() })
        }
        return YearlyReport(year, months, months.sumOf { it.amount }, inYear.size)
    }
}
