package dev.ignotus.openbuds.ble

import dev.ignotus.openbuds.ble.sony.SonyAudioAdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyAudioAdParserTest {
    @Test
    fun parseSonyAudioV2Advertisement_directPayloadReadsControlFields() {
        val payload = byteArrayOf(
            0x04, 0x00, 0x02, 0x03,
            0xB0.toByte(),
            0x7E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x33,
            0x21, 0x00, 0x01,
            0x45,
            0x01, 0x02, 0x03, 0x04,
        )

        val parsed = SonyAudioAdParser.parseSonyAudioV2Advertisement(payload)

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(2, parsed.version)
        assertEquals(0x7E, parsed.modelId)
        assertEquals("GATT", parsed.androidLine)
        assertEquals("A2DP_OR_LE_AUDIO", parsed.audioStream)
        assertTrue(parsed.androidGattCapable)
        assertTrue(parsed.leGattControlFlag)
        assertEquals(0x01020304L, parsed.classicHash)
    }

    @Test
    fun extractSonyAudioManufacturerPayloads_combinesSplitV2Payload() {
        val firstPayload = byteArrayOf(
            0x04, 0x00, 0x02, 0x02,
            0x33,
            0x03, 0x00, 0x01,
        )
        val secondPayload = byteArrayOf(
            0x45,
            0x0A, 0x0B, 0x0C, 0x0D,
        )
        val record = adStructure(firstPayload) + adStructure(secondPayload)

        val payloads = SonyAudioAdParser.extractSonyAudioManufacturerPayloads(record)
        val parsed = payloads.mapNotNull {
            SonyAudioAdParser.parseSonyAudioV2Advertisement(it)
        }.firstOrNull { it.classicHash != null }

        assertTrue(payloads.size >= 2)
        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals("SPP_OR_GATT", parsed.androidLine)
        assertTrue(parsed.androidGattCapable)
        assertTrue(parsed.leGattControlFlag)
        assertEquals(0x0A0B0C0DL, parsed.classicHash)
    }

    @Test
    fun parseSonyAudioV2Advertisement_invalidPayloadReturnsNull() {
        assertNull(SonyAudioAdParser.parseSonyAudioV2Advertisement(byteArrayOf(0x04, 0x00, 0x01, 0x01)))
        assertNull(SonyAudioAdParser.parseSonyAudioV2Advertisement(byteArrayOf(0x04, 0x00, 0x02, 0x00)))
    }

    @Test
    fun parseSonyAudioV2Advertisement_truncatedChunkDoesNotCrash() {
        val parsed = SonyAudioAdParser.parseSonyAudioV2Advertisement(
            byteArrayOf(0x04, 0x00, 0x02, 0x01, 0x45, 0x01)
        )

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertFalse(parsed.androidGattCapable)
        assertFalse(parsed.leGattControlFlag)
        assertNull(parsed.classicHash)
    }

    private fun adStructure(payload: ByteArray): ByteArray =
        byteArrayOf(
            (payload.size + 3).toByte(),
            SonyAudioAdParser.AD_TYPE_MANUFACTURER_SPECIFIC.toByte(),
            0x2D,
            0x01,
        ) + payload
}
