package dev.ignotus.openbuds.lsposed

import io.github.libxposed.api.XposedInterface

class SystemUiHook(private val classLoader: ClassLoader) {
    private val targets = listOf(
        "com.android.systemui.shared.plugins.PluginInstance" to listOf("loadPlugin"),
    )

    fun probe() {
        for ((className, methods) in targets) {
            try {
                val cls = classLoader.loadClass(className)
                ProbeResultCache.markFound(className)
                for (methodName in methods) {
                    val found = cls.declaredMethods.any { m -> m.name == methodName }
                        || cls.methods.any { m -> m.name == methodName }
                    if (found) {
                        ProbeResultCache.markMethodFound(className, methodName)
                    } else {
                        ProbeResultCache.markMethodNotFound(className, methodName)
                    }
                }
            } catch (_: ClassNotFoundException) {
                ProbeResultCache.markNotFound(className)
            }
        }
    }

    fun hook() {
        hookPluginInstance()
    }

    private fun hookPluginInstance() {
        try {
            val pluginInstanceClass = classLoader.loadClass(
                "com.android.systemui.shared.plugins.PluginInstance"
            )
            val loadPluginMethod = pluginInstanceClass.declaredMethods
                .firstOrNull { it.name == "loadPlugin" && it.parameterTypes.isEmpty() }
                ?: pluginInstanceClass.declaredMethods
                    .firstOrNull { it.name == "loadPlugin" }
                ?: return

            ModuleMain.instance.hook(loadPluginMethod)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        try {
                            val instance = chain.thisObject ?: return result
                            val pkgName = instance.javaClass
                                .getDeclaredMethod("getPackage")
                                .invoke(instance) as? String

                            if (pkgName == "miui.systemui.plugin") {
                                val factory = instance.javaClass
                                    .getDeclaredField("mPluginFactory")
                                    .apply { isAccessible = true }
                                    .get(instance)
                                val classLoaderFactory = factory?.javaClass
                                    ?.getDeclaredField("mClassLoaderFactory")
                                    ?.apply { isAccessible = true }
                                    ?.get(factory)
                                val pluginCl = classLoaderFactory?.javaClass
                                    ?.getDeclaredMethod("get")
                                    ?.invoke(classLoaderFactory) as? ClassLoader

                                if (pluginCl != null && pluginCl != classLoader) {
                                    ModuleMain.instance.log("SystemUiHook: init DeviceCardHook with plugin ClassLoader")
                                    DeviceCardHook(pluginCl).hook()
                                    pluginClassLoader = pluginCl
                                }
                            }
                        } catch (e: Exception) {
                            ModuleMain.instance.log("SystemUiHook: plugin ClassLoader extraction failed: ${e.message}")
                        }
                        return result
                    }
                })
            ModuleMain.instance.log("SystemUiHook: registered hook on PluginInstance.loadPlugin")
        } catch (e: Exception) {
            ModuleMain.instance.log("SystemUiHook: PluginInstance.loadPlugin not found — ${e.message}")
        }
    }

    companion object {
        @Volatile
        var pluginClassLoader: ClassLoader? = null
            private set
    }
}
