package dev.ignotus.openbuds.ble.qcy

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Log
import dev.ignotus.openbuds.ble.HeadphoneTransportClient
import dev.ignotus.openbuds.ble.sony.DiscoveredSonyDevice
import dev.ignotus.openbuds.ble.sony.SonyBleClientListener
import dev.ignotus.openbuds.ble.sony.SonyBleConnectionInfo
import dev.ignotus.openbuds.ble.transport.GattTransport
import dev.ignotus.openbuds.ble.transport.GattTransportConfig
import dev.ignotus.openbuds.ble.transport.GattTransportListener
import dev.ignotus.openbuds.ble.transport.TransportInfo
import dev.ignotus.openbuds.headphones.TandemChannel
import dev.ignotus.openbuds.protocol.qcy.QcyGatt
import java.util.UUID

/**
 * BLE client for QCY headphones.
 *
 * Connection flow is delegated to [GattTransport]:
 *   1. connectGatt(mac, TRANSPORT_LE)
 *   2. discover QCY service 0xA001 and command characteristic 0x1001
 *   3. enable notify characteristics, read battery/version, request MTU
 *   4. serialize CCCD, read, and command writes through one GATT operation queue
 *
 * Unlike SonyBleClient there is no SPP path, no multi-step handshake, and no
 * heartbeat. CCCD writes and command writes share a single FIFO so we never
 * issue two GATT operations concurrently (Android only permits one).
 *
 * Reference: com.qcymall.qcylibrary.QCYHeadsetClient.java
 */
class QcyBleClient(
    private val context: Context,
    private val listener: SonyBleClientListener,
) : HeadphoneTransportClient {
    override val id: String = "qcy-gatt"

    override fun matches(device: DiscoveredSonyDevice, reportedModelName: String?): Boolean {
        val name = (reportedModelName ?: device.name).orEmpty().lowercase()
        if (name.contains("qcy")) return true
        // Also match if the device advertises the QCY service UUID.
        val qcyServiceLowercase = QcyGatt.SERVICE_UUID.toString().lowercase()
        return device.advertisedServices.any { it.lowercase() == qcyServiceLowercase }
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = bluetoothManager.adapter

    private var transport: GattTransport? = null
    private var connectedDevice: DiscoveredSonyDevice? = null
    private var effectiveMtu: Int = 20

    private fun handleCharacteristicChanged(uuid: UUID, value: ByteArray) {
        val channel = when (uuid) {
            QcyGatt.CHARACTER_BATTERY_UUID -> TandemChannel.QCY_BATTERY
            QcyGatt.CHARACTER_VERSION_UUID -> TandemChannel.QCY_VERSION
            QcyGatt.CHARACTER_READSET_UUID -> TandemChannel.QCY_READSET
            QcyGatt.CHARACTER_EQ_UUID -> TandemChannel.QCY_EQ_RAW
            QcyGatt.CHARACTER_FUNCTION_UUID -> TandemChannel.QCY_FUNCTION
            else -> {
                log("Unhandled characteristic notify: $uuid")
                return
            }
        }
        listener.onMessage(channel, value)
    }

    private fun createTransport(): GattTransport =
        GattTransport(
            context = context,
            config = GattTransportConfig(
                name = "QCY_GATT",
                kind = "QCY_GATT",
                serviceUuid = QcyGatt.SERVICE_UUID,
                writeCharacteristicUuid = QcyGatt.CHARACTER_SETTING_UUID,
                notifyCharacteristicUuids = null,
                readOnReadyCharacteristicUuids = listOf(
                    QcyGatt.CHARACTER_BATTERY_UUID,
                    QcyGatt.CHARACTER_VERSION_UUID,
                ),
                clientCharacteristicConfigUuid = QcyGatt.CLIENT_CHARACTERISTIC_CONFIG,
                requestedMtu = 512,
                androidTransport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    BluetoothDevice.TRANSPORT_LE
                } else {
                    null
                },
                characteristicLabel = QcyGatt::characteristicLabel,
            ),
            listener = object : GattTransportListener {
                override fun onConnected() {
                    listener.onConnectionStateChanged(true, connectedDevice)
                }

                override fun onReady(info: TransportInfo) {
                    effectiveMtu = info.mtu
                    listener.onReady(SonyBleConnectionInfo(mtu = info.mtu, transport = info.kind))
                }

                override fun onMessage(characteristicUuid: UUID, bytes: ByteArray) {
                    handleCharacteristicChanged(characteristicUuid, bytes)
                }

                override fun onDisconnected(reason: String?) {
                    reason?.let(::log)
                    transport = null
                    effectiveMtu = 20
                    listener.onConnectionStateChanged(false, connectedDevice)
                }

                override fun onSetupFailed(message: String) {
                    listener.onBluetoothUnavailable("QCY GATT setup failed: $message")
                }

                override fun onLog(message: String) {
                    log(message)
                }
            },
        )

    // ── Public API ───────────────────────────────────────────────

    /**
     * Scan is currently delegated to the Sony client (which already issues a
     * filterless LE scan and forwards QCY devices via [HeadphoneTransportSelector]).
     * This no-op satisfies the [HeadphoneTransportClient] contract without
     * starting a duplicate scanner.
     */
    override fun startScan(strictFilter: Boolean) {
        log("startScan: delegated to Sony scanner via selector (no-op here)")
    }

    override fun stopScan() {
        // see startScan
    }

    override fun availableChannels(): Set<TandemChannel> = setOf(
        TandemChannel.QCY_SETTING_WRITE,
        TandemChannel.QCY_READSET,
        TandemChannel.QCY_BATTERY,
        TandemChannel.QCY_VERSION,
        TandemChannel.QCY_EQ_RAW,
        TandemChannel.QCY_FUNCTION,
    )

    /**
     * Connect using a [DiscoveredSonyDevice]. The device may have been
     * discovered through a classic BT bond listing rather than a BLE scan;
     * in that case the MAC may differ from the actual BLE GATT MAC (QCY
     * dual-mode devices typically differ in the last byte). [HeadphoneTransportSelector]
     * is responsible for picking the BLE-side MAC before invoking this.
     */
    override fun connect(device: DiscoveredSonyDevice) {
        connect(device.address, device.name)
    }

    @SuppressLint("MissingPermission")
    fun connect(mac: String, deviceName: String = "QCY device") {
        log("Connecting to QCY device: $deviceName ($mac)")
        val device = adapter?.getRemoteDevice(mac) ?: run {
            listener.onBluetoothUnavailable("Bluetooth adapter unavailable")
            return
        }
        disconnect()
        connectedDevice = DiscoveredSonyDevice(
            name = deviceName,
            address = mac,
            rssi = 0,
            source = "qcy-ble",
            isLikelyControlEndpoint = true,
        )
        val activeTransport = createTransport()
        transport = activeTransport
        activeTransport.connect(device)
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        val activeTransport = transport
        if (activeTransport != null) {
            log("Disconnecting QCY device")
            activeTransport.close()
        }
        val previousDevice = connectedDevice
        transport = null
        connectedDevice = null
        effectiveMtu = 20
        // close() does not fire onDisconnected; notify the listener ourselves.
        listener.onConnectionStateChanged(false, previousDevice)
    }

    /**
     * Send raw bytes to the QCY command characteristic (0x1001).
     * All QCY writes ultimately go through the same write characteristic; the
     * `channel` argument is preserved for symmetry with SonyBleClient.
     */
    override fun sendToChannel(channel: TandemChannel, bytes: ByteArray) {
        val activeTransport = transport
        if (activeTransport == null) {
            log("Cannot write on $channel: command characteristic not available")
            return
        }
        when (channel) {
            TandemChannel.QCY_SETTING_WRITE -> activeTransport.send(bytes)
            TandemChannel.QCY_READSET,
            TandemChannel.QCY_BATTERY,
            TandemChannel.QCY_VERSION,
            TandemChannel.QCY_EQ_RAW,
            TandemChannel.QCY_FUNCTION -> {
                log("Write on read channel $channel routed to QCY_SETTING_WRITE")
                activeTransport.send(bytes)
            }
            else -> log("Unsupported channel for QCY: $channel")
        }
    }

    fun getEffectiveMtu(): Int = effectiveMtu

    fun isConnected(): Boolean =
        transport?.isReady == true

    private fun log(message: String) {
        Log.d("QcyBleClient", message)
        listener.onLog("[QCY] $message")
    }
}
