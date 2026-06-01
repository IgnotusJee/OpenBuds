package dev.ignotus.openbuds.ble.sony

/**
 * A discovered GATT Tandem endpoint binding a channel to its
 * TO_ACC (write) and FROM_ACC (notify) characteristics.
 */
data class GattTandemEndpoint(
    val channel: SonyChannel,
    val toAcc: android.bluetooth.BluetoothGattCharacteristic,
    val fromAcc: android.bluetooth.BluetoothGattCharacteristic,
)
