package dev.ignotus.openbuds.lsposed

class BluetoothProcessHook(private val classLoader: ClassLoader) {
    private val targets = listOf(
        "com.android.bluetooth.a2dp.A2dpService" to listOf("handleConnectionStateChanged"),
        "com.android.bluetooth.ble.app.MiuiBluetoothNotification" to emptyList(),
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
