package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import dev.ignotus.openbuds.ble.sony.SonySppPayloadMapper
import dev.ignotus.openbuds.ble.transport.SppFrameType
import dev.ignotus.openbuds.ble.transport.SppFraming
import dev.ignotus.openbuds.integration.milink.normalizeMac
import dev.ignotus.openbuds.protocol.AmbientSoundMode
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dev.ignotus.openbuds.protocol.ParsedHeadphoneResponse
import dev.ignotus.openbuds.protocol.sony.PowerInquiredType
import dev.ignotus.openbuds.protocol.sony.SonyTandemV2Table1Protocol
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SonySppProbe(
    private val logger: XiaomiBluetoothTraceLogger,
) {
    private val started = AtomicBoolean(false)

    fun startOnce(context: Context) {
        if (!XiaomiBluetoothTraceConfig.isSppProbeEnabled()) return
        if (!started.compareAndSet(false, true)) return

        val targetMac = XiaomiBluetoothTraceConfig.sppProbeTargetMac()
        val mode = XiaomiBluetoothTraceConfig.sppProbeMode()
        val uuidPolicy = XiaomiBluetoothTraceConfig.sppProbeUuid()
        if (targetMac == null) {
            logger.info(
                event = "spp_probe_config_invalid",
                mac = null,
                details = "reason=missing_or_invalid_mac property=${XiaomiBluetoothTraceConfig.SPP_PROBE_MAC_PROPERTY}",
                force = true,
            )
            return
        }

        logger.info(
            event = "spp_probe_start",
            mac = targetMac,
            details = "mode=${mode.propertyValue} uuid=${uuidPolicy.propertyValue} window_ms=$PROBE_WINDOW_MS",
            force = true,
        )
        Thread({
            runCatching { runProbe(context.applicationContext ?: context, targetMac, mode, uuidPolicy) }
                .onFailure { logger.warn("spp_probe_crash_guard", "probe thread failed", it) }
        }, "OpenBuds-SonySppProbe").start()
    }

    @SuppressLint("MissingPermission")
    private fun runProbe(
        context: Context,
        targetMac: String,
        mode: SonySppProbeMode,
        uuidPolicy: SonySppProbeUuid,
    ) {
        Thread.sleep(PROBE_START_DELAY_MS)
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            logger.info(
                event = "spp_probe_unavailable",
                mac = targetMac,
                details = "reason=bluetooth_adapter_unavailable_or_disabled",
                force = true,
            )
            return
        }
        val device = adapter.bondedDevices.orEmpty()
            .firstOrNull { candidate ->
                candidate.address.normalizeMac() == targetMac &&
                    candidate.type != BluetoothDevice.DEVICE_TYPE_LE
            }
        if (device == null) {
            logger.info(
                event = "spp_probe_target_missing",
                mac = targetMac,
                details = "bonded_count=${adapter.bondedDevices.orEmpty().size}",
                force = true,
            )
            return
        }

        runCatching { adapter.cancelDiscovery() }
        val uuids = candidateUuids(uuidPolicy)
        logger.info(
            event = "spp_probe_target_found",
            mac = targetMac,
            details = "name=${safeName(device)} type=${device.type} candidates=${uuids.joinToString()}",
            force = true,
        )
        for (uuid in uuids) {
            if (tryUuid(device, uuid, targetMac, mode)) return
        }
        logger.info(
            event = "spp_probe_failed",
            mac = targetMac,
            details = "reason=all_uuid_candidates_failed",
            force = true,
        )
    }

    @SuppressLint("MissingPermission")
    private fun tryUuid(
        device: BluetoothDevice,
        uuid: UUID,
        targetMac: String,
        mode: SonySppProbeMode,
    ): Boolean {
        var socket: BluetoothSocket? = null
        val connected = AtomicBoolean(false)
        return runCatching {
            logger.info(
                event = "spp_probe_connect_attempt",
                mac = targetMac,
                details = "uuid=$uuid mode=${mode.propertyValue}",
                force = true,
            )
            socket = device.createRfcommSocketToServiceRecord(uuid)
            val targetSocket = socket ?: return@runCatching false
            startConnectWatchdog(targetSocket, connected, targetMac, uuid)
            targetSocket.connect()
            connected.set(true)
            logger.info(
                event = "spp_probe_connected",
                mac = targetMac,
                details = "uuid=$uuid mode=${mode.propertyValue}",
                force = true,
            )
            runSession(targetSocket, targetMac, mode)
            true
        }.onFailure {
            logger.warn(
                event = "spp_probe_connect_failed",
                details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)} uuid=$uuid mode=${mode.propertyValue}",
                error = it,
            )
            runCatching { socket?.close() }
        }.getOrDefault(false)
    }

    private fun startConnectWatchdog(
        socket: BluetoothSocket,
        connected: AtomicBoolean,
        targetMac: String,
        uuid: UUID,
    ) {
        Thread({
            Thread.sleep(CONNECT_TIMEOUT_MS)
            if (connected.get()) return@Thread
            logger.info(
                event = "spp_probe_connect_timeout",
                mac = targetMac,
                details = "uuid=$uuid timeout_ms=$CONNECT_TIMEOUT_MS",
                force = true,
            )
            runCatching { socket.close() }
        }, "OpenBuds-SonySppProbeConnectWatchdog").start()
    }

    private fun runSession(
        socket: BluetoothSocket,
        targetMac: String,
        mode: SonySppProbeMode,
    ) {
        val closed = AtomicBoolean(false)
        val sessionState = ProbeSessionState()
        val reader = Thread({
            readLoop(socket, targetMac, closed, sessionState)
        }, "OpenBuds-SonySppProbeReader")
        reader.start()

        if (mode == SonySppProbeMode.READONLY || mode == SonySppProbeMode.WRITE) {
            sendReadonlyProbe(socket.outputStream, targetMac, sessionState)
        }
        if (mode == SonySppProbeMode.WRITE) {
            if (sessionState.readonlyAck.await(ACK_WAIT_MS, TimeUnit.MILLISECONDS)) {
                sendWriteProbe(socket.outputStream, targetMac, sessionState)
                if (!sessionState.writeAck.await(ACK_WAIT_MS, TimeUnit.MILLISECONDS)) {
                    logger.info(
                        event = "spp_probe_write_ack_timeout",
                        mac = targetMac,
                        details = "expected_seq=${SppFraming.inverseSequence(WRITE_SEQUENCE).u} timeout_ms=$ACK_WAIT_MS",
                        force = true,
                    )
                }
            } else {
                logger.info(
                    event = "spp_probe_write_skipped",
                    mac = targetMac,
                    details = "reason=readonly_ack_timeout expected_seq=${SppFraming.inverseSequence(READONLY_SEQUENCE).u} " +
                        "timeout_ms=$ACK_WAIT_MS",
                    force = true,
                )
            }
        }

        Thread.sleep(PROBE_WINDOW_MS)
        closed.set(true)
        runCatching { socket.close() }
        reader.join(READER_JOIN_MS)
        logger.info(
            event = "spp_probe_closed",
            mac = targetMac,
            details = "mode=${mode.propertyValue}",
            force = true,
        )
    }

    private fun sendReadonlyProbe(
        output: OutputStream,
        targetMac: String,
        sessionState: ProbeSessionState,
    ) {
        val tandemBytes = SonyTandemV2Table1Protocol.buildGetBatteryStatus(PowerInquiredType.BATTERY)
        val outbound = SonySppPayloadMapper.outboundFromTandemBytes(tandemBytes)
        val frame = SppFraming.encodeFrame(outbound.frameType, READONLY_SEQUENCE, outbound.payload)
        logger.info(
            event = "spp_probe_tx_readonly",
            mac = targetMac,
            details = "type=${outbound.frameType.name} seq=${READONLY_SEQUENCE.u} " +
                "tandem=${tandemBytes.toHex()} frame=${frame.toHex()}",
            force = true,
        )
        synchronized(sessionState.outputLock) {
            output.write(frame)
            output.flush()
        }
    }

    private fun sendWriteProbe(
        output: OutputStream,
        targetMac: String,
        sessionState: ProbeSessionState,
    ) {
        val tandemBytes = SonyTandemV2Table1Protocol.buildSetNoiseControlMode(
            NoiseControlMode.AMBIENT_SOUND,
            ambientLevel = WRITE_AMBIENT_LEVEL,
            ambientMode = AmbientSoundMode.NORMAL,
        )
        val outbound = SonySppPayloadMapper.outboundFromTandemBytes(tandemBytes)
        val frame = SppFraming.encodeFrame(outbound.frameType, WRITE_SEQUENCE, outbound.payload)
        logger.info(
            event = "spp_probe_tx_write",
            mac = targetMac,
            details = "command=nc_asm_ambient_normal level=$WRITE_AMBIENT_LEVEL " +
                "type=${outbound.frameType.name} seq=${WRITE_SEQUENCE.u} " +
                "tandem=${tandemBytes.toHex()} frame=${frame.toHex()}",
            force = true,
        )
        synchronized(sessionState.outputLock) {
            output.write(frame)
            output.flush()
        }
    }

    private fun readLoop(
        socket: BluetoothSocket,
        targetMac: String,
        closed: AtomicBoolean,
        sessionState: ProbeSessionState,
    ) {
        val input = socket.inputStream
        val output = socket.outputStream
        val buffer = ByteArray(512)
        val frame = mutableListOf<Byte>()
        var inFrame = false
        while (!closed.get()) {
            val read = runCatching { input.read(buffer) }
                .onFailure {
                    if (!closed.get()) {
                        logger.warn(
                            event = "spp_probe_read_failed",
                            details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)}",
                            error = it,
                        )
                    }
                }
                .getOrDefault(-1)
            if (read < 0) break
            for (index in 0 until read) {
                when (val byte = buffer[index]) {
                    SppFraming.FRAME_START -> {
                        frame.clear()
                        inFrame = true
                    }
                    SppFraming.FRAME_END -> {
                        if (inFrame) handleFrame(frame.toByteArray(), output, targetMac, sessionState)
                        frame.clear()
                        inFrame = false
                    }
                    else -> if (inFrame) frame += byte
                }
            }
        }
    }

    private fun handleFrame(
        escapedBody: ByteArray,
        output: OutputStream,
        targetMac: String,
        sessionState: ProbeSessionState,
    ) {
        val body = SppFraming.unescape(escapedBody)
        if (body.size < SppFraming.HEADER_SIZE + SppFraming.CHECKSUM_SIZE) {
            logger.info(
                event = "spp_probe_rx_invalid",
                mac = targetMac,
                details = "reason=short body=${body.toHex()}",
                force = true,
            )
            return
        }
        val expectedChecksum = SppFraming.checksum(body, body.size - SppFraming.CHECKSUM_SIZE)
        val actualChecksum = body.last().u
        if (expectedChecksum != actualChecksum) {
            logger.info(
                event = "spp_probe_rx_invalid",
                mac = targetMac,
                details = "reason=checksum expected=$expectedChecksum actual=$actualChecksum body=${body.toHex()}",
                force = true,
            )
            return
        }
        val type = SppFrameType.fromByte(body[0])
        val sequence = body[1]
        val length = body.int32be(2)
        if (length < 0 || body.size != SppFraming.HEADER_SIZE + length + SppFraming.CHECKSUM_SIZE) {
            logger.info(
                event = "spp_probe_rx_invalid",
                mac = targetMac,
                details = "reason=length length=$length body=${body.toHex()}",
                force = true,
            )
            return
        }
        val payload = body.copyOfRange(SppFraming.HEADER_SIZE, SppFraming.HEADER_SIZE + length)
        logger.info(
            event = "spp_probe_rx_frame",
            mac = targetMac,
            details = "type=${type.name} seq=${sequence.u} payload=${payload.toHex()}",
            force = true,
        )

        when (type) {
            SppFrameType.ACK -> signalAck(sequence, targetMac, sessionState)
            SppFrameType.DATA_MDR,
            SppFrameType.DATA_MDR_NO2,
            SppFrameType.LARGE_DATA_MDR -> sendAck(output, sequence, targetMac, sessionState)
            else -> Unit
        }
        SonySppPayloadMapper.inboundToTandemBytes(type, payload)?.let { raw ->
            val parsed = runCatching { SonyTandemV2Table1Protocol.parse(raw) }.getOrNull()
            logger.info(
                event = "spp_probe_rx_tandem",
                mac = targetMac,
                details = "raw=${raw.toHex()} parsed=${describeParsed(parsed)}",
                force = true,
            )
        }
    }

    private fun signalAck(
        sequence: Byte,
        targetMac: String,
        sessionState: ProbeSessionState,
    ) {
        val phase = when (sequence) {
            SppFraming.inverseSequence(READONLY_SEQUENCE) -> {
                sessionState.readonlyAck.countDown()
                "readonly"
            }
            SppFraming.inverseSequence(WRITE_SEQUENCE) -> {
                sessionState.writeAck.countDown()
                "write"
            }
            else -> null
        }
        if (phase != null) {
            logger.info(
                event = "spp_probe_rx_ack_match",
                mac = targetMac,
                details = "phase=$phase seq=${sequence.u}",
                force = true,
            )
        }
    }

    private fun sendAck(
        output: OutputStream,
        sequence: Byte,
        targetMac: String,
        sessionState: ProbeSessionState,
    ) {
        val ackSequence = SppFraming.inverseSequence(sequence)
        val frame = SppFraming.encodeFrame(SppFrameType.ACK, ackSequence, byteArrayOf())
        logger.info(
            event = "spp_probe_tx_ack",
            mac = targetMac,
            details = "seq=${ackSequence.u} frame=${frame.toHex()}",
            force = true,
        )
        synchronized(sessionState.outputLock) {
            output.write(frame)
            output.flush()
        }
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

    private fun candidateUuids(uuidPolicy: SonySppProbeUuid): List<UUID> =
        when (uuidPolicy) {
            SonySppProbeUuid.AUTO -> SonySppProbeUuid.EXPLICIT_UUIDS.map { it.uuid }
            is SonySppProbeUuid.Explicit -> listOf(uuidPolicy.uuid)
        }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String =
        runCatching { device.name.orEmpty() }.getOrDefault("")

    private fun ByteArray.int32be(offset: Int): Int =
        ((this[offset].u) shl 24) or
            ((this[offset + 1].u) shl 16) or
            ((this[offset + 2].u) shl 8) or
            this[offset + 3].u

    private fun ByteArray.toHex(maxBytes: Int = MAX_HEX_BYTES): String =
        take(maxBytes).joinToString(separator = "") { "%02X".format(it.u) } +
            if (size > maxBytes) "..." else ""

    private val Byte.u: Int
        get() = toInt() and 0xFF

    private class ProbeSessionState {
        val readonlyAck = CountDownLatch(1)
        val writeAck = CountDownLatch(1)
        val outputLock = Any()
    }

    private companion object {
        private const val PROBE_START_DELAY_MS = 1_000L
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val PROBE_WINDOW_MS = 6_000L
        private const val READER_JOIN_MS = 1_000L
        private const val ACK_WAIT_MS = 1_500L
        private const val MAX_HEX_BYTES = 48
        private const val READONLY_SEQUENCE: Byte = 0
        private const val WRITE_SEQUENCE: Byte = 1
        private const val WRITE_AMBIENT_LEVEL = 10
    }
}
