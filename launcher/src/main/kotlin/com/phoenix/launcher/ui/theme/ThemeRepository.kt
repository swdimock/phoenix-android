package com.phoenix.launcher.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.phoenix.uitheme.AccentPreset
import com.phoenix.uitheme.ThemeBroadcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

/** Persists the accent preset and forwards it to the HVAC bar. */
class ThemeRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.themeDataStore

    val preset: Flow<AccentPreset> =
        store.data.map { AccentPreset.byId(it[KEY_PRESET]) }.distinctUntilChanged()

    suspend fun setPreset(preset: AccentPreset) {
        store.edit { it[KEY_PRESET] = preset.id }
    }

    /** Tells the HVAC bar to use [preset]. Explicit package: implicit broadcasts don't reach manifest receivers. */
    fun broadcast(preset: AccentPreset) {
        appContext.sendBroadcast(ThemeBroadcast.intent(preset, HVAC_BAR_PACKAGE))
    }

    private companion object {
        val KEY_PRESET = stringPreferencesKey("accent_preset")
        const val HVAC_BAR_PACKAGE = "com.phoenix.hvacbar"
    }
}
