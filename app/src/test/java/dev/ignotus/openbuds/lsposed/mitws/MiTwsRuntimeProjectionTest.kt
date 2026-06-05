package dev.ignotus.openbuds.lsposed.mitws

import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiTwsRuntimeProjectionTest {
    @Test
    fun targetMatchesActive_acceptsAssignedDeviceIdForAuthorizedActiveSnapshot() {
        val projection = projection(snapshot())

        assertTrue(
            projection.targetMatchesActive(
                address = "aa:bb:cc:dd:ee:ff",
                deviceId = MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
            )
        )
    }

    @Test
    fun targetMatchesActive_acceptsSnapshotDeviceId() {
        val projection = projection(snapshot(deviceId = "SNAPSHOT_ID"))

        assertTrue(
            projection.targetMatchesActive(
                address = "AA:BB:CC:DD:EE:FF",
                deviceId = "SNAPSHOT_ID",
            )
        )
    }

    @Test
    fun targetMatchesActive_rejectsUnauthorizedAddress() {
        val projection = projection(snapshot())

        assertFalse(
            projection.targetMatchesActive(
                address = "AA:BB:CC:DD:EE:00",
                deviceId = MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
            )
        )
    }

    @Test
    fun shouldProjectActiveHeadset_doesNotOverrideNonOpenBudsOriginal() {
        val projection = projection(snapshot())

        assertFalse(projection.shouldProjectActiveHeadset(FakeHeadsetDevice("11:22:33:44:55:66")))
    }

    @Test
    fun shouldProjectActiveHeadset_allowsNullOriginalWhenActiveSnapshotExists() {
        val projection = projection(snapshot())

        assertTrue(projection.shouldProjectActiveHeadset(null))
    }

    @Test
    fun activeSnapshot_returnsNullWhenProjectionGateClosed() {
        val projection = projection(snapshot(), gateOpen = false)

        assertNull(projection.activeSnapshot())
    }

    @Test
    fun activeSnapshot_returnsNullWhenOnlyClassificationEligibilityExists() {
        val projection = projection(snapshot = null, classificationEligible = true)

        assertNull(projection.activeSnapshot())
    }

    @Test
    fun shouldProjectActiveHeadset_rejectsClassificationOnlyEligibility() {
        val projection = projection(snapshot = null, classificationEligible = true)

        assertFalse(projection.shouldProjectActiveHeadset(null))
    }

    @Test
    fun targetMatchesActive_rejectsClassificationOnlyEligibility() {
        val projection = projection(snapshot = null, classificationEligible = true)

        assertFalse(
            projection.targetMatchesActive(
                address = "AA:BB:CC:DD:EE:FF",
                deviceId = MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
            )
        )
    }

    @Test
    fun projectConnectedDevices_doesNotAppendClassificationOnlyDevice() {
        val projection = projection(snapshot = null, classificationEligible = true)

        assertEquals(
            emptyList<android.bluetooth.BluetoothDevice>(),
            projection.projectConnectedDevices(emptyList<android.bluetooth.BluetoothDevice>()),
        )
    }

    @Test
    fun resolvedHeadsetInfoVolume_prefersOriginalWhenSnapshotHasNoSupportedVolume() {
        val snapshot = snapshot(currentVolume = null, supportsVolumeControl = false)
        val projection = projection(snapshot)

        assertEquals(37, projection.resolvedHeadsetInfoVolume(snapshot, FakeHeadsetInfo(volume = 37, audioEffect = 1)))
    }

    @Test
    fun resolvedHeadsetInfoVolume_prefersSnapshotWhenSupported() {
        val snapshot = snapshot(currentVolume = 52, supportsVolumeControl = true)
        val projection = projection(snapshot)

        assertEquals(52, projection.resolvedHeadsetInfoVolume(snapshot, FakeHeadsetInfo(volume = 37, audioEffect = 1)))
    }

    @Test
    fun resolvedHeadsetInfoAudioEffect_prefersOriginalWhenSnapshotHasNoSupportedState() {
        val snapshot = snapshot(currentAudioEffectState = null, supportsAudioEffect = false)
        val projection = projection(snapshot)

        assertEquals(1, projection.resolvedHeadsetInfoAudioEffect(snapshot, FakeHeadsetInfo(volume = 37, audioEffect = 1)))
    }

    @Test
    fun resolvedHeadsetInfoAudioEffect_prefersSnapshotWhenSupported() {
        val snapshot = snapshot(currentAudioEffectState = 2, supportsAudioEffect = true)
        val projection = projection(snapshot)

        assertEquals(2, projection.resolvedHeadsetInfoAudioEffect(snapshot, FakeHeadsetInfo(volume = 37, audioEffect = 1)))
    }

    @Test
    fun resolvedHeadsetInfoType_usesTrueWirelessFormFactorForTwsDevices() {
        val snapshot = snapshot(formFactor = MiTwsRuntimeProjection.FORM_FACTOR_TRUE_WIRELESS)
        val projection = projection(snapshot)

        assertEquals(0, projection.resolvedHeadsetInfoType(snapshot, FakeHeadsetInfo(volume = 37, audioEffect = 1, type = 2)))
    }

    @Test
    fun resolvedHeadsetInfoType_usesHeadsetFormFactorForHeadsetDevices() {
        val snapshot = snapshot(formFactor = MiTwsRuntimeProjection.FORM_FACTOR_HEADSET)
        val projection = projection(snapshot)

        assertEquals(2, projection.resolvedHeadsetInfoType(snapshot, FakeHeadsetInfo(volume = 37, audioEffect = 1, type = 5)))
    }

    private fun projection(
        snapshot: MilinkDeviceSnapshot?,
        gateOpen: Boolean = true,
        classificationEligible: Boolean = snapshot != null,
    ): MiTwsRuntimeProjection {
        val bridge = FakeBridge(snapshot, classificationEligible)
        return MiTwsRuntimeProjection(
            classLoader = javaClass.classLoader!!,
            bridgeClient = bridge,
            deviceLookup = { null },
            deviceIdForMac = { MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID },
            projectionGate = { gateOpen },
        )
    }

    private fun snapshot(
        deviceId: String = MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
        currentVolume: Int? = null,
        currentAudioEffectState: Int? = null,
        supportsVolumeControl: Boolean = false,
        supportsAudioEffect: Boolean = false,
        formFactor: Int? = MiTwsRuntimeProjection.FORM_FACTOR_TRUE_WIRELESS,
    ): MilinkDeviceSnapshot =
        MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = "LinkBuds S",
            brand = "Sony",
            model = "LinkBuds S",
            deviceId = deviceId,
            formFactor = formFactor,
            connected = true,
            protocolReady = true,
            leftBattery = 70,
            rightBattery = 80,
            caseBattery = 90,
            singleBattery = null,
            leftWearing = true,
            rightWearing = false,
            leftCharging = false,
            rightCharging = false,
            caseCharging = false,
            ancMode = 1,
            ringing = false,
            currentVolume = currentVolume,
            currentAudioEffectState = currentAudioEffectState,
            supportsBattery = true,
            supportsNoiseControl = true,
            supportsWearing = true,
            supportsRing = false,
            supportsVolumeControl = supportsVolumeControl,
            supportsAudioEffect = supportsAudioEffect,
            supportsEq = false,
            supportsLeaStatus = false,
            supportsQuickAccess = false,
            supportsAmbientLevel = false,
            revision = 1L,
            updatedAt = 2L,
        )

    private class FakeBridge(
        private val snapshot: MilinkDeviceSnapshot?,
        private val classificationEligible: Boolean,
    ) : MilinkBridgeClientFacade {
        override val adapterEnabled: Boolean = true

        override fun snapshotFor(mac: String?): MilinkDeviceSnapshot? =
            snapshot?.takeIf { mac.equals(it.mac, ignoreCase = true) }

        override fun isAuthorized(mac: String?): Boolean =
            isClassificationEligible(mac)

        override fun isClassificationEligible(mac: String?): Boolean =
            classificationEligible && mac.equals(snapshot?.mac ?: "AA:BB:CC:DD:EE:FF", ignoreCase = true)

        override fun authorizedSnapshots(): List<MilinkDeviceSnapshot> =
            listOfNotNull(snapshot)
    }

    private class FakeHeadsetDevice(private val address: String) {
        fun getAddress(): String = address
    }

    private class FakeHeadsetInfo(
        private val volume: Int,
        private val audioEffect: Int,
        private val type: Int = 2,
    ) {
        fun getVolume(): Int = volume
        fun getAudioEffectState(): Int = audioEffect
        fun getType(): Int = type
    }
}
