package dev.ignotus.openbuds.ble

import dev.ignotus.openbuds.ble.sony.SonyChannel
import dev.ignotus.openbuds.ble.sony.SonyTandemEndpointSupport
import dev.ignotus.openbuds.ble.sony.TandemGattRouting
import dev.ignotus.openbuds.protocol.sony.SonyGatt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Tests for Sony-private GATT service/source routing.
 */
class SonyTransportRoutingTest {

    @Test
    fun tandemV2HpcService_uuidMatchesDocumentedValue() {
        assertEquals("5b833e20-6bc7-4802-8e9a-723ceca4bd8f", SonyGatt.TANDEM_V2_HPC_SERVICE.toString())
    }

    @Test
    fun tandemV2McService_uuidMatchesDocumentedValue() {
        assertEquals("5b833e21-6bc7-4802-8e9a-723ceca4bd8f", SonyGatt.TANDEM_V2_MC_SERVICE.toString())
    }

    @Test
    fun tandemV1McService_uuidMatchesDocumentedValue() {
        assertEquals("5b833e23-6bc7-4802-8e9a-723ceca4bd8f", SonyGatt.TANDEM_V1_MC_SERVICE.toString())
    }

    @Test
    fun hpcToAccCharacteristic_uuidMatchesDocumentedValue() {
        assertEquals("5b833c60-6bc7-4802-8e9a-723ceca4bd8f", SonyGatt.TANDEM_HPC_TO_ACC.toString())
    }

    @Test
    fun hpcFromAccCharacteristic_uuidMatchesDocumentedValue() {
        assertEquals("5b833c61-6bc7-4802-8e9a-723ceca4bd8f", SonyGatt.TANDEM_HPC_FROM_ACC.toString())
    }

    @Test
    fun mcCharacteristics_matchExpectedUuids() {
        assertEquals("5b833c62-6bc7-4802-8e9a-723ceca4bd8f", SonyGatt.TANDEM_MC_TO_ACC.toString())
        assertEquals("5b833c63-6bc7-4802-8e9a-723ceca4bd8f", SonyGatt.TANDEM_MC_FROM_ACC.toString())
    }

    @Test
    fun channelFromService_v2Hpc() {
        assertEquals(SonyChannel.GATT_V2_HPC, SonyChannel.fromServiceUuid(SonyGatt.TANDEM_V2_HPC_SERVICE))
    }

    @Test
    fun channelFromService_v2Mc() {
        assertEquals(SonyChannel.GATT_V2_MC, SonyChannel.fromServiceUuid(SonyGatt.TANDEM_V2_MC_SERVICE))
    }

    @Test
    fun channelFromService_v1Mc() {
        assertEquals(SonyChannel.GATT_V1_MC, SonyChannel.fromServiceUuid(SonyGatt.TANDEM_V1_MC_SERVICE))
    }

    @Test
    fun channelFromService_unknownServiceReturnsNull() {
        val unknownUuid = UUID.fromString("00000000-0000-0000-0000-000000000000")
        assertNull(SonyChannel.fromServiceUuid(unknownUuid))
    }

    @Test
    fun channelFromService_v2HpcIsNotSpp() {
        val v2HpcChannel = SonyChannel.fromServiceUuid(SonyGatt.TANDEM_V2_HPC_SERVICE)
        assertFalse(v2HpcChannel == SonyChannel.SPP_MDR)
    }

    @Test
    fun sourceKeyRoundTrip() {
        SonyChannel.entries.forEach { channel ->
            assertEquals(channel, SonyChannel.fromSourceKey(channel.sourceKey))
        }
    }

    @Test
    fun tandemEndpointSupport_v2HpcIsSupported() {
        assertNull(SonyTandemEndpointSupport.supportState(listOf(SonyGatt.TANDEM_V2_HPC_SERVICE)))
    }

    @Test
    fun tandemEndpointSupport_v1McOnlyIsSupported() {
        assertNull(SonyTandemEndpointSupport.supportState(listOf(SonyGatt.TANDEM_V1_MC_SERVICE)))
    }

    @Test
    fun tandemEndpointSupport_leAudioEndpointIsUnsupported() {
        val reason = SonyTandemEndpointSupport.supportState(listOf(SonyGatt.LE_AUDIO_CAPABILITY_FOR_HPC))
        assertTrue(reason.orEmpty().contains("LE Audio capability"))
    }

    @Test
    fun tandemEndpointSupport_pairingNameEndpointIsUnsupported() {
        val reason = SonyTandemEndpointSupport.supportState(listOf(SonyGatt.BLUETOOTH_PAIRING_COMPLETE_NAME_SERVICE))
        assertTrue(reason.orEmpty().contains("pairing/name endpoint"))
    }

    @Test
    fun v2HpcChannel_characteristics() {
        val endpoint = TandemGattRouting.endpointSpecFor(SonyChannel.GATT_V2_HPC)
        assertEquals(SonyGatt.TANDEM_HPC_TO_ACC, endpoint.toAccUuid)
        assertEquals(SonyGatt.TANDEM_HPC_FROM_ACC, endpoint.fromAccUuid)
    }

    @Test
    fun mcChannels_useMcCharacteristics() {
        listOf(SonyChannel.GATT_V2_MC, SonyChannel.GATT_V1_MC).forEach { channel ->
            val endpoint = TandemGattRouting.endpointSpecFor(channel)
            assertEquals("MC TO_ACC mismatch for $channel", SonyGatt.TANDEM_MC_TO_ACC, endpoint.toAccUuid)
            assertEquals("MC FROM_ACC mismatch for $channel", SonyGatt.TANDEM_MC_FROM_ACC, endpoint.fromAccUuid)
        }
    }

    @Test
    fun tandemGattRouting_distinguishesV2AndV1McFromAccByServiceUuid() {
        assertEquals(
            SonyChannel.GATT_V2_MC,
            TandemGattRouting.fromAccChannelFor(
                serviceUuid = SonyGatt.TANDEM_V2_MC_SERVICE,
                characteristicUuid = SonyGatt.TANDEM_MC_FROM_ACC,
            ),
        )
        assertEquals(
            SonyChannel.GATT_V1_MC,
            TandemGattRouting.fromAccChannelFor(
                serviceUuid = SonyGatt.TANDEM_V1_MC_SERVICE,
                characteristicUuid = SonyGatt.TANDEM_MC_FROM_ACC,
            ),
        )
    }

    @Test
    fun tandemGattRouting_notificationOrderSubscribesHpcThenV2McThenV1Mc() {
        assertEquals(
            listOf(SonyChannel.GATT_V2_HPC, SonyChannel.GATT_V2_MC, SonyChannel.GATT_V1_MC),
            TandemGattRouting.notificationOrder(
                listOf(
                    SonyChannel.GATT_V1_MC,
                    SonyChannel.SPP_MDR,
                    SonyChannel.GATT_V2_MC,
                    SonyChannel.GATT_V2_HPC,
                ),
            ),
        )
    }

    @Test
    fun gattChannels_eachHaveUniqueServiceUuid() {
        val serviceUuids = TandemGattRouting.SONY_GATT_CHANNELS.map {
            TandemGattRouting.endpointSpecFor(it).serviceUuid
        }
        assertEquals(serviceUuids.size, serviceUuids.toSet().size)
    }

    @Test
    fun gattChannels_allHaveDefinedEndpoints() {
        TandemGattRouting.SONY_GATT_CHANNELS.forEach { channel ->
            val endpoint = TandemGattRouting.endpointSpecFor(channel)
            assertTrue("${endpoint.toAccUuid} should not be null", endpoint.toAccUuid.toString().isNotEmpty())
            assertTrue("${endpoint.fromAccUuid} should not be null", endpoint.fromAccUuid.toString().isNotEmpty())
        }
    }
}
