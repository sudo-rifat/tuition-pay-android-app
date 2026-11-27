package com.studentpay.manager.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CellAlign { START, CENTER, END }

data class PdfColumn(val title: String, val weight: Float, val align: CellAlign = CellAlign.START)

/** Everything the PDF writer needs, with no Android types, so report content is unit-testable. */
data class PdfReportSpec(
    val fileStem: String,
    val title: String,
    val subtitle: String,
    val infoLines: List<String>,
    val columns: List<PdfColumn>,
    val rows: List<List<String>>,
    val emptyMessage: String,
    val summaryLines: List<String>,
)

object ReportBasisText {
    const val COLLECTED = "Basis: Collected (money received in the selected period)"
    const val ACTUAL = "Basis: Actual (fees that belong to the selected period)"
}

object ReportFormat {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

    fun date(millis: Long, zone: ZoneId): String =
        dateFormatter.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** "Tk 1,500" - PDFs use "Tk" like the legacy reports (the Taka sign is not in every PDF font). */
    fun money(amount: Long): String = "Tk " + String.format(Locale.US, "%,d", amount)

    fun typeLabel(type: PaymentType): String = when (type) {
        PaymentType.A -> "Current Month"
        PaymentType.B -> "Previous Month"
    }
}

object ReportSpecs {

    private fun displayName(s: Student): String = if (s.isDeleted) s.name + " (deleted)" else s.name

    fun student(r: StudentReport, generatedOn: String, zone: ZoneId): PdfReportSpec = PdfReportSpec(
        fileStem = "report_student_${r.student.id}_${r.year}",
        title = "Student Payment Report",
        subtitle = "Year ${r.year}  |  Generated $generatedOn",
        infoLines = listOf(
            "Student: ${r.student.name}",
            "Current Monthly Fee: ${ReportFormat.money(r.student.monthlyFee.toLong())}",
            "Payment Type: ${ReportFormat.typeLabel(r.student.type)}",
            ReportBasisText.ACTUAL,
        ),
        columns = listOf(
            PdfColumn("Month", 1.2f),
            PdfColumn("Status", 1.0f, CellAlign.CENTER),
            PdfColumn("Payment Date", 1.4f, CellAlign.CENTER),
            PdfColumn("Amount", 1.2f, CellAlign.END),
        ),
        rows = r.months.map { m ->
            val label = Months.FULL[m.month]
            when (m.state) {
                MonthState.PAID -> listOf(
                    label, "Paid",
                    m.paidAtMillis?.let { ReportFormat.date(it, zone) } ?: "N/A",
                    ReportFormat.money((m.amount ?: 0).toLong()),
                )
                MonthState.DUE -> listOf(label, "Due", "-", "-")
                MonthState.FUTURE -> listOf(label, "Upcoming", "-", "-")
            }
        },
        emptyMessage = "No payments found",
        summaryLines = listOf(
            "Paid months: ${r.paidMonths}",
            "Due months: ${r.dueMonths}",
            "Total Amount: ${ReportFormat.money(r.totalAmount)}",
        ),
    )

    fun monthly(r: MonthlyReport, zone: ZoneId): PdfReportSpec = PdfReportSpec(
        fileStem = "report_monthly_${r.year}_${(r.month + 1).toString().padStart(2, '0')}",
        title = "Monthly Payment Report",
        subtitle = "${Months.FULL[r.month]} ${r.year}",
        infoLines = listOf(ReportBasisText.ACTUAL),
        columns = listOf(
            PdfColumn("Student Name", 2.2f),
            PdfColumn("Status", 1.0f, CellAlign.CENTER),
            PdfColumn("Payment Date", 1.4f, CellAlign.CENTER),
            PdfColumn("Amount", 1.2f, CellAlign.END),
        ),
        rows = r.rows.map { row ->
            if (row.isPaid) {
                listOf(
                    displayName(row.student), "Paid",
                    ReportFormat.date(row.paidAtMillis!!, zone),
                    ReportFormat.money((row.amount ?: 0).toLong()),
                )
            } else {
                listOf(displayName(row.student), "Due", "-", "-")
            }
        },
        emptyMessage = "No students",
        summaryLines = listOf(
            "Total Students Paid: ${r.paidCount}",
            "Total Students Due: ${r.dueCount}",
            "Total Collected: ${ReportFormat.money(r.collectedAmount)}",
        ),
    )

    // ---------- COLLECTED basis ----------

    fun monthlyCollected(r: MonthlyCollectedReport, zone: ZoneId): PdfReportSpec = PdfReportSpec(
        fileStem = "report_monthly_collected_${r.year}_${(r.month + 1).toString().padStart(2, '0')}",
        title = "Monthly Collection Report",
        subtitle = "Collected in ${Months.FULL[r.month]} ${r.year}",
        infoLines = listOf(ReportBasisText.COLLECTED),
        columns = listOf(
            PdfColumn("Student Name", 2.0f),
            PdfColumn("For", 1.2f, CellAlign.CENTER),
            PdfColumn("Status", 0.9f, CellAlign.CENTER),
            PdfColumn("Payment Date", 1.3f, CellAlign.CENTER),
            PdfColumn("Amount", 1.1f, CellAlign.END),
        ),
        rows = r.rows.map { row ->
            val forText = Months.SHORT[row.forPeriod.month] + " " + row.forPeriod.year
            if (row.isPaid) {
                listOf(
                    displayName(row.student), forText, "Paid",
                    ReportFormat.date(row.paidAtMillis!!, zone),
                    ReportFormat.money((row.amount ?: 0).toLong()),
                )
            } else {
                listOf(displayName(row.student), forText, "Due", "-", "-")
            }
        },
        emptyMessage = "No students",
        summaryLines = listOf(
            "Total Students Paid: ${r.paidCount}",
            "Total Students Due: ${r.dueCount}",
            "Total Collected: ${ReportFormat.money(r.collectedAmount)}",
        ),
    )

    fun yearlyCollected(r: YearlyReport): PdfReportSpec = PdfReportSpec(
        fileStem = "report_yearly_collected_${r.year}",
        title = "Yearly Collection Report",
        subtitle = "Collected in ${r.year}",
        infoLines = listOf(ReportBasisText.COLLECTED),
        columns = listOf(
            PdfColumn("Month", 1.6f),
            PdfColumn("Payments", 1.2f, CellAlign.CENTER),
            PdfColumn("Amount Collected", 1.8f, CellAlign.END),
        ),
        rows = r.months.map { listOf(Months.FULL[it.month], it.paymentCount.toString(), ReportFormat.money(it.amount)) },
        emptyMessage = "No payments",
        summaryLines = listOf(
            "Grand Total Collected in ${r.year}: ${ReportFormat.money(r.grandTotal)}",
            "Total Payment Records: ${r.recordCount}",
        ),
    )

    fun studentCollected(r: StudentCollectedReport, generatedOn: String, zone: ZoneId): PdfReportSpec = PdfReportSpec(
        fileStem = "report_student_collected_${r.student.id}_${r.year}",
        title = "Student Collection Report",
        subtitle = "Collected in ${r.year}  |  Generated $generatedOn",
        infoLines = listOf(
            "Student: ${r.student.name}",
            "Current Monthly Fee: ${ReportFormat.money(r.student.monthlyFee.toLong())}",
            "Payment Type: ${ReportFormat.typeLabel(r.student.type)}",
            ReportBasisText.COLLECTED,
        ),
        columns = listOf(
            PdfColumn("Received In", 1.3f),
            PdfColumn("For", 1.3f),
            PdfColumn("Payment Date", 1.3f, CellAlign.CENTER),
            PdfColumn("Amount", 1.1f, CellAlign.END),
        ),
        rows = r.rows.map {
            listOf(
                Months.FULL[it.collectedIn.month],
                Months.SHORT[it.forPeriod.month] + " " + it.forPeriod.year,
                ReportFormat.date(it.paidAtMillis, zone),
                ReportFormat.money(it.amount.toLong()),
            )
        },
        emptyMessage = "No payments received in ${r.year}",
        summaryLines = listOf(
            "Payments received: ${r.rows.size}",
            "Total Received: ${ReportFormat.money(r.totalAmount)}",
        ),
    )

    fun yearly(r: YearlyReport): PdfReportSpec = PdfReportSpec(
        fileStem = "report_yearly_${r.year}",
        title = "Yearly Summary Report",
        subtitle = "Year ${r.year}",
        infoLines = listOf(ReportBasisText.ACTUAL),
        columns = listOf(
            PdfColumn("Month", 1.6f),
            PdfColumn("Payments", 1.2f, CellAlign.CENTER),
            PdfColumn("Amount Collected", 1.8f, CellAlign.END),
        ),
        rows = r.months.map { listOf(Months.FULL[it.month], it.paymentCount.toString(), ReportFormat.money(it.amount)) },
        emptyMessage = "No payments",
        summaryLines = listOf(
            "Grand Total for ${r.year}: ${ReportFormat.money(r.grandTotal)}",
            "Total Payment Records: ${r.recordCount}",
        ),
    )
}
