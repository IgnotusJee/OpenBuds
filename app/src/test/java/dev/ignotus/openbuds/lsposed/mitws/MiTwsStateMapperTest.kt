package dev.ignotus.openbuds.lsposed.mitws

import dev.ignotus.openbuds.ble.DiscoveredDevice
import dev.ignotus.openbuds.data.BatteryState
import dev.ignotus.openbuds.data.HeadphoneUiState
import dev.ignotus.openbuds.data.NoiseControlState
import dev.ignotus.openbuds.headphones.HeadphoneFeature
import dev.ignotus.openbuds.headphones.qcy.devices.QcyC30SProfile
import dev.ignotus.openbuds.headphones.sony.devices.LinkBudsSProfile
import dev.ignotus.openbuds.integration.milink.MilinkBridgeSnapshotMapper
import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import dev.ignotus.openbuds.protocol.NoiseControlMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiTwsStateMapperTest {
    @Test
    fun batteryArray_mapsTrueWirelessBatteryWithCase() {
        val snapshot = snapshot(leftBattery = 70, rightBattery = 80, caseBattery = 90)

        assertArrayEquals(intArrayOf(70, 80, 90), MiTwsStateMapper.batteryArray(snapshot))
    }

    @Test
    fun batteryArray_mirrorsSingleBatteryForHeadsetFallback() {
        val snapshot = snapshot(leftBattery = null, rightBattery = null, caseBattery = null, singleBattery = 55)

        assertArrayEquals(intArrayOf(55, 55, -1), MiTwsStateMapper.batteryArray(snapshot))
    }

    @Test
    fun batteryArray_usesMinusOneForUnknownValues() {
        val snapshot = snapshot(leftBattery = null, rightBattery = null, caseBattery = null)

        assertArrayEquals(intArrayOf(-1, -1, -1), MiTwsStateMapper.batteryArray(snapshot))
    }

    @Test
    fun headsetInfoPowers_mapsBatteryAndChargingSlotsInMiLinkOrder() {
        val snapshot = snapshot(
            leftBattery = 70,
            rightBattery = 80,
            caseBattery = 90,
            leftCharging = true,
            rightCharging = false,
            caseCharging = true,
        )

        assertEquals(
            listOf(90, 70, 80, 1, 1, 0),
            MiTwsStateMapper.headsetInfoPowers(snapshot),
        )
    }

    @Test
    fun headsetInfoPowers_defaultsUnknownBatteryAndChargingToSafeValues() {
        val snapshot = snapshot(
            leftBattery = null,
            rightBattery = null,
            caseBattery = null,
            singleBattery = 55,
            leftCharging = null,
            rightCharging = null,
            caseCharging = null,
        )

        assertEquals(
            listOf(-1, 55, 55, 0, 0, 0),
            MiTwsStateMapper.headsetInfoPowers(snapshot),
        )
    }

    @Test
    fun ancState_mapsKnownAndUnknownModes() {
        assertEquals(0, MiTwsStateMapper.ancState(snapshot(ancMode = 0)))
        assertEquals(1, MiTwsStateMapper.ancState(snapshot(ancMode = 1)))
        assertEquals(2, MiTwsStateMapper.ancState(snapshot(ancMode = 2)))
        // When ancMode is null but supportsNoiseControl=true, return OFF (0)
        // to avoid poisoning AncBatteryModel with -1 which blocks ANC switching.
        assertEquals(0, MiTwsStateMapper.ancState(snapshot(ancMode = null)))
        // When ancMode is null and supportsNoiseControl=false, return -1.
        assertEquals(-1, MiTwsStateMapper.ancState(snapshot(ancMode = null, supportsNoiseControl = false)))
    }

    @Test
    fun snapshotMapper_derivesCapabilitiesFromSonyAndQcyProfiles() {
        val sony = MilinkBridgeSnapshotMapper.fromUiState(
            state = HeadphoneUiState(
                connectedDevice = DiscoveredDevice("LinkBuds S", "AA:BB:CC:DD:EE:FF", rssi = -40),
                connectedProfile = LinkBudsSProfile.template.toProfile(
                    adapterId = "sony-tandem",
                    brand = "Sony",
                    protocolName = "Sony Tandem",
                    displayName = "LinkBuds S",
                ),
                batteryState = BatteryState(left = 70, right = 80, cradle = 90),
                noiseControlState = NoiseControlState(controlMode = NoiseControlMode.NOISE_CANCELLING),
            ),
            revision = 1L,
            updatedAt = 2L,
        )!!
        assertTrue(sony.supportsBattery)
        assertTrue(sony.supportsNoiseControl)
        assertTrue(sony.supportsWearing)
        assertFalse(sony.supportsRing)

        val qcy = MilinkBridgeSnapshotMapper.fromUiState(
            state = HeadphoneUiState(
                connectedDevice = DiscoveredDevice("QCY C30S", "AA:BB:CC:DD:EE:11", rssi = -40),
                connectedProfile = QcyC30SProfile.template.toProfile(
                    adapterId = "qcy",
                    brand = "QCY",
                    protocolName = "QCY GATT TLV",
                    displayName = "QCY C30S",
                ),
                batteryState = BatteryState(left = 70, right = 80, cradle = 90),
            ),
            revision = 1L,
            updatedAt = 2L,
        )!!
        assertTrue(qcy.supportsBattery)
        assertTrue(qcy.supportsNoiseControl)
        assertFalse(qcy.supportsWearing)
        assertFalse(qcy.supportsRing)

        assertTrue(LinkBudsSProfile.template.capabilities.features.contains(HeadphoneFeature.WEARING_STATUS))
        assertFalse(QcyC30SProfile.template.capabilities.features.contains(HeadphoneFeature.WEARING_STATUS))
    }

    @Test
    fun wearStatus_mapsBothWorn_toAllowed() {
        val snapshot = snapshot(leftWearing = true, rightWearing = true)
        assertEquals("1", MiTwsStateMapper.wearStatus(snapshot))
    }

    @Test
    fun wearStatus_mapsLeftOnly_toAllowed() {
        val snapshot = snapshot(leftWearing = true, rightWearing = false)
        assertEquals("1", MiTwsStateMapper.wearStatus(snapshot))
    }

    @Test
    fun wearStatus_mapsRightOnly_toAllowed() {
        val snapshot = snapshot(leftWearing = false, rightWearing = true)
        assertEquals("1", MiTwsStateMapper.wearStatus(snapshot))
    }

    @Test
    fun wearStatus_mapsNotWorn() {
        val snapshot = snapshot(leftWearing = false, rightWearing = false)
        assertEquals("0", MiTwsStateMapper.wearStatus(snapshot))
    }

    @Test
    fun wearStatus_mapsUnknown() {
        // When wearing is unknown but supportsNoiseControl=true, return "1"
        // to avoid blocking ANC switching in isSupportOpAnc().
        val snapshot = snapshot(leftWearing = null, rightWearing = null)
        assertEquals("1", MiTwsStateMapper.wearStatus(snapshot))
        // When wearing is unknown and supportsNoiseControl=false, return "-1".
        val noAnc = snapshot(leftWearing = null, rightWearing = null, supportsNoiseControl = false)
        assertEquals("-1", MiTwsStateMapper.wearStatus(noAnc))
    }

    private fun snapshot(
        leftBattery: Int? = null,
        rightBattery: Int? = null,
        caseBattery: Int? = null,
        singleBattery: Int? = null,
        ancMode: Int? = null,
        leftWearing: Boolean? = null,
        rightWearing: Boolean? = null,
        leftCharging: Boolean? = null,
        rightCharging: Boolean? = null,
        caseCharging: Boolean? = null,
        supportsNoiseControl: Boolean = true,
    ): MilinkDeviceSnapshot =
        MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = "LinkBuds S",
            brand = "Sony",
            model = "LinkBuds S",
            deviceId = MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
            formFactor = MiTwsRuntimeProjection.FORM_FACTOR_TRUE_WIRELESS,
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
            ancMode = ancMode,
            ringing = false,
            currentVolume = null,
            currentAudioEffectState = null,
            supportsBattery = true,
            supportsNoiseControl = supportsNoiseControl,
            supportsWearing = true,
            supportsRing = false,
            supportsVolumeControl = false,
            supportsAudioEffect = false,
            supportsEq = false,
            supportsLeaStatus = false,
            supportsQuickAccess = false,
            supportsAmbientLevel = false,
            revision = 1L,
            updatedAt = 2L,
        )
}
