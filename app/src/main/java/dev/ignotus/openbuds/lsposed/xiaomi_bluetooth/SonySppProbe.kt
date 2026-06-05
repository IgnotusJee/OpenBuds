package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import dev.ignotus.openbuds.integration.milink.normalizeMac
import dev.ignotus.openbuds.protocol.AmbientSoundMode
import dev.ignotus.openbuds.protocol.NoiseControlMode
import java.util.UUID
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
        val outputLock = Any()
        val wire = SonySppWireSession(
            targetMac = targetMac,
            logger = logger,
            writer = { frame ->
                runCatching {
                    synchronized(outputLock) {
                        socket.outputStream.write(frame)
                        socket.outputStream.flush()
                    }
                    true
                }.getOrElse { error ->
                    logger.warn(
                        event = "spp_probe_write_failed",
                        details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)}",
                        error = error,
                    )
                    false
                }
            },
        )
        val reader = Thread({
            val input = socket.inputStream
            val buffer = ByteArray(512)
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
                wire.ingest(buffer.copyOf(read))
            }
        }, "OpenBuds-SonySppProbeReader")
        reader.start()

        val readonlyAck = if (mode == SonySppProbeMode.READONLY || mode == SonySppProbeMode.WRITE) {
            val shouldWaitForAck = mode == SonySppProbeMode.WRITE
            wire.sendReadonlyBatteryQuery(waitForAck = shouldWaitForAck).also { ack ->
                if (shouldWaitForAck && !ack) {
                    logger.info(
                        event = "spp_probe_write_skipped",
                        mac = targetMac,
                        details = "reason=readonly_ack_timeout timeout_ms=$ACK_WAIT_MS",
                        force = true,
                    )
                }
            }
        } else {
            true
        }
        if (mode == SonySppProbeMode.WRITE && readonlyAck) {
            val writeAck = wire.sendNoiseControlMode(
                NoiseControlMode.AMBIENT_SOUND,
                ambientLevel = WRITE_AMBIENT_LEVEL,
                ambientMode = AmbientSoundMode.NORMAL,
                waitForAck = true,
            )
            if (!writeAck) {
                logger.info(
                    event = "spp_probe_write_ack_timeout",
                    mac = targetMac,
                    details = "timeout_ms=$ACK_WAIT_MS",
                    force = true,
                )
            }
        }

        Thread.sleep(PROBE_WINDOW_MS)
        closed.set(true)
        wire.close()
        runCatching { socket.close() }
        reader.join(READER_JOIN_MS)
        logger.info(
            event = "spp_probe_closed",
            mac = targetMac,
            details = "mode=${mode.propertyValue}",
            force = true,
        )
    }

    private fun candidateUuids(uuidPolicy: SonySppProbeUuid): List<UUID> =
        when (uuidPolicy) {
            SonySppProbeUuid.AUTO -> SonySppProbeUuid.EXPLICIT_UUIDS.map { it.uuid }
            is SonySppProbeUuid.Explicit -> listOf(uuidPolicy.uuid)
        }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String =
        runCatching { device.name.orEmpty() }.getOrDefault("")

    private companion object {
        private const val PROBE_START_DELAY_MS = 1_000L
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val PROBE_WINDOW_MS = 6_000L
        private const val READER_JOIN_MS = 1_000L
        private const val ACK_WAIT_MS = 1_500L
        private const val WRITE_AMBIENT_LEVEL = 10
    }
}
