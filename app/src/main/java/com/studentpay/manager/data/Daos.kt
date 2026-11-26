package com.studentpay.manager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY id")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE id = :id")
    fun observe(id: Long): Flow<StudentEntity?>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun get(id: Long): StudentEntity?

    @Query("SELECT * FROM students ORDER BY id")
    suspend fun getAll(): List<StudentEntity>

    @Insert
    suspend fun insert(student: StudentEntity): Long

    @Insert
    suspend fun insertAll(students: List<StudentEntity>)

    @Update
    suspend fun update(student: StudentEntity): Int

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM students")
    suspend fun deleteAll()
}

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
