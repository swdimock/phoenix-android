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
        fanSpeed = 3, fanAuto = false,
        driverTemp = Setpoint(tenthsC = 222), passengerTemp = Setpoint(tenthsC = 222),
        driverSeatHeat = 0, driverSeatCool = 2, passengerSeatHeat = 0, passengerSeatCool = 0,
    ).withMode(AirMode.FACE)

    val autoDual = typicalDriving.withMode(AirMode.FACE_FEET).copy(
        auto = true, dual = true, fanSpeed = 5,
        driverTemp = Setpoint(tenthsC = 211), passengerTemp = Setpoint(sentinel = Setpoint.Sentinel.HI),
        driverSeatCool = 0, passengerSeatHeat = 3,
    )

    /** Full AUTO as the WK2 reports it at rest: fan code 15, every airflow flag clear. */
    val fullAuto = typicalDriving.withMode(AirMode.AUTO).copy(
        auto = true, fanSpeed = null, fanAuto = true, dual = true, driverSeatCool = 0,
    )

    /** Named samples: climate state plus the connection state to render with. */
    val byName: Map<String, Pair<ClimateState, ConnectionState>> = mapOf(
        "unknown" to (ClimateState.EMPTY to ConnectionState.CONNECTED),
        "typical" to (typicalDriving to ConnectionState.CONNECTED),
        "auto-dual" to (autoDual to ConnectionState.CONNECTED),
        "full-auto" to (fullAuto to ConnectionState.CONNECTED),
        "disconnected" to (typicalDriving to ConnectionState.DISCONNECTED),
    )
}
