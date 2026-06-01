package dev.ignotus.openbuds.ble.sony

import java.util.UUID

internal const val SONY_ADAPTER_ID = "sony-tandem"

/**
 * Sony Tandem transport channels. This is intentionally brand-private; public
 * repository/transport APIs exchange raw protocol bytes and opaque source keys.
 */
enum class SonyChannel(val sourceKey: String) {
    SPP_MDR("SPP_MDR"),
    GATT_V2_HPC("GATT_V2_HPC"),
    GATT_V2_MC("GATT_V2_MC"),
    GATT_V1_MC("GATT_V1_MC"),
    ;

    companion object {
        fun fromSourceKey(sourceKey: String): SonyChannel? =
            entries.firstOrNull { it.sourceKey == sourceKey }

        /**
         * Maps a Sony Tandem GATT service UUID to its logical channel.
         * Migrated from the former TandemChannel.fromServiceUuid().
         */
        fun fromServiceUuid(uuid: UUID): SonyChannel? {
            val v2Hpc = UUID.fromString("5b833e20-6bc7-4802-8e9a-723ceca4bd8f")
            val v2Mc = UUID.fromString("5b833e21-6bc7-4802-8e9a-723ceca4bd8f")
            val v1Mc = UUID.fromString("5b833e23-6bc7-4802-8e9a-723ceca4bd8f")
            return when (uuid) {
                v2Hpc -> GATT_V2_HPC
                v2Mc -> GATT_V2_MC
                v1Mc -> GATT_V1_MC
                else -> null
            }
        }
    }
}
