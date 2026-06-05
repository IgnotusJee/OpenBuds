package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import dev.ignotus.openbuds.ble.sony.SonySppPayloadMapper
import dev.ignotus.openbuds.ble.transport.SppFrameType
import dev.ignotus.openbuds.ble.transport.SppFraming
import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import dev.ignotus.openbuds.lsposed.mitws.MiTwsDeviceIdPolicy
import dev.ignotus.openbuds.lsposed.mitws.MiTwsRuntimeProjection
import dev.ignotus.openbuds.protocol.AmbientSoundMode
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dev.ignotus.openbuds.protocol.ParsedHeadphoneResponse
import dev.ignotus.openbuds.protocol.sony.PowerInquiredType
import dev.ignotus.openbuds.protocol.sony.SonyTandemV2Table1Protocol
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SonySppWireSession(
    private val targetMac: String,
    private val writer: (ByteArray) -> Boolean,
    private val onStateChanged: (SonySppWireState) -> Unit = {},
    private val logInfo: (event: String, mac: String?, details: String, force: Boolean) -> Unit = { _, _, _, _ -> },
) {
    constructor(
        targetMac: String,
        logger: XiaomiBluetoothTraceLogger,
        writer: (ByteArray) -> Boolean,
        onStateChanged: (SonySppWireState) -> Unit = {},
    ) : this(
        targetMac = targetMac,
        writer = writer,
        onStateChanged = onStateChanged,
        logInfo = logger::info,
    )

    private val parser = SppFrameCollector(::handleEscapedFrame)
    private val outputLock = Any()
    private val pendingAcks = mutableMapOf<Byte, CountDownLatch>()
    private var nextTxSequence: Byte = 0
    private var revision: Long = 0L

    @Volatile
    var state: SonySppWireState = SonySppWireState()
        private set

    fun ingest(bytes: ByteArray) {
        parser.ingest(bytes)
    }

    fun sendReadonlyBatteryQuery(waitForAck: Boolean = false): Boolean {
        val bytes = SonyTandemV2Table1Protocol.buildGetBatteryStatus(PowerInquiredType.BATTERY)
        return sendTandem(
            phase = "readonly",
            tandemBytes = bytes,
            waitForAck = waitForAck,
        )
    }

    fun sendNoiseControlMode(
        mode: NoiseControlMode,
        ambientLevel: Int = DEFAULT_AMBIENT_LEVEL,
        ambientMode: AmbientSoundMode = AmbientSoundMode.NORMAL,
        waitForAck: Boolean = true,
    ): Boolean {
        val bytes = SonyTandemV2Table1Protocol.buildSetNoiseControlMode(
            mode,
            ambientLevel = ambientLevel,
            ambientMode = ambientMode,
        )
        return sendTandem(
            phase = "write_nc_asm",
            tandemBytes = bytes,
            waitForAck = waitForAck,
        )
    }

    fun close() {
        synchronized(outputLock) {
            pendingAcks.values.forEach { it.countDown() }
            pendingAcks.clear()
        }
    }

    private fun sendTandem(
        phase: String,
        tandemBytes: ByteArray,
        waitForAck: Boolean,
    ): Boolean {
        var pending: PendingAck? = null
        var frame = byteArrayOf()
        val outbound = SonySppPayloadMapper.outboundFromTandemBytes(tandemBytes)
        synchronized(outputLock) {
            val sequence = nextTxSequence
            frame = SppFraming.encodeFrame(outbound.frameType, sequence, outbound.payload)
            pending = if (outbound.frameType.ackRequired) {
                val expectedAck = SppFraming.inverseSequence(sequence)
                val latch = CountDownLatch(1)
                pendingAcks[expectedAck] = latch
                PendingAck(sequence, expectedAck, latch)
            } else {
                nextTxSequence = SppFraming.inverseSequence(sequence)
                null
            }
            logInfo(
                "spp_wire_tx",
                targetMac,
                "phase=$phase type=${outbound.frameType.name} seq=${sequence.u} " +
                    "expected_ack=${pending?.expectedAck?.u} tandem=${tandemBytes.toHex()} frame=${frame.toHex()}",
                true,
            )
            if (!writer(frame)) {
                pending?.let { pendingAcks.remove(it.expectedAck) }
                return false
            }
        }
        if (!waitForAck || pending == null) return true
        val matched = pending.latch.await(ACK_WAIT_MS, TimeUnit.MILLISECONDS)
        if (!matched) {
            synchronized(outputLock) {
                pendingAcks.remove(pending.expectedAck)
            }
            logInfo(
                "spp_wire_ack_timeout",
                targetMac,
                "phase=$phase seq=${pending.sequence.u} expected_ack=${pending.expectedAck.u} timeout_ms=$ACK_WAIT_MS",
                true,
            )
        }
        return matched
    }

    private fun handleEscapedFrame(escapedBody: ByteArray) {
        val body = SppFraming.unescape(escapedBody)
        if (body.size < SppFraming.HEADER_SIZE + SppFraming.CHECKSUM_SIZE) {
            logInvalid("short", body)
            return
        }
        val expectedChecksum = SppFraming.checksum(body, body.size - SppFraming.CHECKSUM_SIZE)
        val actualChecksum = body.last().u
        if (expectedChecksum != actualChecksum) {
            logInvalid("checksum expected=$expectedChecksum actual=$actualChecksum", body)
            return
        }
        val type = SppFrameType.fromByte(body[0])
        val sequence = body[1]
        val length = body.int32be(2)
        if (length < 0 || body.size != SppFraming.HEADER_SIZE + length + SppFraming.CHECKSUM_SIZE) {
            logInvalid("length length=$length", body)
            return
        }
        val payload = body.copyOfRange(SppFraming.HEADER_SIZE, SppFraming.HEADER_SIZE + length)
        logInfo(
            "spp_wire_rx_frame",
            targetMac,
            "type=${type.name} seq=${sequence.u} payload=${payload.toHex()}",
            true,
        )

        when (type) {
            SppFrameType.ACK -> signalAck(sequence)
            SppFrameType.DATA_MDR,
            SppFrameType.DATA_MDR_NO2,
            SppFrameType.LARGE_DATA_MDR -> sendAck(sequence)
            else -> Unit
        }

        val raw = SonySppPayloadMapper.inboundToTandemBytes(type, payload) ?: return
        val parsed = runCatching { SonyTandemV2Table1Protocol.parse(raw) }.getOrNull()
        logInfo(
            "spp_wire_rx_tandem",
            targetMac,
            "raw=${raw.toHex()} parsed=${describeParsed(parsed)}",
            true,
        )
        parsed?.let(::applyParsedState)
    }

    private fun signalAck(sequence: Byte) {
        val matched = synchronized(outputLock) {
            val latch = pendingAcks.remove(sequence)
            if (latch != null) {
                nextTxSequence = sequence
                latch.countDown()
                true
            } else {
                false
            }
        }
        logInfo(
            "spp_wire_rx_ack",
            targetMac,
            "seq=${sequence.u} matched=$matched",
            true,
        )
    }

    private fun sendAck(sequence: Byte) {
        val ackSequence = SppFraming.inverseSequence(sequence)
        val frame = SppFraming.encodeFrame(SppFrameType.ACK, ackSequence, byteArrayOf())
        logInfo(
            "spp_wire_tx_ack",
            targetMac,
            "seq=${ackSequence.u} frame=${frame.toHex()}",
            true,
        )
        synchronized(outputLock) {
            writer(frame)
        }
    }

    private fun applyParsedState(parsed: ParsedHeadphoneResponse) {
        val next = when (parsed) {
            is ParsedHeadphoneResponse.SonyTandem.Battery -> {
                when (parsed.kind) {
                    PowerInquiredType.BATTERY -> state.copy(singleBattery = parsed.values.firstOrNull())
                    PowerInquiredType.LEFT_RIGHT_BATTERY -> state.copy(
                        leftBattery = parsed.values.getOrNull(0),
                        rightBattery = parsed.values.getOrNull(1),
                    )
                    PowerInquiredType.CRADLE_BATTERY -> state.copy(caseBattery = parsed.values.firstOrNull())
                    PowerInquiredType.AUTO_POWER_OFF,
                    PowerInquiredType.POWER_SAVE_MODE,
                    PowerInquiredType.STAMINA,
                    null -> state
                }
            }
            is ParsedHeadphoneResponse.SonyTandem.NoiseControl -> state.copy(
                ancMode = parsed.controlMode?.toMiLinkAncMode(),
                ambientLevel = parsed.ambientLevel ?: state.ambientLevel,
            )
            else -> state
        }
        if (next != state) {
            revision += 1
            state = next.copy(revision = revision)
            onStateChanged(state)
        }
    }

    private fun logInvalid(reason: String, body: ByteArray) {
        logInfo(
            "spp_wire_rx_invalid",
            targetMac,
            "reason=$reason body=${body.toHex()}",
            true,
        )
    }

    private fun describeParsed(parsed: ParsedHeadphoneResponse?): String =
        when (parsed) {
            is ParsedHeadphoneResponse.SonyTandem.Battery ->
                "Battery kind=${parsed.kind} values=${parsed.values}"
            is ParsedHeadphoneResponse.SonyTandem.NoiseControl ->
                "NoiseControl type=${parsed.type} mode=${parsed.controlMode} ambientLevel=${parsed.ambientLevel} " +
                    "ambientMode=${parsed.ambientMode} values=${parsed.values}"
            is ParsedHeadphoneResponse.SonyTandem.Unknown ->
                "Unknown command=${parsed.command} payload=${parsed.payload.toHex()}"
            null -> "parse_failed"
            else -> parsed.javaClass.simpleName
        }

    private data class PendingAck(
        val sequence: Byte,
        val expectedAck: Byte,
        val latch: CountDownLatch,
    )

    private companion object {
        private const val ACK_WAIT_MS = 1_500L
        private const val DEFAULT_AMBIENT_LEVEL = 10
    }
}

data class SonySppWireState(
    val leftBattery: Int? = null,
    val rightBattery: Int? = null,
    val caseBattery: Int? = null,
    val singleBattery: Int? = null,
    val ancMode: Int? = null,
    val ambientLevel: Int? = null,
    val revision: Long = 0L,
) {
    fun toMilinkSnapshot(
        mac: String,
        name: String?,
        updatedAt: Long,
    ): MilinkDeviceSnapshot =
        MilinkDeviceSnapshot(
            mac = mac,
            name = name ?: "LinkBuds S",
            brand = "Sony",
            model = "LinkBuds S",
            deviceId = MiTwsDeviceIdPolicy.deviceIdForMac(mac),
            formFactor = MiTwsRuntimeProjection.FORM_FACTOR_TRUE_WIRELESS,
            connected = true,
            protocolReady = true,
            leftBattery = leftBattery,
            rightBattery = rightBattery,
            caseBattery = caseBattery,
            singleBattery = singleBattery,
            leftWearing = null,
            rightWearing = null,
            leftCharging = null,
            rightCharging = null,
            caseCharging = null,
            ancMode = ancMode,
            ringing = false,
            currentVolume = null,
            currentAudioEffectState = null,
            supportsBattery = true,
            supportsNoiseControl = true,
            supportsWearing = false,
            supportsRing = false,
            supportsVolumeControl = false,
            supportsAudioEffect = false,
            supportsEq = false,
            supportsLeaStatus = false,
            supportsQuickAccess = false,
            supportsAmbientLevel = true,
            revision = revision,
            updatedAt = updatedAt,
        )
}

class SppFrameCollector(
    private val onFrame: (ByteArray) -> Unit,
) {
    private val frame = mutableListOf<Byte>()
    private var inFrame = false

    fun ingest(bytes: ByteArray) {
        for (byte in bytes) {
            when (byte) {
                SppFraming.FRAME_START -> {
                    frame.clear()
                    inFrame = true
                }
                SppFraming.FRAME_END -> {
                    if (inFrame) onFrame(frame.toByteArray())
                    frame.clear()
                    inFrame = false
                }
                else -> if (inFrame) frame += byte
            }
        }
    }
}

fun NoiseControlMode.toMiLinkAncMode(): Int =
    when (this) {
        NoiseControlMode.OFF -> 0
        NoiseControlMode.NOISE_CANCELLING -> 1
        NoiseControlMode.AMBIENT_SOUND -> 2
    }

fun Int.toNoiseControlMode(): NoiseControlMode? =
    when (this) {
        0 -> NoiseControlMode.OFF
        1 -> NoiseControlMode.NOISE_CANCELLING
        2 -> NoiseControlMode.AMBIENT_SOUND
        else -> null
    }

internal fun ByteArray.toHex(maxBytes: Int = 48): String =
    take(maxBytes).joinToString(separator = "") { "%02X".format(it.u) } +
        if (size > maxBytes) "..." else ""

private fun ByteArray.int32be(offset: Int): Int =
    ((this[offset].u) shl 24) or
        ((this[offset + 1].u) shl 16) or
        ((this[offset + 2].u) shl 8) or
        this[offset + 3].u

internal val Byte.u: Int
    get() = toInt() and 0xFF
