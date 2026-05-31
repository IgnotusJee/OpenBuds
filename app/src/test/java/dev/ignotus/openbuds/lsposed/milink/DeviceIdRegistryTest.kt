package dev.ignotus.openbuds.lsposed.milink

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIdRegistryTest {

    @Test
    fun defaultTemplate_usesGenericEarbudTypeZero() {
        val template = DeviceIdRegistry.defaultTemplateForMac("AA:BB:CC:DD:EE:FF")

        assertEquals(DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID, template.deviceId)
        assertEquals(DeviceIdRegistry.HEADSET_TYPE_GENERIC_EARBUD, template.expectedHeadsetType)
    }

    @Test
    fun openWearTemplate_isAvailableForFutureBrandSelection() {
        val template = DeviceIdRegistry.openWearTemplate

        assertEquals(DeviceIdRegistry.OPEN_WEAR_DEVICE_ID, template.deviceId)
        assertEquals(DeviceIdRegistry.HEADSET_TYPE_OPEN_WEAR, template.expectedHeadsetType)
    }
}
