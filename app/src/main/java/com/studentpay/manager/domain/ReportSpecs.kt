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

object ReportFormat {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

    fun date(millis: Long, zone: ZoneId): String =
        dateFormatter.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** "Tk 1,500" - PDFs use "Tk" like the legacy reports (the Taka sign is not in every PDF font). */
    fun money(amount: Long): String = "Tk " + String.format(Locale.US, "%,d", amount)

    fun typeLabel(type: PaymentType): String = when (type) {
        PaymentType.A -> "Current Month"
        PaymentType.B -> "Next Month"
    }
}

object ReportSpecs {

    fun student(r: StudentReport, generatedOn: String, zone: ZoneId): PdfReportSpec = PdfReportSpec(
        fileStem = "report_student_${r.student.id}_${r.year}",
        title = "Student Payment Report",
        subtitle = "Year ${r.year}  |  Generated $generatedOn",
        infoLines = listOf(
            "Student: ${r.student.name}",
            "Monthly Fee: ${ReportFormat.money(r.student.monthlyFee.toLong())}",
            "Payment Type: ${ReportFormat.typeLabel(r.student.type)}",
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
                    ReportFormat.money(r.student.monthlyFee.toLong()),
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
        infoLines = emptyList(),
        columns = listOf(
            PdfColumn("Student Name", 2.2f),
            PdfColumn("Status", 1.0f, CellAlign.CENTER),
            PdfColumn("Payment Date", 1.4f, CellAlign.CENTER),
            PdfColumn("Amount", 1.2f, CellAlign.END),
        ),
        rows = r.rows.map { row ->
            if (row.isPaid) {
                listOf(
                    row.student.name, "Paid",
                    ReportFormat.date(row.paidAtMillis!!, zone),
                    ReportFormat.money(row.student.monthlyFee.toLong()),
                )
            } else {
                listOf(row.student.name, "Due", "-", "-")
            }
        },
        emptyMessage = "No students",
        summaryLines = listOf(
            "Total Students Paid: ${r.paidCount}",
            "Total Students Due: ${r.dueCount}",
            "Total Collected: ${ReportFormat.money(r.collectedAmount)}",
        ),
    )

    fun yearly(r: YearlyReport): PdfReportSpec = PdfReportSpec(
        fileStem = "report_yearly_${r.year}",
        title = "Yearly Summary Report",
        subtitle = "Year ${r.year}",
        infoLines = emptyList(),
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
