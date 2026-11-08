package com.phoenix.fytipc.mcu

/**
 * One frame on the MCU <-> Android serial link, as surfaced through CANBUS code 1019 / 1008.
 *
 * Wire format (verified on FYT UIS7870, same MCU family as UIS7862):
 * ```
 * 2E <id> <len> <payload...> <cksum>
 * ```
 * where `cksum = (0xFF - (id + len + sum(payload))) & 0xFF`, i.e. the bytes from `<id>` through
 * `<cksum>` always sum to 0xFF (mod 256). Verified against published captures (Raise/FYT CAN box
 * protocol, XDA "CANBOX reverse engineering" thread):
 *   `2E 20 02 01 01 DB`  0x20+0x02+0x01+0x01 = 0x24 -> 0xFF-0x24 = 0xDB
 *   `2E 41 02 01 00 BB`  0x41+0x02+0x01+0x00 = 0x44 -> 0xBB
 *   `2E 25 02 02 00 D6`  0x25+0x02+0x02+0x00 = 0x29 -> 0xD6
 *   `2E C0 02 01 01 3B`  0xC0+0x02+0x01+0x01 = 0xC4 -> 0x3B
 * Use [checksum] everywhere; do not hand-compute. If truck captures disagree, fix it here and
 * add the offending frame to the unit tests.
 */
data class McuFrame(val id: Int, val payload: ByteArray) {

    init {
        require(id in 0..0xFF) { "id out of range: $id" }
        require(payload.size <= 0xFF) { "payload too long: ${payload.size}" }
    }

    val checksum: Int get() = checksum(id, payload)

    fun encode(): ByteArray {
        val out = ByteArray(payload.size + 4)
        out[0] = START.toByte()
        out[1] = id.toByte()
        out[2] = payload.size.toByte()
        payload.copyInto(out, 3)
        out[out.size - 1] = checksum.toByte()
        return out
    }

    /** Little-endian signed 16-bit at [offset], or null if out of range. */
    fun int16Le(offset: Int): Int? {
        if (offset + 1 >= payload.size) return null
        val lo = payload[offset].toInt() and 0xFF
        val hi = payload[offset + 1].toInt()
        return (hi shl 8) or lo
    }

    fun u8(offset: Int): Int? = payload.getOrNull(offset)?.let { it.toInt() and 0xFF }

    fun toHex(): String = encode().joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    override fun equals(other: Any?): Boolean =
        other is McuFrame && other.id == id && other.payload.contentEquals(payload)

    override fun hashCode(): Int = 31 * id + payload.contentHashCode()

    override fun toString(): String = "McuFrame(${toHex()})"

    companion object {
        const val START = 0x2E

        fun checksum(id: Int, payload: ByteArray): Int {
            var sum = id + payload.size
            for (b in payload) sum += b.toInt() and 0xFF
            return (0xFF - sum) and 0xFF
        }

        /** Parses exactly one complete frame. Returns null if malformed or checksum mismatch. */
        fun parse(bytes: ByteArray): McuFrame? {
            if (bytes.size < 4) return null
            if ((bytes[0].toInt() and 0xFF) != START) return null
            val id = bytes[1].toInt() and 0xFF
            val len = bytes[2].toInt() and 0xFF
            if (bytes.size != len + 4) return null
            val payload = bytes.copyOfRange(3, 3 + len)
            val frame = McuFrame(id, payload)
            if ((bytes[bytes.size - 1].toInt() and 0xFF) != frame.checksum) return null
            return frame
        }

        /** Parses a hex string such as `"2E 20 02 01 01 DB"` (spaces optional). */
        fun parseHex(hex: String): McuFrame? {
            val clean = hex.replace(" ", "").replace("0x", "", ignoreCase = true)
            if (clean.length % 2 != 0) return null
            val bytes = ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            return parse(bytes)
        }

        /** The 1019 callback delivers frame bytes as an `int[]` (one byte per int). */
        fun fromInts(ints: IntArray?): McuFrame? {
            if (ints == null) return null
            return parse(ByteArray(ints.size) { ints[it].toByte() })
        }

        fun of(id: Int, vararg payload: Int): McuFrame = McuFrame(id, ByteArray(payload.size) { payload[it].toByte() })
    }
}

/**
 * Streaming parser for a byte stream that may contain partial or concatenated frames.
 * Feed bytes as they arrive; complete, checksum-valid frames are returned.
 */
class McuFrameParser {
    private val buf = ArrayList<Byte>(64)

    fun feed(bytes: ByteArray): List<McuFrame> {
        val out = ArrayList<McuFrame>()
        for (b in bytes) {
            buf.add(b)
            drain(out)
        }
        return out
    }

    /**
     * Emits every complete frame at the head of the buffer. A candidate that fails its checksum
     * loses only its start byte, and the bytes already buffered behind it are rescanned, so a
     * corrupted frame (or a stray 0x2E with a large length byte) cannot swallow the valid frames
     * that follow it.
     */
    private fun drain(out: MutableList<McuFrame>) {
        while (true) {
            while (buf.isNotEmpty() && (buf[0].toInt() and 0xFF) != McuFrame.START) buf.removeAt(0)
            if (buf.size < 3) return
            val total = (buf[2].toInt() and 0xFF) + 4
            if (buf.size < total) return
            val head = buf.subList(0, total)
            val frame = McuFrame.parse(head.toByteArray())
            if (frame != null) {
                out.add(frame)
                head.clear()
            } else {
                buf.removeAt(0)
            }
        }
    }

    fun reset() = buf.clear()
}
