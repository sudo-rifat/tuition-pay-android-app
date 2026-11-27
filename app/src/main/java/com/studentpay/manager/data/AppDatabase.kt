package com.studentpay.manager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 = first native schema (mirrors the legacy IndexedDB `StudentPayDB` v1 stores).
 * Version 2 = payments record the amount charged at the time (`amount`), so editing a student's fee
 *             no longer rewrites past totals.
 * Version 3 = students can be deleted without losing income: `students.deleted_at` (null = active).
 *
 * Migration rule for every future change: bump [version], add a Migration to [MIGRATIONS], commit the
 * generated schema JSON from app/schemas, and add a MigrationTestHelper test. Never use a destructive fallback:
 * this database holds the user's financial records.
 */
@Database(
    entities = [StudentEntity::class, PaymentEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        const val NAME = "student_pay.db"

        /**
         * v1 -> v2: add `payments.amount` and backfill every existing payment with its student's CURRENT fee
         * (the best information that exists: v1 never stored the historical amount). From now on the amount is
         * written when the payment is recorded and never changes.
         * The SQL is kept in [MIGRATION_1_2_SQL] so it can be tested against a real SQLite.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
            }
        }

        val MIGRATION_1_2_SQL: List<String> = listOf(
            "ALTER TABLE payments ADD COLUMN amount INTEGER NOT NULL DEFAULT 0",
            "UPDATE payments SET amount = COALESCE((SELECT monthly_fee FROM students WHERE students.id = payments.student_id), 0)",
        )

        /** v2 -> v3: add the nullable `students.deleted_at`. Every existing student stays active (NULL). */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3_SQL.forEach { db.execSQL(it) }
            }
        }

        val MIGRATION_2_3_SQL: List<String> = listOf(
            "ALTER TABLE students ADD COLUMN deleted_at INTEGER",
        )

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
