package com.studentpay.manager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Version 1 = first native schema (mirrors the legacy IndexedDB `StudentPayDB` v1 stores).
 *
 * Migration rule for every future change: bump [version], add a Migration to [MIGRATIONS], commit the
 * generated schema JSON from app/schemas, and add a MigrationTestHelper test. Never use a destructive fallback:
 * this database holds the user's financial records.
 */
@Database(
    entities = [StudentEntity::class, PaymentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        const val NAME = "student_pay.db"

        /** Empty at v1. Add Migration(1, 2) etc. here. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
