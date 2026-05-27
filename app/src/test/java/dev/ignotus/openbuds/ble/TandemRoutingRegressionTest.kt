package dev.ignotus.openbuds.ble

import dev.ignotus.openbuds.headphones.TandemChannel
import dev.ignotus.openbuds.protocol.SonyGatt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Regression tests guarding TandemGattRouting against panics introduced when
 * non-Sony channels (QCY_*, SPP) are added to [TandemChannel]. The routing
 * helpers must filter to Sony GATT channels only before calling
 * [TandemGattRouting.endpointSpecFor] (which throws for non-Sony channels).
 */
class TandemRoutingRegressionTest {

    @Test
    fun sonyGattChannels_doNotIncludeSppOrQcy() {
        val sony = TandemGattRouting.SONY_GATT_CHANNELS
        assertTrue(TandemChannel.GATT_V2_HPC in sony)
        assertTrue(TandemChannel.GATT_V2_MC in sony)
        assertTrue(TandemChannel.GATT_V1_MC in sony)
        assertFalse(TandemChannel.SPP_MDR in sony)
        assertFalse(TandemChannel.QCY_SETTING_WRITE in sony)
        assertFalse(TandemChannel.QCY_READSET in sony)
        assertFalse(TandemChannel.QCY_BATTERY in sony)
        assertFalse(TandemChannel.QCY_VERSION in sony)
        assertFalse(TandemChannel.QCY_EQ_RAW in sony)
        assertFalse(TandemChannel.QCY_FUNCTION in sony)
    }

    @Test
    fun fromAccChannelFor_unknownUuids_returnsNullWithoutThrowing() {
        // Random UUIDs that match no Sony service/char. Must not throw via
        // the QCY enum entries even though they live in TandemChannel.entries.
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
        assertEquals(TandemChannel.GATT_V2_HPC, result)
    }

    @Test
    fun notificationOrder_mixedSonyAndQcy_returnsSonyOnly() {
        val input = listOf(
            TandemChannel.QCY_READSET,
            TandemChannel.GATT_V2_MC,
            TandemChannel.QCY_BATTERY,
            TandemChannel.GATT_V2_HPC,
            TandemChannel.SPP_MDR,
        )
        val order = TandemGattRouting.notificationOrder(input)
        assertEquals(listOf(TandemChannel.GATT_V2_HPC, TandemChannel.GATT_V2_MC), order)
    }

    @Test
    fun endpointSpecFor_qcyChannel_throws() {
        // Document the contract: QCY channels MUST be filtered out before
        // calling endpointSpecFor. The throw is the safety mechanism that
        // SONY_GATT_CHANNELS prevents anyone from hitting.
        listOf(
            TandemChannel.QCY_SETTING_WRITE,
            TandemChannel.QCY_READSET,
            TandemChannel.QCY_BATTERY,
            TandemChannel.QCY_VERSION,
            TandemChannel.QCY_EQ_RAW,
            TandemChannel.QCY_FUNCTION,
        ).forEach { channel ->
            try {
                TandemGattRouting.endpointSpecFor(channel)
                throw AssertionError("Expected IllegalStateException for $channel")
            } catch (e: IllegalStateException) {
                // expected
            }
        }
    }
}
