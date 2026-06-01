package dev.ignotus.openbuds.ble.sony

/**
 * Connection metadata reported via [SonyBleClientListener.onReady].
 */
data class SonyBleConnectionInfo(
    val mtu: Int = 23,
    val writableValueLength: Int? = null,
    val optimalMtu: Int? = null,
    val transport: String = "GATT_HPC",
)
