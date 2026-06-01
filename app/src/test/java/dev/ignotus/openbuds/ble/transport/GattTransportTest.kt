package dev.ignotus.openbuds.ble.transport

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [GattTransport] — brand-agnostic BLE GATT transport.
 *
 * GattTransport uses Android Handler/Looper internally, which requires
 * Robolectric or an Android device/emulator to run. These integration-level
 * tests should be added as instrumented tests (androidTest).
 *
 * This test verifies the config class and contract only.
 */
class GattTransportTest {

    @Test
    fun config_hasCorrectDefaults() {
        val config = GattTransportConfig(
            name = "TEST",
            kind = "TEST",
            serviceUuid = java.util.UUID.randomUUID(),
            writeCharacteristicUuid = java.util.UUID.randomUUID(),
        )
        assertEquals("TEST", config.name)
        assertEquals("TEST", config.kind)
        assertEquals(512, config.requestedMtu)
        assertEquals(1500L, config.mtuTimeoutMs)
        assertEquals(5000L, config.readyDeadlineMs)
    }
}
