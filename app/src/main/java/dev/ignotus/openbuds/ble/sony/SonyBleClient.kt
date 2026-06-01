package dev.ignotus.openbuds.ble.sony

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dev.ignotus.openbuds.ble.HeadphoneTransportClient
import dev.ignotus.openbuds.ble.transport.SppTransport
import dev.ignotus.openbuds.ble.transport.TransportListener
import dev.ignotus.openbuds.headphones.TandemChannel
import dev.ignotus.openbuds.protocol.hexString
import dev.ignotus.openbuds.protocol.sony.SonyGatt
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * BLE GATT + SPP client for Sony Tandem protocol headphones.
 *
 * Responsibilities retained after Phase 3 extraction:
 * - GATT connection, multi-endpoint discovery (HPC + MC)
 * - Tandem handshake (OPTIMAL_MTU, DETERMINE_MTU, WRITABLE_VALUE_LENGTH)
 * - SPP connection and routing
 * - Write queue with channel dispatch
 * - Unsupported endpoint probing
 *
 * Extracted to separate files:
 * - [SonyBleScanner] — BLE scan + known-device enumeration
 * - [SonyAudioAdParser] — Sony Audio AD parsing
 * - [DiscoveredSonyDevice], [SonyBleConnectionInfo], [UnsupportedEndpointDiagnostics]
 * - [SonyBleClientListener]
 */
class SonyBleClient(
    private val context: Context,
    private val listener: SonyBleClientListener,
) : HeadphoneTransportClient {
    override val id: String = "sony-tandem"

    override fun matches(device: DiscoveredSonyDevice, reportedModelName: String?): Boolean {
        val name = (reportedModelName ?: device.name).lowercase()
        if (name.contains("qcy")) return false
        val sonyAd = device.sonyAd != null
        val sonyServices = device.advertisedServices.any { uuidStr ->
            try {
                TandemChannel.fromServiceUuid(java.util.UUID.fromString(uuidStr)) != null
            } catch (_: Exception) {
                false
            }
        }
        return sonyAd || sonyServices || isHeadphoneCandidate(name)
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    // ── Scanning (delegated to SonyBleScanner) ──────────────────

    private val scanner = SonyBleScanner(context, object : ScanListener {
        override fun onDeviceFound(device: DiscoveredSonyDevice) {
            listener.onDeviceFound(device)
        }
        override fun onScanStateChanged(scanning: Boolean) {
            listener.onScanStateChanged(scanning)
        }
        override fun onBluetoothUnavailable(reason: String) {
            listener.onBluetoothUnavailable(reason)
        }
        override fun onLog(message: String) {
            log(message)
        }
    })

    override fun startScan(strictSonyServiceFilter: Boolean) {
        scanner.startScan(strictSonyServiceFilter)
    }

    override fun stopScan() {
        scanner.stopScan()
    }

    // ── Connection state ────────────────────────────────────────

    private var gatt: BluetoothGatt? = null
    private var connectedDevice: DiscoveredSonyDevice? = null
    private var toAcc: BluetoothGattCharacteristic? = null
    private var fromAcc: BluetoothGattCharacteristic? = null
    private val gattEndpoints: MutableMap<TandemChannel, GattTandemEndpoint> = mutableMapOf()
    private var writableValueLength: Int? = null
    private var optimalMtu: Int? = null
    private var negotiatedMtu: Int = 23
    private var handshakeStep: HandshakeStep = HandshakeStep.Idle
    private var determineMtuNotificationEnabled = false
    private var unsupportedProbe: UnsupportedEndpointProbe? = null
    private var sppTransport: SppTransport? = null
    private val writeQueue = ConcurrentLinkedQueue<PendingTandemWrite>()
    private val pendingNotifyEndpoints = ArrayDeque<GattTandemEndpoint>()
    @Volatile private var writing = false

    // ── GATT callback ───────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("GATT connected; discovering services")
                listener.onConnectionStateChanged(true, connectedDevice)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("GATT disconnected: status=$status")
                writeQueue.clear()
                pendingNotifyEndpoints.clear()
                writing = false
                toAcc = null
                fromAcc = null
                gattEndpoints.clear()
                handshakeStep = HandshakeStep.Idle
                determineMtuNotificationEnabled = false
                listener.onConnectionStateChanged(false, connectedDevice)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Service discovery failed status=$status")
                listener.onBluetoothUnavailable("Service discovery failed: $status")
                return
            }
            val services = gatt.services.map { it.uuid }
            val service = gatt.getService(SonyGatt.TANDEM_V2_HPC_SERVICE)
            if (service != null) {
                log("Tandem V2 HPC service discovered")
                val hpcSpec = TandemGattRouting.endpointSpecFor(TandemChannel.GATT_V2_HPC)
                toAcc = service.getCharacteristic(hpcSpec.toAccUuid)
                fromAcc = service.getCharacteristic(hpcSpec.fromAccUuid)
                if (toAcc != null && fromAcc != null) {
                    gattEndpoints[TandemChannel.GATT_V2_HPC] = GattTandemEndpoint(
                        channel = TandemChannel.GATT_V2_HPC,
                        toAcc = toAcc!!,
                        fromAcc = fromAcc!!,
                    )
                } else {
                    val characteristics = service.characteristics.joinToString { it.uuid.toString() }
                    log("Tandem V2 HPC characteristics incomplete. Available=[$characteristics]")
                }
            }
            discoverMcEndpoints(gatt)
            if (gattEndpoints.isEmpty()) {
                val labels = services.joinToString { SonyGatt.serviceLabel(it) }
                val reason = unsupportedTandemEndpointReason(services)
                log("No usable Tandem GATT endpoint. Available services=[$labels]")
                beginUnsupportedEndpointProbe(gatt, services, reason)
                return
            }
            log("Tandem GATT endpoints discovered: ${gattEndpoints.keys.joinToString()}")
            beginTandemHandshake(gatt)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            negotiatedMtu = mtu
            log("MTU changed: mtu=$mtu status=$status")
            if (handshakeStep == HandshakeStep.RequestMtu) {
                enableDetermineMtuNotifications(gatt)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            handleCharacteristicRead(gatt, characteristic.uuid, value, status)
        }

        @Deprecated("Used below Android 13")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handleCharacteristicRead(gatt, characteristic.uuid, characteristic.value ?: byteArrayOf(), status)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicChanged(characteristic, value)
        }

        @Deprecated("Used below Android 13")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicChanged(characteristic, characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            log("Write ${characteristic.uuid}: status=$status")
            writing = false
            drainWriteQueue()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            log("Notify descriptor ${descriptor.uuid}: status=$status")
            when {
                descriptor.characteristic?.uuid == SonyGatt.DETERMINE_MTU &&
                    handshakeStep == HandshakeStep.EnableDetermineMtu -> {
                    determineMtuNotificationEnabled = status == BluetoothGatt.GATT_SUCCESS
                    readWritableValueLength(gatt)
                }
                descriptor.characteristic?.uuid == SonyGatt.DETERMINE_MTU &&
                    handshakeStep == HandshakeStep.DisableDetermineMtu -> {
                    readWritableValueLength(gatt)
                }
                handshakeStep == HandshakeStep.EnableTandemNotifications &&
                    TandemGattRouting.fromAccChannel(
                        endpoints = gattEndpoints,
                        serviceUuid = descriptor.characteristic?.service?.uuid,
                        characteristicUuid = descriptor.characteristic?.uuid,
                    ) != null -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        val channel = TandemGattRouting.fromAccChannel(
                            endpoints = gattEndpoints,
                            serviceUuid = descriptor.characteristic?.service?.uuid,
                            characteristicUuid = descriptor.characteristic?.uuid,
                        )
                        listener.onBluetoothUnavailable("Failed to enable Tandem notification for $channel: $status")
                        return
                    }
                    enableNextTandemNotification(gatt)
                }
            }
        }
    }

    // ── Connect / Disconnect ───────────────────────────────────

    fun connect(address: String) {
        connect(DiscoveredSonyDevice(name = "Sony audio device", address = address, rssi = 0, source = "manual-connect"))
    }

    override fun connect(device: DiscoveredSonyDevice) {
        if (!hasConnectPermission()) {
            listener.onBluetoothUnavailable("Bluetooth connect permission is missing")
            return
        }
        stopScan()
        val remote = adapter?.getRemoteDevice(device.address)
        if (remote == null) {
            listener.onBluetoothUnavailable("Cannot resolve remote device ${device.address}")
            return
        }
        if (shouldUseSpp(device, remote)) {
            connectSpp(device, remote)
            return
        }
        connectedDevice = device.copy(
            name = if (device.name == "Unknown BLE device") {
                safeDeviceName(remote) ?: "Sony audio device"
            } else { device.name },
            address = remote.address,
            bluetoothType = if (remote.type != BluetoothDevice.DEVICE_TYPE_UNKNOWN) remote.type else device.bluetoothType,
        )
        disconnect()
        connectedDevice = connectedDevice?.copy(address = remote.address)
        val transport = preferredTransport(remote, device)
        log("Connecting to ${device.address} type=${remote.type} transport=${transportLabel(transport)} source=${device.source} sonyAd=${device.sonyAd?.summary.orEmpty()}")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            remote.connectGatt(context, false, gattCallback, transport)
        } else {
            remote.connectGatt(context, false, gattCallback)
        }
    }

    override fun disconnect() {
        closeGatt(notify = true)
    }

    private fun closeGatt(notify: Boolean) {
        closeSpp(notify = false)
        writeQueue.clear()
        pendingNotifyEndpoints.clear()
        writing = false
        toAcc = null
        fromAcc = null
        gattEndpoints.clear()
        handshakeStep = HandshakeStep.Idle
        determineMtuNotificationEnabled = false
        unsupportedProbe = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        if (notify) {
            listener.onConnectionStateChanged(false, connectedDevice)
        }
    }

    override fun refreshUnsupportedEndpointProbe() {
        val activeGatt = gatt
        if (activeGatt == null) {
            listener.onBluetoothUnavailable("No GATT connection is available for endpoint diagnostics")
            return
        }
        val services = activeGatt.services.map { it.uuid }
        if (services.any { it in supportedGattControlServices } && gattEndpoints.isNotEmpty()) {
            beginTandemHandshake(activeGatt)
        } else {
            beginUnsupportedEndpointProbe(activeGatt, services, unsupportedTandemEndpointReason(services))
        }
    }

    // ── Send / channels ────────────────────────────────────────

    fun send(bytes: ByteArray) {
        log("TX ${bytes.hexString()}")
        val transport = sppTransport
        if (transport != null) { transport.send(bytes); return }
        writeToChannel(defaultGattWriteChannel(), bytes)
    }

    override fun sendToChannel(channel: TandemChannel, bytes: ByteArray) {
        log("TX $channel ${bytes.hexString()}")
        val transport = sppTransport
        if (transport != null) { transport.send(bytes); return }
        writeToChannel(channel, bytes)
    }

    override fun availableChannels(): Set<TandemChannel> {
        val channels = mutableSetOf<TandemChannel>()
        if (sppTransport != null) channels.add(TandemChannel.SPP_MDR)
        channels.addAll(gattEndpoints.keys)
        return channels
    }

    private fun writeToChannel(channel: TandemChannel, bytes: ByteArray) {
        if (channel !in gattEndpoints && sppTransport == null) {
            listener.onBluetoothUnavailable("Channel $channel is not available (available: ${availableChannels()})")
            return
        }
        writeQueue.add(PendingTandemWrite(channel, bytes))
        drainWriteQueue()
    }

    // ── SPP connection ─────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun shouldUseSpp(device: DiscoveredSonyDevice, remote: BluetoothDevice): Boolean {
        if (device.sonyAd?.leGattControlFlag == true) return false
        val hasMdrSppUuid = remote.uuids.orEmpty().any { it.uuid == MDR_SPP_MARKER_UUID }
        val classicCandidate = remote.type == BluetoothDevice.DEVICE_TYPE_CLASSIC ||
            remote.type == BluetoothDevice.DEVICE_TYPE_DUAL ||
            device.source.startsWith("connected-a2dp") ||
            device.source.startsWith("connected-headset")
        return hasMdrSppUuid || classicCandidate || device.sonyAd?.androidLine?.contains("SPP") == true
    }

    @SuppressLint("MissingPermission")
    private fun connectSpp(selected: DiscoveredSonyDevice, selectedRemote: BluetoothDevice) {
        val classicRemote = resolveSppRemoteDevice(selected, selectedRemote)
        if (classicRemote == null) {
            listener.onBluetoothUnavailable("No paired classic Sony SPP device matches ${selected.name}")
            return
        }
        connectedDevice = selected.copy(
            name = safeDeviceName(classicRemote) ?: selected.name.removePrefix("LE_"),
            address = classicRemote.address,
            source = "${selected.source}/spp",
            bluetoothType = classicRemote.type,
            isLikelyControlEndpoint = true,
        )
        disconnect()
        connectedDevice = connectedDevice?.copy(address = classicRemote.address)
        Thread {
            try {
                adapter?.cancelDiscovery()
                log("Connecting SPP to ${classicRemote.address} name=${safeDeviceName(classicRemote).orEmpty()} uuids=${classicRemote.uuids?.joinToString { it.uuid.toString() }.orEmpty()}")
                val socket = createSppSocket(classicRemote)
                socket.connect()
                val transport = SppTransport(
                    socket = socket,
                    listener = object : TransportListener {
                        override fun onReady(info: dev.ignotus.openbuds.ble.transport.TransportInfo) {
                            log("SPP connected")
                            listener.onConnectionStateChanged(true, connectedDevice)
                            listener.onReady(SonyBleConnectionInfo(mtu = info.mtu, transport = "SPP"))
                        }
                        override fun onMessage(bytes: ByteArray) {
                            listener.onMessage(TandemChannel.SPP_MDR, bytes)
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
                closeSpp(notify = true)
                listener.onBluetoothUnavailable("SPP connection failed: ${e.message}")
            } catch (e: SecurityException) {
                log("SPP permission failure: ${e.message}")
                closeSpp(notify = true)
                listener.onBluetoothUnavailable("Bluetooth connect permission failed for SPP")
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun resolveSppRemoteDevice(selected: DiscoveredSonyDevice, selectedRemote: BluetoothDevice): BluetoothDevice? {
        if (selectedRemote.type == BluetoothDevice.DEVICE_TYPE_CLASSIC || selectedRemote.type == BluetoothDevice.DEVICE_TYPE_DUAL) {
            return selectedRemote
        }
        val targetName = selected.name.removePrefix("LE_")
        return adapter?.bondedDevices.orEmpty().firstOrNull { device ->
            val name = safeDeviceName(device).orEmpty()
            device.type != BluetoothDevice.DEVICE_TYPE_LE &&
                (name.equals(targetName, ignoreCase = true) || isHeadphoneCandidate(name))
        }
    }

    @SuppressLint("MissingPermission")
    private fun createSppSocket(device: BluetoothDevice): BluetoothSocket {
        val advertised = device.uuids.orEmpty().map { it.uuid }.toSet()
        val candidates = OFFICIAL_SPP_UUIDS.filter { it in advertised } + OFFICIAL_SPP_UUIDS.filter { it !in advertised }
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

    private fun closeSpp(notify: Boolean) {
        val transport = sppTransport
        sppTransport = null
        if (transport == null) {
            if (notify) listener.onConnectionStateChanged(false, connectedDevice)
            return
        }
        transport.close()
        if (notify) listener.onConnectionStateChanged(false, connectedDevice)
    }

    // ── Tandem handshake ───────────────────────────────────────

    private fun beginTandemHandshake(gatt: BluetoothGatt) {
        handshakeStep = HandshakeStep.ReadOptimalMtu
        val characteristic = gatt.getService(SonyGatt.TANDEM_V2_HPC_SERVICE)?.getCharacteristic(SonyGatt.OPTIMAL_MTU)
        if (characteristic == null) {
            log("OPTIMAL_MTU missing; requesting default large MTU")
            requestLargeMtu(gatt)
            return
        }
        log("Handshake: read OPTIMAL_MTU")
        if (!gatt.readCharacteristic(characteristic)) {
            listener.onBluetoothUnavailable("Failed to read OPTIMAL_MTU")
        }
    }

    private fun handleCharacteristicRead(gatt: BluetoothGatt, uuid: UUID, value: ByteArray, status: Int) {
        unsupportedProbe?.let { probe ->
            handleUnsupportedProbeRead(gatt, probe, uuid, value, status)
            return
        }
        if (status != BluetoothGatt.GATT_SUCCESS) {
            listener.onBluetoothUnavailable("Read $uuid failed: $status")
            return
        }
        val parsed = value.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        when (uuid) {
            SonyGatt.OPTIMAL_MTU -> {
                optimalMtu = parsed
                log("Read $uuid = ${value.hexString()} parsed=$parsed")
                requestLargeMtu(gatt)
            }
            SonyGatt.WRITABLE_VALUE_LENGTH -> {
                writableValueLength = parsed
                log("Read $uuid = ${value.hexString()} parsed=$parsed")
                enableTandemNotifications(gatt)
            }
            else -> log("Read $uuid = ${value.hexString()}")
        }
    }

    private fun handleCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val uuid = characteristic.uuid
        if (uuid == SonyGatt.DETERMINE_MTU) {
            log("Handshake: DETERMINE_MTU notification ${value.hexString()}")
            if (determineMtuNotificationEnabled) {
                gatt?.let(::readWritableValueLength)
            }
            return
        }
        val channel = TandemGattRouting.fromAccChannel(endpoints = gattEndpoints, serviceUuid = characteristic.service?.uuid, characteristicUuid = uuid)
            ?: TandemGattRouting.fromAccChannelFor(characteristic.service?.uuid, uuid)
            ?: gattEndpoints.keys.singleOrNull()
            ?: defaultGattWriteChannel()
        listener.onMessage(channel, value)
    }

    private fun requestLargeMtu(gatt: BluetoothGatt) {
        handshakeStep = HandshakeStep.RequestMtu
        val requested = (optimalMtu ?: 517).coerceIn(23, 517)
        log("Handshake: request MTU $requested")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (!gatt.requestMtu(requested)) {
                log("requestMtu returned false; continuing with current MTU")
                enableDetermineMtuNotifications(gatt)
            }
        } else {
            enableDetermineMtuNotifications(gatt)
        }
    }

    private fun enableDetermineMtuNotifications(gatt: BluetoothGatt) {
        val characteristic = gatt.getService(SonyGatt.TANDEM_V2_HPC_SERVICE)?.getCharacteristic(SonyGatt.DETERMINE_MTU)
        if (characteristic == null) {
            log("DETERMINE_MTU missing; reading WRITABLE_VALUE_LENGTH directly")
            readWritableValueLength(gatt)
            return
        }
        handshakeStep = HandshakeStep.EnableDetermineMtu
        log("Handshake: enable DETERMINE_MTU notification")
        writeNotificationState(gatt, characteristic, enabled = true)
    }

    private fun readWritableValueLength(gatt: BluetoothGatt) {
        if (determineMtuNotificationEnabled) {
            val determine = gatt.getService(SonyGatt.TANDEM_V2_HPC_SERVICE)?.getCharacteristic(SonyGatt.DETERMINE_MTU)
            if (determine != null) {
                handshakeStep = HandshakeStep.DisableDetermineMtu
                determineMtuNotificationEnabled = false
                log("Handshake: disable DETERMINE_MTU notification")
                writeNotificationState(gatt, determine, enabled = false)
                return
            }
        }
        handshakeStep = HandshakeStep.ReadWritableValueLength
        val characteristic = gatt.getService(SonyGatt.TANDEM_V2_HPC_SERVICE)?.getCharacteristic(SonyGatt.WRITABLE_VALUE_LENGTH)
        if (characteristic == null) {
            log("WRITABLE_VALUE_LENGTH missing; enabling Tandem notifications")
            enableTandemNotifications(gatt)
            return
        }
        log("Handshake: read WRITABLE_VALUE_LENGTH")
        if (!gatt.readCharacteristic(characteristic)) {
            listener.onBluetoothUnavailable("Failed to read WRITABLE_VALUE_LENGTH")
        }
    }

    private fun enableTandemNotifications(gatt: BluetoothGatt) {
        handshakeStep = HandshakeStep.EnableTandemNotifications
        pendingNotifyEndpoints.clear()
        val orderedChannels = TandemGattRouting.notificationOrder(gattEndpoints.keys)
        gattEndpoints.values.sortedBy { endpoint -> orderedChannels.indexOf(endpoint.channel) }
            .forEach { pendingNotifyEndpoints.addLast(it) }
        enableNextTandemNotification(gatt)
    }

    private fun enableNextTandemNotification(gatt: BluetoothGatt) {
        val endpoint = pendingNotifyEndpoints.removeFirstOrNull()
        if (endpoint == null) {
            handshakeStep = HandshakeStep.Ready
            listener.onReady(SonyBleConnectionInfo(mtu = negotiatedMtu, writableValueLength = writableValueLength, optimalMtu = optimalMtu, transport = gattTransportLabel()))
            return
        }
        val characteristic = endpoint.fromAcc
        log("Handshake: enable ${endpoint.channel} ${SonyGatt.characteristicLabel(characteristic.uuid)} notification")
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor == null) { enableNextTandemNotification(gatt); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun writeNotificationState(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, enabled: Boolean) {
        gatt.setCharacteristicNotification(characteristic, enabled)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor == null) { readWritableValueLength(gatt); return }
        val value = if (enabled) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    // ── Write queue ────────────────────────────────────────────

    private fun drainWriteQueue() {
        val gatt = gatt ?: return
        if (writing) return
        val pending = writeQueue.poll() ?: return
        val endpoint = gattEndpoints[pending.channel]
        if (endpoint == null) {
            listener.onBluetoothUnavailable("Channel ${pending.channel} is not available (available: ${availableChannels()})")
            drainWriteQueue()
            return
        }
        val characteristic = endpoint.toAcc
        writing = true
        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, pending.bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = pending.bytes
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
        if (!accepted) {
            writing = false
            listener.onBluetoothUnavailable("Failed to enqueue BLE write")
        }
    }

    private fun defaultGattWriteChannel(): TandemChannel = when {
        TandemChannel.GATT_V2_HPC in gattEndpoints -> TandemChannel.GATT_V2_HPC
        TandemChannel.GATT_V1_MC in gattEndpoints -> TandemChannel.GATT_V1_MC
        TandemChannel.GATT_V2_MC in gattEndpoints -> TandemChannel.GATT_V2_MC
        else -> TandemChannel.GATT_V2_HPC
    }

    private fun gattTransportLabel(): String =
        if (TandemChannel.GATT_V2_HPC in gattEndpoints) "GATT_HPC" else "GATT_MC"

    private fun unsupportedTandemEndpointReason(services: Collection<UUID>): String =
        dev.ignotus.openbuds.ble.sony.unsupportedTandemEndpointReason(services)

    // ── Unsupported endpoint probe ─────────────────────────────

    private fun beginUnsupportedEndpointProbe(gatt: BluetoothGatt, services: List<UUID>, reason: String) {
        val serviceLabels = services.map { SonyGatt.serviceLabel(it) }
        unsupportedProbe = UnsupportedEndpointProbe(reason, serviceLabels)
        log("Unsupported endpoint probe starting. reason=$reason")
        gatt.services.forEach { service ->
            val chars = service.characteristics.joinToString { "${SonyGatt.characteristicLabel(it.uuid)} props=0x${it.properties.toString(16)}" }
            log("Probe service ${SonyGatt.serviceLabel(service.uuid)} chars=[$chars]")
        }
        if (!readNextUnsupportedProbeCharacteristic(gatt)) finishUnsupportedEndpointProbe()
    }

    private fun handleUnsupportedProbeRead(gatt: BluetoothGatt, probe: UnsupportedEndpointProbe, uuid: UUID, value: ByteArray, status: Int) {
        val label = SonyGatt.characteristicLabel(uuid)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val hex = value.hexString()
            probe.rawReads[label] = hex
            when (uuid) {
                SonyGatt.LE_AUDIO_SWITCH_SUPPORTED_COMPATIBILITY -> {
                    probe.leAudioSwitchCompatibility = value.firstOrNull()?.toInt()?.and(0xFF)
                    log("Probe read $label compatibility=${probe.leAudioSwitchCompatibility} raw=$hex")
                }
                SonyGatt.COMPLETE_BLUETOOTH_FRIENDLY_NAME -> {
                    probe.friendlyName = parseFriendlyName(value)
                    log("Probe read $label name=${probe.friendlyName.orEmpty()} raw=$hex")
                }
                SonyGatt.BLUETOOTH_PUBLIC_ADDRESS -> {
                    probe.publicAddress = value.toString(Charsets.UTF_8).trim('\u0000')
                    log("Probe read $label publicAddress=${probe.publicAddress.orEmpty()} raw=$hex")
                }
                else -> log("Probe read $label raw=$hex")
            }
        } else {
            log("Probe read $label failed status=$status")
            probe.rawReads[label] = "read failed: $status"
        }
        if (!readNextUnsupportedProbeCharacteristic(gatt)) finishUnsupportedEndpointProbe()
    }

    private fun readNextUnsupportedProbeCharacteristic(gatt: BluetoothGatt): Boolean {
        val probe = unsupportedProbe ?: return false
        while (probe.nextIndex < UNSUPPORTED_PROBE_CHARACTERISTICS.size) {
            val uuid = UNSUPPORTED_PROBE_CHARACTERISTICS[probe.nextIndex++]
            val label = SonyGatt.characteristicLabel(uuid)
            val characteristic = findReadableCharacteristic(gatt, uuid)
            if (characteristic == null) { probe.rawReads[label] = "missing or not readable"; continue }
            log("Probe read $label")
            if (gatt.readCharacteristic(characteristic)) return true
            probe.rawReads[label] = "read rejected"
        }
        return false
    }

    private fun finishUnsupportedEndpointProbe() {
        val probe = unsupportedProbe ?: return
        val diagnostics = UnsupportedEndpointDiagnostics(
            reason = probe.reason,
            serviceLabels = probe.serviceLabels,
            leAudioSwitchCompatibility = probe.leAudioSwitchCompatibility,
            friendlyName = probe.friendlyName,
            publicAddress = probe.publicAddress,
            rawReads = probe.rawReads.toMap(),
        )
        log("Unsupported endpoint probe complete: compatibility=${diagnostics.leAudioSwitchCompatibility} friendlyName=${diagnostics.friendlyName.orEmpty()} publicAddress=${diagnostics.publicAddress.orEmpty()}")
        listener.onUnsupportedEndpoint(diagnostics)
    }

    private fun findReadableCharacteristic(gatt: BluetoothGatt, uuid: UUID): BluetoothGattCharacteristic? =
        gatt.services.asSequence().flatMap { it.characteristics.asSequence() }.firstOrNull {
            it.uuid == uuid && (it.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
        }

    private fun discoverMcEndpoints(gatt: BluetoothGatt) {
        for (channel in listOf(TandemChannel.GATT_V2_MC, TandemChannel.GATT_V1_MC)) {
            val spec = TandemGattRouting.endpointSpecFor(channel)
            val service = gatt.getService(spec.serviceUuid) ?: continue
            val mcToAcc = service.getCharacteristic(spec.toAccUuid)
            val mcFromAcc = service.getCharacteristic(spec.fromAccUuid)
            if (mcToAcc != null && mcFromAcc != null) {
                gattEndpoints[channel] = GattTandemEndpoint(channel = channel, toAcc = mcToAcc, fromAcc = mcFromAcc)
                log("MC endpoint registered: $channel")
            } else {
                log("MC service $channel found but characteristics incomplete")
            }
        }
    }

    // ── Transport helpers ──────────────────────────────────────

    private fun preferredTransport(device: BluetoothDevice, discovered: DiscoveredSonyDevice): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (device.type) {
                BluetoothDevice.DEVICE_TYPE_LE -> BluetoothDevice.TRANSPORT_LE
                BluetoothDevice.DEVICE_TYPE_CLASSIC, BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothDevice.TRANSPORT_AUTO
                else -> if (discovered.source == "ble-scan" || discovered.sonyAd?.androidGattCapable == true || discovered.sonyAd?.leGattControlFlag == true)
                    BluetoothDevice.TRANSPORT_LE else BluetoothDevice.TRANSPORT_AUTO
            }
        } else 0

    private fun transportLabel(transport: Int): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (transport) {
                BluetoothDevice.TRANSPORT_LE -> "LE"
                BluetoothDevice.TRANSPORT_BREDR -> "BREDR"
                BluetoothDevice.TRANSPORT_AUTO -> "AUTO"
                else -> transport.toString()
            }
        } else "DEFAULT"

    private fun parseFriendlyName(value: ByteArray): String? {
        if (value.size < 3) return null
        return value.copyOfRange(2, value.size).toString(Charsets.UTF_8).trim('\u0000').ifBlank { null }
    }

    private fun isHeadphoneCandidate(name: String?): Boolean {
        val normalized = name?.trim()?.lowercase().orEmpty()
        return normalized.contains("sony") || normalized.contains("linkbuds") || normalized.contains("qcy") ||
            normalized.startsWith("wf-") || normalized.startsWith("wh-") || normalized.startsWith("wi-") ||
            normalized.startsWith("xba-") || normalized.startsWith("mdr-")
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String? =
        if (hasConnectPermission()) device.name else null

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun log(message: String) {
        Log.i(LOG_TAG, message)
        listener.onLog(message)
    }

    // ── Internal types ─────────────────────────────────────────

    private enum class HandshakeStep {
        Idle, ReadOptimalMtu, RequestMtu, EnableDetermineMtu, DisableDetermineMtu,
        ReadWritableValueLength, EnableTandemNotifications, Ready,
    }

    private data class UnsupportedEndpointProbe(
        val reason: String,
        val serviceLabels: List<String>,
        var nextIndex: Int = 0,
        var leAudioSwitchCompatibility: Int? = null,
        var friendlyName: String? = null,
        var publicAddress: String? = null,
        val rawReads: MutableMap<String, String> = linkedMapOf(),
    )

    private companion object {
        const val LOG_TAG = "OpenBuds"
        val MDR_SPP_MARKER_UUID: UUID = UUID.fromString("443cce33-e85d-4b85-8d53-6e319ede53ae")
        val OFFICIAL_SPP_UUIDS = listOf(
            UUID.fromString("956c7b26-d49a-4ba8-b03f-b17d393cb6e2"),
            UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba"),
        )
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val UNSUPPORTED_PROBE_CHARACTERISTICS = listOf(
            SonyGatt.LE_AUDIO_SWITCH_SUPPORTED_COMPATIBILITY,
            SonyGatt.BLUETOOTH_CONNECTION, SonyGatt.BLUETOOTH_MODE,
            SonyGatt.BLUETOOTH_CONNECTION_STATUS, SonyGatt.BLUETOOTH_MODE_STATUS,
            SonyGatt.COMPLETE_BLUETOOTH_FRIENDLY_NAME, SonyGatt.SYNC_BLUETOOTH_FRIENDLY_NAME_INDEX,
            SonyGatt.BLUETOOTH_PUBLIC_ADDRESS, SonyGatt.LE_AD_PACKET_IDENTIFIER,
            SonyGatt.LE_AD_PACKET_IDENTIFIER_LEFT, SonyGatt.LE_AD_PACKET_IDENTIFIER_RIGHT,
            SonyGatt.TARGET_ANNOUNCEMENT_LE_AD,
        )
        val supportedGattControlServices = setOf(SonyGatt.TANDEM_V2_HPC_SERVICE, SonyGatt.TANDEM_V1_MC_SERVICE)
    }
}

internal fun tandemEndpointSupportState(services: Collection<UUID>): String? =
    if (services.any { it in supportedGattControlServices }) null else unsupportedTandemEndpointReason(services)

internal fun unsupportedTandemEndpointReason(services: Collection<UUID>): String {
    val labels = services.map { SonyGatt.serviceLabel(it) }
    return when {
        SonyGatt.TANDEM_V1_MC_SERVICE in services ->
            "Tandem V1 MC service was found, but no usable MC control endpoint could be registered. Services: ${labels.joinToString()}"
        SonyGatt.LE_AUDIO_CAPABILITY_FOR_HPC in services ->
            "This LE endpoint exposes LE Audio capability, not Tandem V2 HPC control. Try disabling LE Audio / using classic-only mode, then rescan."
        SonyGatt.BLUETOOTH_PAIRING_COMPLETE_NAME_SERVICE in services ->
            "This LE endpoint is a pairing/name endpoint, not Tandem V2 HPC control. Services: ${labels.joinToString()}"
        else -> "Tandem control service was not found. Services: ${labels.joinToString()}"
    }
}

private val supportedGattControlServices = setOf(
    SonyGatt.TANDEM_V2_HPC_SERVICE,
    SonyGatt.TANDEM_V1_MC_SERVICE,
)
