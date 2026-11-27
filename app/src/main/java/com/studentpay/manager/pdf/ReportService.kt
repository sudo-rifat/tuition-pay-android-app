package com.studentpay.manager.pdf

import android.content.Context
import android.net.Uri
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.domain.PdfReportSpec
import com.studentpay.manager.domain.ReportBasis
import com.studentpay.manager.domain.Period
import com.studentpay.manager.domain.ReportFormat
import com.studentpay.manager.domain.ReportSpecs
import com.studentpay.manager.domain.Reports
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds report data from Room, renders it to a PDF in the app cache, and copies it to a user-chosen Uri. */
class ReportService(
    private val context: Context,
    private val repository: StudentPayRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val writer = PdfReportWriter()

    private fun today(): Period {
        val d = Instant.ofEpochMilli(clock()).atZone(zone())
        return Period(d.year, d.monthValue - 1)
    }

    /** [basis] COLLECTED = money received in the period; ACTUAL = fees belonging to the period. */
    suspend fun studentReport(studentId: Long, year: Int, basis: ReportBasis): File {
        val student = repository.getStudent(studentId) ?: throw IllegalStateException("Student not found")
        val generatedOn = ReportFormat.date(clock(), zone())
        val spec = when (basis) {
            ReportBasis.ACTUAL ->
                ReportSpecs.student(Reports.student(student, repository.allPayments(), year, today()), generatedOn, zone())
            ReportBasis.COLLECTED ->
                ReportSpecs.studentCollected(
                    Reports.studentCollected(student, repository.paymentsBetween(year - 1, year), year), generatedOn, zone(),
                )
        }
        return render(spec)
    }

    suspend fun monthlyReport(year: Int, month: Int, basis: ReportBasis): File {
        val students = repository.allStudents()
        val spec = when (basis) {
            ReportBasis.ACTUAL ->
                ReportSpecs.monthly(Reports.monthly(students, repository.paymentsForYear(year), year, month), zone())
            ReportBasis.COLLECTED ->
                // A Type B fee received in January is stored for the previous December, so look at year-1 too.
                ReportSpecs.monthlyCollected(
                    Reports.monthlyCollected(students, repository.paymentsBetween(year - 1, year), year, month), zone(),
                )
        }
        return render(spec)
    }

    suspend fun yearlyReport(year: Int, basis: ReportBasis): File {
        val students = repository.allStudents()
        val spec = when (basis) {
            ReportBasis.ACTUAL -> ReportSpecs.yearly(Reports.yearly(students, repository.paymentsForYear(year), year))
            ReportBasis.COLLECTED ->
                ReportSpecs.yearlyCollected(Reports.yearlyCollected(students, repository.paymentsBetween(year - 1, year), year))
        }
        return render(spec)
    }

    /** Copies a generated report to a document the user picked with the system file picker. */
    suspend fun saveTo(file: File, uri: Uri) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open file")
        out.use { o -> file.inputStream().use { it.copyTo(o) } }
    }

    fun fileFor(name: String): File = File(reportDir(), name)

    /** Removes generated PDFs (they contain student names). */
    fun clearCache() {
        reportDir().listFiles()?.forEach { it.delete() }
    }

    private fun reportDir(): File = File(context.cacheDir, "reports").apply { mkdirs() }

    private suspend fun render(spec: PdfReportSpec): File = withContext(Dispatchers.IO) {
        val dir = reportDir()
        dir.listFiles()?.forEach { it.delete() } // keep the cache to one report
        val file = File(dir, spec.fileStem + ".pdf")
        file.outputStream().use { writer.write(spec, it) }
        file
    }
}
