package dev.ignotus.openbuds.ble

import dev.ignotus.openbuds.ble.transport.SppFrameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SppFrameTypeTest {

    @Test
    fun fromByte_knownCodes() {
        assertEquals(SppFrameType.DATA_MDR, SppFrameType.fromByte(0x0C))
        assertEquals(SppFrameType.DATA_MDR_NO2, SppFrameType.fromByte(0x0E))
        assertEquals(SppFrameType.ACK, SppFrameType.fromByte(0x01))
        assertEquals(SppFrameType.SHOT_MDR, SppFrameType.fromByte(0x1C))
        assertEquals(SppFrameType.SHOT_MDR_NO2, SppFrameType.fromByte(0x1E))
        assertEquals(SppFrameType.LARGE_DATA_MDR, SppFrameType.fromByte(0x2C))
    }

    @Test
    fun fromByte_unknownCode_returnsUnknown() {
        assertEquals(SppFrameType.UNKNOWN, SppFrameType.fromByte(0x00))
        assertEquals(SppFrameType.UNKNOWN, SppFrameType.fromByte(0x7F))
        assertEquals(SppFrameType.UNKNOWN, SppFrameType.fromByte(0xFF.toByte()))
    }

    @Test
    fun ackRequired_dataMdr_requiresAck() {
        assertTrue(SppFrameType.DATA_MDR.ackRequired)
        assertTrue(SppFrameType.DATA_MDR_NO2.ackRequired)
        assertTrue(SppFrameType.LARGE_DATA_MDR.ackRequired)
    }

    @Test
    fun ackRequired_shotAndAck_doNotRequireAck() {
        assertFalse(SppFrameType.ACK.ackRequired)
        assertFalse(SppFrameType.SHOT_MDR.ackRequired)
        assertFalse(SppFrameType.SHOT_MDR_NO2.ackRequired)
    }

    @Test
    fun allKnownCodes_roundTrip() {
        SppFrameType.entries.forEach { type ->
            if (type == SppFrameType.UNKNOWN) return@forEach
            assertEquals(type, SppFrameType.fromByte(type.code))
            if (type.ackRequired) {
                assertTrue("$type should require ACK", type.ackRequired)
            }
        }
    }
}
