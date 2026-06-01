package dev.ignotus.openbuds.ble

import dev.ignotus.openbuds.ble.sony.SonyChannel
import dev.ignotus.openbuds.ble.sony.TandemGattRouting
import dev.ignotus.openbuds.protocol.sony.SonyGatt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Regression tests guarding Sony-private Tandem GATT routing.
 */
class TandemRoutingRegressionTest {

    @Test
    fun sonyGattChannels_doNotIncludeSpp() {
        val sony = TandemGattRouting.SONY_GATT_CHANNELS
        assertTrue(SonyChannel.GATT_V2_HPC in sony)
        assertTrue(SonyChannel.GATT_V2_MC in sony)
        assertTrue(SonyChannel.GATT_V1_MC in sony)
        assertFalse(SonyChannel.SPP_MDR in sony)
    }

    @Test
    fun fromAccChannelFor_unknownUuids_returnsNullWithoutThrowing() {
        val randomService = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val randomChar = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val result = TandemGattRouting.fromAccChannelFor(randomService, randomChar)
        assertNull(result)
    }

    @Test
    fun fromAccChannelFor_nullUuid_returnsNull() {
        assertNull(TandemGattRouting.fromAccChannelFor(null, null))
        val anyUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        assertNull(TandemGattRouting.fromAccChannelFor(anyUuid, null))
        assertNull(TandemGattRouting.fromAccChannelFor(null, anyUuid))
    }

    @Test
    fun fromAccChannelFor_validSonyV2HpcUuids_returnsCorrectChannel() {
        val result = TandemGattRouting.fromAccChannelFor(
            SonyGatt.TANDEM_V2_HPC_SERVICE,
            SonyGatt.TANDEM_HPC_FROM_ACC,
        )
        assertEquals(SonyChannel.GATT_V2_HPC, result)
    }

    @Test
    fun notificationOrder_mixedSonyChannels_returnsGattOnly() {
        val input = listOf(
            SonyChannel.SPP_MDR,
            SonyChannel.GATT_V2_MC,
            SonyChannel.GATT_V2_HPC,
        )
        val order = TandemGattRouting.notificationOrder(input)
        assertEquals(listOf(SonyChannel.GATT_V2_HPC, SonyChannel.GATT_V2_MC), order)
    }

    @Test
    fun endpointSpecFor_sppChannel_throws() {
        try {
            TandemGattRouting.endpointSpecFor(SonyChannel.SPP_MDR)
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }
}
