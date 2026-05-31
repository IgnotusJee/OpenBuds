package dev.ignotus.openbuds.lsposed.milink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AirpodsStateMapperTest {

    @Test
    fun placeholder_usesNormalizedMacAndGenericDeviceId() {
        val snapshot = AirpodsStateMapper.placeholder("aa:bb:cc:dd:ee:ff")

        assertEquals("AA:BB:CC:DD:EE:FF", snapshot.mac)
        assertEquals(DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID, snapshot.deviceId)
    }

    @Test
    fun toStateArray_matchesMiLinkNineElementShape() {
        val array = AirpodsStateMapper.toStateArray(
            AirpodsStateMapper.placeholder("AA:BB:CC:DD:EE:FF"),
        )

        assertArrayEquals(
            arrayOf(
                "true",
                "75",
                "true",
                "80",
                "90",
                "false",
                "false",
                "false",
                DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID,
            ),
            array,
        )
    }

    @Test
    fun toBundleFields_matchesMiLinkElevenFieldShape() {
        val fields = AirpodsStateMapper.toBundleFields(
            AirpodsStateMapper.placeholder("AA:BB:CC:DD:EE:FF"),
        )

        assertEquals(
            listOf(
                "device",
                "connectState",
                "isLeftWearing",
                "leftBattery",
                "isRightWearing",
                "rightBattery",
                "boxBattery",
                "isLeftCharging",
                "isRightCharging",
                "isBoxCharging",
                "modelName",
            ),
            fields.keys.toList(),
        )
        assertEquals("AA:BB:CC:DD:EE:FF", fields["device"])
        assertEquals("2", fields["connectState"])
        assertEquals("75", fields["leftBattery"])
        assertEquals("80", fields["rightBattery"])
        assertEquals("90", fields["boxBattery"])
        assertEquals(DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID, fields["modelName"])
    }
}
