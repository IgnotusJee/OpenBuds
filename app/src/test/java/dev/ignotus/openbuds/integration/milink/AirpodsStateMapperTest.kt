package dev.ignotus.openbuds.integration.milink

import dev.ignotus.openbuds.lsposed.milink.AirpodsStateMapper
import dev.ignotus.openbuds.lsposed.milink.AirpodsStateSnapshot
import dev.ignotus.openbuds.lsposed.milink.DeviceIdRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AirpodsStateMapperTest {

    // -------------------------------------------------------------------------
    // fromMilinkSnapshot
    // -------------------------------------------------------------------------

    @Test
    fun fromMilinkSnapshot_fullSnapshot_connected_shouldMapAllFields() {
        val snapshot = MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = "Test Device",
            brand = "Test Brand",
            model = "T-100",
            deviceId = "testDeviceId",
            connected = true,
            protocolReady = true,
            leftBattery = 85,
            rightBattery = 90,
            caseBattery = 60,
            singleBattery = null,
            leftWearing = true,
            rightWearing = false,
            leftCharging = true,
            rightCharging = false,
            caseCharging = true,
            revision = 1L,
            updatedAt = 2L,
        )

        val result = AirpodsStateMapper.fromMilinkSnapshot(snapshot)

        assertEquals("AA:BB:CC:DD:EE:FF", result.mac)
        assertEquals("testDeviceId", result.deviceId)
        assertEquals("true", result.isLeftWearing)
        assertEquals("85", result.leftBattery)
        assertEquals("false", result.isRightWearing)
        assertEquals("90", result.rightBattery)
        assertEquals("60", result.boxBattery)
        assertEquals("true", result.isLeftCharging)
        assertEquals("false", result.isRightCharging)
        assertEquals("true", result.isBoxCharging)
        assertEquals(AirpodsStateMapper.CONNECTED_STATE, result.connectState)
    }

    @Test
    fun fromMilinkSnapshot_nullBatteryFields_shouldProduceDash() {
        val snapshot = MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = null,
            brand = null,
            model = null,
            deviceId = "id",
            connected = true,
            protocolReady = false,
            leftBattery = null,
            rightBattery = null,
            caseBattery = null,
            singleBattery = null,
            leftWearing = true,
            rightWearing = true,
            leftCharging = true,
            rightCharging = true,
            caseCharging = true,
            revision = 0L,
            updatedAt = 0L,
        )

        val result = AirpodsStateMapper.fromMilinkSnapshot(snapshot)

        assertEquals("-", result.leftBattery)
        assertEquals("-", result.rightBattery)
        assertEquals("-", result.boxBattery)
    }

    @Test
    fun fromMilinkSnapshot_nullWearingFields_shouldProduceDash() {
        val snapshot = MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = null,
            brand = null,
            model = null,
            deviceId = "id",
            connected = true,
            protocolReady = false,
            leftBattery = 50,
            rightBattery = 50,
            caseBattery = 50,
            singleBattery = null,
            leftWearing = null,
            rightWearing = null,
            leftCharging = true,
            rightCharging = true,
            caseCharging = true,
            revision = 0L,
            updatedAt = 0L,
        )

        val result = AirpodsStateMapper.fromMilinkSnapshot(snapshot)

        assertEquals("-", result.isLeftWearing)
        assertEquals("-", result.isRightWearing)
    }

    @Test
    fun fromMilinkSnapshot_nullChargingFields_shouldProduceFalse() {
        val snapshot = MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = null,
            brand = null,
            model = null,
            deviceId = "id",
            connected = true,
            protocolReady = false,
            leftBattery = 50,
            rightBattery = 50,
            caseBattery = 50,
            singleBattery = null,
            leftWearing = true,
            rightWearing = true,
            leftCharging = null,
            rightCharging = null,
            caseCharging = null,
            revision = 0L,
            updatedAt = 0L,
        )

        val result = AirpodsStateMapper.fromMilinkSnapshot(snapshot)

        assertEquals("false", result.isLeftCharging)
        assertEquals("false", result.isRightCharging)
        assertEquals("false", result.isBoxCharging)
    }

    @Test
    fun fromMilinkSnapshot_singleBatteryFallback_whenLeftAndRightAreNull() {
        val snapshot = MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = null,
            brand = null,
            model = null,
            deviceId = "id",
            connected = true,
            protocolReady = false,
            leftBattery = null,
            rightBattery = null,
            caseBattery = 50,
            singleBattery = 75,
            leftWearing = true,
            rightWearing = true,
            leftCharging = true,
            rightCharging = true,
            caseCharging = true,
            revision = 0L,
            updatedAt = 0L,
        )

        val result = AirpodsStateMapper.fromMilinkSnapshot(snapshot)

        assertEquals("75", result.leftBattery)
        assertEquals("75", result.rightBattery)
    }

    @Test
    fun fromMilinkSnapshot_singleBatteryFallback_notOverriddenWhenLeftAndRightAreSet() {
        val snapshot = MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = null,
            brand = null,
            model = null,
            deviceId = "id",
            connected = true,
            protocolReady = false,
            leftBattery = 80,
            rightBattery = 90,
            caseBattery = 50,
            singleBattery = 75,
            leftWearing = true,
            rightWearing = true,
            leftCharging = true,
            rightCharging = true,
            caseCharging = true,
            revision = 0L,
            updatedAt = 0L,
        )

        val result = AirpodsStateMapper.fromMilinkSnapshot(snapshot)

        assertEquals("80", result.leftBattery)
        assertEquals("90", result.rightBattery)
    }

    @Test
    fun fromMilinkSnapshot_disconnectedDevice_shouldProduceZeroState() {
        val snapshot = MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = null,
            brand = null,
            model = null,
            deviceId = "id",
            connected = false,
            protocolReady = false,
            leftBattery = null,
            rightBattery = null,
            caseBattery = null,
            singleBattery = null,
            leftWearing = null,
            rightWearing = null,
            leftCharging = null,
            rightCharging = null,
            caseCharging = null,
            revision = 0L,
            updatedAt = 0L,
        )

        val result = AirpodsStateMapper.fromMilinkSnapshot(snapshot)

        assertEquals(AirpodsStateMapper.DISCONNECTED_STATE, result.connectState)
    }

    @Test
    fun fromMilinkSnapshot_blankDeviceId_shouldFallBackToRegistryDefault() {
        val snapshot = MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = null,
            brand = null,
            model = null,
            deviceId = "   ",
            connected = true,
            protocolReady = false,
            leftBattery = 50,
            rightBattery = 50,
            caseBattery = 50,
            singleBattery = null,
            leftWearing = true,
            rightWearing = true,
            leftCharging = true,
            rightCharging = true,
            caseCharging = true,
            revision = 0L,
            updatedAt = 0L,
        )

        val result = AirpodsStateMapper.fromMilinkSnapshot(snapshot)

        assertEquals(DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID, result.deviceId)
    }

    @Test
    fun fromMilinkSnapshot_emptyDeviceId_shouldFallBackToRegistryDefault() {
        val snapshot = MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = null,
            brand = null,
            model = null,
            deviceId = "",
            connected = true,
            protocolReady = false,
            leftBattery = 50,
            rightBattery = 50,
            caseBattery = 50,
            singleBattery = null,
            leftWearing = true,
            rightWearing = true,
            leftCharging = true,
            rightCharging = true,
            caseCharging = true,
            revision = 0L,
            updatedAt = 0L,
        )

        val result = AirpodsStateMapper.fromMilinkSnapshot(snapshot)

        assertEquals(DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID, result.deviceId)
    }

    @Test
    fun fromMilinkSnapshot_macNormalized_byMatcher() {
        val snapshot = MilinkDeviceSnapshot(
            mac = " aa:bb:cc:dd:ee:ff ",
            name = null,
            brand = null,
            model = null,
            deviceId = "id",
            connected = true,
            protocolReady = false,
            leftBattery = 50,
            rightBattery = 50,
            caseBattery = 50,
            singleBattery = null,
            leftWearing = true,
            rightWearing = true,
            leftCharging = true,
            rightCharging = true,
            caseCharging = true,
            revision = 0L,
            updatedAt = 0L,
        )

        val result = AirpodsStateMapper.fromMilinkSnapshot(snapshot)

        assertEquals("AA:BB:CC:DD:EE:FF", result.mac)
    }

    @Test
    fun fromMilinkSnapshot_invalidMac_shouldFallBackToOriginal() {
        val rawMac = "not-a-mac"
        val snapshot = MilinkDeviceSnapshot(
            mac = rawMac,
            name = null,
            brand = null,
            model = null,
            deviceId = "id",
            connected = true,
            protocolReady = false,
            leftBattery = 50,
            rightBattery = 50,
            caseBattery = 50,
            singleBattery = null,
            leftWearing = true,
            rightWearing = true,
            leftCharging = true,
            rightCharging = true,
            caseCharging = true,
            revision = 0L,
            updatedAt = 0L,
        )

        val result = AirpodsStateMapper.fromMilinkSnapshot(snapshot)

        assertEquals(rawMac, result.mac)
    }

    // -------------------------------------------------------------------------
    // toStateArray
    // -------------------------------------------------------------------------

    @Test
    fun toStateArray_shouldMapAllFieldsInCorrectOrder() {
        val snapshot = AirpodsStateSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            deviceId = "deviceX",
            isLeftWearing = "true",
            leftBattery = "80",
            isRightWearing = "false",
            rightBattery = "90",
            boxBattery = "60",
            isLeftCharging = "true",
            isRightCharging = "false",
            isBoxCharging = "true",
            connectState = "2",
        )

        val result = AirpodsStateMapper.toStateArray(snapshot)

        assertEquals(9, result.size)
        assertArrayEquals(
            arrayOf(
                "true",   // 0: isLeftWearing
                "80",     // 1: leftBattery
                "false",  // 2: isRightWearing
                "90",     // 3: rightBattery
                "60",     // 4: boxBattery
                "true",   // 5: isLeftCharging
                "false",  // 6: isRightCharging
                "true",   // 7: isBoxCharging
                "deviceX",// 8: deviceId
            ),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // toBundleFields
    // -------------------------------------------------------------------------

    @Test
    fun toBundleFields_shouldContainAllExpectedKeysAndValues() {
        val snapshot = AirpodsStateSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            deviceId = "deviceX",
            isLeftWearing = "true",
            leftBattery = "80",
            isRightWearing = "false",
            rightBattery = "90",
            boxBattery = "60",
            isLeftCharging = "true",
            isRightCharging = "false",
            isBoxCharging = "true",
            connectState = "2",
        )

        val result = AirpodsStateMapper.toBundleFields(snapshot)

        assertEquals(11, result.size)
        assertEquals("AA:BB:CC:DD:EE:FF", result["device"])
        assertEquals("2", result["connectState"])
        assertEquals("true", result["isLeftWearing"])
        assertEquals("80", result["leftBattery"])
        assertEquals("false", result["isRightWearing"])
        assertEquals("90", result["rightBattery"])
        assertEquals("60", result["boxBattery"])
        assertEquals("true", result["isLeftCharging"])
        assertEquals("false", result["isRightCharging"])
        assertEquals("true", result["isBoxCharging"])
        assertEquals("deviceX", result["modelName"])
    }
}
