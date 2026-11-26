package com.studentpay.manager.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.studentpay.manager.domain.Payment
import com.studentpay.manager.domain.PaymentType
import com.studentpay.manager.domain.Student

/** Legacy IndexedDB store `students` (keyPath id, autoIncrement). */
@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Stable code "A" or "B" (never an enum ordinal). */
    @ColumnInfo(name = "payment_type") val paymentType: String,
    @ColumnInfo(name = "monthly_fee", defaultValue = "0") val monthlyFee: Int,
)

/**
 * Legacy IndexedDB store `payments`. The legacy unique compound index
 * [student_id, year, month_paid_for] is preserved as a UNIQUE index, so duplicate payments are
 * impossible at the database level. Deleting a student cascades to the payments (legacy deleteStudent).
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["student_id", "year", "month_paid_for"], unique = true),
        Index(value = ["year"]),
    ],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "student_id") val studentId: Long,
    val year: Int,
    /** 0 = January ... 11 = December, as in the legacy data. */
    @ColumnInfo(name = "month_paid_for") val monthPaidFor: Int,
    @ColumnInfo(name = "paid_on_month") val paidOnMonth: Int?,
    /** UTC epoch millis (legacy `date` ISO string). */
    @ColumnInfo(name = "paid_at") val paidAt: Long,
)

internal fun StudentEntity.toDomain(): Student =
    Student(id, name, PaymentType.fromCode(paymentType) ?: PaymentType.A, monthlyFee)

internal fun PaymentEntity.toDomain(): Payment =
    Payment(id, studentId, year, monthPaidFor, paidOnMonth, paidAt)

internal fun Student.toEntity(): StudentEntity = StudentEntity(id, name, type.code, monthlyFee)

internal fun Payment.toEntity(): PaymentEntity =
    PaymentEntity(id, studentId, year, monthPaidFor, paidOnMonth, paidAtMillis)
