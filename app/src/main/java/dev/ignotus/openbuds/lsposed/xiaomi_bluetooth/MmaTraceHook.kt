package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import dev.ignotus.openbuds.lsposed.ModuleMain
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class MmaTraceHook(
    private val classLoader: ClassLoader,
    private val logger: XiaomiBluetoothTraceLogger,
) {
    private val installedClassNames = ConcurrentHashMap.newKeySet<String>()

    fun install() {
        targetClassNames.forEach { installByName(it) }
    }

    fun installLoadedClass(clazz: Class<*>): Boolean =
        when (clazz.name) {
            MMA_SERVICE -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookMethods(clazz, "mma_service", "registerMMAService", "sendMMAServiceData", "setFunctionStatus")
                true
            }
            MMA_REGISTER_MANAGER -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookMethods(
                    clazz,
                    "mma_register",
                    "registerMMAService",
                    "sendMMAServiceData",
                    "onMMAServiceData",
                    "onMMAServiceStatus",
                    "onError",
                )
                true
            }
            MMA_DATA_HANDLER -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookMethods(clazz, "mma_data_handler", "addSendData", "addRecvData")
                true
            }
            else -> false
        }

    private fun installByName(className: String) {
        val clazz = classLoader.loadTargetClass(className, logger) ?: return
        installLoadedClass(clazz)
    }

    private fun hookMethods(clazz: Class<*>, eventPrefix: String, vararg names: String) {
        names.forEach { name ->
            hookAfter(clazz, name) { method, thisObject, args, result ->
                logger.info(
                    event = "${eventPrefix}_${method.name}",
                    mac = logger.macFromArgs(args) ?: logger.macFromAny(thisObject),
                    details = "mma_path_seen=true args=${logger.describeArgs(args)} result=${logger.describe(result)}",
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
        const val MMA_SERVICE = "com.xiaomi.bluetooth.mma.plugin.MiuiMMAService"
        const val MMA_REGISTER_MANAGER = "com.xiaomi.bluetooth.mma.plugin.MiuiMMARegisterManager"
        const val MMA_DATA_HANDLER = "com.xiaomi.bluetooth.mma.plugin.MiuiMMADataHandler"

        val targetClassNames: Set<String> = setOf(
            MMA_SERVICE,
            MMA_REGISTER_MANAGER,
            MMA_DATA_HANDLER,
        )
    }
}
