package com.phoenix.hvacbar

import android.app.Application
import android.content.Context
import com.phoenix.fytipc.FytToolkit
import com.phoenix.fytipc.profile.VehicleProfiles
import com.phoenix.hvacbar.data.ClimateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide singletons. The toolkit is bound once per process; the overlay service and the debug
 * activity share the same [repository].
 */
class HvacBarApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: BarSettings by lazy { BarSettings(this) }

    val toolkit: FytToolkit by lazy { FytToolkit.connect(this) }

    val repository: ClimateRepository by lazy {
        ClimateRepository(
            toolkit = toolkit,
            profile = VehicleProfiles.byId(settings.vehicleProfileId),
            scope = appScope,
        ).also { it.start() }
    }
}

/** The [HvacBarApp] owning this context. */
val Context.barApp: HvacBarApp get() = applicationContext as HvacBarApp
