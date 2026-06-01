package dev.ignotus.openbuds.ble.sony

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dev.ignotus.openbuds.ble.HeadphoneTransportClient
import dev.ignotus.openbuds.ble.HeadphoneTransportListener
import dev.ignotus.openbuds.headphones.TandemChannel
import java.util.UUID

class SonyTandemTransportClient(
    private val context: Context,
    private val listener: HeadphoneTransportListener,
) : HeadphoneTransportClient {
    override val id: String = "sony-tandem"

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

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

    private var activeSession: SonyTandemSession? = null

    override fun matches(device: DiscoveredSonyDevice, reportedModelName: String?): Boolean =
        SonyDeviceMatcher.matches(device, reportedModelName)

    override fun startScan(strictFilter: Boolean) {
        scanner.startScan(strictFilter)
    }

    override fun stopScan() {
        scanner.stopScan()
    }

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

        val session = if (shouldUseSpp(device, remote)) {
            val sppSession = SonyTandemSppSession(
                adapter = adapter,
                selected = device,
                selectedRemote = remote,
                listener = listener,
                log = ::log,
                safeDeviceName = ::safeDeviceName,
            )
            if (!sppSession.canConnect) {
                sppSession.connect()
                return
            }
            sppSession
        } else {
            SonyTandemGattSession(
                context = context,
                remote = remote,
                discoveredDevice = device,
                listener = listener,
                log = ::log,
                safeDeviceName = ::safeDeviceName,
            )
        }

        activeSession?.disconnect(notify = false)
        activeSession = session
        session.connect()
    }

    override fun disconnect() {
        activeSession?.disconnect(notify = true)
        activeSession = null
    }

    override fun sendToChannel(channel: TandemChannel, bytes: ByteArray) {
        activeSession?.sendToChannel(channel, bytes)
            ?: listener.onBluetoothUnavailable("No Sony Tandem transport is connected")
    }

    override fun availableChannels(): Set<TandemChannel> =
        activeSession?.availableChannels().orEmpty()

    override fun refreshUnsupportedEndpointProbe() {
        activeSession?.refreshUnsupportedEndpointProbe()
            ?: listener.onBluetoothUnavailable("No GATT connection is available for endpoint diagnostics")
    }

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
    private fun safeDeviceName(device: BluetoothDevice): String? =
        if (hasConnectPermission()) device.name else null

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun log(message: String) {
        Log.i(LOG_TAG, message)
        listener.onLog(message)
    }

    private companion object {
        const val LOG_TAG = "OpenBuds"
        val MDR_SPP_MARKER_UUID: UUID = UUID.fromString("443cce33-e85d-4b85-8d53-6e319ede53ae")
    }
}
