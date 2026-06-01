package dev.ignotus.openbuds.ble.qcy

import dev.ignotus.openbuds.protocol.qcy.QcyGatt
import java.util.UUID

internal const val QCY_ADAPTER_ID = "qcy"

/**
 * QCY GATT source keys, one per brand-private source characteristic.
 */
enum class QcyChannel(val sourceKey: String) {
    /** 0x1001 — command write channel (all writes go here) */
    SETTING_WRITE("SETTING_WRITE"),
    /** 0x1002 — TLV response notifications */
    READSET("READSET"),
    /** 0x0008 — battery notifications / reads */
    BATTERY("BATTERY"),
    /** 0x0007 — firmware version reads */
    VERSION("VERSION"),
    /** 0x000B — raw EQ data reads / notifications */
    EQ_RAW("EQ_RAW"),
    /** 0x000F — boolean feature status (RUER, JIANTING) */
    FUNCTION("FUNCTION"),
    ;

    companion object {
        fun fromSourceKey(sourceKey: String): QcyChannel? =
            entries.firstOrNull { it.sourceKey == sourceKey }

        /**
         * Maps a QCY characteristic UUID to its logical channel.
         * Unrecognised UUIDs return null (caller should log and ignore).
         */
        fun fromCharacteristicUuid(uuid: UUID): QcyChannel? = when (uuid) {
            QcyGatt.CHARACTER_BATTERY_UUID -> BATTERY
            QcyGatt.CHARACTER_VERSION_UUID -> VERSION
            QcyGatt.CHARACTER_READSET_UUID -> READSET
            QcyGatt.CHARACTER_EQ_UUID -> EQ_RAW
            QcyGatt.CHARACTER_FUNCTION_UUID -> FUNCTION
            else -> null
        }
    }
}
