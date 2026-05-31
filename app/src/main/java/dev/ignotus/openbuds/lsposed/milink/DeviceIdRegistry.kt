package dev.ignotus.openbuds.lsposed.milink

/**
 * Stable MiLink deviceId templates for the AirPods adapter path.
 *
 * MiLink resolves a headset icon/type by passing the deviceId through
 * `p321o9.AbstractC14649a.m51162b(deviceId)`. M2 intentionally uses a Xiaomi
 * generic earbud id that resolves to type 0, avoiding AirPods-specific type
 * 5/6 branches.
 */
object DeviceIdRegistry {
    const val GENERIC_EARBUD_DEVICE_ID = "01010101"
    const val OPEN_WEAR_DEVICE_ID = "01013400"

    const val HEADSET_TYPE_GENERIC_EARBUD = 0
    const val HEADSET_TYPE_OPEN_WEAR = 4

    val genericEarbudTemplate = DeviceIdTemplate(
        deviceId = GENERIC_EARBUD_DEVICE_ID,
        expectedHeadsetType = HEADSET_TYPE_GENERIC_EARBUD,
        label = "Air 2s generic earbud template",
    )

    val openWearTemplate = DeviceIdTemplate(
        deviceId = OPEN_WEAR_DEVICE_ID,
        expectedHeadsetType = HEADSET_TYPE_OPEN_WEAR,
        label = "O74 open-wear template",
    )

    fun defaultTemplateForMac(mac: String?): DeviceIdTemplate = genericEarbudTemplate

    fun deviceIdForMac(mac: String?): String = defaultTemplateForMac(mac).deviceId
}

data class DeviceIdTemplate(
    val deviceId: String,
    val expectedHeadsetType: Int,
    val label: String,
)
