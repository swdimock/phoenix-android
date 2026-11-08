package com.phoenix.mocksyums.sim

import com.phoenix.fytipc.CanbusCodes
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.climate.ClimateState
import com.phoenix.fytipc.mcu.McuFrame
import com.phoenix.fytipc.profile.FytTemperature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Emits a plausible raw MCU stream on CANBUS [CanbusCodes.U_CANBUS_FRAME_TO_UI] (int[] of unsigned
 * bytes, one frame per update) at about [fps] frames/s; hardware runs ~26.
 *  - `2E 29 02 lo hi`: steering angle, int16 LE, 0.1 deg/LSB (decoded on WK2, wiki page 11),
 *    swept slowly by a sine.
 *  - `2E 20 02 01 01`: the static frame from the McuFrame KDoc capture.
 *  - `2E 7A ...`: SYNTHETIC, not a real F-150 id: fan, driver/passenger setpoint (F), flags; there
 *    only so recorders and diff tools see payloads that move with the climate state.
 */
class RawFrameGenerator(
    private val scope: CoroutineScope,
    private val climate: () -> ClimateState,
    private val publish: (ModuleUpdate) -> Unit,
    private val fps: Int = 5,
) {
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var tick = 0
            val period = 1000L / fps
            while (isActive) {
                val frame = when (tick % 3) {
                    0 -> steering(tick)
                    1 -> McuFrame.of(0x20, 0x01, 0x01)
                    else -> synthetic(climate())
                }
                publish(ModuleUpdate(Modules.CANBUS, CanbusCodes.U_CANBUS_FRAME_TO_UI, frame.toInts()))
                tick++
                delay(period)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    private fun steering(tick: Int): McuFrame {
        val tenths = (900 * sin(tick / 40.0)).roundToInt()
        return McuFrame.of(0x29, tenths and 0xFF, (tenths shr 8) and 0xFF)
    }

    private fun synthetic(s: ClimateState): McuFrame {
        val flags = (if (s.ac == true) 1 else 0) or (if (s.auto == true) 2 else 0) or
            (if (s.recirculate == true) 4 else 0) or (if (s.dual == true) 8 else 0)
        return McuFrame.of(
            0x7A,
            if (s.fanAuto == true) 0x0F else s.fanSpeed ?: 0,
            (FytTemperature.encodeF(s.driverTemp) ?: 0) and 0xFF,
            (FytTemperature.encodeF(s.passengerTemp) ?: 0) and 0xFF,
            flags,
        )
    }

    private fun McuFrame.toInts(): IntArray = encode().let { b -> IntArray(b.size) { b[it].toInt() and 0xFF } }
}
