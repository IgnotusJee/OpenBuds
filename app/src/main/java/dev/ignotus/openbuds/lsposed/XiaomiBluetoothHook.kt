package dev.ignotus.openbuds.lsposed

class XiaomiBluetoothHook(private val classLoader: ClassLoader) {
    private val targetClass = "com.android.bluetooth.ble.app.MiuiBluetoothNotification"

    fun probe() {
        try {
            val cls = classLoader.loadClass(targetClass)
            ProbeResultCache.markFound(targetClass)
            for (ctor in cls.declaredConstructors) {
                ProbeResultCache.markMethodFound(targetClass, "ctor(${ctor.parameterTypes.size})")
            }
        } catch (_: ClassNotFoundException) {
            ProbeResultCache.markNotFound(targetClass)
        }
    }
}
