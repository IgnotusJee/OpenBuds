package dev.ignotus.openbuds.ble.sony

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import dev.ignotus.openbuds.ble.HeadphoneConnectionInfo
import dev.ignotus.openbuds.ble.HeadphoneTransportListener
import dev.ignotus.openbuds.ble.IncomingHeadphoneMessage
import dev.ignotus.openbuds.ble.transport.SppTransport
import dev.ignotus.openbuds.ble.transport.TransportInfo
import dev.ignotus.openbuds.ble.transport.TransportListener
import java.io.IOException
import java.util.UUID

internal class SonyTandemSppSession(
    private val adapter: BluetoothAdapter?,
    selected: DiscoveredSonyDevice,
    selectedRemote: BluetoothDevice,
    private val listener: HeadphoneTransportListener,
    private val log: (String) -> Unit,
    private val safeDeviceName: (BluetoothDevice) -> String?,
) : SonyTandemSession {
    override var connectedDevice: DiscoveredSonyDevice? = null
        private set

    val canConnect: Boolean
        get() = classicRemote != null

    private val selectedName = selected.name
    private val classicRemote: BluetoothDevice? = resolveSppRemoteDevice(selected, selectedRemote)
    private var sppTransport: SppTransport? = null

    init {
        if (classicRemote != null) {
            connectedDevice = selected.copy(
                name = safeDeviceName(classicRemote) ?: selected.name.removePrefix("LE_"),
                address = classicRemote.address,
                source = "${selected.source}/spp",
                bluetoothType = classicRemote.type,
                isLikelyControlEndpoint = true,
            )
        }
    }

    override fun connect() {
        val remote = classicRemote
        if (remote == null) {
            listener.onBluetoothUnavailable("No paired classic Sony SPP device matches $selectedName")
            return
        }
        Thread {
            try {
                adapter?.cancelDiscovery()
                log("Connecting SPP to ${remote.address} name=${safeDeviceName(remote).orEmpty()} uuids=${remote.uuids?.joinToString { it.uuid.toString() }.orEmpty()}")
                val socket = createSppSocket(remote)
                socket.connect()
                val transport = SppTransport(
                    socket = socket,
                    listener = object : TransportListener {
                        override fun onReady(info: TransportInfo) {
                            log("SPP connected")
                            listener.onConnectionStateChanged(true, connectedDevice)
                            listener.onReady(HeadphoneConnectionInfo(mtu = info.mtu, transport = "SPP"))
                        }

                        override fun onMessage(bytes: ByteArray) {
                            listener.onMessage(IncomingHeadphoneMessage(SONY_ADAPTER_ID, SonyChannel.SPP_MDR.sourceKey, bytes))
                        }

                        override fun onDisconnected(reason: String?) {
                            log(reason ?: "SPP transport disconnected unexpectedly")
                            sppTransport = null
                            listener.onConnectionStateChanged(false, connectedDevice)
                        }

                        override fun onLog(message: String) {
                            log(message)
                        }
                    },
                    payloadMapper = SonySppPayloadMapper,
                )
                sppTransport = transport
                transport.start()
            } catch (e: IOException) {
                log("SPP connection failed: ${e.message}")
                disconnect(notify = true)
                listener.onBluetoothUnavailable("SPP connection failed: ${e.message}")
            } catch (e: SecurityException) {
                log("SPP permission failure: ${e.message}")
                disconnect(notify = true)
                listener.onBluetoothUnavailable("Bluetooth connect permission failed for SPP")
            }
        }.start()
    }

    override fun disconnect(notify: Boolean) {
        val transport = sppTransport
        sppTransport = null
        if (transport != null) {
            transport.close()
        }
        if (notify) listener.onConnectionStateChanged(false, connectedDevice)
    }

    override fun send(bytes: ByteArray) {
        sppTransport?.send(bytes)
            ?: listener.onBluetoothUnavailable("SPP channel is not available")
    }

    override fun sendToChannel(channel: SonyChannel, bytes: ByteArray) {
        if (channel != SonyChannel.SPP_MDR) {
            log("SPP session ignoring requested Sony channel $channel; sending over SPP")
        }
        send(bytes)
    }

    override fun availableChannels(): Set<SonyChannel> =
        if (sppTransport != null) setOf(SonyChannel.SPP_MDR) else emptySet()

    @SuppressLint("MissingPermission")
    private fun resolveSppRemoteDevice(
        selected: DiscoveredSonyDevice,
        selectedRemote: BluetoothDevice,
    ): BluetoothDevice? {
        if (selectedRemote.type == BluetoothDevice.DEVICE_TYPE_CLASSIC ||
            selectedRemote.type == BluetoothDevice.DEVICE_TYPE_DUAL
        ) {
            return selectedRemote
        }
        val targetName = selected.name.removePrefix("LE_")
        return adapter?.bondedDevices.orEmpty().firstOrNull { device ->
            val name = safeDeviceName(device).orEmpty()
            device.type != BluetoothDevice.DEVICE_TYPE_LE &&
                (name.equals(targetName, ignoreCase = true) || SonyDeviceMatcher.isHeadphoneCandidate(name))
        }
    }

    @SuppressLint("MissingPermission")
    private fun createSppSocket(device: BluetoothDevice): BluetoothSocket {
        val advertised = device.uuids.orEmpty().map { it.uuid }.toSet()
        val candidates = OFFICIAL_SPP_UUIDS.filter { it in advertised } +
            OFFICIAL_SPP_UUIDS.filter { it !in advertised }
        var lastError: IOException? = null
        for (uuid in candidates.distinct()) {
            try {
                log("SPP create socket uuid=$uuid")
                return device.createRfcommSocketToServiceRecord(uuid)
            } catch (e: IOException) {
                lastError = e
                log("SPP create socket failed uuid=$uuid error=${e.message}")
            }
        }
        throw lastError ?: IOException("No SPP UUID could create a socket")
    }

    private companion object {
        val OFFICIAL_SPP_UUIDS: List<UUID> = listOf(
            UUID.fromString("956c7b26-d49a-4ba8-b03f-b17d393cb6e2"),
            UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba"),
        )
    }
}
