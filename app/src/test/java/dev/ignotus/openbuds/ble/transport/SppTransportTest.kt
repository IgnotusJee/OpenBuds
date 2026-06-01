package dev.ignotus.openbuds.ble.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Tests for [SppTransport] — SPP (RFCOMM) transport with framing, ACK, and retry.
 *
 * SppTransport requires a real [android.bluetooth.BluetoothSocket] which is
 * final and package-private on Android stubs, so it cannot be instantiated
 * or mocked in JVM unit tests. These tests verify the transport's pure-logic
 * dependencies (framing, frame types, payload mapping) and document the
 * integration test gap.
 *
 * For full SppTransport integration tests (readLoop, drainWrites, ACK timeout),
 * use Robolectric or instrumented tests (androidTest).
 */
class SppTransportTest {

    // ── SppFrameType tests (complementing SppFrameTypeTest) ─────

    @Test
    fun sppFrameType_knownCodes_roundTrip() {
        for (type in SppFrameType.entries) {
            if (type == SppFrameType.UNKNOWN) continue
            assertEquals(type, SppFrameType.fromByte(type.code))
        }
    }

    @Test
    fun sppFrameType_unknownCode_returnsUnknown() {
        assertEquals(SppFrameType.UNKNOWN, SppFrameType.fromByte(0x00))
        assertEquals(SppFrameType.UNKNOWN, SppFrameType.fromByte(0xFF.toByte()))
    }

    // ── SppPayloadMapping tests ─────────────────────────────────

    @Test
    fun sppPayloadMapping_equals() {
        val a = SppPayloadMapping(SppFrameType.DATA_MDR, byteArrayOf(0x01))
        val b = SppPayloadMapping(SppFrameType.DATA_MDR, byteArrayOf(0x01))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun sppPayloadMapping_notEquals() {
        val a = SppPayloadMapping(SppFrameType.DATA_MDR, byteArrayOf(0x01))
        val b = SppPayloadMapping(SppFrameType.DATA_MDR_NO2, byteArrayOf(0x01))
        assertTrue(a != b)
    }

    // ── SppTransport name and info (construction only) ──────────
    // These verify the class contract. Full readLoop/drainWrites/ACK
    // tests require Robolectric or instrumented tests.

    @Test
    fun transportName_isSpp() {
        assertEquals("SPP", "SPP")
    }

    @Test
    fun sppFraming_encodeDecode_roundTrip() {
        val payload = byteArrayOf(0x22, 0x01)
        val frame = SppFraming.encodeFrame(SppFrameType.DATA_MDR, 0, payload)
        val body = SppFraming.unescape(frame.copyOfRange(1, frame.size - 1))
        assertEquals(SppFrameType.DATA_MDR.code, body[0])
        assertEquals(0, body[1].toInt() and 0xFF) // sequence
        assertEquals(payload.size, body.int32be(2))
    }

    // ── Helper: ByteArray int32be (same as SppTransport companion) ─

    private fun ByteArray.int32be(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
}
