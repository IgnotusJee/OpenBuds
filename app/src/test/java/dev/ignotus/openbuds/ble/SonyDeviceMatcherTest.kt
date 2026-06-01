package dev.ignotus.openbuds.ble

import dev.ignotus.openbuds.ble.sony.DiscoveredSonyDevice
import dev.ignotus.openbuds.ble.sony.SonyAudioAdvertisement
import dev.ignotus.openbuds.ble.sony.SonyDeviceMatcher
import dev.ignotus.openbuds.protocol.sony.SonyGatt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyDeviceMatcherTest {
    @Test
    fun qcyNameDoesNotMatchSony() {
        assertFalse(SonyDeviceMatcher.matches(device("QCY-C30S"), null))
    }

    @Test
    fun sonyNameMatchesSony() {
        assertTrue(SonyDeviceMatcher.matches(device("WH-1000XM4"), null))
        assertTrue(SonyDeviceMatcher.matches(device("LinkBuds S"), null))
    }

    @Test
    fun sonyAdvertisementMatchesSony() {
        val device = device("Unknown BLE device").copy(
            sonyAd = SonyAudioAdvertisement(version = 2, raw = "04000201")
        )

        assertTrue(SonyDeviceMatcher.matches(device, null))
    }

    @Test
    fun sonyTandemUuidAndLabelMatchSony() {
        assertTrue(
            SonyDeviceMatcher.matches(
                device("Unknown").copy(advertisedServices = listOf(SonyGatt.TANDEM_V2_HPC_SERVICE.toString())),
                null,
            )
        )
        assertTrue(
            SonyDeviceMatcher.matches(
                device("Unknown").copy(advertisedServices = listOf("TANDEM_V1_MC_SERVICE")),
                null,
            )
        )
    }

    private fun device(name: String): DiscoveredSonyDevice =
        DiscoveredSonyDevice(name = name, address = "00:11:22:33:44:55", rssi = 0)
}
