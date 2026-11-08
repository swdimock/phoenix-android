package com.phoenix.hvacbar

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persistent bar preferences: selected vehicle profile and temperature unit. */
class BarSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Id understood by [com.phoenix.fytipc.profile.VehicleProfiles.byId]; null selects the default. */
    val vehicleProfileId: String? get() = prefs.getString(KEY_PROFILE, null)

    private val _useCelsius = MutableStateFlow(prefs.getBoolean(KEY_CELSIUS, false))

    /** True when temperatures are shown in Celsius. Fahrenheit is the default. */
    val useCelsius: StateFlow<Boolean> = _useCelsius.asStateFlow()

    fun toggleUnits() {
        val celsius = !_useCelsius.value
        _useCelsius.value = celsius
        prefs.edit { putBoolean(KEY_CELSIUS, celsius) }
    }

    private companion object {
        const val PREFS_NAME = "hvac_bar"
        const val KEY_PROFILE = "vehicle_profile_id"
        const val KEY_CELSIUS = "use_celsius"
    }
}
