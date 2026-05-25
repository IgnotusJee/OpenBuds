package dev.ignotus.openbuds.lsposed

class SystemUiHook(private val classLoader: ClassLoader) {
    private val targets = listOf(
        "com.android.systemui.shared.plugins.PluginInstance" to listOf("loadPlugin"),
        "miui.systemui.controlcenter.panel.main.MainPanelController" to listOf("onCreate"),
        "miui.systemui.devicecenter.devices.DeviceInfoWrapper" to listOf("performClicked"),
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
}
