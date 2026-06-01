package dev.ignotus.openbuds.headphones

import dev.ignotus.openbuds.ble.sony.DiscoveredSonyDevice
import dev.ignotus.openbuds.headphones.sony.SonyTandemV1Table1Codec
import dev.ignotus.openbuds.headphones.sony.SonyTandemV1Table2Codec
import dev.ignotus.openbuds.headphones.sony.SonyTandemV2Table1Codec
import dev.ignotus.openbuds.headphones.sony.SonyTandemV2Table2Codec
import dev.ignotus.openbuds.protocol.sony.PlaybackControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ProtocolCompatibilityArchitectureTest {
    @Test
    fun profileBindingsBackProtocolForAndCarryChannels() {
        val profile = HeadphoneAdapterRegistry.resolve(
            DiscoveredSonyDevice(
                name = "LinkBuds S",
                address = "00:11:22:33:44:55",
                rssi = 0,
                source = "bonded",
                isLikelyControlEndpoint = true,
            )
        )

        val binding = profile.bindingFor(HeadphoneFeature.NOISE_CONTROL)
        requireNotNull(binding)
        assertEquals(profile.protocolFor(HeadphoneFeature.NOISE_CONTROL), binding.variant)
        assertEquals(TandemChannel.GATT_V2_HPC, binding.channel)
    }

    @Test
    fun protocolDefaultChannelsMatchGattRouting() {
        assertEquals(TandemChannel.GATT_V1_MC, defaultChannelFor(HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1))
        assertEquals(TandemChannel.GATT_V2_MC, defaultChannelFor(HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE2))
        assertEquals(TandemChannel.GATT_V1_MC, defaultChannelFor(HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE2))
        assertEquals(TandemChannel.GATT_V2_HPC, defaultChannelFor(HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1))
        try {
            defaultChannelFor(HeadphoneProtocolVariant.UNKNOWN)
            fail("UNKNOWN protocol must not silently default to a concrete channel")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun codecDefaultChannelsMatchProtocolDefaults() {
        assertEquals(TandemChannel.GATT_V1_MC, SonyTandemV1Table1Codec.defaultChannel)
        assertEquals(TandemChannel.GATT_V1_MC, SonyTandemV1Table2Codec.defaultChannel)
        assertEquals(TandemChannel.GATT_V2_HPC, SonyTandemV2Table1Codec.defaultChannel)
        assertEquals(TandemChannel.GATT_V2_MC, SonyTandemV2Table2Codec.defaultChannel)
    }

    @Test
    fun playbackCommandsAreTandemFirstForStaticProfiles() {
        val profile = HeadphoneAdapterRegistry.resolve(
            DiscoveredSonyDevice(
                name = "LinkBuds S",
                address = "00:11:22:33:44:55",
                rssi = 0,
                source = "bonded",
                isLikelyControlEndpoint = true,
            )
        )

        assertEquals(PlaybackDispatchStrategy.TANDEM_FIRST, profile.playbackDispatchStrategy)
        val command = HeadphoneAdapterRegistry.buildPlaybackCommands(profile, PlaybackControl.PLAY).single()
        assertEquals(TandemChannel.GATT_V2_HPC, command.channel)
        assertEquals(0xA4, command.bytes[1].toInt() and 0xFF)
        assertEquals(0x01, command.bytes[2].toInt() and 0xFF)
    }

    @Test
    fun adapterDoesNotImportProtocolObjectsDirectly() {
        val source = mainSource("headphones/sony/SonyTandemHeadphoneAdapter.kt")
        // The adapter is in headphones.sony subpackage; it must import Sony protocol
        // types (now in protocol.sony) to function. These imports are allowed.
        assertFalse(source.contains("SonyTandemV1Table1Protocol."))
        assertFalse(source.contains("SonyTandemV2Table1Protocol."))
    }

    @Test
    fun profileTemplateDoesNotInferProtocolFromModelName() {
        val source = mainSource("headphones/HeadphoneAdapter.kt")
        assertFalse(source.contains("when (modelName)"))
        assertFalse(source.contains("buildFeatureProtocolMap"))
    }

    @Test
    fun v1Table1ParserDoesNotDelegateToV2Parser() {
        val source = mainSource("protocol/sony/SonyTandemV1Table1Protocol.kt")
        assertFalse(source.contains("SonyTandemV2Table1Protocol.parse"))
    }

    @Test
    fun repositoryDoesNotDropCommandChannel() {
        val source = mainSource("data/HeadphoneRepository.kt")
        assertFalse(source.contains("send(bytes)"))
        assertTrue(source.contains("sendToChannel(command.channel, command.bytes)"))
    }

    @Test
    fun sonyBleClientClassWasDismantled() {
        val sources = listOf(
            "ble/sony/SonyTandemTransportClient.kt",
            "ble/sony/SonyTandemGattSession.kt",
            "ble/sony/SonyTandemSppSession.kt",
            "ble/HeadphoneTransportClient.kt",
            "data/HeadphoneRepository.kt",
        ).joinToString("\n") { mainSource(it) }

        assertFalse(sources.contains("class SonyBleClient"))
        assertFalse(sources.contains("SonyBleClientListener"))
        assertTrue(mainSource("headphones/sony/SonyTandemHeadphoneAdapter.kt").contains("createTransportClient"))
        assertTrue(mainSource("data/HeadphoneRepository.kt").contains("SonyTandemHeadphoneAdapter.createTransportClient"))
    }

    private fun mainSource(path: String): String {
        val relativePath = "src/main/java/dev/ignotus/openbuds/$path"
        val userDir = requireNotNull(System.getProperty("user.dir"))
        val candidates = generateSequence(File(userDir).absoluteFile) { it.parentFile }
            .flatMap { dir ->
                sequenceOf(
                    File(dir, relativePath),
                    File(dir, "app/$relativePath"),
                    File(dir, "App/app/$relativePath"),
                )
            }

        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Cannot locate $relativePath from $userDir")
    }
}
