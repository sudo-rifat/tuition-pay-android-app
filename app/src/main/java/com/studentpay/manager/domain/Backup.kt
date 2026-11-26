package com.studentpay.manager.domain

/** Why a backup/import file was rejected outright (nothing is imported). */
enum class BackupFormatError { NOT_JSON, NOT_A_BACKUP, UNSUPPORTED_VERSION, TOO_LARGE, TOO_MANY_RECORDS, NO_STUDENTS }

class BackupFormatException(val error: BackupFormatError) : Exception(error.name)

/**
 * Parsed-but-unvalidated records. Field names mirror the LEGACY IndexedDB records
 * (students: id, name, payment_type, monthly_fee; payments: id, student_id, year,
 * month_paid_for, paid_on_month, date), so a file exported from the old PWA and a file exported
 * from this app share one format and one importer.
 */
data class RawStudent(val id: Long?, val name: String?, val paymentType: String?, val monthlyFee: Long?)

data class RawPayment(
    val studentId: Long?,
    val year: Int?,
    val monthPaidFor: Int?,
    val paidOnMonth: Int?,
    val dateMillis: Long?,
)

data class ImportSummary(
    val studentsInFile: Int,
    val paymentsInFile: Int,
    val studentsImported: Int,
    val paymentsImported: Int,
    val studentsSkipped: Int,
    val paymentsSkippedOrphan: Int,
    val paymentsSkippedInvalid: Int,
    /** Same student + year + month appeared more than once; the most recent date was kept. */
    val duplicatePaymentsMerged: Int,
    /** Payments without a usable date got the import time (legacy runMigration did the same). */
    val datesDefaulted: Int,
    /** Students without a fee got 0 (legacy `monthly_fee || 0`). */
    val feesDefaulted: Int,
    /** Human-readable details, capped, for the confirmation dialog. */
    val notes: List<String>,
) {
    val hasProblems: Boolean
        get() = studentsSkipped > 0 || paymentsSkippedOrphan > 0 || paymentsSkippedInvalid > 0 ||
            duplicatePaymentsMerged > 0 || datesDefaulted > 0 || feesDefaulted > 0
}

data class ImportPlan(val students: List<Student>, val payments: List<Payment>, val summary: ImportSummary)

object BackupLimits {
    const val MAX_FILE_BYTES = 20L * 1024 * 1024
    const val MAX_STUDENTS = 20_000
    const val MAX_PAYMENTS = 500_000
    const val MIN_YEAR = 2000
    const val MAX_YEAR = 2100
    const val FORMAT_VERSION = 1
    const val APP_MARKER = "StudentPayManager"
}

/** Row-level validation of imported data. Pure Kotlin. Never throws for bad rows: it skips and reports. */
object BackupValidator {
    private const val MAX_NOTES = 8

    fun validate(rawStudents: List<RawStudent>, rawPayments: List<RawPayment>, importedAtMillis: Long): ImportPlan {
        val notes = ArrayList<String>()
        fun note(msg: String) { if (notes.size < MAX_NOTES) notes.add(msg) }

        // ---- students ----
        val students = LinkedHashMap<Long, Student>()
        var skippedStudents = 0
        var feesDefaulted = 0
        rawStudents.forEachIndexed { i, r ->
            val label = "Student #${i + 1}"
            val id = r.id
            val name = r.name?.trim().orEmpty()
            val type = PaymentType.fromCode(r.paymentType)
            val fee = r.monthlyFee
            val problem: String? = when {
                id == null || id <= 0L -> "$label has no valid id"
                students.containsKey(id) -> "$label repeats id $id"
                name.isEmpty() || name.length > StudentValidator.MAX_NAME_LENGTH -> "$label has an invalid name"
                type == null -> "$label ('$name') has an unknown payment type"
                fee != null && (fee < 0 || fee > StudentValidator.MAX_FEE) -> "$label ('$name') has an invalid fee"
                else -> null
            }
            if (problem != null) {
                skippedStudents++
                note("Skipped: $problem")
            } else {
                if (fee == null) feesDefaulted++
                students[id!!] = Student(id, name, type!!, (fee ?: 0L).toInt())
            }
        }

        // ---- payments ----
        // key = student/year/month; value = best record so far
        val kept = LinkedHashMap<Triple<Long, Int, Int>, Payment>()
        var orphan = 0
        var invalid = 0
        var merged = 0
        var datesDefaulted = 0
        rawPayments.forEachIndexed { i, r ->
            val sid = r.studentId
            val y = r.year
            val m = r.monthPaidFor
            when {
                sid == null || y == null || m == null ||
                    y !in BackupLimits.MIN_YEAR..BackupLimits.MAX_YEAR || m !in 0..11 -> {
                    invalid++
                    note("Skipped: payment #${i + 1} has an invalid year or month")
                }
                !students.containsKey(sid) -> {
                    orphan++
                    note("Skipped: payment #${i + 1} belongs to a student that is not in the file")
                }
                else -> {
                    val dateMissing = r.dateMillis == null
                    val date = r.dateMillis ?: importedAtMillis
                    val paidOn = r.paidOnMonth?.takeIf { it in 0..11 }
                    val candidate = Payment(0L, sid, y, m, paidOn, date)
                    val key = Triple(sid, y, m)
                    val existing = kept[key]
                    if (existing == null) {
                        if (dateMissing) datesDefaulted++
                        kept[key] = candidate
                    } else {
                        merged++
                        if (candidate.paidAtMillis > existing.paidAtMillis && !dateMissing) kept[key] = candidate
                    }
                }
            }
        }

        val summary = ImportSummary(
            studentsInFile = rawStudents.size,
            paymentsInFile = rawPayments.size,
            studentsImported = students.size,
            paymentsImported = kept.size,
            studentsSkipped = skippedStudents,
            paymentsSkippedOrphan = orphan,
            paymentsSkippedInvalid = invalid,
            duplicatePaymentsMerged = merged,
            datesDefaulted = datesDefaulted,
            feesDefaulted = feesDefaulted,
            notes = notes,
        )
        return ImportPlan(students.values.toList(), kept.values.toList(), summary)
    }
}
