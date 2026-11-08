package com.phoenix.fytipc.profile

import com.google.common.truth.Truth.assertThat
import com.phoenix.fytipc.CanbusCodes
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.climate.AirMode
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.climate.Setpoint
import com.phoenix.fytipc.profile.Wk2ReferenceProfile.Codes
import org.junit.Before
import org.junit.Test

class ClimateReducersTest {

    private val profile = VehicleProfiles.default

    @Before
    fun reset() = ClimateReducers.resetLatches()

    private fun up(code: Int, v: Int, module: Int = Modules.CANBUS) = ModuleUpdate(module, code, intArrayOf(v), timestampMs = 42)

    private fun fold(vararg updates: ModuleUpdate, from: ClimateState = ClimateState.EMPTY) =
        updates.fold(from) { s, u -> profile.reduce(s, u) }

    /** Registration replay captured on the WK2 (wk2-climate VEHICLE_BASELINE). */
    private val baseline = arrayOf(
        up(Codes.U_AIR_POWER, 1), up(Codes.U_AIR_AC, 1), up(Codes.U_AIR_AUTO, 1), up(Codes.U_AIR_DUAL_SYNC, 1),
        up(Codes.U_AIR_CYCLE, 0), up(Codes.U_AIR_ACMAX, 0), up(Codes.U_AIR_FRONT_DEFROST, 0), up(Codes.U_AIR_REAR_DEFROST, 0),
        up(Codes.U_AIR_WIND_LEVEL, 15), up(Codes.U_AIR_TEMP_UNIT, 1), up(Codes.U_AIR_TEMP_LEFT, 68), up(Codes.U_AIR_TEMP_RIGHT, 68),
        up(Codes.U_AIR_BLOW_UP_LEFT, 0), up(Codes.U_AIR_BLOW_BODY_LEFT, 0), up(Codes.U_AIR_BLOW_FOOT_LEFT, 0),
        up(Codes.U_AIR_SEAT_HEAT_LEFT, 0), up(Codes.U_AIR_SEAT_HEAT_RIGHT, 3), up(Codes.U_AIR_SEAT_BLOW_LEFT, 1),
        up(Codes.U_AIR_SEAT_BLOW_RIGHT, 0),
    )

    @Test
    fun `baseline replay produces the expected snapshot`() {
        val s = fold(*baseline)
        assertThat(s.power).isTrue()
        assertThat(s.ac).isTrue()
        assertThat(s.auto).isTrue()
        assertThat(s.dual).isTrue() // 62 = 1 means zones independent
        assertThat(s.recirculate).isFalse()
        assertThat(s.maxAc).isFalse()
        assertThat(s.fanSpeed).isNull() // 15 = AUTO sentinel, not a speed
        assertThat(s.driverTemp.displayF()).isEqualTo("68")
        assertThat(s.passengerTemp.displayF()).isEqualTo("68")
        assertThat(s.mode).isEqualTo(AirMode.UNKNOWN) // AUTO owns airflow: all flags clear
        assertThat(s.driverSeatHeat).isEqualTo(0)
        assertThat(s.passengerSeatHeat).isEqualTo(3)
        assertThat(s.driverSeatCool).isEqualTo(1)
        assertThat(s.hasAnyData).isTrue()
        assertThat(s.lastUpdateMs).isEqualTo(42)
    }

    @Test
    fun `temperature sentinels and range`() {
        assertThat(fold(up(Codes.U_AIR_TEMP_LEFT, -2)).driverTemp.sentinel).isEqualTo(Setpoint.Sentinel.LO)
        assertThat(fold(up(Codes.U_AIR_TEMP_LEFT, -3)).driverTemp.sentinel).isEqualTo(Setpoint.Sentinel.HI)
        assertThat(fold(up(Codes.U_AIR_TEMP_LEFT, -1)).driverTemp.isKnown).isFalse()
        assertThat(fold(up(Codes.U_AIR_TEMP_LEFT, 200)).driverTemp.isKnown).isFalse()
        for (f in 60..85) {
            assertThat(fold(up(Codes.U_AIR_TEMP_RIGHT, f)).passengerTemp.displayF()).isEqualTo(f.toString())
        }
        // Celsius unit: half degrees.
        val c = fold(up(Codes.U_AIR_TEMP_UNIT, 0), up(Codes.U_AIR_TEMP_LEFT, 44))
        assertThat(c.driverTemp.tenthsC).isEqualTo(220)
    }

    @Test
    fun `airflow flags fold into modes in any arrival order`() {
        val face = fold(up(Codes.U_AIR_BLOW_UP_LEFT, 0), up(Codes.U_AIR_BLOW_BODY_LEFT, 1), up(Codes.U_AIR_BLOW_FOOT_LEFT, 0))
        assertThat(face.mode).isEqualTo(AirMode.FACE)
        val faceFeet = profile.reduce(face, up(Codes.U_AIR_BLOW_FOOT_LEFT, 1))
        assertThat(faceFeet.mode).isEqualTo(AirMode.FACE_FEET)
        // FACE -> FEET_DEFROST via an unrecognised intermediate (up+body).
        val mid = profile.reduce(face, up(Codes.U_AIR_BLOW_UP_LEFT, 1))
        assertThat(mid.mode).isEqualTo(AirMode.UNKNOWN)
        val mid2 = profile.reduce(mid, up(Codes.U_AIR_BLOW_BODY_LEFT, 0))
        assertThat(mid2.mode).isEqualTo(AirMode.DEFROST)
        val defrostFeet = profile.reduce(mid2, up(Codes.U_AIR_BLOW_FOOT_LEFT, 1))
        assertThat(defrostFeet.mode).isEqualTo(AirMode.FEET_DEFROST)
        // Through the all-up combination as well.
        val a = profile.reduce(faceFeet, up(Codes.U_AIR_BLOW_UP_LEFT, 1)) // 111
        val b = profile.reduce(a, up(Codes.U_AIR_BLOW_BODY_LEFT, 0)) // 101
        assertThat(b.mode).isEqualTo(AirMode.FEET_DEFROST)
    }

    @Test
    fun `non climate updates leave state untouched`() {
        val s = fold(*baseline)
        assertThat(profile.reduce(s, up(Codes.U_AIR_POWER, 0, module = Modules.SOUND))).isSameInstanceAs(s)
        assertThat(profile.reduce(s, up(9999, 1))).isSameInstanceAs(s)
        assertThat(profile.reduce(s, ModuleUpdate(Modules.CANBUS, Codes.U_AIR_AC))).isSameInstanceAs(s)
    }

    @Test
    fun `encode is the inverse of reduce`() {
        val s = fold(*baseline).copy(mode = AirMode.FEET_DEFROST, fanSpeed = 5, auto = false)
        val codec = FordF150Profile as ClimateCodec
        val again = codec.encode(s).entries.fold(ClimateState.EMPTY) { acc, (code, v) -> profile.reduce(acc, up(code, v)) }
        assertThat(again).isEqualTo(s.copy(lastUpdateMs = 42))
        assertThat(codec.encode(fold(*baseline))[Codes.U_AIR_WIND_LEVEL]).isEqualTo(15)
    }

    @Test
    fun `audio readiness signal`() {
        assertThat(profile.audioReadinessCodes).containsExactly(CanbusCodes.U_EXIST_AIR)
        assertThat(profile.isAudioPathReadySignal(up(CanbusCodes.U_EXIST_AIR, 1))).isTrue()
        assertThat(profile.isAudioPathReadySignal(up(CanbusCodes.U_EXIST_AIR, 0))).isFalse()
        assertThat(profile.isAudioPathReadySignal(up(CanbusCodes.U_EXIST_AIR, 1, module = Modules.MAIN))).isFalse()
    }

    @Test
    fun `temperature steps into and out of sentinels`() {
        val r = 60..85
        val lo = Setpoint(sentinel = Setpoint.Sentinel.LO)
        val hi = Setpoint(sentinel = Setpoint.Sentinel.HI)
        assertThat(FytTemperature.step(FytTemperature.ofF(85), +1, r)).isEqualTo(hi)
        assertThat(FytTemperature.step(hi, +1, r)).isEqualTo(hi)
        assertThat(FytTemperature.step(hi, -1, r).displayF()).isEqualTo("85")
        assertThat(FytTemperature.step(FytTemperature.ofF(60), -1, r)).isEqualTo(lo)
        assertThat(FytTemperature.step(lo, +1, r).displayF()).isEqualTo("60")
        assertThat(FytTemperature.step(FytTemperature.ofF(70), +1, r).displayF()).isEqualTo("71")
        assertThat(FytTemperature.encodeF(lo)).isEqualTo(-2)
        assertThat(FytTemperature.encodeF(hi)).isEqualTo(-3)
    }
}
