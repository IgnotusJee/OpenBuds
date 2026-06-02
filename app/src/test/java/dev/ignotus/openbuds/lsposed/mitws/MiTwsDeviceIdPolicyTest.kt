package dev.ignotus.openbuds.lsposed.mitws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MiTwsDeviceIdPolicyTest {
    @Test
    fun constants_matchM0Templates() {
        assertEquals("01010101", MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID)
        assertEquals("01013201", MiTwsDeviceIdPolicy.FLORA_EARBUD_DEVICE_ID)
        assertEquals("020104005A", MiTwsDeviceIdPolicy.O73_FLORA_DEVICE_ID)
    }

    @Test
    fun defaultTemplate_usesGenericEarbudTypeZero() {
        val template = MiTwsDeviceIdPolicy.defaultTemplateForMac("AA:BB:CC:DD:EE:FF")

        assertEquals(MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID, template.deviceId)
        assertEquals(MiTwsDeviceIdPolicy.HEADSET_TYPE_GENERIC_EARBUD, template.expectedHeadsetType)
        assertFalse(template.experimental)
    }

    @Test
    fun floraTemplates_areAvailableOnlyAsExperiments() {
        assertTrue(MiTwsDeviceIdPolicy.floraEarbudTemplate.experimental)
        assertTrue(MiTwsDeviceIdPolicy.o73FloraTemplate.experimental)
    }

    @Test
    fun defaultTemplateForMac_withNullReturnsGenericEarbudTemplate() {
        assertSame(MiTwsDeviceIdPolicy.genericEarbudTemplate, MiTwsDeviceIdPolicy.defaultTemplateForMac(null))
    }

    @Test
    fun deviceIdForMac_returnsGenericEarbudTemplateByDefault() {
        assertEquals(MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID, MiTwsDeviceIdPolicy.deviceIdForMac(null))
        assertEquals(
            MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
            MiTwsDeviceIdPolicy.deviceIdForMac("AA:BB:CC:DD:EE:FF"),
        )
    }

    @Test
    fun templateForConfigValue_supportsGenericFloraAndO73() {
        assertSame(MiTwsDeviceIdPolicy.genericEarbudTemplate, MiTwsDeviceIdPolicy.templateForConfigValue(null))
        assertSame(MiTwsDeviceIdPolicy.genericEarbudTemplate, MiTwsDeviceIdPolicy.templateForConfigValue("generic"))
        assertSame(MiTwsDeviceIdPolicy.floraEarbudTemplate, MiTwsDeviceIdPolicy.templateForConfigValue("flora"))
        assertSame(
            MiTwsDeviceIdPolicy.floraEarbudTemplate,
            MiTwsDeviceIdPolicy.templateForConfigValue(MiTwsDeviceIdPolicy.FLORA_EARBUD_DEVICE_ID),
        )
        assertSame(MiTwsDeviceIdPolicy.o73FloraTemplate, MiTwsDeviceIdPolicy.templateForConfigValue("o73"))
        assertSame(
            MiTwsDeviceIdPolicy.o73FloraTemplate,
            MiTwsDeviceIdPolicy.templateForConfigValue(MiTwsDeviceIdPolicy.O73_FLORA_DEVICE_ID),
        )
    }

    @Test
    fun deviceIdTemplate_equalityWorksCorrectly() {
        val a = MiTwsDeviceIdTemplate("01010101", 0, "test", false)
        val b = MiTwsDeviceIdTemplate("01010101", 0, "test", false)
        val c = MiTwsDeviceIdTemplate("02020202", 0, "test", false)
        val d = MiTwsDeviceIdTemplate("01010101", 1, "test", false)
        val e = MiTwsDeviceIdTemplate("01010101", 0, "different label", false)
        val f = MiTwsDeviceIdTemplate("01010101", 0, "test", true)

        assertEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(a, d)
        assertNotEquals(a, e)
        assertNotEquals(a, f)
    }
}
