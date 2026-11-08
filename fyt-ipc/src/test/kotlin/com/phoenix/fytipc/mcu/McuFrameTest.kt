package com.phoenix.fytipc.mcu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class McuFrameTest {

    private val published = listOf(
        "2E 20 02 01 01 DB",
        "2E 41 02 01 00 BB",
        "2E 25 02 02 00 D6",
        "2E C0 02 01 01 3B",
    )

    @Test
    fun `published frames parse and their checksums match`() {
        for (hex in published) {
            val frame = McuFrame.parseHex(hex)
            assertThat(frame).isNotNull()
            assertThat(frame!!.toHex()).isEqualTo(hex)
            val bytes = hex.split(" ").map { it.toInt(16) }
            assertThat(frame.checksum).isEqualTo(bytes.last())
            // id..cksum always sum to 0xFF mod 256.
            assertThat(bytes.drop(1).sum() and 0xFF).isEqualTo(0xFF)
        }
    }

    @Test
    fun `encode round trips through parse and fromInts`() {
        val frame = McuFrame.of(0x29, 0x4B, 0xEB)
        val bytes = frame.encode()
        assertThat(McuFrame.parse(bytes)).isEqualTo(frame)
        val ints = IntArray(bytes.size) { bytes[it].toInt() and 0xFF }
        assertThat(McuFrame.fromInts(ints)).isEqualTo(frame)
        assertThat(McuFrame.of(0x30).encode().size).isEqualTo(4)
    }

    @Test
    fun `bad checksum, wrong start or wrong length is rejected`() {
        assertThat(McuFrame.parseHex("2E 20 02 01 01 DC")).isNull()
        assertThat(McuFrame.parseHex("2F 20 02 01 01 DB")).isNull()
        assertThat(McuFrame.parseHex("2E 20 03 01 01 DB")).isNull()
        assertThat(McuFrame.parseHex("2E 20")).isNull()
    }

    @Test
    fun `steering angle decodes little-endian signed tenths (wiki page 11)`() {
        assertThat(McuFrame.of(0x29, 0x4B, 0xEB).int16Le(0)).isEqualTo(-5301)
        assertThat(McuFrame.of(0x29, 0x10, 0x00).int16Le(0)).isEqualTo(16)
        assertThat(McuFrame.of(0x29, 0xE4, 0x14).int16Le(0)).isEqualTo(5348)
        assertThat(McuFrame.of(0x29, 0x10, 0x00).toHex()).isEqualTo("2E 29 02 10 00 C4")
        assertThat(McuFrame.of(0x29, 0x10).int16Le(0)).isNull()
    }

    @Test
    fun `streaming parser handles concatenated split and corrupted input`() {
        val frames = published.map { McuFrame.parseHex(it)!! }
        val good = frames.flatMap { it.encode().toList() }
        val corrupt = listOf(0x2E, 0x20, 0x02, 0x01, 0x01, 0x00).map { it.toByte() } // bad checksum
        // A stray start byte: its "length" byte is whatever follows, so it claims a long frame.
        val strayStart = listOf(0x2E, 0xFF).map { it.toByte() }
        val noise = listOf(0x00, 0x13, 0x37).map { it.toByte() }
        val stream = (noise + good.take(12) + corrupt + strayStart + good.drop(12) + noise).toByteArray()

        val parser = McuFrameParser()
        val out = mutableListOf<McuFrame>()
        // Feed in awkward chunk sizes.
        var i = 0
        var chunk = 1
        while (i < stream.size) {
            val end = minOf(stream.size, i + chunk)
            out += parser.feed(stream.copyOfRange(i, end))
            i = end
            chunk = chunk % 5 + 1
        }
        // The stray start byte holds back later frames until enough bytes arrive or it is rejected;
        // pad with zeros to flush it.
        out += parser.feed(ByteArray(300))
        assertThat(out).containsExactlyElementsIn(frames).inOrder()
    }

    @Test
    fun `streaming parser emits concatenated frames from one feed`() {
        val frames = published.map { McuFrame.parseHex(it)!! }
        val parser = McuFrameParser()
        val all = frames.flatMap { it.encode().toList() }.toByteArray()
        assertThat(parser.feed(all)).containsExactlyElementsIn(frames).inOrder()
    }
}
