package dev.ignotus.openbuds.headphones

import dev.ignotus.openbuds.ble.sony.DiscoveredSonyDevice
import dev.ignotus.openbuds.protocol.sony.PlaybackControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProtocolCompatibilityArchitectureTest {
    @Test
    fun profileBindingsBackProtocolForWithoutChannels() {
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
    }

    @Test
    fun playbackCommandsArePlainProtocolBytes() {
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
    fun publicTransportApiDoesNotExposeChannels() {
        val source = mainSource("ble/HeadphoneTransportClient.kt")
        assertTrue(source.contains("fun send(bytes: ByteArray)"))
        assertTrue(source.contains("IncomingHeadphoneMessage"))
        assertFalse(source.contains("TransportChannelId"))
        assertFalse(source.contains("sendToChannel"))
        assertFalse(source.contains("availableChannels"))
    }

    @Test
    fun publicHeadphoneModelDoesNotDefineChannelRegistry() {
        val source = mainSource("headphones/HeadphoneAdapter.kt")
        assertFalse(source.contains("TransportChannelId"))
        assertFalse(source.contains("defaultChannelFor"))
        assertFalse(source.contains("channelFor("))
        assertFalse(source.contains("defaultResponseChannel"))
        assertFalse(source.contains("val channel"))
    }

    @Test
    fun repositorySendsPlainCommandBytes() {
        val source = mainSource("data/HeadphoneRepository.kt")
        assertTrue(source.contains("transport.send(command.bytes)"))
        assertFalse(source.contains("sendToChannel(command.channel, command.bytes)"))
        assertFalse(source.contains("command.channel"))
        assertFalse(source.contains("availableChannels()"))
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
