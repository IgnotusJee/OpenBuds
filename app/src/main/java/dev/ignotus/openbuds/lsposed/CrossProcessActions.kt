package dev.ignotus.openbuds.lsposed

object CrossProcessActions {
    // System process → App process (via explicit broadcast)
    const val ACTION_QUERY_DEVICE_MAC = "dev.ignotus.openbuds.action.QUERY_DEVICE_MAC"
    const val ACTION_SHOW_QUICK_POPUP = "dev.ignotus.openbuds.action.SHOW_QUICK_POPUP"

    // App process → System process (response)
    const val ACTION_DEVICE_MAC_RECEIVED = "dev.ignotus.openbuds.action.DEVICE_MAC_RECEIVED"

    // App process → com.xiaomi.bluetooth process (HyperOS notification)
    const val ACTION_UPDATE_HYPEROS_NOTIFICATION = "dev.ignotus.openbuds.action.UPDATE_HYPEROS_NOTIFICATION"
    const val ACTION_CANCEL_HYPEROS_NOTIFICATION = "dev.ignotus.openbuds.action.CANCEL_HYPEROS_NOTIFICATION"

    // Bundle extras
    const val EXTRA_DEVICE_MAC = "device_mac"
    const val EXTRA_DEVICE_ID = "device_id"
    const val EXTRA_BATTERY_PARAMS = "battery_params"
    const val EXTRA_DEVICE_NAME = "device_name"
    const val EXTRA_BATTERY_SINGLE = "battery_single"
    const val EXTRA_BATTERY_LEFT = "battery_left"
    const val EXTRA_BATTERY_RIGHT = "battery_right"
    const val EXTRA_BATTERY_CRADLE = "battery_cradle"
    const val EXTRA_IS_CONNECTED = "is_connected"

    // System packages that may host our LSPosed module and are allowed
    // to send cross-process broadcasts to SystemIntegrationReceiver.
    val ALLOWED_CALLER_PACKAGES = setOf(
        "com.android.bluetooth",
        "com.android.systemui",
        "com.xiaomi.bluetooth",
    )
}
