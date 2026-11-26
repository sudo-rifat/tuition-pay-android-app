package com.studentpay.manager.domain

data class StudentReportMonth(val month: Int, val state: MonthState, val paidAtMillis: Long?)

data class StudentReport(
    val student: Student,
    val year: Int,
    val months: List<StudentReportMonth>,
    val paidMonths: Int,
    val dueMonths: Int,
    /** paidMonths x CURRENT monthly fee (legacy behaviour). */
    val totalAmount: Long,
)

data class MonthlyReportRow(val student: Student, val paidAtMillis: Long?) {
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
 * (month_paid_for + year), and amounts use each student's CURRENT monthly fee.
 */
object Reports {

    fun student(student: Student, payments: Collection<Payment>, year: Int, today: Period): StudentReport {
        val mine = payments.filter { it.studentId == student.id && it.year == year }
        val byMonth = mine.associateBy { it.monthPaidFor }
        val states = Dashboard.monthStates(student.id, year, mine, today)
        val months = (0..11).map { m ->
            StudentReportMonth(m, states[m], byMonth[m]?.paidAtMillis)
        }
        val paid = months.count { it.state == MonthState.PAID }
        val due = months.count { it.state == MonthState.DUE }
        return StudentReport(student, year, months, paid, due, paid.toLong() * student.monthlyFee)
    }

    fun monthly(students: List<Student>, payments: Collection<Payment>, year: Int, month: Int): MonthlyReport {
        val forMonth = payments.filter { it.year == year && it.monthPaidFor == month }
            .associateBy { it.studentId }
        var collected = 0L
        val rows = students.map { s ->
            val p = forMonth[s.id]
            if (p != null) collected += s.monthlyFee
            MonthlyReportRow(s, p?.paidAtMillis)
        }
        val paid = rows.count { it.isPaid }
        return MonthlyReport(year, month, rows, paid, rows.size - paid, collected)
    }

    fun yearly(students: List<Student>, payments: Collection<Payment>, year: Int): YearlyReport {
        val fee = students.associate { it.id to it.monthlyFee }
        val inYear = payments.filter { it.year == year }
        val months = (0..11).map { m ->
            val ps = inYear.filter { it.monthPaidFor == m }
            YearlyReportMonth(m, ps.size, ps.sumOf { (fee[it.studentId] ?: 0).toLong() })
        }
        return YearlyReport(year, months, months.sumOf { it.amount }, inYear.size)
    }
}
