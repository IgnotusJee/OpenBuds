package dev.ignotus.openbuds.ble.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.ignotus.openbuds.protocol.hexString
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class GattTransportConfig(
    val name: String,
    val kind: String,
    val serviceUuid: UUID,
    val writeCharacteristicUuid: UUID,
    val notifyCharacteristicUuids: Set<UUID>? = null,
    val readOnReadyCharacteristicUuids: List<UUID> = emptyList(),
    val clientCharacteristicConfigUuid: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
    val requestedMtu: Int = 512,
    val androidTransport: Int? = null,
    val mtuTimeoutMs: Long = 1_500L,
    val readyDeadlineMs: Long = 5_000L,
    val characteristicLabel: (UUID) -> String = { it.toString() },
)

interface GattTransportListener {
    /** The underlying BLE GATT connection is established. */
    fun onConnected()

    /** Transport setup is complete (CCCDs, MTU) and ready for commands. */
    fun onReady(info: TransportInfo)

    /** Notification or read from a GATT characteristic. */
    fun onMessage(characteristicUuid: UUID, bytes: ByteArray)

    /** Transport disconnected unexpectedly. NOT called for deliberate [GattTransport.close]. */
    fun onDisconnected(reason: String?)

    /** Transport setup failed; transport has been torn down. */
    fun onSetupFailed(message: String)

    /** Debug log message from the transport. */
    fun onLog(message: String)
}

class GattTransport(
    private val context: Context,
    private val config: GattTransportConfig,
    private val listener: GattTransportListener,
) : BluetoothTransport {
    override val name: String = config.name
    override var info: TransportInfo = TransportInfo(mtu = DEFAULT_EFFECTIVE_MTU, kind = config.kind)
        private set

    val isReady: Boolean
        get() = readyFired

    private sealed interface GattOp {
        data class EnableNotify(val characteristic: BluetoothGattCharacteristic) : GattOp
        data class ReadChar(val characteristic: BluetoothGattCharacteristic) : GattOp
        data class WriteChar(
            val characteristic: BluetoothGattCharacteristic,
            val bytes: ByteArray,
        ) : GattOp
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val opQueue = ArrayDeque<GattOp>()
    private val closed = AtomicBoolean(false)

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingOp: GattOp? = null
    private var cccdSetupCount = 0
    private var cccdCompletedCount = 0
    private var mtuNegotiated = false
    private var readyFired = false

    private val mtuTimeoutRunnable = Runnable {
        if (mtuNegotiated || closed.get()) return@Runnable
        log("MTU negotiation timeout after ${config.mtuTimeoutMs}ms; falling back to mtu=23")
        mtuNegotiated = true
        updateInfo(DEFAULT_NEGOTIATED_MTU)
        maybeFireReady()
    }

    private val readyDeadlineRunnable = Runnable {
        if (closed.get()) return@Runnable
        log("Ready deadline reached; continuing with current GATT setup")
        if (!mtuNegotiated) {
            mtuNegotiated = true
            updateInfo(DEFAULT_NEGOTIATED_MTU)
        }
        if (cccdCompletedCount < cccdSetupCount) {
            cccdCompletedCount = cccdSetupCount
        }
        maybeFireReady()
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (closed.get()) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("GATT connected; discovering services")
                    listener.onConnected()
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("GATT disconnected: status=$status")
                    shutdown(if (status == BluetoothGatt.GATT_SUCCESS) null else "GATT disconnected: status=$status")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (closed.get()) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failSetup("Service discovery failed: $status")
                return
            }

            val service = gatt.getService(config.serviceUuid)
            if (service == null) {
                val services = gatt.services.joinToString { it.uuid.toString() }
                failSetup("Service ${config.serviceUuid} not found. Available services: $services")
                return
            }

            val writeChar = service.getCharacteristic(config.writeCharacteristicUuid)
            if (writeChar == null) {
                failSetup("Write characteristic ${config.writeCharacteristicUuid} not found")
                return
            }
            writeCharacteristic = writeChar

            cccdSetupCount = 0
            cccdCompletedCount = 0
            mtuNegotiated = false
            readyFired = false

            val notifyChars = service.characteristics.filter { characteristic ->
                val wantsNotify = config.notifyCharacteristicUuids?.contains(characteristic.uuid) ?: true
                wantsNotify && (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
            }
            notifyChars.forEach { characteristic ->
                cccdSetupCount++
                enqueue(GattOp.EnableNotify(characteristic))
            }

            config.readOnReadyCharacteristicUuids.forEach { uuid ->
                service.getCharacteristic(uuid)?.let { enqueue(GattOp.ReadChar(it)) }
            }

            log(
                "GATT service ready: notify=$cccdSetupCount " +
                    "reads=${config.readOnReadyCharacteristicUuids.size}; requesting MTU ${config.requestedMtu}"
            )
            requestMtu(gatt)
            mainHandler.postDelayed(mtuTimeoutRunnable, config.mtuTimeoutMs)
            mainHandler.postDelayed(readyDeadlineRunnable, config.readyDeadlineMs)
            pumpQueue()
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (closed.get()) return
            val negotiated = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_NEGOTIATED_MTU
            log("MTU changed: negotiated=$mtu effective=${effectiveMtu(negotiated)} status=$status")
            mainHandler.removeCallbacks(mtuTimeoutRunnable)
            mtuNegotiated = true
            updateInfo(negotiated)
            maybeFireReady()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (closed.get()) return
            handleIncoming(characteristic.uuid, value)
        }

        @Deprecated("Used below Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (closed.get()) return
            handleIncoming(characteristic.uuid, characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (closed.get()) return
            handleRead(characteristic.uuid, value, status)
        }

        @Deprecated("Used below Android 13")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (closed.get()) return
            handleRead(characteristic.uuid, characteristic.value ?: byteArrayOf(), status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (closed.get()) return
            log("Write ${config.characteristicLabel(characteristic.uuid)}: status=$status")
            opCompleted()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (closed.get()) return
            val characteristicUuid = descriptor.characteristic?.uuid
            log("Notify CCCD for char $characteristicUuid: status=$status")
            cccdCompletedCount++
            if (cccdCompletedCount >= cccdSetupCount) {
                log("All $cccdSetupCount CCCDs completed")
                maybeFireReady()
            }
            opCompleted()
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        closed.set(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && config.androidTransport != null) {
            gatt = device.connectGatt(context, false, callback, config.androidTransport)
        } else {
            gatt = device.connectGatt(context, false, callback)
        }
    }

    override fun send(bytes: ByteArray) {
        val characteristic = writeCharacteristic
        if (characteristic == null) {
            log("Cannot write: write characteristic is not available")
            return
        }
        enqueue(GattOp.WriteChar(characteristic, bytes))
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        if (!closed.getAndSet(true)) {
            clearState()
            gatt?.disconnect()
            gatt?.close()
            gatt = null
        }
    }

    private fun handleIncoming(uuid: UUID, value: ByteArray) {
        log("Notify/Read ${config.characteristicLabel(uuid)}: ${value.hexString()}")
        listener.onMessage(uuid, value)
    }

    private fun handleRead(uuid: UUID, value: ByteArray, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            handleIncoming(uuid, value)
        } else {
            log("Read ${config.characteristicLabel(uuid)} failed: status=$status")
        }
        opCompleted()
    }

    private fun enqueue(op: GattOp) {
        if (closed.get()) return
        opQueue.add(op)
        pumpQueue()
    }

    private fun opCompleted() {
        pendingOp = null
        pumpQueue()
    }

    @SuppressLint("MissingPermission")
    private fun pumpQueue() {
        if (closed.get() || pendingOp != null) return
        val op = opQueue.poll() ?: return
        val activeGatt = gatt ?: run {
            log("Cannot execute $op: GATT is null")
            return
        }

        pendingOp = op
        val success = when (op) {
            is GattOp.EnableNotify -> enableNotification(activeGatt, op.characteristic)
            is GattOp.ReadChar -> activeGatt.readCharacteristic(op.characteristic)
            is GattOp.WriteChar -> writeCharacteristic(activeGatt, op.characteristic, op.bytes)
        }

        if (!success) {
            when (op) {
                is GattOp.EnableNotify, is GattOp.ReadChar -> {
                    failSetup("Permanent GATT setup failure: $op")
                    return
                }
                is GattOp.WriteChar -> {
                    log("GATT busy for $op; retry in 200ms")
                    opQueue.addFirst(op)
                    pendingOp = null
                    mainHandler.postDelayed({ pumpQueue() }, GATT_RETRY_DELAY_MS)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ): Boolean =
        GattHelpers.enableNotification(gatt, characteristic, config.clientCharacteristicConfigUuid)

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        bytes: ByteArray,
    ): Boolean =
        GattHelpers.writeCharacteristic(gatt, characteristic, bytes)

    @SuppressLint("MissingPermission")
    private fun requestMtu(gatt: BluetoothGatt) {
        if (!GattHelpers.requestMtu(gatt, config.requestedMtu)) {
            log("requestMtu returned false; using default MTU")
            mtuNegotiated = true
            updateInfo(DEFAULT_NEGOTIATED_MTU)
            maybeFireReady()
        }
    }

    private fun maybeFireReady() {
        if (closed.get() || readyFired || !mtuNegotiated) return
        if (cccdCompletedCount < cccdSetupCount) return
        readyFired = true
        mainHandler.removeCallbacks(mtuTimeoutRunnable)
        mainHandler.removeCallbacks(readyDeadlineRunnable)
        log("Setup complete; firing onReady (effective MTU: ${info.mtu})")
        mainHandler.post { listener.onReady(info) }
    }

    private fun updateInfo(negotiatedMtu: Int) {
        val effective = effectiveMtu(negotiatedMtu)
        info = TransportInfo(
            mtu = effective,
            kind = config.kind,
            writableValueLength = effective,
        )
    }

    private fun failSetup(message: String) {
        log(message)
        listener.onSetupFailed(message)
        // Tear down silently — do NOT fire onDisconnected so the caller
        // can preserve the error state (e.g. permissionIssue in QCY client).
        if (!closed.getAndSet(true)) {
            clearState()
            gatt?.close()
            gatt = null
        }
    }

    private fun shutdown(reason: String?) {
        if (!closed.getAndSet(true)) {
            clearState()
            gatt?.close()
            gatt = null
            listener.onDisconnected(reason)
        }
    }

    private fun clearState() {
        opQueue.clear()
        pendingOp = null
        writeCharacteristic = null
        cccdSetupCount = 0
        cccdCompletedCount = 0
        mtuNegotiated = false
        readyFired = false
        mainHandler.removeCallbacks(mtuTimeoutRunnable)
        mainHandler.removeCallbacks(readyDeadlineRunnable)
    }

    private fun log(message: String) {
        listener.onLog("[$name] $message")
    }

    private companion object {
        const val DEFAULT_NEGOTIATED_MTU = 23
        const val DEFAULT_EFFECTIVE_MTU = 20
        const val GATT_RETRY_DELAY_MS = 200L

        fun effectiveMtu(negotiatedMtu: Int): Int =
            (negotiatedMtu - 3).coerceAtLeast(DEFAULT_EFFECTIVE_MTU)
    }
}
