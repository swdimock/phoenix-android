package com.phoenix.fytipc.log

import com.phoenix.fytipc.ModuleUpdate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Replays decoded capture entries with their original relative timing, divided by [speed]
 * (2.0 = twice as fast). Pure Kotlin; timing uses coroutine `delay`, so tests can run it on
 * virtual time. With [skipLeadingSilence] the first entry is emitted immediately instead of
 * after its own `t`.
 */
class FrameLogReplayer(
    entries: List<FrameLogEntry>,
    private val speed: Double = 1.0,
    private val skipLeadingSilence: Boolean = true,
) {
    init {
        require(speed > 0.0) { "speed must be positive: $speed" }
    }

    /** Entries in timestamp order (stable for equal `t`). */
    val entries: List<FrameLogEntry> = entries.sortedBy { it.t }

    /** Capture length in original milliseconds. */
    val durationMs: Long get() = if (entries.isEmpty()) 0 else entries.last().t - origin

    private val origin: Long get() = if (skipLeadingSilence) entries.firstOrNull()?.t ?: 0 else 0

    /** Every entry (up, cmd, mark) at its scaled time. */
    fun play(): Flow<FrameLogEntry> = flow {
        var prev = origin
        for (e in entries) {
            val wait = ((e.t - prev) / speed).toLong()
            if (wait > 0) delay(wait)
            prev = maxOf(prev, e.t)
            emit(e)
        }
    }

    /** Only `up` entries, re-stamped with the current wall time as they are emitted. */
    fun updates(): Flow<ModuleUpdate> = play().mapNotNull { e ->
        e.toUpdate()?.copy(timestampMs = System.currentTimeMillis())
    }

    companion object {
        fun fromText(text: String, speed: Double = 1.0): FrameLogReplayer =
            FrameLogReplayer(FrameLogEntry.decodeAll(text), speed)
    }
}
