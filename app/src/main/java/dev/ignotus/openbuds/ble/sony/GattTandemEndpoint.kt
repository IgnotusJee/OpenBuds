package dev.ignotus.openbuds.ble.sony

import dev.ignotus.openbuds.headphones.TandemChannel

/**
 * A discovered GATT Tandem endpoint binding a channel to its
 * TO_ACC (write) and FROM_ACC (notify) characteristics.
 */
data class GattTandemEndpoint(
    val channel: TandemChannel,
    val toAcc: android.bluetooth.BluetoothGattCharacteristic,
    val fromAcc: android.bluetooth.BluetoothGattCharacteristic,
)
