package dev.ignotus.openbuds.headphones

import dev.ignotus.openbuds.ble.IncomingHeadphoneMessage
import dev.ignotus.openbuds.ble.qcy.QcyChannel
import dev.ignotus.openbuds.ble.DiscoveredDevice
import dev.ignotus.openbuds.headphones.qcy.QcyHeadphoneAdapter
import dev.ignotus.openbuds.protocol.ParsedHeadphoneResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QCY adapter parse-path tests. Verifies per-channel dispatch, multi-TLV
 * Batch wrapping, and field decoding against the protocol reference.
 *
 * These tests instantiate a fallback profile for routing (no Android deps).
 */
class QcyAdapterParseTest {

    private val adapter = QcyHeadphoneAdapter
    private val profile = adapter.fallbackProfile(
        DiscoveredDevice(name = "QCY-C30S", address = "AA:BB:CC:DD:EE:FF", rssi = 0)
    )

    private fun hex(s: String): ByteArray =
        s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun message(channel: QcyChannel, raw: ByteArray): IncomingHeadphoneMessage =
        IncomingHeadphoneMessage(adapterId = "qcy", sourceKey = channel.sourceKey, raw = raw)

    // ── Battery (0x0008) ────────────────────────────────────────────

    @Test
    fun parseQcyBattery_charging_bitDecodedFromHighBit() {
        // L=100 (0x64) not charging, R=100|0x80=0xE4 charging, Case=50 (0x32) not charging
        val parsed = adapter.parse(profile, message(QcyChannel.BATTERY, hex("64 E4 32")))
        assertTrue(parsed is ParsedHeadphoneResponse.Qcy.Battery)
        parsed as ParsedHeadphoneResponse.Qcy.Battery
        assertEquals(100, parsed.leftLevel)
        assertFalse(parsed.leftCharging)
        assertEquals(100, parsed.rightLevel)
        assertTrue(parsed.rightCharging)
        assertEquals(50, parsed.caseLevel)
        assertFalse(parsed.caseCharging)
    }

    @Test
    fun parseQcyBattery_partialData_defaultsZero() {
        val parsed = adapter.parse(profile, message(QcyChannel.BATTERY, hex("32")))
        parsed as ParsedHeadphoneResponse.Qcy.Battery
        assertEquals(50, parsed.leftLevel)
        assertEquals(0, parsed.rightLevel)
        assertEquals(0, parsed.caseLevel)
    }

    // ── Version (0x0007 raw) ────────────────────────────────────────

    @Test
    fun parseQcyVersionRaw_sixBytes_splitsLeftAndRight() {
        val parsed = adapter.parse(profile, message(QcyChannel.VERSION, hex("01 02 03 04 05 06")))
        parsed as ParsedHeadphoneResponse.Qcy.DeviceInfo
        assertEquals("1.2.3", parsed.leftFirmware)
        assertEquals("4.5.6", parsed.rightFirmware)
    }

    // ── Function status (0x000F) ────────────────────────────────────

    @Test
    fun parseQcyFunctionStatus_bothFlagsOn() {
        val parsed = adapter.parse(profile, message(QcyChannel.FUNCTION, hex("01 01")))
        parsed as ParsedHeadphoneResponse.Qcy.FunctionStatus
        assertTrue(parsed.inEarDetectionOn)
        assertTrue(parsed.transparencyOn)
    }

    @Test
    fun parseQcyFunctionStatus_inEarOnly() {
        val parsed = adapter.parse(profile, message(QcyChannel.FUNCTION, hex("01 00")))
        parsed as ParsedHeadphoneResponse.Qcy.FunctionStatus
        assertTrue(parsed.inEarDetectionOn)
        assertFalse(parsed.transparencyOn)
    }

    // ── Readset multi-TLV → Batch ───────────────────────────────────

    @Test
    fun parseQcyReadset_multiTlv_wrappedAsBatch() {
        // FF 06 0C 01 01 07 01 64 → CMD_NOISE_MODE + CMD_NOISE_VALUE
        val parsed = adapter.parse(profile, message(QcyChannel.READSET, hex("FF 06 0C 01 01 07 01 64")))
        assertTrue("Expected Batch, got $parsed", parsed is ParsedHeadphoneResponse.Batch)
        parsed as ParsedHeadphoneResponse.Batch
        assertEquals(2, parsed.items.size)
        val modeItem = parsed.items[0] as ParsedHeadphoneResponse.Qcy.NoiseControl
        assertEquals(1, modeItem.mode)
        assertEquals(null, modeItem.noiseValue)
        val valueItem = parsed.items[1] as ParsedHeadphoneResponse.Qcy.NoiseControl
        assertEquals(null, valueItem.mode)
        assertEquals(100, valueItem.noiseValue)
    }

    @Test
    fun parseQcyReadset_singleTlv_unwrapped() {
        // FF 03 16 01 32 → balance set to center
        val parsed = adapter.parse(profile, message(QcyChannel.READSET, hex("FF 03 16 01 32")))
        // Balance CMD (0x16) is not currently mapped → returns Unknown
        // The point of the test: single-TLV frames must NOT return as Batch.
        assertFalse(parsed is ParsedHeadphoneResponse.Batch)
    }

    // ── EQ raw (0x000B) ─────────────────────────────────────────────

    @Test
    fun parseQcyEqRaw_newFormat_detectedByLength() {
        // body = [eqType=0][mg=0,0][freq=1000,0=E803][gain=350=5E01][q=120=7800][bandType=0]
        // total = 3 + 7 = 10 bytes
        val raw = hex("00 00 00 E8 03 5E 01 78 00 00")
        val parsed = adapter.parse(profile, message(QcyChannel.EQ_RAW, raw))
        parsed as ParsedHeadphoneResponse.Qcy.EqData
        assertEquals(1, parsed.bands.size)
        assertEquals(1000, parsed.bands[0].frequency)
        assertEquals(3.5f, parsed.bands[0].gain, 0.001f)
    }
}
