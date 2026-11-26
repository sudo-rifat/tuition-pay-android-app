package com.studentpay.manager

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.studentpay.manager.data.AppDatabase
import com.studentpay.manager.data.BackupService
import com.studentpay.manager.data.KeystoreCrypto
import com.studentpay.manager.data.PinRepository
import com.studentpay.manager.data.SettingsRepository
import com.studentpay.manager.data.StudentPayRepository
import com.studentpay.manager.data.settingsDataStore
import com.studentpay.manager.pdf.ReportService
import java.io.File

class App : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Manual dependency container: a handful of singletons, created lazily so cold start stays fast. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(app) }
    val repository: StudentPayRepository by lazy { StudentPayRepository(database) }
    val settings: SettingsRepository by lazy { SettingsRepository(app.settingsDataStore) }

    /** noBackupFilesDir: the PIN verifier is never included in Android backups or device transfer. */
    private val pinStore by lazy {
        PreferenceDataStoreFactory.create(produceFile = { File(app.noBackupFilesDir, "pin.preferences_pb") })
    }
    val pin: PinRepository by lazy { PinRepository(pinStore, KeystoreCrypto()) }

    val backup: BackupService by lazy { BackupService(app, repository) }
    val reports: ReportService by lazy { ReportService(app, repository) }
}
