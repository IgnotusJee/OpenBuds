package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import dev.ignotus.openbuds.ble.transport.SppFrameType
import dev.ignotus.openbuds.ble.transport.SppFraming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class SonySppWireSessionTest {
    @Test
    fun sendReadonlyBatteryQuery_waitsForMatchingAck() {
        val writes = mutableListOf<ByteArray>()
        val session = SonySppWireSession(
            targetMac = TARGET_MAC,
            writer = { frame ->
                synchronized(writes) { writes += frame }
                true
            },
        )
        val result = AtomicReference<Boolean>()
        val sender = Thread {
            result.set(session.sendReadonlyBatteryQuery(waitForAck = true))
        }

        sender.start()
        waitForWrite(writes)
        val outbound = decode(writes.first())
        assertEquals(SppFrameType.DATA_MDR, outbound.type)
        assertEquals(listOf(0x22, 0x00), outbound.payload.map { it.u })

        session.ingest(SppFraming.encodeFrame(SppFrameType.ACK, SppFraming.inverseSequence(outbound.sequence), byteArrayOf()))
        sender.join(1_000L)

        assertEquals(true, result.get())
    }

    @Test
    fun ingestBatteryFrame_sendsAckAndUpdatesSingleBatteryState() {
        val writes = mutableListOf<ByteArray>()
        var latestState = SonySppWireState()
        val session = SonySppWireSession(
            targetMac = TARGET_MAC,
            writer = { frame ->
                writes += frame
                true
            },
            onStateChanged = { latestState = it },
        )
        val inbound = SppFraming.encodeFrame(
            SppFrameType.DATA_MDR,
            0,
            byteArrayOf(0x23, 0x00, 70, 0x01),
        )

        session.ingest(inbound)

        assertEquals(70, latestState.singleBattery)
        assertEquals(1L, latestState.revision)
        val ack = decode(writes.single())
        assertEquals(SppFrameType.ACK, ack.type)
        assertEquals(1, ack.sequence.u)
    }

    @Test
    fun ingestNoiseControlFrame_mapsAmbientModeToMilinkSnapshotState() {
        var latestState = SonySppWireState()
        val session = SonySppWireSession(
            targetMac = TARGET_MAC,
            writer = { true },
            onStateChanged = { latestState = it },
        )
        val inbound = SppFraming.encodeFrame(
            SppFrameType.DATA_MDR,
            0,
            byteArrayOf(0x67, 0x17, 0x01, 0x01, 0x01, 0x00, 0x0C),
        )

        session.ingest(inbound)

        assertEquals(2, latestState.ancMode)
        assertEquals(12, latestState.ambientLevel)
    }

    @Test
    fun stateToMilinkSnapshot_setsM5Capabilities() {
        val snapshot = SonySppWireState(
            singleBattery = 70,
            ancMode = 2,
            ambientLevel = 12,
            revision = 3L,
        ).toMilinkSnapshot(
            mac = TARGET_MAC,
            name = "LinkBuds S",
            updatedAt = 10L,
        )

        assertEquals(TARGET_MAC, snapshot.mac)
        assertEquals(70, snapshot.singleBattery)
        assertEquals(2, snapshot.ancMode)
        assertTrue(snapshot.supportsBattery)
        assertTrue(snapshot.supportsNoiseControl)
        assertTrue(snapshot.supportsAmbientLevel)
        assertEquals(3L, snapshot.revision)
    }

    private fun waitForWrite(writes: List<ByteArray>) {
        repeat(20) {
            synchronized(writes) {
                if (writes.isNotEmpty()) return
            }
            Thread.sleep(50L)
        }
        error("timed out waiting for SPP write")
    }

    private fun decode(frame: ByteArray): DecodedFrame {
        val body = SppFraming.unescape(frame.copyOfRange(1, frame.lastIndex))
        val length = body.int32be(2)
        return DecodedFrame(
            type = SppFrameType.fromByte(body[0]),
            sequence = body[1],
            payload = body.copyOfRange(SppFraming.HEADER_SIZE, SppFraming.HEADER_SIZE + length),
        )
    }

    private fun ByteArray.int32be(offset: Int): Int =
        ((this[offset].u) shl 24) or
            ((this[offset + 1].u) shl 16) or
            ((this[offset + 2].u) shl 8) or
            this[offset + 3].u

    private data class DecodedFrame(
        val type: SppFrameType,
        val sequence: Byte,
        val payload: ByteArray,
    )

    private companion object {
        private const val TARGET_MAC = "AA:BB:CC:DD:EE:FF"
    }
}
