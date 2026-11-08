package com.phoenix.launcher.vehicle

import com.phoenix.fytipc.FytToolkit
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.MockSyuDebug
import com.phoenix.fytipc.log.FrameRecorder
import com.phoenix.fytipc.profile.VehicleProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.plus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Start/stop wrapper around fyt-ipc's [FrameRecorder] for the diagnostics screen. Records the profile's
 * climate and audio-readiness codes plus the mock debug codes into `capture-<timestamp>.jsonl` in
 * [directory], and every command the launcher sends.
 */
class CaptureRecorder(
    private val scope: CoroutineScope,
    private val toolkit: FytToolkit,
    private val directory: () -> File?,
    private val profile: () -> VehicleProfile,
    private val log: (String) -> Unit = {},
) {
    private val _file = MutableStateFlow<File?>(null)

    /** The capture being written, or null when idle. */
    val recording: StateFlow<File?> = _file.asStateFlow()

    private var recorder: FrameRecorder? = null

    @Synchronized
    fun start(): File? {
        _file.value?.let { return it }
        val dir = directory()?.apply { mkdirs() } ?: return null.also { log("no captures directory") }
        val file = File(dir, "capture-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.jsonl")
        val subscriptions = FrameRecorder.profileSubscriptions(profile()) +
            (MockSyuDebug.MODULE to setOf(MockSyuDebug.AUDIO_PATH_READY, MockSyuDebug.AUDIBLE))
        return try {
            val r = FrameRecorder.toFile(toolkit, file, subscriptions)
            r.start(scope + Dispatchers.IO)
            r.mark("recording started by com.phoenix.launcher")
            recorder = r
            _file.value = file
            file
        } catch (t: Throwable) {
            log("cannot start capture: $t")
            null
        }
    }

    @Synchronized
    fun stop() {
        recorder?.run {
            runCatching { mark("recording stopped") }
            close()
        }
        recorder = null
        _file.value = null
    }

    fun recordCommand(command: ModuleCommand, note: String? = null) {
        val r = synchronized(this) { recorder } ?: return
        runCatching { r.recordCommand(command, note) }.onFailure { log("capture write failed: $it") }
    }
}
