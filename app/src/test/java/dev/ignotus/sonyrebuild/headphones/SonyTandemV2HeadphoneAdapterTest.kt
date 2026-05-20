package dev.ignotus.sonyrebuild.headphones

import dev.ignotus.sonyrebuild.ble.DiscoveredSonyDevice
import dev.ignotus.sonyrebuild.protocol.EqEbbInquiredType
import dev.ignotus.sonyrebuild.protocol.NcAsmInquiredType
import dev.ignotus.sonyrebuild.protocol.SonyTandemV1Table1Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyTandemV2HeadphoneAdapterTest {
    @Test
    fun match_wh1000xm4_returnsPremiumProfile() {
        val profile = SonyTandemV2HeadphoneAdapter.match(
            xm4Device(),
            reportedModelName = "WH-1000XM4",
        )

        requireNotNull(profile)
        assertEquals("sony-tandem-v2", profile.adapterId)
        assertEquals("Sony", profile.brand)
        assertEquals("WH-1000XM4", profile.modelName)
        assertEquals("PREMIUM", profile.series)
        assertTrue(profile.supports(HeadphoneFeature.NOISE_CONTROL))
        assertEquals(listOf(dev.ignotus.sonyrebuild.protocol.PowerInquiredType.BATTERY), profile.capabilities.batteryQueries)
        assertEquals(HeadphoneFormFactor.HEADSET, profile.capabilities.formFactor)
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1,
            profile.protocolFor(HeadphoneFeature.BATTERY),
        )
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1,
            profile.protocolFor(HeadphoneFeature.NOISE_CONTROL),
        )
        assertEquals(
            HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
            profile.protocolFor(HeadphoneFeature.EQ),
        )
    }

    @Test
    fun refreshCommands_wh1000xm4_includeExistingFeatureReads() {
        val profile = SonyTandemV2HeadphoneAdapter.match(xm4Device(), "WH-1000XM4")!!
        val commands = SonyTandemV2HeadphoneAdapter.buildRefreshCommands(profile)
        val labels = commands.map { it.label }

        assertFalse(labels.any { it == "GET protocol info" })
        assertTrue(labels.any { it == "GET battery BATTERY" })
        assertFalse(labels.any { it == "GET battery LEFT_RIGHT_BATTERY" })
        assertFalse(labels.any { it == "GET battery CRADLE_BATTERY" })
        assertFalse(labels.any { it == "GET EQ param CUSTOM_EQ" })
        assertTrue(labels.any { it == "GET EQ param EBB" })
        assertTrue(labels.any { it == "GET playback status" })
        assertTrue(labels.any { it == "GET display firmware version" })
        assertTrue(commands.any {
            it.label == "GET NC/ASM param ${NcAsmInquiredType.V1_TABLE_SET1_NC_ASM}"
        })
        assertArrayEquals(
            byteArrayOf(0x0E, 0x10, 0x00),
            commands.first { it.label == "GET battery BATTERY" }.bytes,
        )
        assertFalse(commands.any { it.bytes.contentEquals(byteArrayOf(0x0E, 0x22, 0x00)) })
    }

    @Test
    fun noiseControlCommands_wh1000xm4_useTableSet1NcAsmWrites() {
        val profile = SonyTandemV2HeadphoneAdapter.match(xm4Device(), "WH-1000XM4")!!
        val commands = SonyTandemV2HeadphoneAdapter.buildSetNoiseControlModeCommands(
            profile = profile,
            mode = dev.ignotus.sonyrebuild.protocol.NoiseControlMode.AMBIENT_SOUND,
            ambientLevel = 12,
            ambientMode = dev.ignotus.sonyrebuild.protocol.AmbientSoundMode.NORMAL,
        )

        assertEquals(1, commands.size)
        assertTrue(commands.first().bytes.contentEquals(SonyTandemV1Table1Protocol.buildSetNoiseControlMode(
            dev.ignotus.sonyrebuild.protocol.NoiseControlMode.AMBIENT_SOUND,
            ambientLevel = 12,
        )))
    }

    @Test
    fun registry_resolvesWh1000xm4ByDeviceName() {
        val profile = HeadphoneAdapterRegistry.resolve(xm4Device())

        assertEquals("WH-1000XM4", profile.modelName)
        assertEquals("Sony Tandem", profile.protocolName)
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
                dev.ignotus.sonyrebuild.protocol.PowerInquiredType.LEFT_RIGHT_BATTERY,
                dev.ignotus.sonyrebuild.protocol.PowerInquiredType.CRADLE_BATTERY,
            ),
            profile.capabilities.batteryQueries,
        )
        assertTrue(profile.capabilities.eqStatusTypes.contains(EqEbbInquiredType.CUSTOM_EQ))
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
