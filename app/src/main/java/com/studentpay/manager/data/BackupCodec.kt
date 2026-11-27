package com.studentpay.manager.data

import com.studentpay.manager.domain.BackupFormatError
import com.studentpay.manager.domain.BackupFormatException
import com.studentpay.manager.domain.BackupLimits
import com.studentpay.manager.domain.Payment
import com.studentpay.manager.domain.RawPayment
import com.studentpay.manager.domain.RawStudent
import com.studentpay.manager.domain.Student
import java.time.Instant
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Versioned JSON backup (org.json is part of Android: no dependency).
 *
 * {
 *   "app": "StudentPayManager", "formatVersion": 1, "exportedAt": "2026-11-25T10:00:00Z",
 *   "students": [ { "id", "name", "payment_type", "monthly_fee", "deleted_at" } ],
 *   "payments": [ { "id", "student_id", "year", "month_paid_for", "paid_on_month", "date", "amount" } ]
 * }
 *
 * The record field names are identical to the legacy IndexedDB records, plus `amount` (the fee recorded on the
 * payment; optional on import). A file without "app"/"formatVersion" or without `amount` is accepted as legacy
 * data: those payments take the student's fee from the file.
 */
object BackupCodec {

    fun encode(students: List<Student>, payments: List<Payment>, exportedAtMillis: Long): String {
        val root = JSONObject()
        root.put("app", BackupLimits.APP_MARKER)
        root.put("formatVersion", BackupLimits.FORMAT_VERSION)
        root.put("exportedAt", Instant.ofEpochMilli(exportedAtMillis).toString())

        val studentArray = JSONArray()
        for (s in students) {
            studentArray.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("payment_type", s.type.code)
                    .put("monthly_fee", s.monthlyFee)
                    .put("deleted_at", s.deletedAtMillis?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL),
            )
        }
        val paymentArray = JSONArray()
        for (p in payments) {
            paymentArray.put(
                JSONObject()
                    .put("id", p.id)
                    .put("student_id", p.studentId)
                    .put("year", p.year)
                    .put("month_paid_for", p.monthPaidFor)
                    .put("paid_on_month", p.paidOnMonth ?: JSONObject.NULL)
                    .put("date", Instant.ofEpochMilli(p.paidAtMillis).toString())
                    .put("amount", p.amount),
            )
        }
        root.put("students", studentArray)
        root.put("payments", paymentArray)
        return root.toString(1)
    }

    /** Structural checks only; row-level validation is [com.studentpay.manager.domain.BackupValidator]. */
    fun decode(json: String): Pair<List<RawStudent>, List<RawPayment>> {
        val root: Any? = try {
            JSONTokener(json.removePrefix("\uFEFF")).nextValue()
        } catch (e: JSONException) {
            throw BackupFormatException(BackupFormatError.NOT_JSON)
        }
        if (root !is JSONObject) throw BackupFormatException(BackupFormatError.NOT_A_BACKUP)

        val marker = root.opt("app")
        if (marker != null && marker != JSONObject.NULL && marker != BackupLimits.APP_MARKER) {
            throw BackupFormatException(BackupFormatError.NOT_A_BACKUP)
        }
        val version = root.optNumber("formatVersion")
        if (version != null && version > BackupLimits.FORMAT_VERSION) {
            throw BackupFormatException(BackupFormatError.UNSUPPORTED_VERSION)
        }

        val studentArray = root.opt("students") as? JSONArray
            ?: throw BackupFormatException(BackupFormatError.NOT_A_BACKUP)
        val paymentsRaw = root.opt("payments")
        val paymentArray = when (paymentsRaw) {
            null, JSONObject.NULL -> JSONArray()
            is JSONArray -> paymentsRaw
            else -> throw BackupFormatException(BackupFormatError.NOT_A_BACKUP)
        }

        if (studentArray.length() == 0) throw BackupFormatException(BackupFormatError.NO_STUDENTS)
        if (studentArray.length() > BackupLimits.MAX_STUDENTS || paymentArray.length() > BackupLimits.MAX_PAYMENTS) {
            throw BackupFormatException(BackupFormatError.TOO_MANY_RECORDS)
        }

        val students = (0 until studentArray.length()).map { i ->
            val o = studentArray.opt(i) as? JSONObject
            RawStudent(
                id = o?.optNumber("id"),
                name = o?.opt("name") as? String,
                paymentType = o?.opt("payment_type") as? String,
                monthlyFee = o?.optNumber("monthly_fee"),
                deletedAtMillis = o?.optDateMillis("deleted_at"),
            )
        }
        val payments = (0 until paymentArray.length()).map { i ->
            val o = paymentArray.opt(i) as? JSONObject
            RawPayment(
                studentId = o?.optNumber("student_id"),
                year = o?.optNumber("year")?.toIntOrNullSafe(),
                monthPaidFor = o?.optNumber("month_paid_for")?.toIntOrNullSafe(),
                paidOnMonth = o?.optNumber("paid_on_month")?.toIntOrNullSafe(),
                dateMillis = o?.optDateMillis("date"),
                amount = o?.optNumber("amount"),
            )
        }
        return students to payments
    }

    private fun Long.toIntOrNullSafe(): Int? = if (this in Int.MIN_VALUE..Int.MAX_VALUE) toInt() else null

    /** Tolerant integer read: accepts ints, whole doubles and numeric strings; anything else is null. */
    private fun JSONObject.optNumber(key: String): Long? = when (val v = opt(key)) {
        is Long -> v
        is Int -> v.toLong()
        is Number -> {
            val d = v.toDouble()
            if (d == Math.floor(d) && abs(d) < 9.0e15) d.toLong() else null
        }
        is String -> v.trim().toLongOrNull()
        else -> null
    }

    /** ISO-8601 string (legacy JS toISOString) or epoch millis. Anything unusable is null. */
    private fun JSONObject.optDateMillis(key: String): Long? {
        val millis: Long? = when (val v = opt(key)) {
            is String -> {
                val t = v.trim()
                runCatching { Instant.parse(t).toEpochMilli() }.getOrNull() ?: t.toLongOrNull()
            }
            is Number -> v.toLong()
            else -> null
        }
        return millis?.takeIf { it > 0L }
    }
}
