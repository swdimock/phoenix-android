package com.phoenix.mocksyums.sim

import com.phoenix.fytipc.MockSyuDebug
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.profile.VehicleProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The boot audio race (docs/ARCHITECTURE.md). After a cold boot / ACC on, the profile's
 * [VehicleProfile.audioReadinessCodes] read 0 on CANBUS for [bootDelayMs], then 1. Audio becomes
 * [audible] only when a source-select arrives:
 *  - [VehicleProfile.audioSourceSelectCommand] if the profile defines one (exact match);
 *  - otherwise, as a documented stand-in, ANY cmd on MAIN or CANBUS with code [STAND_IN_SELECT_CODE];
 *  - or any command on the RADIO module (what tapping "Radio" in the stock UI does).
 * With [requireReadyForSelect] (default) a select received before readiness is lost, the way a
 * command sent to an ACM that is still asleep would be. [VehicleProfile.audioSourceRadioCommand]
 * switches back to the OEM source (not audible).
 *
 * Both flags are mirrored on [MockSyuDebug.MODULE] codes [MockSyuDebug.AUDIO_PATH_READY] /
 * [MockSyuDebug.AUDIBLE] so the launcher can display them.
 */
class AudioPathSimulator(
    private val scope: CoroutineScope,
    private val profile: VehicleProfile,
    private val publish: (ModuleUpdate) -> Unit,
) {
    private val _ready = MutableStateFlow(false)
    private val _audible = MutableStateFlow(false)
    private val _bootDelayMs = MutableStateFlow(DEFAULT_BOOT_DELAY_MS)
    private val _bootStartedAt = MutableStateFlow(0L)

    val audioPathReady: StateFlow<Boolean> = _ready.asStateFlow()
    val audible: StateFlow<Boolean> = _audible.asStateFlow()
    val bootDelayMs: StateFlow<Long> = _bootDelayMs.asStateFlow()
    val bootStartedAt: StateFlow<Long> = _bootStartedAt.asStateFlow()

    @Volatile var requireReadyForSelect: Boolean = true

    private var timer: Job? = null

    fun setBootDelay(ms: Long) {
        _bootDelayMs.value = ms.coerceAtLeast(0)
    }

    /** Resets both flags and starts the readiness timer, like a real cold boot. */
    @Synchronized
    fun coldBoot() {
        timer?.cancel()
        setReady(false)
        setAudible(false)
        _bootStartedAt.value = System.currentTimeMillis()
        timer = scope.launch {
            delay(_bootDelayMs.value)
            setReady(true)
        }
    }

    /** Unit asleep: nothing is ready or audible and no timer runs. */
    @Synchronized
    fun sleep() {
        timer?.cancel()
        setReady(false)
        setAudible(false)
    }

    fun onCommand(cmd: ModuleCommand) {
        val select = profile.audioSourceSelectCommand
        val isSelect = when {
            cmd.module == Modules.RADIO -> true
            select != null -> cmd == select
            else -> cmd.module in STAND_IN_MODULES && cmd.code == STAND_IN_SELECT_CODE
        }
        if (cmd == profile.audioSourceRadioCommand) {
            setAudible(false)
            return
        }
        if (!isSelect) return
        if (requireReadyForSelect && !_ready.value) return
        setAudible(true)
    }

    /** Debug override from the mock's own screen. */
    fun forceAudible(value: Boolean) = setAudible(value)

    private fun setReady(value: Boolean) {
        _ready.value = value
        val v = if (value) 1 else 0
        for (code in profile.audioReadinessCodes) publish(ModuleUpdate(Modules.CANBUS, code, intArrayOf(v)))
        publish(ModuleUpdate(MockSyuDebug.MODULE, MockSyuDebug.AUDIO_PATH_READY, intArrayOf(v)))
    }

    private fun setAudible(value: Boolean) {
        _audible.value = value
        publish(ModuleUpdate(MockSyuDebug.MODULE, MockSyuDebug.AUDIBLE, intArrayOf(if (value) 1 else 0)))
    }

    companion object {
        const val DEFAULT_BOOT_DELAY_MS = 8_000L

        /** UNVERIFIED stand-in for the unknown F-150 AUX source-select command. */
        const val STAND_IN_SELECT_CODE = 0xA0
        private val STAND_IN_MODULES = setOf(Modules.MAIN, Modules.CANBUS)
    }
}
