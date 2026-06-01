package dev.ignotus.openbuds.ble

import dev.ignotus.openbuds.ble.transport.SppFraming
import dev.ignotus.openbuds.ble.transport.SppFrameType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SppFramingTest {

    // ── escape / unescape round-trip ────────────────────────────

    @Test
    fun escapeThenUnescape_plainBytes_isIdentity() {
        val input = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val escaped = SppFraming.escape(input)
        val unescaped = SppFraming.unescape(escaped)
        assertArrayEquals(input, unescaped)
    }

    @Test
    fun escape_frameEnd_isEscaped() {
        val escaped = SppFraming.escape(byteArrayOf(SppFraming.FRAME_END))
        // 0x3C → 0x3D 0x2C
        assertArrayEquals(byteArrayOf(SppFraming.ESCAPE, 0x2C.toByte()), escaped)
    }

    @Test
    fun escape_frameStart_isEscaped() {
        val escaped = SppFraming.escape(byteArrayOf(SppFraming.FRAME_START))
        // 0x3E → 0x3D 0x2E
        assertArrayEquals(byteArrayOf(SppFraming.ESCAPE, 0x2E.toByte()), escaped)
    }

    @Test
    fun escape_escapeByte_isEscaped() {
        val escaped = SppFraming.escape(byteArrayOf(SppFraming.ESCAPE))
        // 0x3D → 0x3D 0x2D
        assertArrayEquals(byteArrayOf(SppFraming.ESCAPE, 0x2D.toByte()), escaped)
    }

    @Test
    fun escapeAndUnescape_allSpecialBytes_roundTrip() {
        val input = byteArrayOf(
            SppFraming.FRAME_START, 0x01, SppFraming.FRAME_END,
            0x02, SppFraming.ESCAPE, 0x03,
        )
        val result = SppFraming.unescape(SppFraming.escape(input))
        assertArrayEquals(input, result)
    }

    @Test
    fun unescape_loneEscapeAtEnd_keepsEscapedSequence() {
        // Lone 0x3D at end with no following byte — kept as-is (not a valid escape seq)
        val unescaped = SppFraming.unescape(byteArrayOf(0x01, SppFraming.ESCAPE))
        assertArrayEquals(byteArrayOf(0x01, SppFraming.ESCAPE), unescaped)
    }

    // ── checksum ────────────────────────────────────────────────

    @Test
    fun checksum_knownValue() {
        val data = byteArrayOf(0x0C, 0x00, 0x00, 0x00, 0x00, 0x02, 0x22, 0x01)
        // sum of first 7 bytes: 12+0+0+0+0+2+34 = 48 = 0x30
        assertEquals(0x30, SppFraming.checksum(data, data.size - 1))
    }

    @Test
    fun checksum_emptyRange_isZero() {
        assertEquals(0, SppFraming.checksum(byteArrayOf(0x01, 0x02, 0x03), 0))
    }

    @Test
    fun checksum_mod256() {
        val data = ByteArray(300) { 0xFF.toByte() }
        assertEquals(300 * 0xFF % 256, SppFraming.checksum(data, data.size))
    }

    // ── encodeFrame ─────────────────────────────────────────────

    @Test
    fun encodeFrame_placesFrameStartAndEnd() {
        val frame = SppFraming.encodeFrame(SppFrameType.DATA_MDR, 0, byteArrayOf(0x42))
        assertEquals(SppFraming.FRAME_START, frame.first())
        assertEquals(SppFraming.FRAME_END, frame.last())
    }

    @Test
    fun encodeFrame_headerHasTypeAndSequence() {
        val frame = SppFraming.encodeFrame(SppFrameType.DATA_MDR_NO2, 0x01, byteArrayOf(0x01, 0x02))
        // unescape the body
        val body = SppFraming.unescape(frame.copyOfRange(1, frame.size - 1))
        assertEquals(SppFrameType.DATA_MDR_NO2.code, body[0])
        assertEquals(0x01.toByte(), body[1])
    }

    @Test
    fun encodeFrame_lengthIsBigEndian() {
        val payload = ByteArray(258) { it.toByte() } // length = 0x00000102
        val frame = SppFraming.encodeFrame(SppFrameType.DATA_MDR, 0, payload)
        val body = SppFraming.unescape(frame.copyOfRange(1, frame.size - 1))
        assertEquals(0x00.toByte(), body[2])
        assertEquals(0x00.toByte(), body[3])
        assertEquals(0x01.toByte(), body[4])
        assertEquals(0x02.toByte(), body[5])
    }

    @Test
    fun encodeFrame_emptyPayload() {
        val frame = SppFraming.encodeFrame(SppFrameType.ACK, 0x01, byteArrayOf())
        val body = SppFraming.unescape(frame.copyOfRange(1, frame.size - 1))
        assertEquals(SppFraming.HEADER_SIZE + SppFraming.CHECKSUM_SIZE, body.size)
    }

    @Test
    fun encodeFrame_checksumInLastByte() {
        val payload = byteArrayOf(0x22, 0x01)
        val frame = SppFraming.encodeFrame(SppFrameType.DATA_MDR, 0, payload)
        val body = SppFraming.unescape(frame.copyOfRange(1, frame.size - 1))
        val expected = SppFraming.checksum(body, body.size - 1)
        assertEquals(expected.toByte(), body.last())
    }

    // ── inverseSequence ─────────────────────────────────────────

    @Test
    fun inverseSequence_zeroToOne() {
        assertEquals(1.toByte(), SppFraming.inverseSequence(0))
    }

    @Test
    fun inverseSequence_oneToZero() {
        assertEquals(0.toByte(), SppFraming.inverseSequence(1))
    }

    @Test
    fun inverseSequence_isInvolution() {
        for (s in 0..1) {
            assertEquals(s.toByte(), SppFraming.inverseSequence(SppFraming.inverseSequence(s.toByte())))
        }
    }

    // ── int32be ─────────────────────────────────────────────────

    private fun ByteArray.int32be(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    @Test
    fun int32be_zero() {
        assertEquals(0, byteArrayOf(0, 0, 0, 0).int32be(0))
    }

    @Test
    fun int32be_knownValue() {
        val input = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        assertEquals(0x12345678, input.int32be(0))
    }

    @Test
    fun int32be_fromOffset() {
        val input = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x12, 0x34, 0x56, 0x78)
        assertEquals(0x12345678, input.int32be(4))
    }

    @Test
    fun int32be_maxValue() {
        val input = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertEquals(-1, input.int32be(0))
    }

    // ── Byte.u (unsigned) ──────────────────────────────────────

    private fun Byte.toUnsigned(): Int = toInt() and 0xFF

    @Test
    fun byteU_negativeBytes_areUnsigned() {
        assertEquals(255, (-1).toByte().toUnsigned())
        assertEquals(128, (-128).toByte().toUnsigned())
    }

    @Test
    fun byteU_positiveBytes_unchanged() {
        assertEquals(0, 0.toByte().toUnsigned())
        assertEquals(127, 127.toByte().toUnsigned())
    }
}
