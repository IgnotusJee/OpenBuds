package dev.ignotus.openbuds.lsposed.mitws

/**
 * Stable MiLink MiTWS deviceId templates.
 *
 * M1 uses [templateForConfigValue] to select the active template at runtime
 * via the `debug.openbuds.milink_mitws_device_id` system property.
 * Default is the generic earbud template (`01010101`).
 */
object MiTwsDeviceIdPolicy {
    const val GENERIC_EARBUD_DEVICE_ID = "01010101"
    const val FLORA_EARBUD_DEVICE_ID = "01013201"
    const val O73_FLORA_DEVICE_ID = "020104005A"

    const val HEADSET_TYPE_GENERIC_EARBUD = 0
    const val HEADSET_TYPE_FLORA_EXPERIMENT = 0

    val genericEarbudTemplate = MiTwsDeviceIdTemplate(
        deviceId = GENERIC_EARBUD_DEVICE_ID,
        expectedHeadsetType = HEADSET_TYPE_GENERIC_EARBUD,
        label = "MiTWS generic earbud template",
        experimental = false,
    )

    val floraEarbudTemplate = MiTwsDeviceIdTemplate(
        deviceId = FLORA_EARBUD_DEVICE_ID,
        expectedHeadsetType = HEADSET_TYPE_FLORA_EXPERIMENT,
        label = "MiTWS Flora earbud template",
        experimental = true,
    )

    val o73FloraTemplate = MiTwsDeviceIdTemplate(
        deviceId = O73_FLORA_DEVICE_ID,
        expectedHeadsetType = HEADSET_TYPE_FLORA_EXPERIMENT,
        label = "MiTWS O73 Flora template",
        experimental = true,
    )

    fun defaultTemplateForMac(mac: String?): MiTwsDeviceIdTemplate = genericEarbudTemplate

    fun deviceIdForMac(mac: String?): String = defaultTemplateForMac(mac).deviceId

    fun templateForConfigValue(value: String?): MiTwsDeviceIdTemplate =
        when (value?.trim()?.lowercase()) {
            "flora", FLORA_EARBUD_DEVICE_ID.lowercase() -> floraEarbudTemplate
            "o73", O73_FLORA_DEVICE_ID.lowercase() -> o73FloraTemplate
            else -> genericEarbudTemplate
        }
}

data class MiTwsDeviceIdTemplate(
    val deviceId: String,
    val expectedHeadsetType: Int,
    val label: String,
    val experimental: Boolean,
)
