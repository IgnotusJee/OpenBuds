package dev.ignotus.openbuds.lsposed

import android.content.Context
import android.content.IntentFilter
import io.github.libxposed.api.XposedInterface

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

    fun hook() {
        try {
            val cls = classLoader.loadClass(targetClass)
            val constructor = cls.declaredConstructors
                .firstOrNull { it.parameterTypes.size == 2 }
                ?: cls.declaredConstructors.firstOrNull()
                ?: return

            ModuleMain.instance.hook(constructor)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        registerReceiverIfNeeded(chain.thisObject ?: return result)
                        return result
                    }
                })

            ModuleMain.instance.log("XiaomiBluetoothHook: registered hook on $targetClass constructor")
        } catch (e: Exception) {
            ModuleMain.instance.log("XiaomiBluetoothHook hook failed: ${e.message}")
        }
    }

    private fun registerReceiverIfNeeded(instance: Any) {
        if (receiverRegistered) return
        receiverRegistered = true

        try {
            val mContextField = instance.javaClass.getDeclaredField("mContext").apply {
                isAccessible = true
            }
            val context = mContextField.get(instance) as? Context ?: run {
                ModuleMain.instance.log("XiaomiBluetoothHook: mContext field not a Context")
                receiverRegistered = false
                return
            }

            val receiver = HyperOsBatteryNotification()
            val filter = IntentFilter().apply {
                addAction(CrossProcessActions.ACTION_UPDATE_HYPEROS_NOTIFICATION)
                addAction(CrossProcessActions.ACTION_CANCEL_HYPEROS_NOTIFICATION)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            ModuleMain.instance.log("XiaomiBluetoothHook: registered HyperOsBatteryReceiver")
        } catch (e: Exception) {
            ModuleMain.instance.log("Failed to register HyperOS receiver: ${e.message}")
            receiverRegistered = false
        }
    }

    companion object {
        @Volatile
        private var receiverRegistered = false
    }
}
