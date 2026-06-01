package dev.ignotus.openbuds.ble.transport

/**
 * Pure functions for Sony SPP wire framing.
 *
 * These are lifted out of [SppTransport] so they can be unit-tested without
 * any Android or Bluetooth dependencies. All functions are deterministic and
 * operate only on [ByteArray] values.
 */
object SppFraming {
    /** Frame start delimiter. */
    const val FRAME_START: Byte = 0x3E

    /** Frame end delimiter. */
    const val FRAME_END: Byte = 0x3C

    /** Escape prefix byte. */
    const val ESCAPE: Byte = 0x3D

    /** Frame header size: type(1) + seq(1) + length(4 BE). */
    const val HEADER_SIZE = 6

    /** Trailer checksum size (1 byte). */
    const val CHECKSUM_SIZE = 1

    /** The "inverse" sequence value: 1 -> 0, 0 -> 1. */
    fun inverseSequence(sequence: Byte): Byte = (1 - sequence).toByte()

    /**
     * Build a complete SPP wire frame (delimited + escaped body).
     *
     * Frame layout:
     * ```
     * [FRAME_START] [escaped-body] [FRAME_END]
     * body = [type:1] [seq:1] [length:4 BE] [payload:N] [checksum:1]
     * ```
     */
    fun encodeFrame(type: SppFrameType, sequence: Byte, payload: ByteArray): ByteArray {
        val body = ByteArray(HEADER_SIZE + payload.size + CHECKSUM_SIZE)
        body[0] = type.code
        body[1] = sequence
        body[2] = ((payload.size ushr 24) and 0xFF).toByte()
        body[3] = ((payload.size ushr 16) and 0xFF).toByte()
        body[4] = ((payload.size ushr 8) and 0xFF).toByte()
        body[5] = (payload.size and 0xFF).toByte()
        payload.copyInto(body, HEADER_SIZE)
        body[body.lastIndex] = checksum(body, body.size - CHECKSUM_SIZE).toByte()
        return byteArrayOf(FRAME_START) + escape(body) + byteArrayOf(FRAME_END)
    }

    /**
     * Escape special bytes in [bytes] with 0x3D prefix followed by (byte | 0x10).
     */
    fun escape(bytes: ByteArray): ByteArray {
        val escaped = ArrayList<Byte>(bytes.size)
        bytes.forEach { byte ->
            when (byte) {
                FRAME_END -> {
                    escaped += ESCAPE
                    escaped += 0x2C.toByte()
                }
                ESCAPE -> {
                    escaped += ESCAPE
                    escaped += 0x2D.toByte()
                }
                FRAME_START -> {
                    escaped += ESCAPE
                    escaped += 0x2E.toByte()
                }
                else -> escaped += byte
            }
        }
        return escaped.toByteArray()
    }

    /**
     * Reverse [escape], restoring original bytes.
     */
    fun unescape(bytes: ByteArray): ByteArray {
        val unescaped = ArrayList<Byte>(bytes.size)
        var index = 0
        while (index < bytes.size) {
            val byte = bytes[index]
            if (byte == ESCAPE && index + 1 < bytes.size) {
                index++
                unescaped += (bytes[index].toInt() or 0x10).toByte()
            } else {
                unescaped += byte
            }
            index++
        }
        return unescaped.toByteArray()
    }

    /**
     * Compute 8-bit modulo-256 checksum over the first [length] bytes of [bytes].
     */
    fun checksum(bytes: ByteArray, length: Int): Int =
        (0 until length).fold(0) { acc, index -> (acc + bytes[index].u) and 0xFF }

    /** Unsigned byte as Int (0–255). */
    val Byte.u: Int
        get() = toInt() and 0xFF

    /** Read big-endian 32-bit int from [bytes] at [offset]. */
    fun ByteArray.int32be(offset: Int): Int =
        ((this[offset].u) shl 24) or
            ((this[offset + 1].u) shl 16) or
            ((this[offset + 2].u) shl 8) or
            this[offset + 3].u
}
