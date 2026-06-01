package dev.ignotus.openbuds.integration.milink

import dev.ignotus.openbuds.ble.DiscoveredDevice
import dev.ignotus.openbuds.data.BatteryState
import dev.ignotus.openbuds.data.DeviceInfoState
import dev.ignotus.openbuds.data.HeadphoneUiState
import dev.ignotus.openbuds.data.WearingState
import dev.ignotus.openbuds.lsposed.milink.DeviceIdRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MilinkBridgeSnapshotMapperTest {

    @Test
    fun fromUiState_returnsNullWhenDisconnected() {
        assertNull(
            MilinkBridgeSnapshotMapper.fromUiState(
                HeadphoneUiState(),
                revision = 1L,
                updatedAt = 2L,
            )
        )
    }

    @Test
    fun fromUiState_mapsBatteryWearingAndCharging() {
        val state = HeadphoneUiState(
            connectedDevice = DiscoveredDevice(
                name = "QCY C30S",
                address = "aa:bb:cc:dd:ee:ff",
                rssi = -40,
            ),
            deviceInfo = DeviceInfoState(modelName = "QCY C30S", protocolReady = false),
            batteryState = BatteryState(
                left = 80,
                right = 90,
                cradle = 50,
                leftCharging = true,
                rightCharging = false,
                cradleCharging = true,
            ),
            wearingState = WearingState(leftWearing = true, rightWearing = null),
        )

        val snapshot = MilinkBridgeSnapshotMapper.fromUiState(state, revision = 7L, updatedAt = 9L)!!

        assertEquals("AA:BB:CC:DD:EE:FF", snapshot.mac)
        assertEquals("QCY C30S", snapshot.name)
        assertEquals("QCY C30S", snapshot.model)
        assertEquals(DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID, snapshot.deviceId)
        assertEquals(80, snapshot.leftBattery)
        assertEquals(90, snapshot.rightBattery)
        assertEquals(50, snapshot.caseBattery)
        assertEquals(true, snapshot.leftCharging)
        assertEquals(false, snapshot.rightCharging)
        assertEquals(true, snapshot.caseCharging)
        assertEquals(true, snapshot.leftWearing)
        assertNull(snapshot.rightWearing)
        assertEquals(7L, snapshot.revision)
        assertEquals(9L, snapshot.updatedAt)
    }
}
