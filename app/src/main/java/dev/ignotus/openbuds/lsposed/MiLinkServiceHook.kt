package dev.ignotus.openbuds.lsposed

/**
 * LSPosed hook for the com.milink.service process.
 *
 * Phases:
 *   1. Identity spoofing — make Sony headphones appear as first-party Xiaomi TWS devices
 *      by hooking BluetoothServiceClient.isMiHeadset / getHeadsetType / isCirculateDevice.
 *   2. (Future) Protocol redirect — intercept headset control stack for full feature parity.
 */

class MiLinkServiceHook(private val classLoader: ClassLoader) {

    private var probed = false
    private var btServiceClientClass: Class<*>? = null

    // ---- Probe ----

    fun probe() {
        if (probed) return
        probed = true

        btServiceClientClass = try {
            classLoader.loadClass("com.miui.circulate.api.protocol.bluetooth.BluetoothServiceClient")
        } catch (_: Exception) {
            ProbeResultCache.markNotFound("BluetoothServiceClient")
            ModuleMain.instance.log("MiLinkServiceHook: BluetoothServiceClient not found — skipping")
            return
        }
        ProbeResultCache.markFound("BluetoothServiceClient")
        probeMethods()
    }

    private fun probeMethods() {
        val clazz = btServiceClientClass ?: return
        listOf("isMiHeadset", "getHeadsetType", "isCirculateDevice").forEach { methodName ->
            try {
                clazz.getDeclaredMethod(methodName, android.bluetooth.BluetoothDevice::class.java)
                ProbeResultCache.markMethodFound("BluetoothServiceClient", methodName)
                ModuleMain.instance.log("MiLinkServiceHook: found BluetoothServiceClient.$methodName")
            } catch (_: Exception) {
                ProbeResultCache.markMethodNotFound("BluetoothServiceClient", methodName)
                ModuleMain.instance.log("MiLinkServiceHook: BluetoothServiceClient.$methodName not found")
            }
        }
    }

    // ---- Hook ----

    fun hook() {
        val clazz = btServiceClientClass ?: return

        try {
            val isMiHeadsetMethod = clazz.getDeclaredMethod(
                "isMiHeadset", android.bluetooth.BluetoothDevice::class.java
            )
            ModuleMain.instance.hook(isMiHeadsetMethod)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        val device = chain.args[0] as? android.bluetooth.BluetoothDevice
                        if (device != null && DeviceWhitelist.contains(device.address)) {
                            ModuleMain.instance.log("isMiHeadset: spoofing true for ${device.address}")
                            return true
                        }
                        return chain.proceed()
                    }
                })
            ModuleMain.instance.log("MiLinkServiceHook: hooked isMiHeadset")
        } catch (e: Exception) {
            ModuleMain.instance.log("MiLinkServiceHook: failed to hook isMiHeadset — ${e.message}")
        }

        try {
            val getHeadsetTypeMethod = clazz.getDeclaredMethod(
                "getHeadsetType", android.bluetooth.BluetoothDevice::class.java
            )
            ModuleMain.instance.hook(getHeadsetTypeMethod)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        val device = chain.args[0] as? android.bluetooth.BluetoothDevice
                        if (device != null && DeviceWhitelist.contains(device.address)) {
                            // 2 = SINGER_BATTERY: standard TWS with per-ear battery
                            ModuleMain.instance.log("getHeadsetType: returning SINGER_BATTERY for ${device.address}")
                            return 2
                        }
                        return chain.proceed()
                    }
                })
            ModuleMain.instance.log("MiLinkServiceHook: hooked getHeadsetType")
        } catch (e: Exception) {
            ModuleMain.instance.log("MiLinkServiceHook: failed to hook getHeadsetType — ${e.message}")
        }

        try {
            val isCirculateDeviceMethod = clazz.getDeclaredMethod(
                "isCirculateDevice", android.bluetooth.BluetoothDevice::class.java
            )
            ModuleMain.instance.hook(isCirculateDeviceMethod)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        val device = chain.args[0] as? android.bluetooth.BluetoothDevice
                        if (device != null && DeviceWhitelist.contains(device.address)) {
                            ModuleMain.instance.log("isCirculateDevice: spoofing true for ${device.address}")
                            return true
                        }
                        return chain.proceed()
                    }
                })
            ModuleMain.instance.log("MiLinkServiceHook: hooked isCirculateDevice")
        } catch (e: Exception) {
            ModuleMain.instance.log("MiLinkServiceHook: failed to hook isCirculateDevice — ${e.message}")
        }
    }
}
