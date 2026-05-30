package dev.ignotus.openbuds.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.ignotus.openbuds.headphones.TandemChannel
import dev.ignotus.openbuds.protocol.qcy.QcyGatt
import dev.ignotus.openbuds.protocol.hexString
import java.util.ArrayDeque
import java.util.UUID

/**
 * BLE client for QCY headphones.
 *
 * Connection flow:
 *   1. connectGatt(mac, TRANSPORT_LE)
 *   2. onServicesDiscovered → locate 0xA001 service
 *   3. Enqueue: enable notify for each NOTIFY characteristic, then
 *      battery read, version read, MTU request.
 *   4. Operations are serialized through [gattOpQueue]; one pending op at a
 *      time. Each GATT callback drains the next op.
 *   5. onReady fires when CCCD writes for all NOTIFY chars complete AND MTU
 *      negotiation finishes (or after MTU timeout fallback to 23).
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

    private var gatt: BluetoothGatt? = null
    private var connectedDevice: DiscoveredSonyDevice? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var negotiatedMtu: Int = 23
    private var effectiveMtu: Int = 20

    /** Sealed type for GATT operations queued through [gattOpQueue]. */
    private sealed interface GattOp {
        data class EnableNotify(val characteristic: BluetoothGattCharacteristic) : GattOp
        data class ReadChar(val characteristic: BluetoothGattCharacteristic) : GattOp
        data class WriteChar(
            val characteristic: BluetoothGattCharacteristic,
            val bytes: ByteArray,
        ) : GattOp
    }

    private val gattOpQueue = ArrayDeque<GattOp>()
    @Volatile private var pendingOp: GattOp? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Ready gating: both CCCDs done AND MTU response received,
    // with a 5 s overall deadline to guarantee writes work eventually.
    private var cccdSetupCount = 0
    private var cccdCompletedCount = 0
    private var mtuNegotiated = false
    private var readyFired = false
    private val mtuTimeoutMs = 1_500L
    private val overallReadyDeadlineMs = 5_000L
    private val overallReadyTimeoutRunnable = Runnable { maybeFireReady() }
    private val mtuTimeoutRunnable = Runnable {
        if (mtuNegotiated) return@Runnable
        log("MTU negotiation timeout after ${mtuTimeoutMs}ms; falling back to mtu=23")
        mtuNegotiated = true
        maybeFireReady()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("GATT connected; discovering services")
                listener.onConnectionStateChanged(true, connectedDevice)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("GATT disconnected: status=$status")
                resetConnectionState()
                listener.onConnectionStateChanged(false, connectedDevice)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Service discovery failed status=$status")
                listener.onBluetoothUnavailable("QCY service discovery failed: $status")
                return
            }

            val service = gatt.getService(QcyGatt.SERVICE_UUID)
            if (service == null) {
                val services = gatt.services.joinToString { it.uuid.toString() }
                log("QCY service 0xA001 not found. Available services=[$services]")
                listener.onBluetoothUnavailable(
                    "QCY service not found. This device may not be a QCY headphone. " +
                        "Available services: $services"
                )
                return
            }

            commandCharacteristic = service.getCharacteristic(QcyGatt.CHARACTER_SETTING_UUID)
            if (commandCharacteristic == null) {
                log("QCY setting characteristic 0x1001 not found")
                listener.onBluetoothUnavailable("QCY setting characteristic not found")
                return
            }

            log("QCY service discovered with ${service.characteristics.size} characteristics")

            // Reset ready gating for this connection
            cccdSetupCount = 0
            cccdCompletedCount = 0
            mtuNegotiated = false
            readyFired = false

            // Enqueue: CCCD writes for every NOTIFY characteristic, then battery read,
            // version read. MTU is requested out-of-queue (Android handles it separately).
            for (characteristic in service.characteristics) {
                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    cccdSetupCount++
                    enqueue(GattOp.EnableNotify(characteristic))
                }
            }
            service.getCharacteristic(QcyGatt.CHARACTER_BATTERY_UUID)?.let {
                enqueue(GattOp.ReadChar(it))
            }
            service.getCharacteristic(QcyGatt.CHARACTER_VERSION_UUID)?.let {
                enqueue(GattOp.ReadChar(it))
            }
            log("Enqueued $cccdSetupCount CCCDs + battery/version reads; requesting MTU")

            // MTU request is independent of the op queue (Android tracks it separately).
            gatt.requestMtu(512)
            mainHandler.postDelayed(mtuTimeoutRunnable, mtuTimeoutMs)
            // Overall 5 s deadline: fire ready even if CCCDs/MTU don't complete.
            mainHandler.postDelayed(overallReadyTimeoutRunnable, overallReadyDeadlineMs)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            negotiatedMtu = mtu
            effectiveMtu = (mtu - 3).coerceAtLeast(20)
            log("MTU changed: negotiated=$mtu effective=$effectiveMtu status=$status")
            mainHandler.removeCallbacks(mtuTimeoutRunnable)
            mtuNegotiated = true
            maybeFireReady()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleCharacteristicChanged(characteristic.uuid, value)
        }

        @Deprecated("Used below Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handleCharacteristicChanged(characteristic.uuid, characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Read ${QcyGatt.characteristicLabel(characteristic.uuid)}: ${value.hexString()}")
                handleCharacteristicChanged(characteristic.uuid, value)
            } else {
                log("Read ${QcyGatt.characteristicLabel(characteristic.uuid)} failed: status=$status")
            }
            opCompleted()
        }

        @Deprecated("Used below Android 13")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value ?: byteArrayOf()
                log("Read ${QcyGatt.characteristicLabel(characteristic.uuid)}: ${value.hexString()}")
                handleCharacteristicChanged(characteristic.uuid, value)
            } else {
                log("Read ${QcyGatt.characteristicLabel(characteristic.uuid)} failed: status=$status")
            }
            opCompleted()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            log("Write ${characteristic.uuid}: status=$status")
            opCompleted()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            val charUuid = descriptor.characteristic?.uuid
            log("Notify CCCD ${descriptor.uuid} for char $charUuid: status=$status")
            cccdCompletedCount++
            if (cccdCompletedCount >= cccdSetupCount) {
                log("All $cccdSetupCount CCCDs completed")
                maybeFireReady()
            }
            opCompleted()
        }
    }

    private fun handleCharacteristicChanged(uuid: UUID, value: ByteArray) {
        log("Notify/Read ${QcyGatt.characteristicLabel(uuid)}: ${value.hexString()}")

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

    // ── GATT op queue ─────────────────────────────────────────────

    private fun enqueue(op: GattOp) {
        gattOpQueue.add(op)
        pumpQueue()
    }

    private fun opCompleted() {
        pendingOp = null
        pumpQueue()
    }

    @SuppressLint("MissingPermission")
    private fun pumpQueue() {
        if (pendingOp != null) return
        val op = gattOpQueue.poll() ?: return
        val g = gatt ?: run {
            log("Cannot execute $op: gatt is null")
            return
        }
        pendingOp = op
        val success = when (op) {
            is GattOp.EnableNotify -> {
                val ok = g.setCharacteristicNotification(op.characteristic, true)
                val descriptor = op.characteristic.getDescriptor(QcyGatt.CLIENT_CHARACTERISTIC_CONFIG)
                if (ok && descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(descriptor)
                } else {
                    log("EnableNotify failed: setNotification=$ok descriptor=$descriptor")
                    false
                }
            }
            is GattOp.ReadChar -> g.readCharacteristic(op.characteristic)
            is GattOp.WriteChar -> {
                op.characteristic.value = op.bytes
                op.characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                g.writeCharacteristic(op.characteristic)
            }
        }
        if (!success) {
            // GATT busy: requeue at the front and retry shortly. This is rare with
            // a serial queue but defends against late callbacks from previous ops.
            log("GATT busy for $op; retry in 200ms")
            gattOpQueue.addFirst(op)
            pendingOp = null
            mainHandler.postDelayed({ pumpQueue() }, 200)
        }
    }

    private fun maybeFireReady() {
        if (readyFired) return
        readyFired = true
        mainHandler.removeCallbacks(mtuTimeoutRunnable)
        mainHandler.removeCallbacks(overallReadyTimeoutRunnable)
        log("Setup complete; firing onReady (effective MTU: $effectiveMtu)")
        mainHandler.post {
            listener.onReady(
                SonyBleConnectionInfo(mtu = effectiveMtu, transport = "QCY_GATT")
            )
        }
    }

    private fun resetConnectionState() {
        gattOpQueue.clear()
        pendingOp = null
        commandCharacteristic = null
        readyFired = false
        mtuNegotiated = false
        cccdSetupCount = 0
        cccdCompletedCount = 0
        mainHandler.removeCallbacks(mtuTimeoutRunnable)
        mainHandler.removeCallbacks(overallReadyTimeoutRunnable)
    }

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
        connectedDevice = DiscoveredSonyDevice(
            name = deviceName,
            address = mac,
            rssi = 0,
            source = "qcy-ble",
            isLikelyControlEndpoint = true,
        )
        // QCY uses BLE GATT exclusively. For dual-mode devices (classic BT + BLE),
        // we must use TRANSPORT_LE to reach the BLE GATT server.
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        log("Disconnecting QCY device")
        resetConnectionState()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        connectedDevice = null
    }

    /**
     * Send raw bytes to the QCY command characteristic (0x1001).
     * All QCY writes ultimately go through the same write characteristic; the
     * `channel` argument is preserved for symmetry with SonyBleClient.
     */
    override fun sendToChannel(channel: TandemChannel, bytes: ByteArray) {
        val char = commandCharacteristic
        if (char == null) {
            log("Cannot write on $channel: command characteristic not available")
            return
        }
        when (channel) {
            TandemChannel.QCY_SETTING_WRITE -> enqueue(GattOp.WriteChar(char, bytes))
            TandemChannel.QCY_READSET,
            TandemChannel.QCY_BATTERY,
            TandemChannel.QCY_VERSION,
            TandemChannel.QCY_EQ_RAW,
            TandemChannel.QCY_FUNCTION -> {
                log("Write on read channel $channel routed to QCY_SETTING_WRITE")
                enqueue(GattOp.WriteChar(char, bytes))
            }
            else -> log("Unsupported channel for QCY: $channel")
        }
    }

    fun getEffectiveMtu(): Int = effectiveMtu

    fun isConnected(): Boolean =
        gatt != null && commandCharacteristic != null

    private fun log(message: String) {
        Log.d("QcyBleClient", message)
        listener.onLog("[QCY] $message")
    }
}
