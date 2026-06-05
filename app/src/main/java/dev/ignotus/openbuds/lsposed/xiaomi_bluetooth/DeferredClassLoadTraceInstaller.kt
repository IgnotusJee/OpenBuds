package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import dev.ignotus.openbuds.lsposed.ModuleMain
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class DeferredClassLoadTraceInstaller(
    private val fastConnectTraceHook: FastConnectTraceHook? = null,
    private val notificationTraceHook: NotificationTraceHook? = null,
    private val peripheralTraceHook: PeripheralTraceHook? = null,
    private val mmaTraceHook: MmaTraceHook? = null,
    private val onLoadedClass: ((String, Class<*>) -> Unit)? = null,
    private val logger: XiaomiBluetoothTraceLogger,
) {
    private val installedClassNames = ConcurrentHashMap.newKeySet<String>()

    fun install() {
        hookLoadClass(String::class.java)
        hookLoadClass(String::class.java, Boolean::class.javaPrimitiveType)
    }

    private fun hookLoadClass(vararg parameterTypes: Class<*>?) {
        val method = runCatching {
            ClassLoader::class.java.getDeclaredMethod("loadClass", *parameterTypes)
                .also { it.isAccessible = true }
        }.onFailure {
            logger.warn(
                event = "deferred_class_loader_missing",
                details = "ClassLoader.loadClass(${parameterTypes.joinToString { type -> type?.simpleName.orEmpty() }})",
                error = it,
            )
        }.getOrNull() ?: return

        hookLoadClass(method)
    }

    private fun hookLoadClass(method: Method) {
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    runCatching {
                        val className = chain.args.firstOrNull() as? String ?: return@runCatching
                        val clazz = result as? Class<*> ?: return@runCatching
                        installLoadedClass(className, clazz)
                    }.onFailure { logger.warn("deferred_class_load_error", "ClassLoader.${method.name}", it) }
                    return result
                }
            })
        logger.methodHooked(ClassLoader::class.java.name, method.signatureLabel())
    }

    private fun installLoadedClass(className: String, clazz: Class<*>) {
        if (className !in targetClassNames) return
        if (!installedClassNames.add(className)) return

        val installed =
            fastConnectTraceHook?.installLoadedClass(clazz) == true ||
                notificationTraceHook?.installLoadedClass(clazz) == true ||
                peripheralTraceHook?.installLoadedClass(clazz) == true ||
                mmaTraceHook?.installLoadedClass(clazz) == true

        onLoadedClass?.invoke(className, clazz)

        logger.info(
            event = "deferred_hook_install",
            mac = null,
            details = "class=$className installed=$installed loader=${clazz.classLoader?.javaClass?.name}",
            force = true,
        )
    }

    companion object {
        val targetClassNames: Set<String> =
            FastConnectTraceHook.targetClassNames +
                NotificationTraceHook.targetClassNames +
                PeripheralTraceHook.targetClassNames +
                MmaTraceHook.targetClassNames +
                SonySppProxy.targetClassNames
    }
}

private fun Method.signatureLabel(): String =
    "$name(${parameterTypes.joinToString { it.simpleName }})"
