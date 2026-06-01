package dev.ignotus.openbuds.ble.sony

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dev.ignotus.openbuds.protocol.hexString

/**
 * Listener for scan events from [SonyBleScanner].
 */
interface ScanListener {
    fun onDeviceFound(device: DiscoveredSonyDevice)
    fun onScanStateChanged(scanning: Boolean)
    fun onBluetoothUnavailable(reason: String)
    fun onLog(message: String)
}

/**
 * BLE scanner for Sony and compatible headphone devices.
 *
 * Handles:
 * - BLE scan start/stop with permission checks
 * - Known-device enumeration (bonded, connected A2DP/headset/GATT)
 * - Sony Audio Advertisement parsing via [SonyAudioAdParser]
 * - Device candidate filtering via [isHeadphoneCandidate]
 *
 * Scan results are forwarded to [ScanListener] for the caller to handle
 * deduplication and routing.
 */
class SonyBleScanner(
    private val context: Context,
    private val listener: ScanListener,
) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner?
        get() = adapter?.bluetoothLeScanner

    @Volatile
    var scanning: Boolean = false
        private set

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val serviceUuids = result.scanRecord?.serviceUuids?.joinToString { it.uuid.toString() }.orEmpty()
            val serviceList = result.scanRecord?.serviceUuids?.map {
                dev.ignotus.openbuds.protocol.sony.SonyGatt.serviceLabel(it.uuid)
            }.orEmpty()
            val name = safeDeviceName(device) ?: result.scanRecord?.deviceName
            val manufacturerData = result.scanRecord?.manufacturerSummary().orEmpty()
            val serviceData = result.scanRecord?.serviceDataSummary().orEmpty()
            val sonyAd = result.scanRecord?.sonyAudioAdvertisement()
            val found = DiscoveredSonyDevice(
                name = name ?: "Unknown BLE device",
                address = device.address,
                rssi = result.rssi,
                source = "ble-scan",
                bluetoothType = device.type,
                advertisedServices = serviceList,
                isLikelyControlEndpoint = sonyAd?.leGattControlFlag == true ||
                    serviceList.any {
                        it == "TANDEM_V2_HPC_SERVICE" ||
                            it == "TANDEM_V2_MC_SERVICE" ||
                            it == "TANDEM_V1_MC_SERVICE"
                    } ||
                    serviceUuids.contains(
                        dev.ignotus.openbuds.protocol.qcy.QcyGatt.SERVICE_UUID.toString().lowercase()
                    ),
                sonyAd = sonyAd,
            )
            log(
                "BLE result callbackType=$callbackType name=${found.name} address=${found.address} " +
                    "rssi=${found.rssi} services=[$serviceUuids] manufacturer=[$manufacturerData] " +
                    "serviceData=[$serviceData] sonyAd=${sonyAd?.summary.orEmpty()} raw=${sonyAd?.raw.orEmpty()}"
            )
            if (SonyDeviceMatcher.isHeadphoneCandidate(name) || found.isLikelyControlEndpoint || sonyAd != null) {
                listener.onDeviceFound(found)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            listener.onScanStateChanged(false)
            log("BLE scan failed errorCode=$errorCode")
            listener.onBluetoothUnavailable("BLE scan failed: $errorCode")
        }
    }

    fun startScan(strictSonyServiceFilter: Boolean) {
        if (!hasScanPermission()) {
            listener.onBluetoothUnavailable("Bluetooth scan permission is missing")
            return
        }
        val a = adapter
        val s = scanner
        if (s == null || a?.isEnabled != true) {
            listener.onBluetoothUnavailable("Bluetooth is disabled or unavailable")
            return
        }
        log("Start discovery strictServiceFilter=$strictSonyServiceFilter")
        enumerateKnownDevices(a)

        val filters = emptyList<ScanFilter>()
        if (strictSonyServiceFilter) {
            log(
                "Strict Sony service filter requested, but Sony official discovery uses an unfiltered " +
                    "scan plus manufacturer-data parsing; keeping filters empty."
            )
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanning = true
        listener.onScanStateChanged(true)
        log("BLE scan starting filters=${filters.size}")
        s.startScan(filters, settings, scanCallback)
    }

    fun stopScan() {
        if (!scanning || !hasScanPermission()) return
        scanner?.stopScan(scanCallback)
        scanning = false
        listener.onScanStateChanged(false)
    }

    // ── Known device enumeration ───────────────────────────────

    @SuppressLint("MissingPermission")
    private fun enumerateKnownDevices(adapter: BluetoothAdapter) {
        if (!hasConnectPermission()) {
            log("Known-device enumeration skipped: BLUETOOTH_CONNECT permission is missing")
            return
        }

        val bonded = adapter.bondedDevices.orEmpty()
        log("Bonded devices count=${bonded.size}")
        bonded.forEach { device ->
            recordKnownDevice("bonded", device, rssi = 0)
        }

        runCatching {
            bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
        }.onSuccess { devices ->
            log("Connected GATT devices count=${devices.size}")
            devices.forEach { device ->
                recordKnownDevice("connected-gatt", device, rssi = 0)
            }
        }.onFailure {
            log("Connected GATT lookup failed: ${it.message}")
        }

        requestProfileDevices(adapter, BluetoothProfile.A2DP, "connected-a2dp")
        requestProfileDevices(adapter, BluetoothProfile.HEADSET, "connected-headset")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            requestProfileDevices(adapter, BluetoothProfile.HEARING_AID, "connected-hearing-aid")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestProfileDevices(
        adapter: BluetoothAdapter,
        profile: Int,
        source: String,
    ) {
        val serviceListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
                val devices = proxy.connectedDevices.orEmpty()
                log("$source devices count=${devices.size}")
                devices.forEach { device ->
                    recordKnownDevice(source, device, rssi = 0)
                }
                adapter.closeProfileProxy(profileId, proxy)
            }

            override fun onServiceDisconnected(profileId: Int) {
                log("$source profile disconnected id=$profileId")
            }
        }
        val requested = adapter.getProfileProxy(context, serviceListener, profile)
        log("$source profile proxy requested=$requested")
    }

    @SuppressLint("MissingPermission")
    private fun recordKnownDevice(source: String, device: BluetoothDevice, rssi: Int) {
        val name = safeDeviceName(device)
        val uuids = device.uuids?.joinToString { it.uuid.toString() }.orEmpty()
        log(
            "Known device source=$source name=${name ?: "<unknown>"} address=${device.address} " +
                "type=${device.type} bond=${device.bondState} uuids=[$uuids]"
        )
        if (!SonyDeviceMatcher.isHeadphoneCandidate(name)) return

        listener.onDeviceFound(
            DiscoveredSonyDevice(
                name = name ?: "Sony audio device",
                address = device.address,
                rssi = rssi,
                source = source,
                bluetoothType = device.type,
                advertisedServices = device.uuids?.map { it.uuid.toString() }.orEmpty(),
                isLikelyControlEndpoint = device.type == BluetoothDevice.DEVICE_TYPE_LE ||
                    device.type == BluetoothDevice.DEVICE_TYPE_DUAL ||
                    name?.startsWith("LE_", ignoreCase = true) == true,
            )
        )
    }

    // ── Helpers ────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String? =
        if (hasConnectPermission()) device.name else null

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun log(message: String) {
        Log.i("SonyBleScanner", message)
        listener.onLog(message)
    }
}

// ── ScanRecord extensions (defined here for internal use) ─────

private fun android.bluetooth.le.ScanRecord.manufacturerSummary(): String {
    val data = manufacturerSpecificData ?: return ""
    return (0 until data.size()).joinToString { index ->
        val id = data.keyAt(index)
        val bytes = data.valueAt(index)
        "0x${id.toString(16)}:${bytes.hexString()}"
    }
}

private fun android.bluetooth.le.ScanRecord.serviceDataSummary(): String =
    serviceData?.entries.orEmpty().joinToString { (uuid, bytes) ->
        "${uuid.uuid}:${bytes.hexString()}"
    }

private fun android.bluetooth.le.ScanRecord.sonyAudioAdvertisement(): SonyAudioAdvertisement? {
    val fromRawRecord = SonyAudioAdParser.extractSonyAudioManufacturerPayloads(bytes ?: byteArrayOf())
        .firstNotNullOfOrNull { SonyAudioAdParser.parseSonyAudioV2Advertisement(it) }
    if (fromRawRecord != null) return fromRawRecord

    val direct = manufacturerSpecificData?.get(SonyAudioAdParser.SONY_AUDIO_MANUFACTURER_ID)
    return direct?.let { SonyAudioAdParser.parseSonyAudioV2Advertisement(it) }
}
