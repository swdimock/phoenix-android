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
        outsideTemp = Setpoint(tenthsC = 180), // 18.0 C ≈ 64 F
        driverSeatHeat = 2, passengerSeatHeat = 0,
    ).withMode(AirMode.FACE)

    val autoDual = typicalDriving.withMode(AirMode.FACE_FEET).copy(
        auto = true, dual = true, fanSpeed = 5,
        driverTemp = Setpoint(tenthsC = 211), passengerTemp = Setpoint(sentinel = Setpoint.Sentinel.HI),
        driverSeatHeat = 0, passengerSeatHeat = 3,
    )

    /** Full AUTO as the WK2 reports it at rest: fan code 15, every airflow flag clear. */
    val fullAuto = typicalDriving.withMode(AirMode.AUTO).copy(
        auto = true, fanSpeed = null, fanAuto = true, dual = true, driverSeatHeat = 0,
    )

    /** Cold morning: front defrost on, feet + windshield airflow, fan high, seats heated. */
    val defrost = typicalDriving.withMode(AirMode.FEET_DEFROST).copy(
        ac = false, frontDefrost = true, recirculate = false, fanSpeed = 6,
        driverTemp = Setpoint(sentinel = Setpoint.Sentinel.HI), passengerTemp = Setpoint(tenthsC = 240),
        outsideTemp = Setpoint(tenthsC = 20), // 2.0 C ≈ 36 F
        driverSeatHeat = 3, passengerSeatHeat = 1,
    )

    /** Named samples: climate state plus the connection state to render with. */
    val byName: Map<String, Pair<ClimateState, ConnectionState>> = mapOf(
        "unknown" to (ClimateState.EMPTY to ConnectionState.CONNECTED),
        "typical" to (typicalDriving to ConnectionState.CONNECTED),
        "auto-dual" to (autoDual to ConnectionState.CONNECTED),
        "full-auto" to (fullAuto to ConnectionState.CONNECTED),
        "defrost" to (defrost to ConnectionState.CONNECTED),
        "disconnected" to (typicalDriving to ConnectionState.DISCONNECTED),
    )
}
