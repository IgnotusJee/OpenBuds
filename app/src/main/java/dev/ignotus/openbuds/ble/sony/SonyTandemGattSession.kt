package dev.ignotus.openbuds.ble.sony

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import dev.ignotus.openbuds.ble.HeadphoneConnectionInfo
import dev.ignotus.openbuds.ble.HeadphoneTransportListener
import dev.ignotus.openbuds.headphones.TandemChannel
import dev.ignotus.openbuds.protocol.hexString
import dev.ignotus.openbuds.protocol.sony.SonyGatt
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

internal class SonyTandemGattSession(
    private val context: Context,
    private val remote: BluetoothDevice,
    discoveredDevice: DiscoveredSonyDevice,
    private val listener: HeadphoneTransportListener,
    private val log: (String) -> Unit,
    private val safeDeviceName: (BluetoothDevice) -> String?,
) : SonyTandemSession {
    override var connectedDevice: DiscoveredSonyDevice? = discoveredDevice.copy(
        name = if (discoveredDevice.name == "Unknown BLE device") {
            safeDeviceName(remote) ?: "Sony audio device"
        } else {
            discoveredDevice.name
        },
        address = remote.address,
        bluetoothType = if (remote.type != BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
            remote.type
        } else {
            discoveredDevice.bluetoothType
        },
    )
        private set

    private var gatt: BluetoothGatt? = null
    private var toAcc: BluetoothGattCharacteristic? = null
    private var fromAcc: BluetoothGattCharacteristic? = null
    private val gattEndpoints: MutableMap<TandemChannel, GattTandemEndpoint> = mutableMapOf()
    private var writableValueLength: Int? = null
    private var optimalMtu: Int? = null
    private var negotiatedMtu: Int = 23
    private var handshakeStep: HandshakeStep = HandshakeStep.Idle
    private var determineMtuNotificationEnabled = false
    private var unsupportedProbe: UnsupportedEndpointProbe? = null
    private val writeQueue = ConcurrentLinkedQueue<PendingTandemWrite>()
    private val pendingNotifyEndpoints = ArrayDeque<GattTandemEndpoint>()
    @Volatile private var writing = false

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("GATT connected; discovering services")
                listener.onConnectionStateChanged(true, connectedDevice)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("GATT disconnected: status=$status")
                clearGattState()
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
                val reason = SonyTandemEndpointSupport.unsupportedReason(services)
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

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleCharacteristicRead(gatt, characteristic.uuid, value, status)
        }

        @Deprecated("Used below Android 13")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            handleCharacteristicRead(gatt, characteristic.uuid, characteristic.value ?: byteArrayOf(), status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleCharacteristicChanged(characteristic, value)
        }

        @Deprecated("Used below Android 13")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicChanged(characteristic, characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
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

    @SuppressLint("MissingPermission")
    override fun connect() {
        val transport = preferredTransport(remote, connectedDevice ?: return)
        log("Connecting to ${remote.address} type=${remote.type} transport=${transportLabel(transport)} source=${connectedDevice?.source.orEmpty()} sonyAd=${connectedDevice?.sonyAd?.summary.orEmpty()}")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            remote.connectGatt(context, false, gattCallback, transport)
        } else {
            remote.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect(notify: Boolean) {
        clearGattState()
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
        if (services.any { it in SonyTandemEndpointSupport.supportedControlServices } && gattEndpoints.isNotEmpty()) {
            beginTandemHandshake(activeGatt)
        } else {
            beginUnsupportedEndpointProbe(activeGatt, services, SonyTandemEndpointSupport.unsupportedReason(services))
        }
    }

    override fun sendToChannel(channel: TandemChannel, bytes: ByteArray) {
        log("TX $channel ${bytes.hexString()}")
        writeToChannel(channel, bytes)
    }

    override fun availableChannels(): Set<TandemChannel> = gattEndpoints.keys.toSet()

    private fun clearGattState() {
        writeQueue.clear()
        pendingNotifyEndpoints.clear()
        writing = false
        toAcc = null
        fromAcc = null
        gattEndpoints.clear()
        handshakeStep = HandshakeStep.Idle
        determineMtuNotificationEnabled = false
        unsupportedProbe = null
    }

    private fun writeToChannel(channel: TandemChannel, bytes: ByteArray) {
        if (channel !in gattEndpoints) {
            listener.onBluetoothUnavailable("Channel $channel is not available (available: ${availableChannels()})")
            return
        }
        writeQueue.add(PendingTandemWrite(channel, bytes))
        drainWriteQueue()
    }

    private fun beginTandemHandshake(gatt: BluetoothGatt) {
        handshakeStep = HandshakeStep.ReadOptimalMtu
        val characteristic = gatt.getService(SonyGatt.TANDEM_V2_HPC_SERVICE)
            ?.getCharacteristic(SonyGatt.OPTIMAL_MTU)
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
        when (uuid) {
            SonyGatt.OPTIMAL_MTU -> {
                optimalMtu = if (value.size >= 2) {
                    ((value[0].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
                } else {
                    null
                }
                log("Read OPTIMAL_MTU=$optimalMtu raw=${value.hexString()}")
                requestLargeMtu(gatt)
                return
            }
            SonyGatt.WRITABLE_VALUE_LENGTH -> {
                writableValueLength = if (value.size >= 2) {
                    ((value[0].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
                } else {
                    null
                }
                log("Read WRITABLE_VALUE_LENGTH=$writableValueLength raw=${value.hexString()}")
                enableTandemNotifications(gatt)
                return
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
        val channel = TandemGattRouting.fromAccChannel(
            endpoints = gattEndpoints,
            serviceUuid = characteristic.service?.uuid,
            characteristicUuid = uuid,
        )
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
        val characteristic = gatt.getService(SonyGatt.TANDEM_V2_HPC_SERVICE)
            ?.getCharacteristic(SonyGatt.DETERMINE_MTU)
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
            val determine = gatt.getService(SonyGatt.TANDEM_V2_HPC_SERVICE)
                ?.getCharacteristic(SonyGatt.DETERMINE_MTU)
            if (determine != null) {
                handshakeStep = HandshakeStep.DisableDetermineMtu
                determineMtuNotificationEnabled = false
                log("Handshake: disable DETERMINE_MTU notification")
                writeNotificationState(gatt, determine, enabled = false)
                return
            }
        }
        handshakeStep = HandshakeStep.ReadWritableValueLength
        val characteristic = gatt.getService(SonyGatt.TANDEM_V2_HPC_SERVICE)
            ?.getCharacteristic(SonyGatt.WRITABLE_VALUE_LENGTH)
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
            listener.onReady(
                HeadphoneConnectionInfo(
                    mtu = negotiatedMtu,
                    writableValueLength = writableValueLength,
                    optimalMtu = optimalMtu,
                    transport = gattTransportLabel(),
                )
            )
            return
        }
        val characteristic = endpoint.fromAcc
        log("Handshake: enable ${endpoint.channel} ${SonyGatt.characteristicLabel(characteristic.uuid)} notification")
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor == null) {
            enableNextTandemNotification(gatt)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun writeNotificationState(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        enabled: Boolean,
    ) {
        gatt.setCharacteristicNotification(characteristic, enabled)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor == null) {
            readWritableValueLength(gatt)
            return
        }
        val value = if (enabled) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

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
            gatt.writeCharacteristic(
                characteristic,
                pending.bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothGatt.GATT_SUCCESS
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

    private fun beginUnsupportedEndpointProbe(gatt: BluetoothGatt, services: List<UUID>, reason: String) {
        val serviceLabels = services.map { SonyGatt.serviceLabel(it) }
        unsupportedProbe = UnsupportedEndpointProbe(reason, serviceLabels)
        log("Unsupported endpoint probe starting. reason=$reason")
        gatt.services.forEach { service ->
            val chars = service.characteristics.joinToString {
                "${SonyGatt.characteristicLabel(it.uuid)} props=0x${it.properties.toString(16)}"
            }
            log("Probe service ${SonyGatt.serviceLabel(service.uuid)} chars=[$chars]")
        }
        if (!readNextUnsupportedProbeCharacteristic(gatt)) finishUnsupportedEndpointProbe()
    }

    private fun handleUnsupportedProbeRead(
        gatt: BluetoothGatt,
        probe: UnsupportedEndpointProbe,
        uuid: UUID,
        value: ByteArray,
        status: Int,
    ) {
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
            if (characteristic == null) {
                probe.rawReads[label] = "missing or not readable"
                continue
            }
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

    private fun preferredTransport(device: BluetoothDevice, discovered: DiscoveredSonyDevice): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (device.type) {
                BluetoothDevice.DEVICE_TYPE_LE -> BluetoothDevice.TRANSPORT_LE
                BluetoothDevice.DEVICE_TYPE_CLASSIC,
                BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothDevice.TRANSPORT_AUTO
                else -> if (
                    discovered.source == "ble-scan" ||
                    discovered.sonyAd?.androidGattCapable == true ||
                    discovered.sonyAd?.leGattControlFlag == true
                ) {
                    BluetoothDevice.TRANSPORT_LE
                } else {
                    BluetoothDevice.TRANSPORT_AUTO
                }
            }
        } else {
            0
        }

    private fun transportLabel(transport: Int): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (transport) {
                BluetoothDevice.TRANSPORT_LE -> "LE"
                BluetoothDevice.TRANSPORT_BREDR -> "BREDR"
                BluetoothDevice.TRANSPORT_AUTO -> "AUTO"
                else -> transport.toString()
            }
        } else {
            "DEFAULT"
        }

    private fun parseFriendlyName(value: ByteArray): String? {
        if (value.size < 3) return null
        return value.copyOfRange(2, value.size).toString(Charsets.UTF_8).trim('\u0000').ifBlank { null }
    }

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
        val CLIENT_CHARACTERISTIC_CONFIG: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val UNSUPPORTED_PROBE_CHARACTERISTICS: List<UUID> = listOf(
            SonyGatt.LE_AUDIO_SWITCH_SUPPORTED_COMPATIBILITY,
            SonyGatt.BLUETOOTH_CONNECTION,
            SonyGatt.BLUETOOTH_MODE,
            SonyGatt.BLUETOOTH_CONNECTION_STATUS,
            SonyGatt.BLUETOOTH_MODE_STATUS,
            SonyGatt.COMPLETE_BLUETOOTH_FRIENDLY_NAME,
            SonyGatt.SYNC_BLUETOOTH_FRIENDLY_NAME_INDEX,
            SonyGatt.BLUETOOTH_PUBLIC_ADDRESS,
            SonyGatt.LE_AD_PACKET_IDENTIFIER,
            SonyGatt.LE_AD_PACKET_IDENTIFIER_LEFT,
            SonyGatt.LE_AD_PACKET_IDENTIFIER_RIGHT,
            SonyGatt.TARGET_ANNOUNCEMENT_LE_AD,
        )
    }
}
