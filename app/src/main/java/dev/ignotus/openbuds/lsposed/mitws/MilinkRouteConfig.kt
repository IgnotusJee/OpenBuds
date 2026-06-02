package dev.ignotus.openbuds.lsposed.mitws

object MilinkRouteConfig {
    const val MITWS_ENABLE_PROPERTY = "debug.openbuds.milink_mitws_enable"
    const val MITWS_DEVICE_ID_PROPERTY = "debug.openbuds.milink_mitws_device_id"
    const val MITWS_MMA_PASSTHROUGH_PROPERTY = "debug.openbuds.milink_mitws_mma_passthrough"

    fun mode(): MilinkRouteMode =
        if (isSystemFacadeEnabled()) MilinkRouteMode.MITWS else MilinkRouteMode.TRACE_ONLY

    fun canUseFacade(bridgeClient: MilinkBridgeClientFacade?): Boolean =
        isSystemFacadeEnabled() && bridgeClient?.adapterEnabled == true

    fun selectedDeviceIdTemplate(): MiTwsDeviceIdTemplate =
        MiTwsDeviceIdPolicy.templateForConfigValue(
            readSystemProp(MITWS_DEVICE_ID_PROPERTY, "generic"),
        )

    fun allowOpenBudsMmaPassthrough(): Boolean =
        readSystemProp(MITWS_MMA_PASSTHROUGH_PROPERTY, "false").equals("true", ignoreCase = true)

    fun isSystemFacadeEnabled(): Boolean =
        readSystemProp(MITWS_ENABLE_PROPERTY, "false").equals("true", ignoreCase = true)

    private fun readSystemProp(key: String, default: String): String =
        runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, default) as? String ?: default
        }.getOrDefault(default)
}

enum class MilinkRouteMode {
    MITWS,
    TRACE_ONLY,
}
