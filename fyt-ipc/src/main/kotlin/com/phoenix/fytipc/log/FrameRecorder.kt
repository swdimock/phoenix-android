package com.phoenix.fytipc.log

import com.phoenix.fytipc.CanbusCodes
import com.phoenix.fytipc.FytToolkit
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.profile.VehicleProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileWriter
import java.io.Writer

/**
 * Records a JSONL capture ([FrameLogEntry] per line) from a live [FytToolkit].
 *
 * [subscriptions] maps module id -> codes to register. With [captureRawFrames] the raw MCU stream
 * (CANBUS [CanbusCodes.U_CANBUS_FRAME_TO_UI], ~26 fps on hardware) is added. `t` is milliseconds
 * since [start]. Every line is flushed so a capture survives the app being killed when the unit
 * sleeps. Thread-safe; [mark] and [recordCommand] may be called from any thread.
 */
class FrameRecorder(
    private val toolkit: FytToolkit,
    subscriptions: Map<Int, Set<Int>>,
    captureRawFrames: Boolean = false,
    private val writer: Writer,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : Closeable {

    val subscriptions: Map<Int, Set<Int>> =
        if (!captureRawFrames) subscriptions
        else subscriptions + (Modules.CANBUS to (subscriptions[Modules.CANBUS].orEmpty() + CanbusCodes.U_CANBUS_FRAME_TO_UI))

    private val lock = Any()
    private var startMs: Long? = null
    private var job: Job? = null
    private var closed = false

    /** Milliseconds since [start], or 0 before it. */
    val elapsedMs: Long get() = synchronized(lock) { startMs?.let { clock() - it } ?: 0L }

    /** Starts collecting in [scope]. Call once. */
    fun start(scope: CoroutineScope): Job = synchronized(lock) {
        check(job == null) { "already started" }
        startMs = clock()
        val flows = subscriptions.filterValues { it.isNotEmpty() }
            .map { (module, codes) -> toolkit.module(module).updates(*codes.toIntArray()) }
        scope.launch {
            flows.merge().collect { write(FrameLogEntry.fromUpdate(elapsedMs, it)) }
        }.also { job = it }
    }

    /** Human annotation, e.g. "tapped Radio in stock UI". */
    fun mark(note: String) = write(FrameLogEntry.mark(elapsedMs, note))

    /** Logs a command the app sent (call next to `module.cmd(command)`). */
    fun recordCommand(command: ModuleCommand, note: String? = null) =
        write(FrameLogEntry.fromCommand(elapsedMs, command, note))

    private fun write(entry: FrameLogEntry) {
        synchronized(lock) {
            if (closed) return
            writer.write(FrameLogEntry.encodeLine(entry))
            writer.write("\n")
            writer.flush()
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            job?.cancel()
            runCatching { writer.flush() }
            runCatching { writer.close() }
        }
    }

    companion object {
        /** Climate read codes plus audio-readiness codes of [profile], all on CANBUS. */
        fun profileSubscriptions(profile: VehicleProfile): Map<Int, Set<Int>> =
            mapOf(Modules.CANBUS to profile.climateReadCodes + profile.audioReadinessCodes)

        fun toFile(
            toolkit: FytToolkit,
            file: File,
            subscriptions: Map<Int, Set<Int>>,
            captureRawFrames: Boolean = false,
            append: Boolean = false,
        ): FrameRecorder = FrameRecorder(toolkit, subscriptions, captureRawFrames, BufferedWriter(FileWriter(file, append)))
    }
}
