package com.phoenix.launcher.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.phoenix.launcher.mode.LauncherMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Source of truth for [LauncherSettings]. Survives process death. */
interface SettingsRepository {
    val settings: Flow<LauncherSettings>

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings)
}

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

/** [SettingsRepository] backed by Preferences DataStore. */
class DataStoreSettingsRepository(
    private val store: DataStore<Preferences>,
    private val defaults: LauncherSettings = LauncherSettings(),
) : SettingsRepository {

    override val settings: Flow<LauncherSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.DEFAULT_MODE] = next.defaultMode.name
            prefs[Keys.VEHICLE_PROFILE_ID] = next.vehicleProfileId
            prefs[Keys.AUDIO_READY_TIMEOUT_MS] = next.audioReadyTimeoutMs
            prefs[Keys.AA_RECEIVER_PACKAGE] = next.aaReceiverPackage
            prefs[Keys.AA_TRANSPORT] = next.aaTransport.id
            prefs[Keys.AA_TCP_HOST] = next.aaTcpHost
            prefs[Keys.AA_TCP_PORT] = next.aaTcpPort
        }
    }

    private fun Preferences.toSettings(): LauncherSettings {
        return LauncherSettings(
            defaultMode = LauncherMode.parse(this[Keys.DEFAULT_MODE]) ?: defaults.defaultMode,
            vehicleProfileId = this[Keys.VEHICLE_PROFILE_ID] ?: defaults.vehicleProfileId,
            audioReadyTimeoutMs = this[Keys.AUDIO_READY_TIMEOUT_MS] ?: defaults.audioReadyTimeoutMs,
            aaReceiverPackage = this[Keys.AA_RECEIVER_PACKAGE]?.takeIf { it.isNotBlank() } ?: defaults.aaReceiverPackage,
            aaTransport = AaTransport.parse(this[Keys.AA_TRANSPORT]) ?: defaults.aaTransport,
            aaTcpHost = this[Keys.AA_TCP_HOST]?.takeIf { it.isNotBlank() } ?: defaults.aaTcpHost,
            aaTcpPort = this[Keys.AA_TCP_PORT]?.takeIf { it in 1..65535 } ?: defaults.aaTcpPort,
        )
    }

    private object Keys {
        val DEFAULT_MODE = stringPreferencesKey("defaultMode")
        val VEHICLE_PROFILE_ID = stringPreferencesKey("vehicleProfileId")
        val AUDIO_READY_TIMEOUT_MS = longPreferencesKey("audioReadyTimeoutMs")
        val AA_RECEIVER_PACKAGE = stringPreferencesKey("aaReceiverPackage")
        val AA_TRANSPORT = stringPreferencesKey("aaTransport")
        val AA_TCP_HOST = stringPreferencesKey("aaTcpHost")
        val AA_TCP_PORT = intPreferencesKey("aaTcpPort")
    }

    companion object {
        fun create(context: Context) =
            DataStoreSettingsRepository(context.applicationContext.launcherDataStore, LauncherSettings.platformDefaults())
    }
}
