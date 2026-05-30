package dev.ignotus.openbuds.lsposed

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.util.Log

/**
 * LSPosed hook for com.android.bluetooth process.
 *
 * Detects Sony headphone A2DP connections, writes MAC to the shared whitelist,
 * and attempts to hook BluetoothServiceClient for MiLink identity spoofing.
 */
class BluetoothProcessHook(private val classLoader: ClassLoader) {

    private var probed = false
    private var a2dpClass: Class<*>? = null
    private var btServiceClientClass: Class<*>? = null

    fun probe() {
        if (probed) return
        probed = true

        // Probe A2dpService
        a2dpClass = try {
            classLoader.loadClass("com.android.bluetooth.a2dp.A2dpService")
        } catch (_: Exception) {
            null
        }
        if (a2dpClass != null) {
            ProbeResultCache.markFound("A2dpService")
            log("found A2dpService")
            try {
                a2dpClass!!.getDeclaredMethod(
                    "handleConnectionStateChanged",
                    BluetoothDevice::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                ProbeResultCache.markMethodFound("A2dpService", "handleConnectionStateChanged")
                log("found handleConnectionStateChanged")
            } catch (_: Exception) {
                ProbeResultCache.markMethodNotFound("A2dpService", "handleConnectionStateChanged")
            }
        } else {
            ProbeResultCache.markNotFound("A2dpService")
            log("A2dpService not found")
        }

        // Probe BluetoothServiceClient (may be in this process too)
        btServiceClientClass = try {
            classLoader.loadClass("com.miui.circulate.api.protocol.bluetooth.BluetoothServiceClient")
        } catch (_: Exception) {
            null
        }
        if (btServiceClientClass != null) {
            ProbeResultCache.markFound("BluetoothServiceClient")
            log("found BluetoothServiceClient in com.android.bluetooth")
            probeBtServiceClientMethods()
        }
        ProbeResultCache.persistShared()
    }

    private fun probeBtServiceClientMethods() {
        val clazz = btServiceClientClass ?: return
        for (methodName in listOf("isMiHeadset", "getHeadsetType", "isCirculateDevice")) {
            try {
                clazz.getDeclaredMethod(methodName, BluetoothDevice::class.java)
                ProbeResultCache.markMethodFound("BluetoothServiceClient", methodName)
                log("found $methodName")
            } catch (_: Exception) {}
            try {
                clazz.getDeclaredMethod(methodName, String::class.java)
                ProbeResultCache.markMethodFound("BluetoothServiceClient", "$methodName(String)")
                log("found $methodName(String)")
            } catch (_: Exception) {}
        }
    }

    fun hook() {
        hookA2dpConnectionState()
        hookBtServiceClient()
    }

    // ---- A2DP hook: detect Sony devices and write MAC to whitelist ----

    private fun hookA2dpConnectionState() {
        val clazz = a2dpClass ?: return
        try {
            val method = clazz.getDeclaredMethod(
                "handleConnectionStateChanged",
                BluetoothDevice::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            ModuleMain.instance.hook(method)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        val args = chain.args
                        val device = args[0] as? BluetoothDevice
                        val currState = (args[2] as? Int) ?: 0

                        if (device != null && currState == BluetoothProfile.STATE_CONNECTED) {
                            if (isSonyDevice(device)) {
                                val mac = device.address
                                DeviceWhitelist.add(mac)
                                log("A2DP connected: Sony device $mac → added to whitelist")
                                log("  name=${device.name}")
                            }
                        } else if (device != null && currState == BluetoothProfile.STATE_DISCONNECTED) {
                            if (isSonyDevice(device)) {
                                DeviceWhitelist.remove(device.address)
                                log("A2DP disconnected: removed ${device.address} from whitelist")
                            }
                        }
                        return chain.proceed()
                    }
                })
            log("hooked A2dpService.handleConnectionStateChanged")
        } catch (e: Exception) {
            log("failed to hook A2dpService: ${e.message}")
        }
    }

    // ---- BluetoothServiceClient hooks (identity spoofing, if class found) ----

    private fun hookBtServiceClient() {
        val clazz = btServiceClientClass ?: return
        hookMiMethod(clazz, "isMiHeadset") { true }
        hookMiMethod(clazz, "getHeadsetType") { 2 }
        hookMiMethod(clazz, "isCirculateDevice") { true }
    }

    private fun hookMiMethod(clazz: Class<*>, methodName: String, spoofValue: () -> Any?) {
        for (paramType in listOf(
            arrayOf<Class<*>>(BluetoothDevice::class.java),
            arrayOf<Class<*>>(String::class.java),
        )) {
            try {
                val method = clazz.getDeclaredMethod(methodName, *paramType)
                val useString = paramType.first() == String::class.java
                ModuleMain.instance.hook(method)
                    .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                        override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                            val mac = if (useString) chain.args[0] as? String
                                      else (chain.args[0] as? BluetoothDevice)?.address
                            if (mac != null && DeviceWhitelist.contains(mac)) {
                                val v = spoofValue()
                                log("$methodName: spoofed $v for $mac")
                                return v
                            }
                            return chain.proceed()
                        }
                    })
                log("hooked $methodName(${paramType.first().simpleName}) in com.android.bluetooth")
                return
            } catch (_: Exception) {}
        }
        log("$methodName: no matching signature found in com.android.bluetooth")
    }

    // ---- Sony device detection ----

    private fun isSonyDevice(device: BluetoothDevice): Boolean {
        // Check by name pattern (reliable, similar to OppoPods' approach)
        val name = device.name ?: return false
        return isHeadphoneCandidate(name)
    }

    companion object {
        fun isHeadphoneCandidate(name: String): Boolean {
            val n = name.trim().lowercase()
            return n.contains("sony") ||
                n.contains("linkbuds") ||
                n.startsWith("wf-") ||
                n.startsWith("wh-") ||
                n.startsWith("wi-") ||
                n.startsWith("xba-") ||
                n.startsWith("mdr-")
        }

        private fun log(msg: String) {
            Log.i("OpenBuds", "[LSPosed/BTHook] $msg")
        }
    }
}
