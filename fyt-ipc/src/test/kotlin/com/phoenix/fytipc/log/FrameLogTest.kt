package com.phoenix.fytipc.log

import com.google.common.truth.Truth.assertThat
import com.phoenix.fytipc.ModuleCommand
import com.phoenix.fytipc.ModuleUpdate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FrameLogTest {

    private val sample = """
        # comment lines and blanks are skipped

        {"t":1234,"dir":"up","module":7,"code":27,"ints":[215],"note":"driver temp"}
        {"t":1250,"dir":"up","module":7,"code":1019,"ints":[46,41,2,16,0,196]}
        {"t":5000,"dir":"cmd","module":7,"code":6,"ints":[4,1],"note":"temp left up (user tap)"}
        {"t":5001,"dir":"mark","note":"tapped Radio in stock UI"}
    """.trimIndent()

    @Test
    fun `decodes the documented example`() {
        val entries = FrameLogEntry.decodeAll(sample)
        assertThat(entries).hasSize(4)
        assertThat(entries[0].toUpdate()).isEqualTo(ModuleUpdate(7, 27, intArrayOf(215)))
        assertThat(entries[1].toUpdate()!!.ints).isEqualTo(intArrayOf(46, 41, 2, 16, 0, 196))
        assertThat(entries[2].toCommand()).isEqualTo(ModuleCommand(7, 6, intArrayOf(4, 1)))
        assertThat(entries[2].toUpdate()).isNull()
        assertThat(entries[3].dir).isEqualTo(FrameLogEntry.Dir.MARK)
        assertThat(entries[3].note).isEqualTo("tapped Radio in stock UI")
    }

    @Test
    fun `encode decode round trip omits nulls`() {
        val u = ModuleUpdate(7, 21, intArrayOf(3), floatArrayOf(1.5f), arrayOf("x"))
        val entries = listOf(
            FrameLogEntry.fromUpdate(10, u, "fan"),
            FrameLogEntry.fromCommand(20, ModuleCommand(7, 6, intArrayOf(1, 6))),
            FrameLogEntry.mark(30, "hello \"quoted\""),
        )
        for (e in entries) {
            val line = FrameLogEntry.encodeLine(e)
            assertThat(line).doesNotContain("null")
            assertThat(line).doesNotContain("\n")
            assertThat(FrameLogEntry.decodeLine(line)).isEqualTo(e)
        }
        assertThat(FrameLogEntry.encodeLine(entries[2])).isEqualTo("""{"t":30,"dir":"mark","note":"hello \"quoted\""}""")
        assertThat(FrameLogEntry.decodeLine("""{"t":1,"dir":"up","module":7,"code":1,"extra":true}""")).isNotNull()
    }

    @Test
    fun `replayer keeps relative timing and scales it`() = runTest {
        val entries = FrameLogEntry.decodeAll(sample)
        val times = mutableListOf<Long>()
        val job = launch { FrameLogReplayer(entries).play().collect { times += currentTime } }
        runCurrent()
        advanceTimeBy(10_000)
        job.join()
        // First entry immediately (leading silence skipped), then the original gaps.
        assertThat(times).containsExactly(0L, 16L, 3766L, 3767L).inOrder()

        val fast = mutableListOf<Long>()
        val start = currentTime
        FrameLogReplayer(entries, speed = 2.0).play().collect { fast += currentTime - start }
        assertThat(fast).containsExactly(0L, 8L, 1883L, 1883L).inOrder()
    }

    @Test
    fun `replayer updates yields only up entries`() = runTest {
        val updates = FrameLogReplayer.fromText(sample, speed = 100.0).updates().toList()
        assertThat(updates.map { it.code }).containsExactly(27, 1019).inOrder()
    }
}
