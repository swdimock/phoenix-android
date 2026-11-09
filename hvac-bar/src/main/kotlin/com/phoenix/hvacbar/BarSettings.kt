package com.phoenix.hvacbar

import android.content.Context
import androidx.core.content.edit
import com.phoenix.uitheme.AccentPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persistent bar preferences: selected vehicle profile, temperature unit and accent preset. */
class BarSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Id understood by [com.phoenix.fytipc.profile.VehicleProfiles.byId]; null selects the default. */
    val vehicleProfileId: String? get() = prefs.getString(KEY_PROFILE, null)

    private val _useCelsius = MutableStateFlow(prefs.getBoolean(KEY_CELSIUS, false))

    /** True when temperatures are shown in Celsius. Fahrenheit is the default. */
    val useCelsius: StateFlow<Boolean> = _useCelsius.asStateFlow()

    private val _accentPreset = MutableStateFlow(AccentPreset.byId(prefs.getString(KEY_ACCENT, null)))

    /** Accent pushed by the launcher (`ThemeBroadcast`). [AccentPreset.DEFAULT] (Tuxedo Black) until the launcher says otherwise. */
    val accentPreset: StateFlow<AccentPreset> = _accentPreset.asStateFlow()

    fun toggleUnits() {
        val celsius = !_useCelsius.value
        _useCelsius.value = celsius
        prefs.edit { putBoolean(KEY_CELSIUS, celsius) }
    }

    fun setAccentPreset(preset: AccentPreset) {
        _accentPreset.value = preset
        prefs.edit { putString(KEY_ACCENT, preset.id) }
    }

    private companion object {
        const val PREFS_NAME = "hvac_bar"
        const val KEY_PROFILE = "vehicle_profile_id"
        const val KEY_CELSIUS = "use_celsius"
        const val KEY_ACCENT = "accent_preset"
    }
}
