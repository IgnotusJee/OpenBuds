package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import android.bluetooth.BluetoothDevice
import dev.ignotus.openbuds.lsposed.ModuleMain
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class NotificationTraceHook(
    private val classLoader: ClassLoader,
    private val logger: XiaomiBluetoothTraceLogger,
) {
    private val installedClassNames = ConcurrentHashMap.newKeySet<String>()

    fun install() {
        targetClassNames.forEach { installByName(it) }
    }

    fun installLoadedClass(clazz: Class<*>): Boolean =
        when (clazz.name) {
            NOTIFICATION_API -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookNotificationApi(clazz)
                true
            }
            HEADSET_PLUGIN -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookHeadsetPlugin(clazz)
                true
            }
            DETAIL_NOTIFICATION -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookDetailNotification(clazz)
                true
            }
            else -> false
        }

    private fun installByName(className: String) {
        val clazz = classLoader.loadTargetClass(className, logger) ?: return
        installLoadedClass(clazz)
    }

    private fun hookNotificationApi(clazz: Class<*>) {
        listOf(
            "showNewConnectedToast",
            "showNewConnectNotification",
            "setShowStatusBar",
            "addConnectManager",
        ).forEach { name ->
            hookAfter(clazz, name) { method, args, result ->
                val mac = logger.macFromArgs(args)
                logger.info(
                    event = "notification_api_${method.name}",
                    mac = mac,
                    details = "toast_called=${method.name.startsWith("show")} notification_state_seen=true args=${logger.describeArgs(args)} result=${logger.describe(result)}",
                    force = true,
                )
            }
        }
    }

    private fun hookHeadsetPlugin(clazz: Class<*>) {
        listOf("updateDetailNotification", "dealWithNotificationLogcal").forEach { name ->
            hookAfter(clazz, name) { method, args, result ->
                val device = args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                logger.info(
                    event = "headset_plugin_${method.name}",
                    mac = safeAddress(device),
                    details = "detail_notification_gate_seen=true args=${logger.describeArgs(args)} result=${logger.describe(result)}",
                    force = true,
                )
            }
        }
    }

    private fun hookDetailNotification(clazz: Class<*>) {
        listOf(
            "UpdateHeadsetDetailNotification",
            "getSwitchStatus",
            "checkSupport",
            "getBluetoothDeviceBattery",
        ).forEach { name ->
            hookAfter(clazz, name) { method, args, result ->
                val mac = logger.macFromArgs(args)
                val passed = when (method.name) {
                    "UpdateHeadsetDetailNotification" -> true
                    "getSwitchStatus", "checkSupport" -> result == true
                    "getBluetoothDeviceBattery" -> result is IntArray && result.size >= 3
                    else -> false
                }
                logger.info(
                    event = "detail_notification_${method.name}",
                    mac = mac,
                    details = "detail_notification_gate_passed=$passed args=${logger.describeArgs(args)} result=${logger.describe(result)}",
                    force = true,
                )
            }
        }
    }

    private fun hookAfter(
        clazz: Class<*>,
        methodName: String,
        onAfter: (Method, List<Any?>, Any?) -> Unit,
    ) {
        val methods = clazz.findMethodsByName(methodName)
        if (methods.isEmpty()) {
            logger.methodMissing(clazz.name, methodName)
            return
        }
        methods.forEach { method ->
            ModuleMain.instance.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        runCatching { onAfter(method, chain.args, result) }
                            .onFailure { logger.warn("trace_error", "${clazz.name}.${method.name}", it) }
                        return result
                    }
                })
            logger.methodHooked(clazz.name, method.name)
        }
    }

    private fun safeAddress(device: BluetoothDevice?): String? =
        runCatching { device?.address }.getOrNull()

    companion object {
        const val NOTIFICATION_API = "com.android.bluetooth.ble.app.fastconnect.MiuiBluetoothNotificationApi"
        const val HEADSET_PLUGIN = "com.android.bluetooth.ble.app.headset.plugin.BluetoothHeadsetServicePlugin"
        const val DETAIL_NOTIFICATION = "com.android.bluetooth.ble.app.headset.plugin.MiuiHeadsetOfDetailInfoNotification"

        val targetClassNames: Set<String> = setOf(
            NOTIFICATION_API,
            HEADSET_PLUGIN,
            DETAIL_NOTIFICATION,
        )
    }
}
