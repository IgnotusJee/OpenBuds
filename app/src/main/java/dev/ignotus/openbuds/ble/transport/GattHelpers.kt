package dev.ignotus.openbuds.ble.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import java.util.UUID

/**
 * Reusable low-level BLE GATT primitives.
 *
 * These are brand-agnostic helpers for characteristic read/write, CCCD
 * notification setup, and descriptor writes. Higher-level transports
 * ([GattTransport]) and brand adapters (SonyBleClient) compose these
 * instead of duplicating Android-version-specific GATT calls.
 */
object GattHelpers {

    /**
     * Write [value] to [characteristic] on [gatt].
     *
     * Handles the Tiramisu (API 33+) vs. pre-Tiramisu API split:
     *   - API ≥33: `gatt.writeCharacteristic(char, value, WRITE_TYPE_DEFAULT)`
     *   - API <33: deprecated `char.value = value; char.writeType = ... ; gatt.writeCharacteristic(char)`
     */
    @SuppressLint("MissingPermission")
    fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }

    /**
     * Write [value] to [descriptor] on [gatt].
     */
    @SuppressLint("MissingPermission")
    fun writeDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }

    /**
     * Enable notifications for [characteristic] on [gatt].
     *
     * Writes the CCCD descriptor with ENABLE_NOTIFICATION_VALUE.
     * Returns false if the characteristic lacks a CCCD descriptor or
     * if setCharacteristicNotification fails.
     *
     * @param cccdUuid UUID of the Client Characteristic Configuration Descriptor
     *                 (default: standard 00002902-...).
     */
    @SuppressLint("MissingPermission")
    fun enableNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        cccdUuid: UUID = STANDARD_CCCD_UUID,
    ): Boolean {
        val notificationSet = gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(cccdUuid)
        if (!notificationSet || descriptor == null) {
            return false
        }
        return writeDescriptor(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
    }

    /**
     * Request the BLE GATT connection to negotiate [mtu].
     *
     * Returns true if the request was accepted by the stack (asynchronous;
     * completion is reported via [BluetoothGattCallback.onMtuChanged]).
     *
     * On pre-Lollipop devices returns false (MTU negotiation not supported).
     */
    @SuppressLint("MissingPermission")
    fun requestMtu(gatt: BluetoothGatt, mtu: Int): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && gatt.requestMtu(mtu)

    /** Standard CCCD UUID used by most BLE devices. */
    val STANDARD_CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
