package dev.ignotus.openbuds.protocol.qcy

import java.util.UUID

/**
 * QCY BLE GATT service and characteristic UUID definitions.
 * All QCY devices share the same service UUID with different characteristic
 * UUIDs for command/response, battery, EQ, button customization, etc.
 *
 * Reference: com.qcymall.qcylibrary.QCYHeadsetClient (lines 75-80)
 *            com.qcymall.earphonesetup.model.ControlerPanl (lines 24-42)
 */
object QcyGatt {
    private const val UUID_TEMPLATE = "%s-0000-1000-8000-00805F9B34FB"

    val SERVICE_UUID: UUID = service("0000A001")
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = cccd()

    // V2 protocol characteristics (current gen, used by C30S)
    val CHARACTER_SETTING_UUID: UUID = characteristic("00001001")    // Write — main command channel
    val CHARACTER_READSET_UUID: UUID = characteristic("00001002")   // Notify — command response channel
    val CHARACTER_EQ_UUID: UUID = characteristic("0000000B")        // Read/Write/Notify — EQ data
    val CHARACTER_BUTTON_UUID: UUID = characteristic("0000000D")    // Read/Write — button customization
    val CHARACTER_BATTERY_UUID: UUID = characteristic("00000008")   // Notify — battery information
    val CHARACTER_VERSION_UUID: UUID = characteristic("00000007")   // Read/Write — firmware version
    val CHARACTER_SENDTIME_UUID: UUID = characteristic("0000000C")  // Write — time sync
    val CHARACTER_FUNCTION_UUID: UUID = characteristic("0000000F")  // Read/Notify — feature boolean status
    val CHARACTER_LANGUAGE_UUID: UUID = characteristic("00000009")  // Read — voice language

    // V1 protocol characteristics (legacy, individual key function UUIDs)
    // 0x0001-0x000A map to per-gesture key customization
    // ⚠ Some short UUIDs conflict between V1 and V2 (e.g. 0x0008 is both V1_KEY_CR4 and V2_BATTERY)

    fun service(shortHex: String): UUID =
        UUID.fromString(UUID_TEMPLATE.format(shortHex.lowercase()))

    fun characteristic(shortHex: String): UUID =
        UUID.fromString(UUID_TEMPLATE.format(shortHex.lowercase()))

    fun cccd(): UUID =
        UUID.fromString(UUID_TEMPLATE.format("00002902"))

    fun serviceLabel(uuid: UUID): String = when (uuid) {
        SERVICE_UUID -> "QCY_SERVICE"
        else -> uuid.toString()
    }

    fun characteristicLabel(uuid: UUID): String = when (uuid) {
        CHARACTER_SETTING_UUID -> "QCY_SETTING (Write)"
        CHARACTER_READSET_UUID -> "QCY_READSET (Notify)"
        CHARACTER_EQ_UUID -> "QCY_EQ"
        CHARACTER_BUTTON_UUID -> "QCY_BUTTON"
        CHARACTER_BATTERY_UUID -> "QCY_BATTERY"
        CHARACTER_VERSION_UUID -> "QCY_VERSION"
        CHARACTER_SENDTIME_UUID -> "QCY_SENDTIME"
        CHARACTER_FUNCTION_UUID -> "QCY_FUNCTION"
        CHARACTER_LANGUAGE_UUID -> "QCY_LANGUAGE"
        else -> uuid.toString()
    }
}
