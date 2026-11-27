package com.studentpay.manager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    /** Students shown in lists (not deleted). */
    @Query("SELECT * FROM students WHERE deleted_at IS NULL ORDER BY id")
    fun observeActive(): Flow<List<StudentEntity>>

    /** Deleted students, most recently deleted first. Their payments are still in the database. */
    @Query("SELECT * FROM students WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC, id")
    fun observeDeleted(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE id = :id AND deleted_at IS NULL")
    fun observeActiveById(id: Long): Flow<StudentEntity?>

    /** Any student, deleted or not. */
    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun get(id: Long): StudentEntity?

    /** Every student including deleted ones (reports and backups need them). */
    @Query("SELECT * FROM students ORDER BY id")
    suspend fun getAll(): List<StudentEntity>

    @Query("UPDATE students SET deleted_at = :at WHERE id = :id AND deleted_at IS NULL")
    suspend fun markDeleted(id: Long, at: Long): Int

    @Query("UPDATE students SET deleted_at = NULL WHERE id = :id")
    suspend fun markRestored(id: Long): Int

    @Insert
    suspend fun insert(student: StudentEntity): Long

    @Insert
    suspend fun insertAll(students: List<StudentEntity>)

    @Update
    suspend fun update(student: StudentEntity): Int

    @Query("DELETE FROM students")
    suspend fun deleteAll()
}

/** Query result: one student's payments summed. Column names match the aliases in [PaymentDao]. */
data class StudentTotalRow(val studentId: Long, val total: Long, val count: Int)

@Dao
interface PaymentDao {
    /** Payments whose "paid for" year is in [fromYear, toYear]. */
    @Query("SELECT * FROM payments WHERE year BETWEEN :fromYear AND :toYear")
    fun observeYears(fromYear: Int, toYear: Int): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE student_id = :studentId ORDER BY year, month_paid_for")
    fun observeForStudent(studentId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments")
    suspend fun getAll(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE year = :year")
    suspend fun getForYear(year: Int): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE student_id = :studentId")
    suspend fun getForStudent(studentId: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE student_id = :studentId AND year = :year AND month_paid_for = :month")
    suspend fun find(studentId: Long, year: Int, month: Int): PaymentEntity?

    @Query("SELECT COUNT(*) FROM payments WHERE student_id = :studentId")
    suspend fun countForStudent(studentId: Long): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE student_id = :studentId")
    suspend fun totalForStudent(studentId: Long): Long

    /** What each deleted student paid in total. */
    @Query(
        "SELECT student_id AS studentId, COALESCE(SUM(amount), 0) AS total, COUNT(*) AS count FROM payments " +
            "WHERE student_id IN (SELECT id FROM students WHERE deleted_at IS NOT NULL) GROUP BY student_id",
    )
    fun observeDeletedTotals(): Flow<List<StudentTotalRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PaymentEntity): Long

    /** Used by Undo: restoring must never clobber a record that exists again. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(payment: PaymentEntity): Long

    @Insert
    suspend fun insertAll(payments: List<PaymentEntity>)

    @Query("UPDATE payments SET paid_at = :paidAt WHERE id = :id")
    suspend fun updateDate(id: Long, paidAt: Long): Int

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}
