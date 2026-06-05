package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import dev.ignotus.openbuds.integration.milink.normalizeMac
import java.util.UUID

object XiaomiBluetoothTraceConfig {
    const val TARGET_PACKAGE = "com.xiaomi.bluetooth"
    const val TRACE_ENABLE_PROPERTY = "debug.openbuds.xiaomi_bt_trace_enable"
    const val TRACE_MAC_ALLOWLIST_PROPERTY = "debug.openbuds.xiaomi_bt_trace_macs"
    const val TRACE_SAMPLE_MS_PROPERTY = "debug.openbuds.xiaomi_bt_trace_sample_ms"
    const val SPP_PROBE_ENABLE_PROPERTY = "debug.openbuds.xiaomi_bt_spp_probe_enable"
    const val SPP_PROBE_MAC_PROPERTY = "debug.openbuds.xiaomi_bt_spp_probe_mac"
    const val SPP_PROBE_MODE_PROPERTY = "debug.openbuds.xiaomi_bt_spp_probe_mode"
    const val SPP_PROBE_UUID_PROPERTY = "debug.openbuds.xiaomi_bt_spp_probe_uuid"
    const val SPP_PROXY_ENABLE_PROPERTY = "debug.openbuds.xiaomi_bt_spp_proxy_enable"
    const val SPP_PROXY_MAC_PROPERTY = "debug.openbuds.xiaomi_bt_spp_proxy_mac"
    const val SPP_PROXY_TRANSPORT_PROPERTY = "debug.openbuds.xiaomi_bt_spp_proxy_transport"
    const val SPP_PROXY_COMMAND_ENABLE_PROPERTY = "debug.openbuds.xiaomi_bt_spp_proxy_command_enable"
    const val PC_REGISTER_PACKAGE_PROPERTY = "debug.openbuds.xiaomi_bt_pc_register_package"
    const val PC_REGISTER_ACTION_PROPERTY = "debug.openbuds.xiaomi_bt_pc_register_action"
    const val DEFAULT_PC_REGISTER_PACKAGE = "com.mi.health"
    const val DEFAULT_PC_REGISTER_ACTION = "dev.ignotus.openbuds.SONY_SPP_PROXY"
    const val DEFAULT_SAMPLE_MS = 2_000L

    fun shouldInstallForPackage(packageName: String): Boolean =
        packageName == TARGET_PACKAGE && (isTraceEnabled() || isSppProbeEnabled() || isSppProxyEnabled())

    fun isTraceEnabled(): Boolean =
        readSystemProp(TRACE_ENABLE_PROPERTY, "false").equals("true", ignoreCase = true)

    fun isSppProbeEnabled(): Boolean =
        readSystemProp(SPP_PROBE_ENABLE_PROPERTY, "false").equals("true", ignoreCase = true)

    fun isSppProxyEnabled(): Boolean =
        readSystemProp(SPP_PROXY_ENABLE_PROPERTY, "false").equals("true", ignoreCase = true)

    fun isSppProxyCommandEnabled(): Boolean =
        readSystemProp(SPP_PROXY_COMMAND_ENABLE_PROPERTY, "false").equals("true", ignoreCase = true)

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

    fun sppProbeTargetMac(): String? =
        readSystemProp(SPP_PROBE_MAC_PROPERTY, "").normalizeMac()

    fun sppProbeMode(): SonySppProbeMode =
        parseSppProbeMode(readSystemProp(SPP_PROBE_MODE_PROPERTY, SonySppProbeMode.CONNECT.propertyValue))

    fun sppProbeUuid(): SonySppProbeUuid =
        parseSppProbeUuid(readSystemProp(SPP_PROBE_UUID_PROPERTY, SonySppProbeUuid.AUTO.propertyValue))

    fun sppProxyTargetMac(): String? =
        readSystemProp(SPP_PROXY_MAC_PROPERTY, "").normalizeMac()

    fun sppProxyTransport(): MiuiSppProxyTransport =
        parseSppProxyTransport(
            readSystemProp(SPP_PROXY_TRANSPORT_PROPERTY, MiuiSppProxyTransport.PC.propertyValue),
        )

    fun pcRegisterPackage(): String =
        readSystemProp(PC_REGISTER_PACKAGE_PROPERTY, DEFAULT_PC_REGISTER_PACKAGE)
            .trim()
            .ifBlank { DEFAULT_PC_REGISTER_PACKAGE }

    fun pcRegisterAction(): String =
        readSystemProp(PC_REGISTER_ACTION_PROPERTY, DEFAULT_PC_REGISTER_ACTION)
            .trim()
            .ifBlank { DEFAULT_PC_REGISTER_ACTION }

    fun parseSppProbeMode(raw: String): SonySppProbeMode =
        SonySppProbeMode.entries.firstOrNull { it.propertyValue.equals(raw.trim(), ignoreCase = true) }
            ?: SonySppProbeMode.CONNECT

    fun parseSppProbeUuid(raw: String): SonySppProbeUuid {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed.equals(SonySppProbeUuid.AUTO.propertyValue, ignoreCase = true)) {
            return SonySppProbeUuid.AUTO
        }
        return SonySppProbeUuid.EXPLICIT_UUIDS.firstOrNull {
            it.uuid.toString().equals(trimmed, ignoreCase = true)
        } ?: SonySppProbeUuid.AUTO
    }

    fun parseSppProxyTransport(raw: String): MiuiSppProxyTransport =
        MiuiSppProxyTransport.entries.firstOrNull {
            it.propertyValue.equals(raw.trim(), ignoreCase = true)
        } ?: MiuiSppProxyTransport.PC

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

enum class SonySppProbeMode(val propertyValue: String) {
    CONNECT("connect"),
    READONLY("readonly"),
    WRITE("write"),
}

enum class MiuiSppProxyTransport(val propertyValue: String) {
    PC("pc"),
    DIRECT("direct"),
}

sealed class SonySppProbeUuid(val propertyValue: String) {
    data object AUTO : SonySppProbeUuid("auto")
    data class Explicit(val uuid: UUID) : SonySppProbeUuid(uuid.toString())

    companion object {
        val MDR_UUID_1: Explicit =
            Explicit(UUID.fromString("956c7b26-d49a-4ba8-b03f-b17d393cb6e2"))
        val MDR_UUID_2: Explicit =
            Explicit(UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba"))
        val EXPLICIT_UUIDS: List<Explicit> = listOf(MDR_UUID_1, MDR_UUID_2)
    }
}
