package com.phoenix.hvacbar.ui

import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.climate.AirMode
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.climate.Setpoint

/** Canned states for previews and `DebugBarActivity --es sample <name>`. */
object BarSamples {

    val typicalDriving = ClimateState(
        power = true, auto = false, ac = true, maxAc = false, dual = false,
        recirculate = false, rearDefrost = false, frontDefrost = false,
        fanSpeed = 3, mode = AirMode.FACE,
        driverTemp = Setpoint(tenthsC = 222), passengerTemp = Setpoint(tenthsC = 222),
        driverSeatHeat = 0, driverSeatCool = 2, passengerSeatHeat = 0, passengerSeatCool = 0,
    )

    val autoDual = typicalDriving.copy(
        auto = true, dual = true, fanSpeed = 5, mode = AirMode.FACE_FEET,
        driverTemp = Setpoint(tenthsC = 211), passengerTemp = Setpoint(sentinel = Setpoint.Sentinel.HI),
        driverSeatCool = 0, passengerSeatHeat = 3,
    )

    /** Named samples: climate state plus the connection state to render with. */
    val byName: Map<String, Pair<ClimateState, ConnectionState>> = mapOf(
        "unknown" to (ClimateState.EMPTY to ConnectionState.CONNECTED),
        "typical" to (typicalDriving to ConnectionState.CONNECTED),
        "auto-dual" to (autoDual to ConnectionState.CONNECTED),
        "disconnected" to (typicalDriving to ConnectionState.DISCONNECTED),
    )
}
