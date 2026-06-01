package dev.ignotus.openbuds.lsposed.milink

import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AirpodsStateMapperTest {

    @Test
    fun fromMilinkSnapshot_usesNormalizedMacAndGenericDeviceId() {
        val snapshot = AirpodsStateMapper.fromMilinkSnapshot(snapshot(mac = "aa:bb:cc:dd:ee:ff"))

        assertEquals("AA:BB:CC:DD:EE:FF", snapshot.mac)
        assertEquals(DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID, snapshot.deviceId)
    }

    @Test
    fun toStateArray_matchesMiLinkNineElementShape() {
        val array = AirpodsStateMapper.toStateArray(
            AirpodsStateSnapshot(
                mac = "AA:BB:CC:DD:EE:FF",
                deviceId = DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID,
                isLeftWearing = "true",
                leftBattery = "75",
                isRightWearing = "true",
                rightBattery = "80",
                boxBattery = "90",
                isLeftCharging = "false",
                isRightCharging = "false",
                isBoxCharging = "false",
                connectState = AirpodsStateMapper.CONNECTED_STATE,
            ),
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
            AirpodsStateSnapshot(
                mac = "AA:BB:CC:DD:EE:FF",
                deviceId = DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID,
                isLeftWearing = "true",
                leftBattery = "75",
                isRightWearing = "true",
                rightBattery = "80",
                boxBattery = "90",
                isLeftCharging = "false",
                isRightCharging = "false",
                isBoxCharging = "false",
                connectState = AirpodsStateMapper.CONNECTED_STATE,
            ),
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

    @Test
    fun fromMilinkSnapshot_usesRealBatteryAndChargingFields() {
        val snapshot = AirpodsStateMapper.fromMilinkSnapshot(
            snapshot(
                leftBattery = 61,
                rightBattery = 62,
                caseBattery = 63,
                leftWearing = true,
                rightWearing = false,
                leftCharging = true,
                rightCharging = false,
                caseCharging = true,
            )
        )

        assertArrayEquals(
            arrayOf("true", "61", "false", "62", "63", "true", "false", "true", DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID),
            AirpodsStateMapper.toStateArray(snapshot),
        )
    }

    @Test
    fun fromMilinkSnapshot_missingFieldsUseStrictUnknownDefaults() {
        val snapshot = AirpodsStateMapper.fromMilinkSnapshot(
            snapshot(
                leftBattery = null,
                rightBattery = null,
                caseBattery = null,
                singleBattery = 77,
                leftWearing = null,
                rightWearing = null,
                leftCharging = null,
                rightCharging = null,
                caseCharging = null,
            )
        )

        assertArrayEquals(
            arrayOf("-", "77", "-", "77", "-", "false", "false", "false", DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID),
            AirpodsStateMapper.toStateArray(snapshot),
        )
    }

    private fun snapshot(
        mac: String = "AA:BB:CC:DD:EE:FF",
        leftBattery: Int? = null,
        rightBattery: Int? = null,
        caseBattery: Int? = null,
        singleBattery: Int? = null,
        leftWearing: Boolean? = null,
        rightWearing: Boolean? = null,
        leftCharging: Boolean? = null,
        rightCharging: Boolean? = null,
        caseCharging: Boolean? = null,
    ): MilinkDeviceSnapshot =
        MilinkDeviceSnapshot(
            mac = mac,
            name = "LinkBuds S",
            brand = "Sony",
            model = "LinkBuds S",
            deviceId = DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID,
            connected = true,
            protocolReady = true,
            leftBattery = leftBattery,
            rightBattery = rightBattery,
            caseBattery = caseBattery,
            singleBattery = singleBattery,
            leftWearing = leftWearing,
            rightWearing = rightWearing,
            leftCharging = leftCharging,
            rightCharging = rightCharging,
            caseCharging = caseCharging,
            revision = 1L,
            updatedAt = 2L,
        )
}
