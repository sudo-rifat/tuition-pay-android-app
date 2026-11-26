package com.studentpay.manager.data

import com.studentpay.manager.domain.BackupFormatError
import com.studentpay.manager.domain.BackupFormatException
import com.studentpay.manager.domain.BackupValidator
import com.studentpay.manager.domain.Payment
import com.studentpay.manager.domain.PaymentType
import com.studentpay.manager.domain.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    /** Real output of legacy-export/export-old-data.js run against the real old db.js (see legacy-export/). */
    private val legacy = "{\"app\":\"StudentPayManager\",\"formatVersion\":1,\"source\":\"legacy-pwa\",\"exportedAt\":\"2026-11-25T08:11:07.028Z\",\"students\":[{\"name\":\"Rafi Ahmed\",\"payment_type\":\"A\",\"monthly_fee\":500,\"id\":1},{\"name\":\"মিনা আক্তার\",\"payment_type\":\"B\",\"monthly_fee\":700,\"id\":2},{\"name\":\"No Fee\",\"payment_type\":\"A\",\"id\":3}],\"payments\":[{\"student_id\":1,\"year\":2026,\"month_paid_for\":9,\"paid_on_month\":9,\"date\":\"2026-10-09T00:00:00.000Z\",\"id\":1},{\"student_id\":1,\"year\":2026,\"month_paid_for\":10,\"paid_on_month\":10,\"date\":\"2026-11-03T08:00:00.000Z\",\"id\":2},{\"student_id\":2,\"year\":2025,\"month_paid_for\":11,\"paid_on_month\":0,\"date\":\"2026-01-04T09:00:00.000Z\",\"id\":3},{\"student_id\":2,\"year\":2026,\"month_paid_for\":3,\"id\":4}]}"

    private fun expectError(error: BackupFormatError, json: String) {
        try {
            BackupCodec.decode(json)
            throw AssertionError("expected $error")
        } catch (e: BackupFormatException) {
            assertEquals(error, e.error)
        }
    }

    @Test fun legacyExportImportsWithAllRulesApplied() {
        val (rs, rp) = BackupCodec.decode(legacy)
        val plan = BackupValidator.validate(rs, rp, 9_000_000_000_000L)
        assertEquals(3, plan.students.size)
        assertEquals(listOf(1L, 2L, 3L), plan.students.map { it.id })          // ids preserved
        assertEquals("মিনা আক্তার", plan.students[1].name)                         // Bengali survives
        assertEquals(PaymentType.B, plan.students[1].type)
        assertEquals(0, plan.students[2].monthlyFee)                            // legacy missing fee -> 0
        assertEquals(1, plan.summary.feesDefaulted)
        assertEquals(4, plan.payments.size)
        assertEquals(1, plan.summary.datesDefaulted)                            // legacy payment without date
        assertEquals(0, plan.summary.studentsSkipped)
        val decJan = plan.payments.first { it.studentId == 2L && it.year == 2025 }
        assertEquals(11, decJan.monthPaidFor)                                   // Type B Jan -> previous Dec, kept as stored
        assertEquals(0, decJan.paidOnMonth)
        // ISO date parsed to the exact instant (2026-10-09T00:00:00.000Z)
        assertEquals(1_791_504_000_000L, plan.payments.first { it.studentId == 1L && it.monthPaidFor == 9 }.paidAtMillis)
    }

    @Test fun roundTripKeepsEverything() {
        val students = listOf(Student(5, "Rafi \"Q\" Ahmed", PaymentType.A, 500), Student(8, "মিনা", PaymentType.B, 0))
        val payments = listOf(Payment(1, 5, 2026, 1, 1, 1_772_668_800_000L), Payment(2, 8, 2025, 11, null, 1_772_668_800_123L))
        val json = BackupCodec.encode(students, payments, 1_772_668_800_000L)
        val (rs, rp) = BackupCodec.decode(json)
        val plan = BackupValidator.validate(rs, rp, 1L)
        assertEquals(students, plan.students)
        assertEquals(payments.map { it.copy(id = 0) }, plan.payments)
        assertFalse(plan.summary.hasProblems)
        assertTrue(json.contains("\"formatVersion\""))
    }

    @Test fun toleratesNumbersAsStringsAndNumericDates() {
        val json = "{\"students\":[{\"id\":\"4\",\"name\":\"A\",\"payment_type\":\"A\",\"monthly_fee\":\"250\"}]," +
            "\"payments\":[{\"student_id\":4,\"year\":2026.0,\"month_paid_for\":\"0\",\"date\":1772668800000}]}"
        val (rs, rp) = BackupCodec.decode(json)
        val plan = BackupValidator.validate(rs, rp, 1L)
        assertEquals(1, plan.students.size)
        assertEquals(250, plan.students[0].monthlyFee)
        assertEquals(1, plan.payments.size)
        assertEquals(1_772_668_800_000L, plan.payments[0].paidAtMillis)
    }

    @Test fun badUnixOrGarbageDateIsDefaultedNotCrashed() {
        val json = "{\"students\":[{\"id\":1,\"name\":\"A\",\"payment_type\":\"A\",\"monthly_fee\":1}]," +
            "\"payments\":[{\"student_id\":1,\"year\":2026,\"month_paid_for\":1,\"date\":\"yesterday\"}]}"
        val (rs, rp) = BackupCodec.decode(json)
        assertNull(rp[0].dateMillis)
        assertEquals(1, BackupValidator.validate(rs, rp, 5L).summary.datesDefaulted)
    }

    @Test fun rejectsFilesThatAreNotBackups() {
        expectError(BackupFormatError.NOT_A_BACKUP, "[1,2,3]")
        expectError(BackupFormatError.NOT_A_BACKUP, "{\"hello\":1}")
        expectError(BackupFormatError.NOT_A_BACKUP, "{\"app\":\"Other\",\"students\":[{}]}")
        expectError(BackupFormatError.NOT_A_BACKUP, "{\"students\":{}}")
        expectError(BackupFormatError.NOT_A_BACKUP, "plain text")
        expectError(BackupFormatError.NOT_JSON, "{\"students\":[")
        expectError(BackupFormatError.UNSUPPORTED_VERSION, "{\"formatVersion\":99,\"students\":[{}]}")
        expectError(BackupFormatError.NO_STUDENTS, "{\"students\":[],\"payments\":[]}")
    }

    @Test fun nonObjectRowsBecomeSkippedRowsNotCrashes() {
        val (rs, rp) = BackupCodec.decode("{\"students\":[5,null,{\"id\":1,\"name\":\"A\",\"payment_type\":\"A\"}],\"payments\":[7]}")
        val plan = BackupValidator.validate(rs, rp, 1L)
        assertEquals(1, plan.students.size)
        assertEquals(2, plan.summary.studentsSkipped)
        assertEquals(1, plan.summary.paymentsSkippedInvalid)
    }

    @Test fun byteOrderMarkIsIgnored() {
        val (rs, _) = BackupCodec.decode("\uFEFF{\"students\":[{\"id\":1,\"name\":\"A\",\"payment_type\":\"B\",\"monthly_fee\":5}]}")
        assertEquals(1, rs.size)
    }
}
