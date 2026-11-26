package com.studentpay.manager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How long the app may stay in the background before the PIN is asked again. */
enum class LockTimeout(val millis: Long) {
    IMMEDIATE(0L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(300_000L),
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val lockTimeout: LockTimeout = LockTimeout.ONE_MINUTE,
    /** FLAG_SECURE: hides content in the recent-apps screen and blocks screenshots. */
    val hideInRecents: Boolean = true,
)

/** One DataStore instance per file per process - hence the top-level delegate. */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val store: DataStore<Preferences>) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LOCK_TIMEOUT = stringPreferencesKey("lock_timeout")
        val HIDE_IN_RECENTS = booleanPreferencesKey("hide_in_recents")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val d = AppSettings()
            AppSettings(
                theme = p[Keys.THEME].toEnumOr(d.theme),
                lockTimeout = p[Keys.LOCK_TIMEOUT].toEnumOr(d.lockTimeout),
                hideInRecents = p[Keys.HIDE_IN_RECENTS] ?: d.hideInRecents,
            )
        }

    suspend fun setTheme(mode: ThemeMode) { store.edit { it[Keys.THEME] = mode.name } }
    suspend fun setLockTimeout(t: LockTimeout) { store.edit { it[Keys.LOCK_TIMEOUT] = t.name } }
    suspend fun setHideInRecents(hide: Boolean) { store.edit { it[Keys.HIDE_IN_RECENTS] = hide } }

    private inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E =
        enumValues<E>().firstOrNull { it.name == this } ?: default
}
