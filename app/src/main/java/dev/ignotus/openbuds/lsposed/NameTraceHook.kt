package dev.ignotus.openbuds.lsposed

import android.bluetooth.BluetoothDevice

/**
 * Diagnostic: trace ALL BluetoothServiceClient methods (including obfuscated)
 * for Sony devices, focusing on methods that take/produce String values.
 */
class NameTraceHook(private val classLoader: ClassLoader) {

    private var probed = false
    private var btServiceClientClass: Class<*>? = null

    fun probe(): Boolean {
        if (probed) return btServiceClientClass != null
        probed = true
        btServiceClientClass = try {
            classLoader.loadClass("com.miui.circulate.api.protocol.bluetooth.BluetoothServiceClient")
        } catch (_: Exception) { null }
        return btServiceClientClass != null
    }

    fun hook() {
        val clazz = btServiceClientClass ?: return
        var count = 0
        for (method in clazz.declaredMethods) {
            // Hook ALL methods that take BluetoothDevice (trace all Sony calls)
            val hasBtDevice = method.parameterTypes.any {
                BluetoothDevice::class.java.isAssignableFrom(it)
            }
            if (!hasBtDevice) continue

            // Also check if method takes or returns String (likely name-related)
            val hasString = method.returnType == String::class.java ||
                method.parameterTypes.any { it == String::class.java }

            try {
                ModuleMain.instance.hook(method)
                    .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                        override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                            val device = chain.args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                            val isSony = device != null && MiLinkIdentityHook.matchesSonyPattern(device.name ?: "")
                            val result = chain.proceed()
                            if (isSony) {
                                val stringArgs = chain.args.filter { it is String }.joinToString { "\"$it\"" }
                                val extra = if (stringArgs.isNotEmpty()) " args=[$stringArgs]" else ""
                                log("${method.name}(${device!!.name}) → $result$extra")
                            }
                            return result
                        }
                    })
                count++
            } catch (_: Exception) {}
        }
        log("hooked $count methods")
    }

    companion object {
        fun log(msg: String) {
            android.util.Log.i("OpenBuds", "[NT] $msg")
        }
    }
}
