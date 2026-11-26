package com.studentpay.manager.pdf

import android.content.Context
import android.net.Uri
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.domain.PdfReportSpec
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

    suspend fun studentReport(studentId: Long, year: Int): File {
        val student = repository.getStudent(studentId) ?: throw IllegalStateException("Student not found")
        val report = Reports.student(student, repository.allPayments(), year, today())
        val generatedOn = ReportFormat.date(clock(), zone())
        return render(ReportSpecs.student(report, generatedOn, zone()))
    }

    suspend fun monthlyReport(year: Int, month: Int): File {
        val report = Reports.monthly(repository.allStudents(), repository.paymentsForYear(year), year, month)
        return render(ReportSpecs.monthly(report, zone()))
    }

    suspend fun yearlyReport(year: Int): File {
        val report = Reports.yearly(repository.allStudents(), repository.paymentsForYear(year), year)
        return render(ReportSpecs.yearly(report))
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
