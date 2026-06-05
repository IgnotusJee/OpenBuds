package dev.ignotus.openbuds.integration.milink

import android.os.Bundle

object MilinkTransportProxyCoordinator {
    fun snapshotFor(
        mac: String,
        appSnapshots: Map<String, MilinkDeviceSnapshot>,
        proxySnapshots: Map<String, MilinkDeviceSnapshot>,
        activeProxyMacs: Set<String>,
    ): MilinkDeviceSnapshot? {
        val normalized = mac.normalizeMac() ?: return null
        return if (normalized in activeProxyMacs) {
            proxySnapshots[normalized] ?: appSnapshots[normalized]
        } else {
            appSnapshots[normalized]
        }
    }

    fun mergedSnapshots(
        appSnapshots: Collection<MilinkDeviceSnapshot>,
        proxySnapshots: Collection<MilinkDeviceSnapshot>,
        activeProxyMacs: Set<String>,
    ): List<MilinkDeviceSnapshot> {
        val merged = appSnapshots.associateBy { it.mac }.toMutableMap()
        proxySnapshots.forEach { snapshot ->
            if (snapshot.mac in activeProxyMacs) {
                merged[snapshot.mac] = snapshot
            }
        }
        return merged.values.sortedBy { it.mac }
    }

    fun shouldRouteNoiseCommandToProxy(
        mac: String,
        activeProxyMacs: Set<String>,
        commandEnabled: Boolean,
        action: MilinkBridgeCommandAction?,
    ): Boolean =
        mac.normalizeMac() in activeProxyMacs &&
            commandEnabled &&
            action is MilinkBridgeCommandAction.SetNoiseControl

    fun commandEvaluationSnapshotFor(
        mac: String,
        command: MilinkBridgeCommandEnvelope,
        appSnapshots: Map<String, MilinkDeviceSnapshot>,
        proxySnapshots: Map<String, MilinkDeviceSnapshot>,
        activeProxyMacs: Set<String>,
        proxyCommandEnabled: Boolean,
    ): MilinkDeviceSnapshot? {
        val normalized = mac.normalizeMac() ?: return null
        val mayUseProxyForCommand =
            command.commandType == MilinkBridgeContract.COMMAND_SET_NOISE_CONTROL &&
                normalized in activeProxyMacs &&
                proxyCommandEnabled
        return if (mayUseProxyForCommand) {
            proxySnapshots[normalized] ?: appSnapshots[normalized]
        } else {
            appSnapshots[normalized]
        }
    }

    fun acceptedBundle(requestId: String? = null): Bundle =
        Bundle().apply {
            putBoolean(MilinkBridgeContract.KEY_SUCCESS, true)
            putString(MilinkBridgeContract.KEY_REASON, MilinkBridgeContract.REASON_OK)
            requestId?.let { putString(MilinkBridgeContract.KEY_REQUEST_ID, it) }
        }

    fun rejectedBundle(reason: String, requestId: String? = null): Bundle =
        Bundle().apply {
            putBoolean(MilinkBridgeContract.KEY_SUCCESS, false)
            putString(MilinkBridgeContract.KEY_REASON, reason)
            requestId?.let { putString(MilinkBridgeContract.KEY_REQUEST_ID, it) }
        }

    fun isProxyCommandPropertyEnabled(
        propertyReader: (String, String) -> String = MilinkSystemPropertyReader::get,
    ): Boolean =
        propertyReader(PROXY_COMMAND_ENABLE_PROPERTY, "false")
            .equals("true", ignoreCase = true)

    const val PROXY_COMMAND_ENABLE_PROPERTY = "debug.openbuds.xiaomi_bt_spp_proxy_command_enable"
}

private object MilinkSystemPropertyReader {
    fun get(key: String, defaultValue: String): String =
        runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, defaultValue) as? String ?: defaultValue
        }.getOrDefault(defaultValue)
}
