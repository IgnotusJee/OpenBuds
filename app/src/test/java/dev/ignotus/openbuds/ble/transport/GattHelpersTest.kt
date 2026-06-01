package dev.ignotus.openbuds.ble.transport

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [GattHelpers] — low-level BLE GATT primitives.
 *
 * Tests the pure-logic paths (constants). Android BLE framework calls
 * require instrumented tests or Robolectric.
 */
class GattHelpersTest {

    @Test
    fun standardCccdUuid_matchesBleSpec() {
        assertEquals(
            "00002902-0000-1000-8000-00805f9b34fb",
            GattHelpers.STANDARD_CCCD_UUID.toString(),
        )
    }
}
