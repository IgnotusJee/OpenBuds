package dev.ignotus.openbuds.ble

import android.bluetooth.BluetoothDevice
import dev.ignotus.openbuds.ble.sony.SonyAudioAdvertisement

/**
 * A headphone device discovered through BLE scan, bonded-device enumeration,
 * or manual connection.
 */
data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val source: String = "unknown",
    val bluetoothType: Int = BluetoothDevice.DEVICE_TYPE_UNKNOWN,
    val advertisedServices: List<String> = emptyList(),
    val isLikelyControlEndpoint: Boolean = false,
    val sonyAd: SonyAudioAdvertisement? = null,
)
