package dev.ignotus.openbuds.integration.milink

import dev.ignotus.openbuds.lsposed.milink.DeviceIdRegistry
import dev.ignotus.openbuds.lsposed.milink.DeviceIdTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class DeviceIdRegistryTest {

    @Test
    fun `GENERIC_EARBUD_DEVICE_ID constant is 01010101`() {
        assertEquals("01010101", DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID)
    }

    @Test
    fun `OPEN_WEAR_DEVICE_ID constant is 01013400`() {
        assertEquals("01013400", DeviceIdRegistry.OPEN_WEAR_DEVICE_ID)
    }

    @Test
    fun `HEADSET_TYPE_GENERIC_EARBUD is 0`() {
        assertEquals(0, DeviceIdRegistry.HEADSET_TYPE_GENERIC_EARBUD)
    }

    @Test
    fun `HEADSET_TYPE_OPEN_WEAR is 4`() {
        assertEquals(4, DeviceIdRegistry.HEADSET_TYPE_OPEN_WEAR)
    }

    @Test
    fun `genericEarbudTemplate has correct deviceId and expectedHeadsetType`() {
        val template = DeviceIdRegistry.genericEarbudTemplate
        assertNotNull(template)
        assertEquals(DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID, template.deviceId)
        assertEquals(DeviceIdRegistry.HEADSET_TYPE_GENERIC_EARBUD, template.expectedHeadsetType)
        assertEquals("Air 2s generic earbud template", template.label)
    }

    @Test
    fun `openWearTemplate has correct deviceId and expectedHeadsetType`() {
        val template = DeviceIdRegistry.openWearTemplate
        assertNotNull(template)
        assertEquals(DeviceIdRegistry.OPEN_WEAR_DEVICE_ID, template.deviceId)
        assertEquals(DeviceIdRegistry.HEADSET_TYPE_OPEN_WEAR, template.expectedHeadsetType)
        assertEquals("O74 open-wear template", template.label)
    }

    @Test
    fun `defaultTemplateForMac with null returns genericEarbudTemplate`() {
        assertSame(DeviceIdRegistry.genericEarbudTemplate, DeviceIdRegistry.defaultTemplateForMac(null))
    }

    @Test
    fun `defaultTemplateForMac with AA BB CC DD EE FF returns genericEarbudTemplate`() {
        assertSame(
            DeviceIdRegistry.genericEarbudTemplate,
            DeviceIdRegistry.defaultTemplateForMac("AA:BB:CC:DD:EE:FF")
        )
    }

    @Test
    fun `deviceIdForMac with null returns GENERIC_EARBUD_DEVICE_ID`() {
        assertEquals(DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID, DeviceIdRegistry.deviceIdForMac(null))
    }

    @Test
    fun `deviceIdForMac with AA BB CC DD EE FF returns GENERIC_EARBUD_DEVICE_ID`() {
        assertEquals(
            DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID,
            DeviceIdRegistry.deviceIdForMac("AA:BB:CC:DD:EE:FF")
        )
    }

    @Test
    fun `DeviceIdTemplate data class equality works correctly`() {
        val a = DeviceIdTemplate("01010101", 0, "test")
        val b = DeviceIdTemplate("01010101", 0, "test")
        val c = DeviceIdTemplate("02020202", 0, "test")
        val d = DeviceIdTemplate("01010101", 1, "test")
        val e = DeviceIdTemplate("01010101", 0, "different label")

        assertEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(a, d)
        assertNotEquals(a, e)
    }
}
