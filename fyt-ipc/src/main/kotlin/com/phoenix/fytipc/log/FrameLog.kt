package com.phoenix.fytipc.log

import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One line of a JSONL capture. Captures are recorded in the truck by the recorder tool (fyt-ipc worker
 * adds `FrameRecorder`) and replayed by `mock-syu-ms` in the emulator.
 *
 * ```json
 * {"t":1234,"dir":"up","module":7,"code":27,"ints":[215],"note":"driver temp"}
 * {"t":1250,"dir":"up","module":7,"code":1019,"ints":[46,41,2,16,0,196]}
 * {"t":5000,"dir":"cmd","module":7,"code":6,"ints":[4,1],"note":"temp left up (user tap)"}
 * {"t":5001,"dir":"mark","note":"tapped Radio in stock UI"}
 * ```
 * `t` is milliseconds since capture start. `dir`: `up` = vendor -> us (ModuleUpdate),
 * `cmd` = us -> vendor (ModuleCommand), `mark` = human annotation.
 */
@Serializable
data class FrameLogEntry(
    val t: Long,
    val dir: Dir,
    val module: Int? = null,
    val code: Int? = null,
    val ints: List<Int>? = null,
    val floats: List<Float>? = null,
    val strings: List<String>? = null,
    val note: String? = null,
) {
    @Serializable
    enum class Dir {
        @SerialName("up") UP,
        @SerialName("cmd") CMD,
        @SerialName("mark") MARK,
    }

    fun toUpdate(): ModuleUpdate? {
        if (dir != Dir.UP || module == null || code == null) return null
        return ModuleUpdate(module, code, ints?.toIntArray(), floats?.toFloatArray(), strings?.toTypedArray(), t)
    }

    fun toCommand(): ModuleCommand? {
        if (dir != Dir.CMD || module == null || code == null) return null
        return ModuleCommand(module, code, ints?.toIntArray(), floats?.toFloatArray(), strings?.toTypedArray())
    }

    companion object {
        val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

        fun fromUpdate(t: Long, u: ModuleUpdate, note: String? = null) = FrameLogEntry(
            t, Dir.UP, u.module, u.code, u.ints?.toList(), u.floats?.toList(), u.strings?.toList(), note,
        )

        fun fromCommand(t: Long, c: ModuleCommand, note: String? = null) = FrameLogEntry(
            t, Dir.CMD, c.module, c.code, c.ints?.toList(), c.floats?.toList(), c.strings?.toList(), note,
        )

        fun mark(t: Long, note: String) = FrameLogEntry(t, Dir.MARK, note = note)

        fun encodeLine(e: FrameLogEntry): String = json.encodeToString(serializer(), e)
        fun decodeLine(line: String): FrameLogEntry? =
            line.trim().takeIf { it.isNotEmpty() && !it.startsWith("#") }?.let { json.decodeFromString(serializer(), it) }
        fun decodeAll(text: String): List<FrameLogEntry> = text.lineSequence().mapNotNull(::decodeLine).toList()
    }
}
