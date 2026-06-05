package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import dev.ignotus.openbuds.lsposed.ModuleMain
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class PeripheralTraceHook(
    private val classLoader: ClassLoader,
    private val logger: XiaomiBluetoothTraceLogger,
) {
    private val installedClassNames = ConcurrentHashMap.newKeySet<String>()

    fun install() {
        targetClassNames.forEach { installByName(it) }
    }

    fun installLoadedClass(clazz: Class<*>): Boolean =
        when (clazz.name) {
            PERIPHERAL_SERVICE -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookPeripheralConnectionService(clazz)
                true
            }
            PC_REGISTER_MANAGER -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookPcRegisterManager(clazz)
                true
            }
            GATT_PERIPHERAL -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookGattPeripheral(clazz)
                true
            }
            SPP_PERIPHERAL -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookSppPeripheral(clazz)
                true
            }
            else -> false
        }

    private fun installByName(className: String) {
        val clazz = classLoader.loadTargetClass(className, logger) ?: return
        installLoadedClass(clazz)
    }

    private fun hookPeripheralConnectionService(clazz: Class<*>) {
        listOf(
            "registerPCService",
            "sendData",
            "writeCharacteristic",
            "readCharacteristic",
            "writeDescriptor",
            "requestMTU",
            "setCharacteristicNotification",
            "discoverServices",
        ).forEach { name ->
            hookAfter(clazz, name) { method, thisObject, args, result ->
                logger.info(
                    event = "peripheral_service_${method.name}",
                    mac = logger.macFromArgs(args) ?: logger.macFromAny(thisObject),
                    details = "peripheral_path_seen=true args=${logger.describeArgs(args)} result=${logger.describe(result)}",
                    force = true,
                )
            }
        }
    }

    private fun hookPcRegisterManager(clazz: Class<*>) {
        listOf(
            "registerPCService",
            "connectApp",
            "disconnectApp",
            "reConnectApp",
        ).forEach { name ->
            hookAfter(clazz, name) { method, thisObject, args, result ->
                logger.info(
                    event = "peripheral_register_${method.name}",
                    mac = logger.macFromArgs(args) ?: logger.macFromAny(thisObject),
                    details = "peripheral_path_seen=true args=${logger.describeArgs(args)} result=${logger.describe(result)}",
                    force = true,
                )
            }
        }
    }

    private fun hookGattPeripheral(clazz: Class<*>) {
        listOf(
            "connect",
            "disconnect",
            "discoverServices",
            "writeCharacteristic",
            "readCharacteristic",
            "writeDescriptor",
            "requestMTU",
            "setCharacteristicNotification",
            "onCharacteristicChanged",
        ).forEach { name ->
            hookAfter(clazz, name) { method, thisObject, args, result ->
                val details = if (method.name == "onCharacteristicChanged") {
                    "peripheral_path_seen=true ${logger.characteristicSummary(args.getOrNull(1))} result=${logger.describe(result)}"
                } else {
                    "peripheral_path_seen=true args=${logger.describeArgs(args)} result=${logger.describe(result)}"
                }
                logger.info(
                    event = "gatt_${method.name}",
                    mac = logger.macFromArgs(args) ?: logger.macFromAny(thisObject),
                    details = details,
                    force = true,
                )
            }
        }
    }

    private fun hookSppPeripheral(clazz: Class<*>) {
        listOf("connect", "sendData").forEach { name ->
            hookAfter(clazz, name) { method, thisObject, args, result ->
                val mac = logger.macFromArgs(args) ?: logger.macFromAny(thisObject)
                logger.info(
                    event = "spp_${method.name}",
                    mac = mac,
                    details = "peripheral_path_seen=true args=${logger.describeArgs(args)} result=${logger.describe(result)}",
                    force = true,
                )
            }
        }
    }

    private fun hookAfter(
        clazz: Class<*>,
        methodName: String,
        onAfter: (Method, Any?, List<Any?>, Any?) -> Unit,
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
                        runCatching { onAfter(method, chain.thisObject, chain.args, result) }
                            .onFailure { logger.warn("trace_error", "${clazz.name}.${method.name}", it) }
                        return result
                    }
                })
            logger.methodHooked(clazz.name, method.name)
        }
    }

    companion object {
        const val PERIPHERAL_SERVICE = "com.xiaomi.bluetooth.peripheral.MiuiPeripheralConnectionServiceReal"
        const val PC_REGISTER_MANAGER = "com.xiaomi.bluetooth.peripheral.MiuiPCRegisterManager"
        const val GATT_PERIPHERAL = "com.xiaomi.bluetooth.peripheral.MiuiGattPeripheral"
        const val SPP_PERIPHERAL = "com.xiaomi.bluetooth.peripheral.MiuiSppPeripheral"

        val targetClassNames: Set<String> = setOf(
            PERIPHERAL_SERVICE,
            PC_REGISTER_MANAGER,
            GATT_PERIPHERAL,
            SPP_PERIPHERAL,
        )
    }
}
