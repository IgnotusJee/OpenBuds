package dev.ignotus.sonyrebuild.headphones

import dev.ignotus.sonyrebuild.ble.DiscoveredSonyDevice
import dev.ignotus.sonyrebuild.protocol.AmbientSoundMode
import dev.ignotus.sonyrebuild.protocol.EqEbbInquiredType
import dev.ignotus.sonyrebuild.protocol.EqPresetId
import dev.ignotus.sonyrebuild.protocol.NcAsmInquiredType
import dev.ignotus.sonyrebuild.protocol.NoiseControlMode
import dev.ignotus.sonyrebuild.protocol.ParsedTandemResponse
import dev.ignotus.sonyrebuild.protocol.PowerInquiredType
import dev.ignotus.sonyrebuild.protocol.SonyTandemV1Table1Protocol
import dev.ignotus.sonyrebuild.protocol.unsigned
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1 regression tests for protocol routing correctness.
 *
 * These tests verify that each headphone model's feature→protocol mapping
 * is consistent across profile declaration, command construction, and response
 * parsing. Inconsistencies between the declared map and actual builder/parser
 * behaviour are flagged with comments referencing the relevant Phase.
 */
class SonyTandemProfileRoutingTest {

    // ── LinkBuds S ──────────────────────────────────────────────────────────

    @Test
    fun linkBudsS_allCoreFeaturesRouteToV2Table1() {
        val profile = linkBudsSProfile()

        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
            profile.protocolFor(HeadphoneFeature.BATTERY),
        )
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
            profile.protocolFor(HeadphoneFeature.NOISE_CONTROL),
        )
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
            profile.protocolFor(HeadphoneFeature.EQ),
        )
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
            profile.protocolFor(HeadphoneFeature.PLAYBACK_CONTROL),
        )
    }

    @Test
    fun linkBudsS_refreshCommands_useV2Builders() {
        val profile = linkBudsSProfile()
        val commands = SonyTandemHeadphoneAdapter.buildRefreshCommands(profile)

        // Battery refresh uses V2 POWER_GET_STATUS (0x22), not V1 COMMON_GET_BATTERY (0x10)
        val batteryCmd = commands.first { it.label.startsWith("GET battery") }
        assertEquals(0x0E, batteryCmd.bytes[0].unsigned)
        assertEquals(0x22, batteryCmd.bytes[1].unsigned)

        // NC/ASM refresh uses V2 NCASM_GET_PARAM (0x66) with V2 inquired type
        val ncCmd = commands.first { it.label.startsWith("GET NC/ASM") }
        assertEquals(0x0E, ncCmd.bytes[0].unsigned)
        assertEquals(0x66, ncCmd.bytes[1].unsigned)
    }

    @Test
    fun linkBudsS_noiseControlWrite_usesV2Builder() {
        val profile = linkBudsSProfile()
        val commands = SonyTandemHeadphoneAdapter.buildSetNoiseControlModeCommands(
            profile = profile,
            mode = NoiseControlMode.AMBIENT_SOUND,
            ambientLevel = 15,
            ambientMode = AmbientSoundMode.VOICE,
        )

        assertEquals(1, commands.size)
        // V2 uses 0x17 inquired type
        assertEquals(
            NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS.code.unsigned,
            commands[0].bytes[2].unsigned,
        )
    }

    @Test
    fun linkBudsS_eqPresetWrite_usesOfficialPresetPayloadWithoutCachedBands() {
        val profile = linkBudsSProfile()
        val context = EqWriteContext(rawBandSteps = listOf(0x13, 0x0A, 0x0A, 0x0A, 0x0B, 0x0C))

        val command = SonyTandemHeadphoneAdapter.buildSetEqPresetCommands(profile, EqPresetId.BASS, context).single()

        assertArrayEquals(byteArrayOf(0x0E, 0x58, 0x00, 0x16, 0x00), command.bytes)
        assertEquals(TandemChannel.GATT_V2_HPC, command.channel)
    }

    @Test
    fun linkBudsS_eqBandWrite_usesOfficialPresetEqBandPayload() {
        val profile = linkBudsSProfile()
        val rawSteps = listOf(0x13, 0x0A, 0x0A, 0x0A, 0x0B, 0x0C)
        val context = EqWriteContext(rawBandSteps = rawSteps)

        val command = SonyTandemHeadphoneAdapter.buildSetEqBandCommands(profile, rawSteps, EqPresetId.USER_SETTING2, context).single()

        assertArrayEquals(
            byteArrayOf(0x0E, 0x58, 0x00, 0xA2.toByte(), 0x06, 0x13, 0x0A, 0x0A, 0x0A, 0x0B, 0x0C),
            command.bytes,
        )
    }

    // ── WH-1000XM4 ──────────────────────────────────────────────────────────

    @Test
    fun xm4_batteryRoutesToV1Table1() {
        val profile = xm4Profile()
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1,
            profile.protocolFor(HeadphoneFeature.BATTERY),
        )
    }

    /**
     * Phase 4 fix: profile map now correctly maps NC/ASM → V1_TABLE1
     * to match the V1 builder that XM4 uses.
     */
    @Test
    fun xm4_noiseControl_profileMapNowShowsV1() {
        val profile = xm4Profile()
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1,
            profile.protocolFor(HeadphoneFeature.NOISE_CONTROL),
        )
    }

    /**
     * Phase 4 fix: both the profile map and the builder now use V1 for XM4 NC/ASM.
     * The inconsistency documented in Phase 1 is resolved.
     */
    @Test
    fun xm4_noiseControlWrite_usesV1Builder_consistentWithProfileMap() {
        val profile = xm4Profile()
        val commands = SonyTandemHeadphoneAdapter.buildSetNoiseControlModeCommands(
            profile = profile,
            mode = NoiseControlMode.AMBIENT_SOUND,
            ambientLevel = 12,
            ambientMode = AmbientSoundMode.NORMAL,
        )

        assertEquals(1, commands.size)
        // Builder produces V1 bytes (TableSet1), not V2 bytes
        assertArrayEquals(
            SonyTandemV1Table1Protocol.buildSetNoiseControlMode(
                NoiseControlMode.AMBIENT_SOUND,
                ambientLevel = 12,
            ),
            commands[0].bytes,
        )
        assertEquals(TandemChannel.GATT_V1_MC, commands[0].channel)
    }

    @Test
    fun xm4_batteryRefresh_usesV1Builder() {
        val profile = xm4Profile()
        val commands = SonyTandemHeadphoneAdapter.buildRefreshBatteryCommands(profile)

        val batteryCmd = commands.first { it.label == "GET battery BATTERY" }
        // V1: COMMON_GET_BATTERY_LEVEL (0x10), not V2 POWER_GET_STATUS (0x22)
        assertEquals(0x10, batteryCmd.bytes[1].unsigned)
        assertArrayEquals(
            byteArrayOf(0x0E, 0x10, 0x00),
            batteryCmd.bytes,
        )
        assertEquals(TandemChannel.GATT_V1_MC, batteryCmd.channel)
    }

    @Test
    fun xm4_refreshNcCommands_useV1Routing() {
        val profile = xm4Profile()
        val commands = SonyTandemHeadphoneAdapter.buildRefreshNoiseControlCommands(profile)

        // XM4 NC now routes through V1 path → single command with V1 label
        val ncCmd = commands.first()
        assertEquals("GET NC/ASM param V1", ncCmd.label)
        // V1 buildGetNcAsmParam produces: [0x0E, 0x66, 0x02]
        assertEquals(0x66, ncCmd.bytes[1].unsigned) // NCASM_GET_PARAM
        assertEquals(
            NcAsmInquiredType.V1_TABLE_SET1_NC_ASM.code.unsigned,
            ncCmd.bytes[2].unsigned,
        )
        assertEquals(TandemChannel.GATT_V1_MC, ncCmd.channel)
    }

    @Test
    fun xm4_eqRoutesToV1Table1() {
        val profile = xm4Profile()
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1,
            profile.protocolFor(HeadphoneFeature.EQ),
        )
        assertTrue(
            SonyTandemHeadphoneAdapter.buildRefreshEqCommands(profile)
                .all { it.channel == TandemChannel.GATT_V1_MC },
        )
    }

    @Test
    fun xm4_clearBassRoutesToV1Table1() {
        val profile = xm4Profile()
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1,
            profile.protocolFor(HeadphoneFeature.CLEAR_BASS),
        )
        val context = EqWriteContext(rawBandSteps = emptyList())
        assertEquals(
            TandemChannel.GATT_V1_MC,
            SonyTandemHeadphoneAdapter.buildSetClearBassCommands(profile, level = 3, context).single().channel,
        )
    }

    @Test
    fun xm4_playbackRoutesToV2Table1ByDefault() {
        val profile = xm4Profile()
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
            profile.protocolFor(HeadphoneFeature.PLAYBACK_CONTROL),
        )
        assertEquals(
            TandemChannel.GATT_V2_HPC,
            SonyTandemHeadphoneAdapter.buildRefreshPlaybackCommands(profile).single().channel,
        )
    }

    @Test
    fun xm4_eqRefresh_onlyPresetEqInParamQueries() {
        val profile = xm4Profile()
        val commands = SonyTandemHeadphoneAdapter.buildRefreshEqCommands(profile)

        val labels = commands.map { it.label }
        // XM4 uses V1 type codes: queries PRESET_EQ param for preset+bands
        assertFalse(labels.any { it == "GET EQ status CUSTOM_EQ" })
        assertTrue(labels.any { it == "GET EQ param PRESET_EQ" })
        val paramLabels = labels.filter { it.startsWith("GET EQ param") }
        assertEquals(1, paramLabels.size)
        assertFalse(paramLabels.any { it.contains("CUSTOM_EQ") })
        assertFalse(paramLabels.any { it.contains("EBB") })
    }

    @Test
    fun xm4_eqPresetWrite_usesSonyV1PresetPayloadWithoutBands() {
        val profile = xm4Profile()
        val context = EqWriteContext(rawBandSteps = listOf(0x11, 0x0A, 0x0A, 0x0A, 0x0A, 0x0A))

        assertArrayEquals(
            byteArrayOf(0x0E, 0x58, 0x01, 0x16, 0x00),
            SonyTandemHeadphoneAdapter.buildSetEqPresetCommands(profile, EqPresetId.BASS, context).single().bytes,
        )
        assertArrayEquals(
            byteArrayOf(0x0E, 0x58, 0x01, 0x13, 0x00),
            SonyTandemHeadphoneAdapter.buildSetEqPresetCommands(profile, EqPresetId.RELAXED, context).single().bytes,
        )
    }

    @Test
    fun xm4_userEqWrites_useV1PresetEqType() {
        val profile = xm4Profile()
        val rawSteps = listOf(0x11, 0x0A, 0x0A, 0x0A, 0x0A, 0x0A)
        val context = EqWriteContext(rawBandSteps = rawSteps)

        // buildSetPreset sends Sony's active-preset payload with no bands.
        val userPreset = SonyTandemHeadphoneAdapter.buildSetEqPresetCommands(profile, EqPresetId.USER_SETTING1, context).single()
        // buildSetBands sends Sony's V1 custom-band shape: PRESET_EQ + UNSPECIFIED + bands.
        val customBands = SonyTandemHeadphoneAdapter.buildSetEqBandCommands(profile, rawSteps, EqPresetId.CUSTOM, context).single()

        assertArrayEquals(
            byteArrayOf(0x0E, 0x58, 0x01, 0xA1.toByte(), 0x00),
            userPreset.bytes,
        )
        assertArrayEquals(
            byteArrayOf(0x0E, 0x58, 0x01, 0xFF.toByte(), 0x06, 0x11, 0x0A, 0x0A, 0x0A, 0x0A, 0x0A),
            customBands.bytes,
        )
    }

    @Test
    fun clearBassWrite_usesCorrectEbbTypeCodePerDevice() {
        val context = EqWriteContext(rawBandSteps = listOf(0x13, 0x0A, 0x0A, 0x0A, 0x0B, 0x0C))

        // LinkBuds S: V2 EBB type (0x01)
        assertArrayEquals(
            byteArrayOf(0x0E, 0x58, 0x01, 0x03),
            SonyTandemHeadphoneAdapter.buildSetClearBassCommands(linkBudsSProfile(), level = 3, context).single().bytes,
        )
        // WH-1000XM4: V1 EBB type (0x02)
        assertArrayEquals(
            byteArrayOf(0x0E, 0x58, 0x02, 0xFE.toByte()),
            SonyTandemHeadphoneAdapter.buildSetClearBassCommands(xm4Profile(), level = -2, context).single().bytes,
        )
    }

    // ── Parse routing: XM4 ──────────────────────────────────────────────────

    @Test
    fun xm4_batteryResponse_0x11_parsedViaV1() {
        val profile = xm4Profile()
        val raw = byteArrayOf(0x0E, 0x11, 0x00, 88.toByte(), 0x00)
        val parsed = SonyTandemHeadphoneAdapter.parse(profile, raw)

        assertTrue("Expected Battery but got ${parsed::class.simpleName}", parsed is ParsedTandemResponse.Battery)
        parsed as ParsedTandemResponse.Battery
        assertEquals(PowerInquiredType.BATTERY, parsed.kind)
        assertEquals(listOf(88), parsed.values)
    }

    @Test
    fun xm4_ncAsmResponse_0x67_parsedViaV1() {
        val profile = xm4Profile()
        // NCASM_RET_PARAM for V1_TABLE_SET1_NC_ASM is classified as NOISE_CONTROL
        // and routed through the V1 codec path declared by the profile binding.
        val raw = byteArrayOf(0x0E, 0x67, 0x02, 0x01, 0x02, 0x02, 0x01, 0x00, 0x00)
        val parsed = SonyTandemHeadphoneAdapter.parse(profile, raw)

        assertTrue("Expected NoiseControl but got ${parsed::class.simpleName}", parsed is ParsedTandemResponse.NoiseControl)
        parsed as ParsedTandemResponse.NoiseControl
        assertEquals(NcAsmInquiredType.V1_TABLE_SET1_NC_ASM, parsed.type)
        assertEquals(NoiseControlMode.NOISE_CANCELLING, parsed.controlMode)
    }

    // ── Unknown / fallback Sony device ──────────────────────────────────────

    @Test
    fun unknownSony_onlyHasDeviceInfoAndBattery() {
        val device = DiscoveredSonyDevice(
            name = "Unknown Sony Device",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -60,
            source = "scan",
            isLikelyControlEndpoint = true,
        )
        val profile = HeadphoneAdapterRegistry.resolve(device)

        assertTrue(profile.supports(HeadphoneFeature.DEVICE_INFO))
        assertTrue(profile.supports(HeadphoneFeature.BATTERY))
        assertFalse(profile.supports(HeadphoneFeature.NOISE_CONTROL))
        assertFalse(profile.supports(HeadphoneFeature.EQ))
        assertFalse(profile.supports(HeadphoneFeature.PLAYBACK_CONTROL))
        assertFalse(profile.supports(HeadphoneFeature.CLEAR_BASS))
    }

    @Test
    fun unknownSony_writableFeaturesAreDisabled() {
        val device = DiscoveredSonyDevice(
            name = "Unknown Sony Device",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -60,
            source = "scan",
            isLikelyControlEndpoint = true,
        )
        val profile = HeadphoneAdapterRegistry.resolve(device)

        assertFalse(SonyTandemHeadphoneAdapter.canWrite(profile, HeadphoneFeature.NOISE_CONTROL))
        assertFalse(SonyTandemHeadphoneAdapter.canWrite(profile, HeadphoneFeature.EQ))
        assertFalse(SonyTandemHeadphoneAdapter.canWrite(profile, HeadphoneFeature.CLEAR_BASS))
        assertFalse(SonyTandemHeadphoneAdapter.canWrite(profile, HeadphoneFeature.PLAYBACK_CONTROL))
    }

    @Test
    fun unknownSony_refreshCommands_areReadOnlyBasicQueries() {
        val device = DiscoveredSonyDevice(
            name = "Sony WH-Abcd1234",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -60,
            source = "scan",
            isLikelyControlEndpoint = true,
        )
        val profile = HeadphoneAdapterRegistry.resolve(device)
        val commands = SonyTandemHeadphoneAdapter.buildRefreshCommands(profile)

        val labels = commands.map { it.label }

        // Fallback profile: queryProtocolInfo=true (default), so GET protocol info IS present
        assertTrue(labels.any { it == "GET protocol info" })
        // Battery queries present (only basic BATTERY type)
        assertTrue(labels.any { it == "GET battery BATTERY" })
        // No NC/ASM, EQ, playback, LEA, quick access, or wearing queries
        assertFalse(labels.any { it.startsWith("GET NC/ASM") })
        assertFalse(labels.any { it.startsWith("GET EQ") })
        assertFalse(labels.any { it.contains("playback") })
        assertFalse(labels.any { it.contains("LEA") })
        assertFalse(labels.any { it.contains("Quick Access") })
        assertFalse(labels.any { it.contains("Wearing") })
    }

    @Test
    fun unknownSony_fallbackProfileHasNoStaticProfileEvidence() {
        val device = DiscoveredSonyDevice(
            name = "Sony WH-Abcd1234",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -60,
            source = "scan",
            isLikelyControlEndpoint = true,
        )
        val profile = HeadphoneAdapterRegistry.resolve(device)

        assertEquals("Sony WH-Abcd1234", profile.modelName)
        assertTrue(profile.protocolEvidence.any { it.startsWith("probe-only:") })
        assertFalse(profile.protocolEvidence.any { it.startsWith("static-profile:") })
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun xm4Profile(): ConnectedHeadphoneProfile =
        HeadphoneAdapterRegistry.resolve(
            DiscoveredSonyDevice(
                name = "WH-1000XM4",
                address = "00:11:22:33:44:55",
                rssi = 0,
                source = "bonded",
                isLikelyControlEndpoint = true,
            )
        )

    private fun linkBudsSProfile(): ConnectedHeadphoneProfile =
        HeadphoneAdapterRegistry.resolve(
            DiscoveredSonyDevice(
                name = "LinkBuds S",
                address = "00:11:22:33:44:56",
                rssi = 0,
                source = "bonded",
                isLikelyControlEndpoint = true,
            )
        )
}
