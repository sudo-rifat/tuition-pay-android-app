package com.studentpay.manager.data

import androidx.room.withTransaction
import com.studentpay.manager.domain.DeletedStudentSummary
import com.studentpay.manager.domain.ImportPlan
import com.studentpay.manager.domain.Payment
import com.studentpay.manager.domain.PaymentEntry
import com.studentpay.manager.domain.PaymentRules
import com.studentpay.manager.domain.PaymentType
import com.studentpay.manager.domain.Student
import com.studentpay.manager.domain.StudentValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Single source of truth for students and payments (Room). All writes that touch more than one
 * row run in one transaction. Room suspend DAO calls are main-safe.
 */
class StudentPayRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val students = db.studentDao()
    private val payments = db.paymentDao()

    // ---------- reads ----------

    /** Active students only (the ones shown in lists and on the Payments screen). */
    fun observeStudents(): Flow<List<Student>> =
        students.observeActive().map { list -> list.map { it.toDomain() } }

    /** Deleted students: not listed anywhere, but their payments still count as income. */
    fun observeDeletedStudents(): Flow<List<Student>> =
        students.observeDeleted().map { list -> list.map { it.toDomain() } }

    /** Deleted students with how much each paid in total, for the "Deleted students" screen. */
    fun observeDeletedSummaries(): Flow<List<DeletedStudentSummary>> =
        combine(students.observeDeleted(), payments.observeDeletedTotals()) { list, totals ->
            val byId = totals.associateBy { it.studentId }
            list.map { e -> DeletedStudentSummary(e.toDomain(), byId[e.id]?.total ?: 0L, byId[e.id]?.count ?: 0) }
        }

    /** An active student; emits null if the student does not exist or was deleted. */
    fun observeStudent(id: Long): Flow<Student?> = students.observeActiveById(id).map { it?.toDomain() }

    fun observePayments(fromYear: Int, toYear: Int): Flow<List<Payment>> =
        payments.observeYears(fromYear, toYear).map { list -> list.map { it.toDomain() } }

    fun observeStudentPayments(studentId: Long): Flow<List<Payment>> =
        payments.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    suspend fun getStudent(id: Long): Student? = students.get(id)?.toDomain()
    /** EVERY student including deleted ones: reports and backups must not lose them. */
    suspend fun allStudents(): List<Student> = students.getAll().map { it.toDomain() }
    suspend fun activeStudentCount(): Int = students.getAll().count { it.deletedAt == null }
    suspend fun allPayments(): List<Payment> = payments.getAll().map { it.toDomain() }
    suspend fun paymentsForYear(year: Int): List<Payment> = payments.getForYear(year).map { it.toDomain() }
    /** Payments whose "paid for" year is in [fromYear, toYear] (collected-basis reports need year-1..year). */
    suspend fun paymentsBetween(fromYear: Int, toYear: Int): List<Payment> =
        payments.getYears(fromYear, toYear).map { it.toDomain() }
    suspend fun paymentCountFor(studentId: Long): Int = payments.countForStudent(studentId)
    suspend fun paymentTotalFor(studentId: Long): Long = payments.totalForStudent(studentId)

    // ---------- students ----------

    suspend fun addStudent(name: String, type: PaymentType, fee: Int): Long {
        requireValid(name, fee)
        return students.insert(StudentEntity(name = name.trim(), paymentType = type.code, monthlyFee = fee))
    }

    suspend fun updateStudent(id: Long, name: String, type: PaymentType, fee: Int) {
        requireValid(name, fee)
        val existing = students.get(id)
        require(existing != null && existing.deletedAt == null) { "Student $id not found" }
        students.update(existing.copy(name = name.trim(), paymentType = type.code, monthlyFee = fee))
    }

    /**
     * "Deletes" a student: hides them from every list but KEEPS all their payments, so the income already received
     * stays in the dashboard, reports and backups. Reversible with [restoreStudent].
     */
    suspend fun deleteStudent(id: Long) {
        students.markDeleted(id, clock())
    }

    suspend fun restoreStudent(id: Long) {
        students.markRestored(id)
    }

    // ---------- payments ----------

    /**
     * Records payments for the selected COLLECTION month/year, all in ONE transaction (all or nothing).
     * For each entry the stored period is derived with [PaymentRules] (Type A / Type B); the amount saved is
     * [PaymentEntry.amount], which the user may have changed from the student's monthly fee. The student's fee
     * itself is not touched. A student who already has a payment for that period is skipped (never overwritten,
     * the UNIQUE index guarantees one record). Returns the payments that were created.
     */
    suspend fun recordPayments(entries: List<PaymentEntry>, collectionMonth: Int, collectionYear: Int): List<Payment> {
        entries.forEach { require(it.amount in 0..StudentValidator.MAX_FEE) { "Invalid amount" } }
        return db.withTransaction {
            val now = clock()
            entries.mapNotNull { entry ->
                val student = entry.student
                val target = PaymentRules.targetPeriod(student.type, collectionMonth, collectionYear)
                if (payments.find(student.id, target.year, target.month) != null) {
                    null
                } else {
                    val entity = PaymentEntity(
                        studentId = student.id,
                        year = target.year,
                        monthPaidFor = target.month,
                        paidOnMonth = collectionMonth,
                        paidAt = now,
                        amount = entry.amount,
                    )
                    entity.copy(id = payments.insert(entity)).toDomain()
                }
            }
        }
    }

    /** Reverses payments in one transaction. Returns exactly what was removed (so the UI can offer Undo). */
    suspend fun removePayments(students: List<Student>, collectionMonth: Int, collectionYear: Int): List<Payment> =
        db.withTransaction {
            students.mapNotNull { student ->
                val target = PaymentRules.targetPeriod(student.type, collectionMonth, collectionYear)
                val existing = payments.find(student.id, target.year, target.month)
                if (existing != null) payments.delete(existing.id)
                existing?.toDomain()
            }
        }

    /** Undo of [removePayments]: puts the exact records back (same date and amount) in one transaction. */
    suspend fun restorePayments(removed: List<Payment>) {
        db.withTransaction { removed.forEach { payments.insertIgnore(it.copy(id = 0).toEntity()) } }
    }

    // ---------- backup / restore ----------

    /** Replaces ALL data with [plan] in one transaction; on any failure nothing changes. */
    suspend fun replaceAll(plan: ImportPlan) {
        db.withTransaction {
            payments.deleteAll()
            students.deleteAll()
            students.insertAll(plan.students.map { it.toEntity() })
            payments.insertAll(plan.payments.map { it.copy(id = 0).toEntity() })
        }
    }

    /** Wipes every table (used by "Forgot PIN" reset). */
    suspend fun eraseAll() = withContext(Dispatchers.IO) { db.clearAllTables() }

    private fun requireValid(name: String, fee: Int) {
        require(name.isNotBlank() && name.trim().length <= StudentValidator.MAX_NAME_LENGTH) { "Invalid name" }
        require(fee in 0..StudentValidator.MAX_FEE) { "Invalid fee" }
    }
}
