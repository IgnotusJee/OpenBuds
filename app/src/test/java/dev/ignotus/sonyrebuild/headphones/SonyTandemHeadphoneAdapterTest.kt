package dev.ignotus.sonyrebuild.headphones

import dev.ignotus.sonyrebuild.ble.DiscoveredSonyDevice
import dev.ignotus.sonyrebuild.protocol.CommonInquiredType
import dev.ignotus.sonyrebuild.protocol.EqEbbInquiredType
import dev.ignotus.sonyrebuild.protocol.NcAsmInquiredType
import dev.ignotus.sonyrebuild.protocol.NoiseControlMode
import dev.ignotus.sonyrebuild.protocol.ParsedTandemResponse
import dev.ignotus.sonyrebuild.protocol.PowerInquiredType
import dev.ignotus.sonyrebuild.protocol.SonyTandemV1Table1Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyTandemHeadphoneAdapterTest {
    @Test
    fun match_wh1000xm4_returnsPremiumProfile() {
        val profile = SonyTandemHeadphoneAdapter.match(
            xm4Device(),
            reportedModelName = "WH-1000XM4",
        )

        requireNotNull(profile)
        assertEquals("sony-tandem", profile.adapterId)
        assertEquals("Sony", profile.brand)
        assertEquals("WH-1000XM4", profile.modelName)
        assertEquals("PREMIUM", profile.series)
        assertTrue(profile.supports(HeadphoneFeature.NOISE_CONTROL))
        assertEquals(listOf(PowerInquiredType.BATTERY), profile.capabilities.batteryQueries)
        assertEquals(HeadphoneFormFactor.HEADSET, profile.capabilities.formFactor)
        // XM4: battery, NC/ASM, ambient level, ambient voice → V1 Table1
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1,
            profile.protocolFor(HeadphoneFeature.BATTERY),
        )
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1,
            profile.protocolFor(HeadphoneFeature.NOISE_CONTROL),
        )
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1,
            profile.protocolFor(HeadphoneFeature.AMBIENT_LEVEL),
        )
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1,
            profile.protocolFor(HeadphoneFeature.AMBIENT_VOICE_MODE),
        )
        // XM4: EQ, Clear Bass, playback → V2 Table1
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
            profile.protocolFor(HeadphoneFeature.EQ),
        )
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
            profile.protocolFor(HeadphoneFeature.CLEAR_BASS),
        )
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
            profile.protocolFor(HeadphoneFeature.PLAYBACK_CONTROL),
        )
    }

    @Test
    fun refreshCommands_wh1000xm4_includeExistingFeatureReads() {
        val profile = SonyTandemHeadphoneAdapter.match(xm4Device(), "WH-1000XM4")!!
        val commands = SonyTandemHeadphoneAdapter.buildRefreshCommands(profile)
        val labels = commands.map { it.label }

        assertFalse(labels.any { it == "GET protocol info" })
        assertTrue(labels.any { it == "GET battery BATTERY" })
        assertFalse(labels.any { it == "GET battery LEFT_RIGHT_BATTERY" })
        assertFalse(labels.any { it == "GET battery CRADLE_BATTERY" })
        assertFalse(labels.any { it == "GET EQ param CUSTOM_EQ" })
        assertTrue(labels.any { it == "GET EQ param EBB" })
        assertTrue(labels.any { it == "GET playback status" })
        assertTrue(labels.any { it == "GET display firmware version" })
        // XM4 NC refresh now routes through V1 path → label is "GET NC/ASM param V1"
        assertTrue(commands.any { it.label == "GET NC/ASM param V1" })
        assertArrayEquals(
            byteArrayOf(0x0E, 0x10, 0x00),
            commands.first { it.label == "GET battery BATTERY" }.bytes,
        )
        assertFalse(commands.any { it.bytes.contentEquals(byteArrayOf(0x0E, 0x22, 0x00)) })
    }

    @Test
    fun noiseControlCommands_wh1000xm4_useTableSet1NcAsmWrites() {
        val profile = SonyTandemHeadphoneAdapter.match(xm4Device(), "WH-1000XM4")!!
        val commands = SonyTandemHeadphoneAdapter.buildSetNoiseControlModeCommands(
            profile = profile,
            mode = NoiseControlMode.AMBIENT_SOUND,
            ambientLevel = 12,
            ambientMode = dev.ignotus.sonyrebuild.protocol.AmbientSoundMode.NORMAL,
        )

        assertEquals(1, commands.size)
        assertTrue(commands.first().bytes.contentEquals(SonyTandemV1Table1Protocol.buildSetNoiseControlMode(
            NoiseControlMode.AMBIENT_SOUND,
            ambientLevel = 12,
        )))
    }

    @Test
    fun registry_resolvesWh1000xm4ByDeviceName() {
        val profile = HeadphoneAdapterRegistry.resolve(xm4Device())

        assertEquals("WH-1000XM4", profile.modelName)
        assertEquals("Sony Tandem", profile.protocolName)
        assertEquals("sony-tandem", profile.adapterId)
    }

    @Test
    fun match_linkBudsS_usesV2Table1AndTrueWirelessBattery() {
        val profile = HeadphoneAdapterRegistry.resolve(
            DiscoveredSonyDevice(
                name = "LinkBuds S",
                address = "00:11:22:33:44:56",
                rssi = 0,
                source = "bonded",
                isLikelyControlEndpoint = true,
            )
        )

        assertEquals(HeadphoneFormFactor.TRUE_WIRELESS, profile.capabilities.formFactor)
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
            profile.protocolFor(HeadphoneFeature.NOISE_CONTROL),
        )
        assertEquals(
            listOf(
                PowerInquiredType.LEFT_RIGHT_BATTERY,
                PowerInquiredType.CRADLE_BATTERY,
            ),
            profile.capabilities.batteryQueries,
        )
        assertTrue(profile.capabilities.eqConfig.statusQueryTypes.contains(EqEbbInquiredType.CUSTOM_EQ))
    }

    @Test
    fun parse_xm4TableSet1NcAsmResponse_routesViaV1_returnsNoiseControl() {
        val profile = SonyTandemHeadphoneAdapter.match(xm4Device(), "WH-1000XM4")!!
        // 0x67 = NCASM_RET_PARAM, 0x02 = V1_TABLE_SET1_NC_ASM type
        val raw = byteArrayOf(0x0E, 0x67, 0x02, 0x01, 0x02, 0x02, 0x01, 0x00, 0x00)
        val parsed = SonyTandemHeadphoneAdapter.parse(profile, raw)

        assertTrue("Expected NoiseControl but got ${parsed::class.simpleName}", parsed is ParsedTandemResponse.NoiseControl)
        parsed as ParsedTandemResponse.NoiseControl
        assertEquals(NcAsmInquiredType.V1_TABLE_SET1_NC_ASM, parsed.type)
        assertEquals(NoiseControlMode.NOISE_CANCELLING, parsed.controlMode)
    }

    @Test
    fun parse_xm4BatteryResponse_routesToV1Parser_returnsBattery() {
        val profile = SonyTandemHeadphoneAdapter.match(xm4Device(), "WH-1000XM4")!!
        val raw = byteArrayOf(0x0E, 0x11, 0x00, 88.toByte(), 0x00)
        val parsed = SonyTandemHeadphoneAdapter.parse(profile, raw)

        assertTrue("Expected Battery but got ${parsed::class.simpleName}", parsed is ParsedTandemResponse.Battery)
        parsed as ParsedTandemResponse.Battery
        assertEquals(PowerInquiredType.BATTERY, parsed.kind)
        assertEquals(listOf(88), parsed.values)
    }

    @Test
    fun parse_xm4DisplayFirmwareResponse_classifiedAsDeviceInfo_routesToV2() {
        val profile = SonyTandemHeadphoneAdapter.match(xm4Device(), "WH-1000XM4")!!
        val version = "2.5.0".encodeToByteArray()
        val raw = byteArrayOf(0x0E, 0x13, 0x09, version.size.toByte()) + version
        val parsed = SonyTandemHeadphoneAdapter.parse(profile, raw)

        assertTrue("Expected CommonStatus but got ${parsed::class.simpleName}", parsed is ParsedTandemResponse.CommonStatus)
        parsed as ParsedTandemResponse.CommonStatus
        assertEquals(CommonInquiredType.DISPLAY_FW_VERSION, parsed.type)
        assertEquals("2.5.0", parsed.text)
    }

    @Test
    fun parse_linkBudsSNcAsmResponse_routesToV2Parser_returnsNoiseControl() {
        val profile = HeadphoneAdapterRegistry.resolve(
            DiscoveredSonyDevice(
                name = "LinkBuds S",
                address = "00:11:22:33:44:56",
                rssi = 0,
                source = "bonded",
                isLikelyControlEndpoint = true,
            )
        )
        val raw = byteArrayOf(0x0E, 0x67, 0x17, 0x01, 0x01, 0x01, 0x00, 0x0C)
        val parsed = SonyTandemHeadphoneAdapter.parse(profile, raw)

        assertTrue("Expected NoiseControl but got ${parsed::class.simpleName}", parsed is ParsedTandemResponse.NoiseControl)
        parsed as ParsedTandemResponse.NoiseControl
        assertEquals(NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS, parsed.type)
        assertEquals(NoiseControlMode.AMBIENT_SOUND, parsed.controlMode)
        assertEquals(12, parsed.ambientLevel)
    }

    // ── 0x13 collision tests ──

    @Test
    fun parse_xm4V2CommonStatus0x01_notRoutedAsBattery() {
        val profile = SonyTandemHeadphoneAdapter.match(xm4Device(), "WH-1000XM4")!!
        val raw = byteArrayOf(0x0E, 0x13, 0x01, 0x00, 0x01)
        val parsed = SonyTandemHeadphoneAdapter.parse(profile, raw)

        assertTrue("Expected CommonStatus but got ${parsed::class.simpleName}", parsed is ParsedTandemResponse.CommonStatus)
        parsed as ParsedTandemResponse.CommonStatus
        assertEquals(CommonInquiredType.CONNECTION_STATUS, parsed.type)
    }

    @Test
    fun parse_xm4V2CommonStatus0x02_notRoutedAsBattery() {
        val profile = SonyTandemHeadphoneAdapter.match(xm4Device(), "WH-1000XM4")!!
        val raw = byteArrayOf(0x0E, 0x13, 0x02, 0x00, 0x01)
        val parsed = SonyTandemHeadphoneAdapter.parse(profile, raw)

        assertTrue("Expected CommonStatus but got ${parsed::class.simpleName}", parsed is ParsedTandemResponse.CommonStatus)
        parsed as ParsedTandemResponse.CommonStatus
        assertEquals(CommonInquiredType.AUDIO_CODEC, parsed.type)
    }

    @Test
    fun parse_xm4V1Battery0x00_stillRoutedAsBattery() {
        val profile = SonyTandemHeadphoneAdapter.match(xm4Device(), "WH-1000XM4")!!
        val raw = byteArrayOf(0x0E, 0x13, 0x00, 88.toByte())
        val parsed = SonyTandemHeadphoneAdapter.parse(profile, raw)

        assertTrue("Expected Battery but got ${parsed::class.simpleName}", parsed is ParsedTandemResponse.Battery)
        parsed as ParsedTandemResponse.Battery
        assertEquals(PowerInquiredType.BATTERY, parsed.kind)
        assertEquals(listOf(88), parsed.values)
    }

    // ── Model name normalization tests ──

    @Test
    fun registry_resolvesWh1000xm4WithSpaces() {
        val profile = HeadphoneAdapterRegistry.resolve(
            DiscoveredSonyDevice(
                name = "WH 1000XM4",
                address = "00:11:22:33:44:55",
                rssi = 0,
                source = "bonded",
                isLikelyControlEndpoint = true,
            )
        )
        assertEquals("WH-1000XM4", profile.modelName)
    }

    @Test
    fun registry_resolvesWh1000xm4WithUnderscores() {
        val profile = HeadphoneAdapterRegistry.resolve(
            DiscoveredSonyDevice(
                name = "WH_1000XM4",
                address = "00:11:22:33:44:55",
                rssi = 0,
                source = "bonded",
                isLikelyControlEndpoint = true,
            )
        )
        assertEquals("WH-1000XM4", profile.modelName)
    }

    private fun xm4Device(): DiscoveredSonyDevice =
        DiscoveredSonyDevice(
            name = "WH-1000XM4",
            address = "00:11:22:33:44:55",
            rssi = 0,
            source = "bonded",
            isLikelyControlEndpoint = true,
        )
}
