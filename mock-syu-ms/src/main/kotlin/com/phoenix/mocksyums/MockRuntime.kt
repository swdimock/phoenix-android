package com.phoenix.mocksyums

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.phoenix.fytipc.CanbusCodes
import com.phoenix.fytipc.MockSyuDebug
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.log.FrameLogEntry
import com.phoenix.fytipc.log.FrameLogReplayer
import com.phoenix.fytipc.profile.ClimateCodec
import com.phoenix.fytipc.profile.VehicleProfiles
import com.phoenix.fytipc.profile.Wk2ReferenceProfile
import com.phoenix.mocksyums.server.ModuleBinder
import com.phoenix.mocksyums.server.ToolkitBinder
import com.phoenix.mocksyums.sim.AudioPathSimulator
import com.phoenix.mocksyums.sim.RawFrameGenerator
import com.phoenix.mocksyums.sim.VehicleSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Process-wide state of the mock vendor service: the module binders, the vehicle and audio
 * simulations, and the debug controls the [com.phoenix.mocksyums.ui.DebugActivity] drives.
 * Created by whichever of the service or the activity starts first.
 */
class MockRuntime private constructor(private val context: Context) {

    val profile = VehicleProfiles.default
    private val codec: ClimateCodec = profile as? ClimateCodec ?: Wk2ReferenceProfile

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val delivery = Executors.newSingleThreadExecutor { r -> Thread(r, "mock-syu-callbacks") }
    private val modules = ConcurrentHashMap<Int, ModuleBinder>()

    val toolkitBinder: IBinder = ToolkitBinder { module(it) }

    val vehicle = VehicleSimulator(profile, codec, ::publish)
    val audio = AudioPathSimulator(scope, profile, ::publish)
    private val rawFrames = RawFrameGenerator(scope, { vehicle.state.value }, ::publish)

    private val _clients = MutableStateFlow(0)
    private val _registrations = MutableStateFlow(0)
    private val _accOn = MutableStateFlow(true)
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    private val _replayStatus = MutableStateFlow("idle")

    /** Distinct live callback binders across all modules (roughly: connected client apps x modules). */
    val clients: StateFlow<Int> = _clients.asStateFlow()
    val registrations: StateFlow<Int> = _registrations.asStateFlow()
    val accOn: StateFlow<Boolean> = _accOn.asStateFlow()
    val recentCommands: StateFlow<List<String>> = _recent.asStateFlow()
    val replayStatus: StateFlow<String> = _replayStatus.asStateFlow()

    private var replayJob: Job? = null

    init {
        ACTIVE_MODULES.forEach { module(it) }
        coldBoot()
    }

    fun module(id: Int): ModuleBinder = modules.getOrPut(id) {
        ModuleBinder(id, passive = id !in ACTIVE_MODULES, delivery, { onCommand(it) }, ::recountClients)
    }

    fun publish(update: ModuleUpdate) {
        module(update.module).publish(update)
    }

    private fun onCommand(cmd: ModuleCommand) {
        val effect = if (_accOn.value && cmd.module == Modules.CANBUS) vehicle.onCommand(cmd) else null
        audio.onCommand(cmd)
        val line = "${Modules.name(cmd.module)} cmd(${cmd.code}, ${cmd.ints?.contentToString()})" +
            (effect?.let { " -> $it" } ?: "")
        Log.i(TAG, line)
        _recent.value = (listOf(line) + _recent.value).take(RECENT_MAX)
    }

    private fun recountClients() {
        val all = modules.values.flatMapTo(HashSet()) { it.callbackBinders() }
        _clients.value = all.size
        _registrations.value = modules.values.sumOf { it.registrationCount() }
    }

    /** Cold boot: capability flags, full climate snapshot, readiness timer, raw stream. */
    private fun coldBoot() {
        publishCapabilities()
        vehicle.publishAll()
        audio.coldBoot()
        rawFrames.start()
    }

    private fun publishCapabilities() {
        val flags = mapOf(
            CanbusCodes.U_EXIST_DOOR to 1,
            CanbusCodes.U_EXIST_PANORAMA to 0,
            CanbusCodes.U_EXIST_TEMP_OUT to 1,
            CanbusCodes.U_EXIST_CAR_RADIO to 1,
            CanbusCodes.U_EXIST_AIR_CONTROL to 1,
            // UNVERIFIED: the PA base-374 id the wiki lists for a 2014 F-150; the 2011 box may differ.
            Wk2ReferenceProfile.Codes.U_CANBUS_ID to 3015030,
        )
        for ((code, v) in flags) {
            // Readiness codes (default U_EXIST_AIR) are owned by the audio simulation's boot delay.
            if (code in profile.audioReadinessCodes) continue
            publish(ModuleUpdate(Modules.CANBUS, code, intArrayOf(v)))
        }
        if (CanbusCodes.U_EXIST_AIR !in profile.audioReadinessCodes) {
            publish(ModuleUpdate(Modules.CANBUS, CanbusCodes.U_EXIST_AIR, intArrayOf(1)))
        }
        // MAIN 40 = outside temp: tenths °C offset by 1000 in the low 16 bits, bit 28 valid (wiki/wk2-climate).
        // 220 tenths = 22.0 °C ≈ 72 °F — a plausible cabin-side ambient for the emulator.
        publish(ModuleUpdate(Modules.MAIN, 40, intArrayOf(0x10000000 or (1000 + 220))))
    }

    /** Simulated ignition off: everything sleeps and the broadcast tells our apps. */
    fun accOff() {
        if (!_accOn.compareAndSet(true, false)) return
        rawFrames.stop()
        audio.sleep()
        broadcastToOurApps(MockSyuDebug.ACTION_ACC_OFF)
    }

    /** Simulated ignition on: a cold boot, readiness and audible reset. */
    fun accOn() {
        if (!_accOn.compareAndSet(false, true)) return
        broadcastToOurApps(MockSyuDebug.ACTION_ACC_ON)
        coldBoot()
    }

    /** Implicit broadcasts do not reach manifest receivers on Android 8+, so address each app. */
    private fun broadcastToOurApps(action: String) {
        for (pkg in ACC_RECEIVER_PACKAGES) context.sendBroadcast(Intent(action).setPackage(pkg))
    }

    /** Restarts the boot race without an ACC cycle (applies a changed boot delay). */
    fun rebootAudio() = audio.coldBoot()

    /** Replays `files/capture.jsonl` if present, else the bundled sample. */
    fun replayCapture() {
        replayJob?.cancel()
        replayJob = scope.launch {
            val file = File(context.filesDir, CAPTURE_FILE)
            val (name, text) = if (file.exists()) {
                file.path to file.readText()
            } else {
                "assets/$SAMPLE_ASSET" to context.assets.open(SAMPLE_ASSET).bufferedReader().use { it.readText() }
            }
            val entries = runCatching { FrameLogEntry.decodeAll(text) }.getOrElse {
                _replayStatus.value = "bad capture $name: ${it.message}"
                return@launch
            }
            _replayStatus.value = "replaying $name (${entries.size} entries)"
            FrameLogReplayer(entries).play().collect { e ->
                when (e.dir) {
                    FrameLogEntry.Dir.UP -> e.toUpdate()?.let(vehicle::ingest)
                    FrameLogEntry.Dir.MARK -> _replayStatus.value = "replay mark: ${e.note}"
                    FrameLogEntry.Dir.CMD -> Unit
                }
            }
            _replayStatus.value = "replay of $name finished"
        }
    }

    fun stopReplay() {
        replayJob?.cancel()
        _replayStatus.value = "replay stopped"
    }

    companion object {
        private const val TAG = "MockRuntime"
        private const val RECENT_MAX = 12
        const val CAPTURE_FILE = "capture.jsonl"
        const val SAMPLE_ASSET = "captures/sample.jsonl"

        /** Apps that react to the simulated ignition broadcasts. */
        val ACC_RECEIVER_PACKAGES = listOf("com.phoenix.hvacbar", "com.phoenix.launcher")

        /** Modules that hold state and deliver updates; every other id is passive. */
        val ACTIVE_MODULES = setOf(Modules.MAIN, Modules.RADIO, Modules.BT, Modules.SOUND, Modules.CANBUS, Modules.STEER, Modules.CUSTOMER)

        @Volatile private var instance: MockRuntime? = null

        fun get(context: Context): MockRuntime =
            instance ?: synchronized(this) { instance ?: MockRuntime(context.applicationContext).also { instance = it } }
    }
}
