package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import dev.ignotus.openbuds.integration.milink.normalizeMac

object XiaomiBluetoothTraceConfig {
    const val TARGET_PACKAGE = "com.xiaomi.bluetooth"
    const val TRACE_ENABLE_PROPERTY = "debug.openbuds.xiaomi_bt_trace_enable"
    const val TRACE_MAC_ALLOWLIST_PROPERTY = "debug.openbuds.xiaomi_bt_trace_macs"
    const val TRACE_SAMPLE_MS_PROPERTY = "debug.openbuds.xiaomi_bt_trace_sample_ms"
    const val DEFAULT_SAMPLE_MS = 2_000L

    fun shouldInstallForPackage(packageName: String): Boolean =
        packageName == TARGET_PACKAGE && isTraceEnabled()

    fun isTraceEnabled(): Boolean =
        readSystemProp(TRACE_ENABLE_PROPERTY, "false").equals("true", ignoreCase = true)

    fun sampleWindowMs(): Long =
        parseSampleWindowMs(readSystemProp(TRACE_SAMPLE_MS_PROPERTY, DEFAULT_SAMPLE_MS.toString()))

    fun macAllowlist(): Set<String> =
        parseMacAllowlist(readSystemProp(TRACE_MAC_ALLOWLIST_PROPERTY, ""))

    fun shouldTraceMac(mac: String?): Boolean =
        shouldTraceMac(mac, macAllowlist())

    fun shouldTraceMac(mac: String?, allowlist: Set<String>): Boolean {
        if (allowlist.isEmpty()) return true
        val normalized = mac?.normalizeMac() ?: return false
        return normalized in allowlist
    }

    fun parseMacAllowlist(raw: String): Set<String> =
        raw.split(',', ';', ' ', '\n', '\t')
            .mapNotNull { it.trim().normalizeMac() }
            .toSet()

    fun parseSampleWindowMs(raw: String): Long =
        raw.toLongOrNull()?.coerceAtLeast(0L) ?: DEFAULT_SAMPLE_MS

    fun maskMac(mac: String?): String {
        val normalized = mac?.normalizeMac() ?: return mac.orEmpty()
        return "**:**:**:${normalized.substring(9)}"
    }

    private fun readSystemProp(key: String, default: String): String =
        runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, default) as? String ?: default
        }.getOrDefault(default)
}
