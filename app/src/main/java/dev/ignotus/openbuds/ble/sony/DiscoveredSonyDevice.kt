package dev.ignotus.openbuds.ble.sony

import android.bluetooth.BluetoothDevice

/**
 * A headphone device discovered through BLE scan, bonded-device enumeration,
 * or manual connection.
 */
data class DiscoveredSonyDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val source: String = "unknown",
    val bluetoothType: Int = BluetoothDevice.DEVICE_TYPE_UNKNOWN,
    val advertisedServices: List<String> = emptyList(),
    val isLikelyControlEndpoint: Boolean = false,
    val sonyAd: SonyAudioAdvertisement? = null,
)
