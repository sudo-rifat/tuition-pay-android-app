package com.studentpay.manager.data

import android.content.Context
import android.net.Uri
import com.studentpay.manager.domain.BackupFormatError
import com.studentpay.manager.domain.BackupFormatException
import com.studentpay.manager.domain.BackupLimits
import com.studentpay.manager.domain.BackupValidator
import com.studentpay.manager.domain.ImportPlan
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backup/restore through the Storage Access Framework (no storage permission needed).
 * Reading validates everything BEFORE any data is touched; applying is one transaction.
 */
class BackupService(
    private val context: Context,
    private val repository: StudentPayRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Writes all students and payments as JSON to [uri]. Returns (students, payments) counts. */
    suspend fun export(uri: Uri): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val students = repository.allStudents()
        val payments = repository.allPayments()
        val json = BackupCodec.encode(students, payments, clock())
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open file")
        out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        students.size to payments.size
    }

    /**
     * Reads and validates a backup (or legacy export) file. Throws [BackupFormatException] for files that are
     * unusable as a whole, [IOException] if the file cannot be read. Nothing is written.
     */
    suspend fun readPlan(uri: Uri): ImportPlan = withContext(Dispatchers.IO) {
        val bytes = readLimited(uri)
        val (students, payments) = BackupCodec.decode(String(bytes, Charsets.UTF_8))
        BackupValidator.validate(students, payments, clock())
    }

    /** Replaces all current data with [plan] atomically. */
    suspend fun apply(plan: ImportPlan) = repository.replaceAll(plan)

    private fun readLimited(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open file")
        input.use { stream ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(chunk)
                if (n < 0) break
                total += n
                if (total > BackupLimits.MAX_FILE_BYTES) throw BackupFormatException(BackupFormatError.TOO_LARGE)
                buffer.write(chunk, 0, n)
            }
            return buffer.toByteArray()
        }
    }
}
