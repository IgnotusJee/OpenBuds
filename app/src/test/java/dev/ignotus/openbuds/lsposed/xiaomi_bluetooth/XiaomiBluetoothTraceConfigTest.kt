package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiBluetoothTraceConfigTest {

    @Test
    fun trace_defaultsToDisabled_whenSystemPropertyUnavailable() {
        assertFalse(XiaomiBluetoothTraceConfig.isTraceEnabled())
        assertFalse(XiaomiBluetoothTraceConfig.shouldInstallForPackage(XiaomiBluetoothTraceConfig.TARGET_PACKAGE))
    }

    @Test
    fun constants_matchPlannedSystemProperties() {
        assertEquals("com.xiaomi.bluetooth", XiaomiBluetoothTraceConfig.TARGET_PACKAGE)
        assertEquals("debug.openbuds.xiaomi_bt_trace_enable", XiaomiBluetoothTraceConfig.TRACE_ENABLE_PROPERTY)
        assertEquals("debug.openbuds.xiaomi_bt_trace_macs", XiaomiBluetoothTraceConfig.TRACE_MAC_ALLOWLIST_PROPERTY)
        assertEquals("debug.openbuds.xiaomi_bt_trace_sample_ms", XiaomiBluetoothTraceConfig.TRACE_SAMPLE_MS_PROPERTY)
        assertEquals(
            "debug.openbuds.xiaomi_bt_spp_probe_enable",
            XiaomiBluetoothTraceConfig.SPP_PROBE_ENABLE_PROPERTY,
        )
        assertEquals(
            "debug.openbuds.xiaomi_bt_spp_probe_mac",
            XiaomiBluetoothTraceConfig.SPP_PROBE_MAC_PROPERTY,
        )
        assertEquals(
            "debug.openbuds.xiaomi_bt_spp_probe_mode",
            XiaomiBluetoothTraceConfig.SPP_PROBE_MODE_PROPERTY,
        )
        assertEquals(
            "debug.openbuds.xiaomi_bt_spp_probe_uuid",
            XiaomiBluetoothTraceConfig.SPP_PROBE_UUID_PROPERTY,
        )
    }

    @Test
    fun parseMacAllowlist_acceptsCommaSpaceAndSemicolonSeparatedValues() {
        val parsed = XiaomiBluetoothTraceConfig.parseMacAllowlist(
            "aa:bb:cc:dd:ee:ff 11:22:33:44:55:66;77:88:99:aa:bb:cc,invalid",
        )

        assertEquals(
            setOf(
                "AA:BB:CC:DD:EE:FF",
                "11:22:33:44:55:66",
                "77:88:99:AA:BB:CC",
            ),
            parsed,
        )
    }

    @Test
    fun shouldTraceMac_allowsEverything_whenAllowlistEmpty() {
        assertTrue(XiaomiBluetoothTraceConfig.shouldTraceMac("AA:BB:CC:DD:EE:FF", emptySet()))
        assertTrue(XiaomiBluetoothTraceConfig.shouldTraceMac(null, emptySet()))
    }

    @Test
    fun shouldTraceMac_filtersByNormalizedAllowlist_whenAllowlistPresent() {
        val allowlist = setOf("AA:BB:CC:DD:EE:FF")

        assertTrue(XiaomiBluetoothTraceConfig.shouldTraceMac("aa:bb:cc:dd:ee:ff", allowlist))
        assertFalse(XiaomiBluetoothTraceConfig.shouldTraceMac("11:22:33:44:55:66", allowlist))
        assertFalse(XiaomiBluetoothTraceConfig.shouldTraceMac(null, allowlist))
    }

    @Test
    fun parseSampleWindowMs_usesDefaultForInvalidValues() {
        assertEquals(0L, XiaomiBluetoothTraceConfig.parseSampleWindowMs("-1"))
        assertEquals(250L, XiaomiBluetoothTraceConfig.parseSampleWindowMs("250"))
        assertEquals(
            XiaomiBluetoothTraceConfig.DEFAULT_SAMPLE_MS,
            XiaomiBluetoothTraceConfig.parseSampleWindowMs("not-a-number"),
        )
    }

    @Test
    fun maskMac_redactsFirstThreeOctets() {
        assertEquals("**:**:**:DD:EE:FF", XiaomiBluetoothTraceConfig.maskMac("aa:bb:cc:dd:ee:ff"))
        assertEquals("not-a-mac", XiaomiBluetoothTraceConfig.maskMac("not-a-mac"))
        assertEquals("", XiaomiBluetoothTraceConfig.maskMac(null))
    }

    @Test
    fun parseSppProbeMode_acceptsKnownModesCaseInsensitively() {
        assertEquals(SonySppProbeMode.CONNECT, XiaomiBluetoothTraceConfig.parseSppProbeMode("connect"))
        assertEquals(SonySppProbeMode.READONLY, XiaomiBluetoothTraceConfig.parseSppProbeMode("READONLY"))
        assertEquals(SonySppProbeMode.WRITE, XiaomiBluetoothTraceConfig.parseSppProbeMode(" write "))
    }

    @Test
    fun parseSppProbeMode_fallsBackToConnectForUnknownValues() {
        assertEquals(SonySppProbeMode.CONNECT, XiaomiBluetoothTraceConfig.parseSppProbeMode(""))
        assertEquals(SonySppProbeMode.CONNECT, XiaomiBluetoothTraceConfig.parseSppProbeMode("mutation"))
    }

    @Test
    fun parseSppProbeUuid_autoAndBlankUseAutoPolicy() {
        assertEquals(SonySppProbeUuid.AUTO, XiaomiBluetoothTraceConfig.parseSppProbeUuid(""))
        assertEquals(SonySppProbeUuid.AUTO, XiaomiBluetoothTraceConfig.parseSppProbeUuid("AUTO"))
    }

    @Test
    fun parseSppProbeUuid_acceptsOnlyKnownSonyMdrUuids() {
        assertEquals(
            SonySppProbeUuid.MDR_UUID_1,
            XiaomiBluetoothTraceConfig.parseSppProbeUuid("956C7B26-D49A-4BA8-B03F-B17D393CB6E2"),
        )
        assertEquals(
            SonySppProbeUuid.MDR_UUID_2,
            XiaomiBluetoothTraceConfig.parseSppProbeUuid("96cc203e-5068-46ad-b32d-e316f5e069ba"),
        )
        assertEquals(
            SonySppProbeUuid.AUTO,
            XiaomiBluetoothTraceConfig.parseSppProbeUuid("00000000-0000-0000-0000-000000000000"),
        )
    }
}
