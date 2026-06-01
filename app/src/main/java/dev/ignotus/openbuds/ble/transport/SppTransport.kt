package dev.ignotus.openbuds.ble.transport

import android.bluetooth.BluetoothSocket
import dev.ignotus.openbuds.protocol.hexString
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SPP (RFCOMM) transport implementing the shared SPP wire protocol.
 *
 * Wire format per frame:
 * ```
 * [0x3E] [escaped-body] [0x3C]
 * body = [type:1] [seq:1] [length:4 BE] [payload:N] [checksum:1]
 * ```
 *
 * Special bytes (0x3C, 0x3D, 0x3E) in the body are escaped with 0x3D prefix
 * followed by (byte | 0x10).
 *
 * The send/receive API works with protocol bytes. The transport handles:
 * - Sequence numbers and ACK retransmission
 * - Escape encoding and checksumming
 *
 * The caller receives protocol bytes with the type prefix intact via
 * [TransportListener.onMessage], and sends protocol bytes with the prefix
 * via [send].
 */
class SppTransport(
    private val socket: BluetoothSocket,
    private val listener: TransportListener,
    private val payloadMapper: SppPayloadMapper,
) : BluetoothTransport {
    override val name: String = "SPP"
    override val info: TransportInfo = TransportInfo(mtu = WRITABLE_VALUE_LENGTH, kind = "SPP")

    private val input = socket.inputStream
    private val output = socket.outputStream
    private val closed = AtomicBoolean(false)
    private val pendingWrites = ConcurrentLinkedQueue<SppPayloadMapping>()
    private val lock = Any()

    private var readerThread: Thread? = null
    private var nextTxSequence: Byte = 0
    private var awaitingAck: Byte? = null
    private var awaitingFrame: ByteArray? = null
    private var awaitingRetries: Int = 0
    private var ackGeneration: Int = 0

    // ── Lifecycle ──────────────────────────────────────────────

    /** Start the reader thread. Must be called after construction. */
    fun start() {
        readerThread = Thread(::readLoop, "OpenBuds-SppTransport").also { it.start() }
        listener.onReady(info)
    }

    override fun close() {
        if (!closed.getAndSet(true)) {
            pendingWrites.clear()
            awaitingAck = null
            runCatching { input.close() }
            runCatching { output.close() }
            runCatching { socket.close() }
        }
    }

    // ── Send ───────────────────────────────────────────────────

    override fun send(bytes: ByteArray) {
        val frame = payloadMapper.outbound(bytes)
        pendingWrites.add(frame)
        drainWrites()
    }

    // ── Read loop ──────────────────────────────────────────────

    private fun readLoop() {
        val frame = mutableListOf<Byte>()
        var inFrame = false
        val buffer = ByteArray(512)
        try {
            while (!closed.get()) {
                val read = input.read(buffer)
                if (read < 0) break
                for (index in 0 until read) {
                    when (val byte = buffer[index]) {
                        FRAME_START -> {
                            frame.clear()
                            inFrame = true
                        }
                        FRAME_END -> {
                            if (inFrame) {
                                handleFrame(frame.toByteArray())
                            }
                            frame.clear()
                            inFrame = false
                        }
                        else -> if (inFrame) frame += byte
                    }
                }
            }
            shutdown(null)
        } catch (e: IOException) {
            if (!closed.get()) {
                shutdown(e.message)
            }
        }
    }

    private fun handleFrame(escapedBody: ByteArray) {
        val body = SppFraming.unescape(escapedBody)
        if (body.size < SppFraming.HEADER_SIZE + SppFraming.CHECKSUM_SIZE) {
            log("SPP RX short frame ${body.hexString()}")
            return
        }
        val expectedChecksum = checksum(body, body.size - CHECKSUM_SIZE)
        val actualChecksum = body.last().u
        if (expectedChecksum != actualChecksum) {
            log(
                "SPP RX checksum mismatch expected=${expectedChecksum.toString(16)} " +
                    "actual=${actualChecksum.toString(16)} raw=${body.hexString()}"
            )
            return
        }

        val type = SppFrameType.fromByte(body[0])
        val sequence = body[1]
        val length = body.int32be(2)
        if (length < 0 || body.size != HEADER_SIZE + length + CHECKSUM_SIZE) {
            log("SPP RX invalid length=$length raw=${body.hexString()}")
            return
        }
        val payload = body.copyOfRange(HEADER_SIZE, HEADER_SIZE + length)
        log("SPP RX type=${type.name} seq=${sequence.u} payload=${payload.hexString()}")

        when (type) {
            SppFrameType.ACK -> {
                synchronized(lock) {
                    if (awaitingAck == sequence) {
                        nextTxSequence = sequence
                        awaitingAck = null
                        awaitingFrame = null
                        awaitingRetries = 0
                    } else {
                        log("SPP RX ACK unexpected seq=${sequence.u} awaiting=${awaitingAck?.u}")
                    }
                }
                drainWrites()
            }
            SppFrameType.DATA_MDR,
            SppFrameType.DATA_MDR_NO2,
            SppFrameType.LARGE_DATA_MDR -> {
                sendAck(sequence)
                payloadMapper.inbound(type, payload)?.let { listener.onMessage(it) }
            }
            SppFrameType.SHOT_MDR,
            SppFrameType.SHOT_MDR_NO2 -> {
                payloadMapper.inbound(type, payload)?.let { listener.onMessage(it) }
            }
            SppFrameType.UNKNOWN -> log("SPP RX unsupported data type=0x${body[0].u.toString(16)}")
        }
    }

    // ── Write queue ────────────────────────────────────────────

    private fun drainWrites() {
        synchronized(lock) {
            if (closed.get() || awaitingAck != null) return
            val outbound = pendingWrites.poll() ?: return
            val sequence = nextTxSequence
            val encoded = encodeFrame(outbound.frameType, sequence, outbound.payload)
            val expectedAck = if (outbound.frameType.ackRequired) inverseSequence(sequence) else null
            awaitingAck = expectedAck
            awaitingFrame = if (expectedAck != null) encoded else null
            awaitingRetries = 0
            if (!outbound.frameType.ackRequired) {
                nextTxSequence = inverseSequence(sequence)
                awaitingFrame = null
            }
            val generation = ++ackGeneration
            log(
                "SPP TX type=${outbound.frameType.name} seq=${sequence.u} " +
                    "payload=${outbound.payload.hexString()} frame=${encoded.hexString()}"
            )
            try {
                output.write(encoded)
                output.flush()
                if (expectedAck != null) {
                    scheduleAckTimeout(expectedAck, generation)
                }
            } catch (e: IOException) {
                awaitingAck = null
                shutdown("SPP write failed: ${e.message}")
            }
        }
    }

    private fun scheduleAckTimeout(expectedAck: Byte, generation: Int) {
        Thread({
            try {
                Thread.sleep(ACK_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                return@Thread
            }
            var retryScheduled = false
            synchronized(lock) {
                if (closed.get() || ackGeneration != generation || awaitingAck != expectedAck) return@synchronized
                val frame = awaitingFrame
                if (frame != null && awaitingRetries < MAX_ACK_RETRIES) {
                    awaitingRetries += 1
                    val retryGeneration = ++ackGeneration
                    log(
                        "SPP ACK timeout expected=${expectedAck.u}; " +
                            "resending frame retry=$awaitingRetries"
                    )
                    try {
                        output.write(frame)
                        output.flush()
                    } catch (e: IOException) {
                        awaitingAck = null
                        awaitingFrame = null
                        shutdown("SPP retry failed: ${e.message}")
                        return@synchronized
                    }
                    scheduleAckTimeout(expectedAck, retryGeneration)
                    retryScheduled = true
                    return@synchronized
                }
                log("SPP ACK timeout expected=${expectedAck.u}; closing transport")
                awaitingAck = null
                awaitingFrame = null
                shutdown("SPP remote endpoint did not ACK seq=${inverseSequence(expectedAck).u}")
            }
            if (retryScheduled) return@Thread
        }, "OpenBuds-SppAckTimeout").start()
    }

    private fun sendAck(sequence: Byte) {
        val ackSequence = inverseSequence(sequence)
        val encoded = encodeFrame(SppFrameType.ACK, ackSequence, byteArrayOf())
        log("SPP TX ACK seq=${ackSequence.u} frame=${encoded.hexString()}")
        try {
            output.write(encoded)
            output.flush()
        } catch (e: IOException) {
            shutdown("SPP ACK failed: ${e.message}")
        }
    }

    private fun shutdown(reason: String?) {
        if (!closed.getAndSet(true)) {
            pendingWrites.clear()
            awaitingAck = null
            runCatching { socket.close() }
            listener.onDisconnected(reason)
        }
    }

    // ── Logging ────────────────────────────────────────────────

    private fun log(message: String) {
        listener.onLog(message)
    }

    // ── Companion: constants + delegation to SppFraming ─────────

    private companion object {
        const val WRITABLE_VALUE_LENGTH = 1024
        private const val ACK_TIMEOUT_MS = 1_200L
        private const val MAX_ACK_RETRIES = 1

        fun encodeFrame(type: SppFrameType, sequence: Byte, payload: ByteArray) =
            SppFraming.encodeFrame(type, sequence, payload)

        fun escape(bytes: ByteArray) = SppFraming.escape(bytes)
        fun unescape(bytes: ByteArray) = SppFraming.unescape(bytes)
        fun checksum(bytes: ByteArray, length: Int) = SppFraming.checksum(bytes, length)
        fun inverseSequence(sequence: Byte) = SppFraming.inverseSequence(sequence)

        val Byte.u: Int get() = toInt() and 0xFF

        fun ByteArray.int32be(offset: Int): Int =
            ((this[offset].u) shl 24) or
                ((this[offset + 1].u) shl 16) or
                ((this[offset + 2].u) shl 8) or
                this[offset + 3].u

        const val HEADER_SIZE = SppFraming.HEADER_SIZE
        const val CHECKSUM_SIZE = SppFraming.CHECKSUM_SIZE
        const val FRAME_START: Byte = SppFraming.FRAME_START
        const val FRAME_END: Byte = SppFraming.FRAME_END
        const val ESCAPE: Byte = SppFraming.ESCAPE
    }
}

// ── SPP frame types ───────────────────────────────────────────

/**
 * SPP frame type on the wire.
 *
 * Each frame type has a one-byte code and a flag indicating whether the
 * sender expects an ACK response.
 */
enum class SppFrameType(val code: Byte, val ackRequired: Boolean) {
    DATA_MDR(0x0C, true),
    DATA_MDR_NO2(0x0E, true),
    ACK(0x01, false),
    SHOT_MDR(0x1C, false),
    SHOT_MDR_NO2(0x1E, false),
    LARGE_DATA_MDR(0x2C, true),
    UNKNOWN(0xFF.toByte(), true);

    companion object {
        fun fromByte(code: Byte): SppFrameType = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/**
 * Internal pairing of frame type + payload queued for transmission.
 */
data class SppPayloadMapping(
    val frameType: SppFrameType,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SppPayloadMapping) return false
        return frameType == other.frameType && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * frameType.hashCode() + payload.contentHashCode()
}

interface SppPayloadMapper {
    fun outbound(bytes: ByteArray): SppPayloadMapping
    fun inbound(type: SppFrameType, payload: ByteArray): ByteArray?
}
