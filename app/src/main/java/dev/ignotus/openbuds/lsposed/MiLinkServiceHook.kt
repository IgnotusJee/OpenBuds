package dev.ignotus.openbuds.lsposed

/**
 * LSPosed hook for the MiLink Fusion Device Center (com.milink.service / com.miui.circulate).
 *
 * Phase 1: Identity spoofing — make Sony headphones appear as first-party Xiaomi TWS.
 *   Hooks BluetoothServiceClient.isMiHeadset / getHeadsetType / isCirculateDevice.
 *
 * The hook consults DeviceWhitelist (shared /sdcard/headset_whitelist.txt) to decide
 * whether to spoof first-party identity for a given BluetoothDevice.
 */

class MiLinkServiceHook(private val classLoader: ClassLoader) {

    // Possible class names across HyperOS versions (try them in order)
    private val btServiceClientNames = listOf(
        "com.miui.circulate.api.protocol.bluetooth.BluetoothServiceClient",
    )

    // Possible method parameter types (BluetoothDevice vs String MAC)
    private val paramTypes = listOf(
        arrayOf<Class<*>>(android.bluetooth.BluetoothDevice::class.java),
        arrayOf<Class<*>>(String::class.java),
    )

    private var probed = false
    private var btServiceClientClass: Class<*>? = null
    private var resolvedParamType: Array<Class<*>>? = null

    // ---- Probe ----

    fun probe() {
        if (probed) return
        probed = true

        for (name in btServiceClientNames) {
            btServiceClientClass = try {
                classLoader.loadClass(name)
            } catch (_: Exception) { null }
            if (btServiceClientClass != null) break
        }

        if (btServiceClientClass == null) {
            ProbeResultCache.markNotFound("BluetoothServiceClient")
            ModuleMain.instance.log("MiLinkServiceHook: BluetoothServiceClient not found in any known package")
            writeDiagnostic("BluetoothServiceClient NOT FOUND — tried: ${btServiceClientNames}")
            return
        }

        ProbeResultCache.markFound("BluetoothServiceClient")
        ModuleMain.instance.log("MiLinkServiceHook: found ${btServiceClientClass!!.name}")
        probeMethods()
        writeDiagnostic("BluetoothServiceClient FOUND: ${btServiceClientClass!!.name}")

        // Also probe companion classes for diagnostics
        probeCompanionClasses()
    }

    private fun probeMethods() {
        val clazz = btServiceClientClass ?: return
        for (paramType in paramTypes) {
            var allFound = true
            for (methodName in listOf("isMiHeadset", "getHeadsetType", "isCirculateDevice")) {
                try {
                    clazz.getDeclaredMethod(methodName, *paramType)
                    ProbeResultCache.markMethodFound("BluetoothServiceClient", "$methodName(${paramType.first().simpleName})")
                    ModuleMain.instance.log("MiLinkServiceHook: found $methodName(${paramType.first().simpleName})")
                } catch (_: Exception) {
                    ProbeResultCache.markMethodNotFound("BluetoothServiceClient", "$methodName(${paramType.first().simpleName})")
                    allFound = false
                }
            }
            if (allFound) {
                resolvedParamType = paramType
                ModuleMain.instance.log("MiLinkServiceHook: using param type ${paramType.first().simpleName}")
                return
            }
        }
        // Check instance methods too (non-static)
        if (resolvedParamType == null) {
            for (paramType in paramTypes) {
                try {
                    // Try instance method detection
                    clazz.getDeclaredMethod("isMiHeadset", *paramType)
                    resolvedParamType = paramType
                    ModuleMain.instance.log("MiLinkServiceHook: using param type ${paramType.first().simpleName} (instance method)")
                    return
                } catch (_: Exception) {}
            }
        }
        ModuleMain.instance.log("MiLinkServiceHook: could not resolve method signatures")
        writeDiagnostic("METHOD SIGNATURE FAILURE — no matching param types found")
    }

    private fun probeCompanionClasses() {
        // Probe related classes for future phases
        val companionClasses = listOf(
            "com.miui.circulate.device.service.search.impl.BluetoothDeviceObserver",
            "com.miui.circulate.world.CirculateWorldActivity",
            "com.miui.circulate.world.MLCardViewHostService",
        )
        for (name in companionClasses) {
            try {
                classLoader.loadClass(name)
                ProbeResultCache.markFound(name)
                ModuleMain.instance.log("MiLinkServiceHook: found $name")
            } catch (_: Exception) {
                ProbeResultCache.markNotFound(name)
            }
        }
    }

    // ---- Hook ----

    fun hook() {
        val clazz = btServiceClientClass ?: run {
            ModuleMain.instance.log("MiLinkServiceHook: skipping hook — class not found")
            return
        }
        val paramType = resolvedParamType ?: run {
            ModuleMain.instance.log("MiLinkServiceHook: skipping hook — no resolved param type")
            return
        }

        val useStringParam = paramType.first() == String::class.java
        ModuleMain.instance.log("MiLinkServiceHook: hooking with param=${paramType.first().simpleName}")

        // Helper to extract MAC from hook args
        fun extractMac(args: Array<out Any?>): String? {
            if (args.isEmpty()) return null
            return if (useStringParam) {
                args[0] as? String
            } else {
                (args[0] as? android.bluetooth.BluetoothDevice)?.address
            }
        }

        tryHookMethod(clazz, "isMiHeadset", paramType) { mac ->
            DeviceWhitelist.contains(mac)
        }
        tryHookMethod(clazz, "getHeadsetType", paramType) { mac ->
            if (DeviceWhitelist.contains(mac)) 2 else null  // 2 = SINGER_BATTERY
        }
        tryHookMethod(clazz, "isCirculateDevice", paramType) { mac ->
            DeviceWhitelist.contains(mac)
        }

        writeDiagnostic("HOOKS REGISTERED: isMiHeadset, getHeadsetType, isCirculateDevice")
    }

    /**
     * Generic hook helper. [decide] receives the device MAC and returns the spoofed value,
     * or null to let the original method proceed.
     */
    private fun tryHookMethod(
        clazz: Class<*>,
        methodName: String,
        paramType: Array<Class<*>>,
        decide: (String) -> Any?,
    ) {
        try {
            val method = clazz.getDeclaredMethod(methodName, *paramType)
            ModuleMain.instance.hook(method)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        val args = chain.args
                        val mac = try {
                            if (args.isNotEmpty()) {
                                if (paramType.first() == String::class.java) args[0] as? String
                                else (args[0] as? android.bluetooth.BluetoothDevice)?.address
                            } else null
                        } catch (_: Exception) { null }

                        if (mac != null) {
                            val result = decide(mac)
                            if (result != null) {
                                ModuleMain.instance.log("$methodName: spoofed $result for $mac")
                                return result
                            }
                        }
                        return chain.proceed()
                    }
                })
            ModuleMain.instance.log("MiLinkServiceHook: hooked $methodName")
        } catch (e: Exception) {
            ModuleMain.instance.log("MiLinkServiceHook: failed to hook $methodName — ${e.message}")
        }
    }

    // ---- Diagnostic ----

    private fun writeDiagnostic(msg: String) {
        try {
            val file = java.io.File("/sdcard/openbuds_milink_diag.txt")
            val existing = if (file.exists()) file.readText() else ""
            file.writeText("$existing${System.currentTimeMillis()}: $msg\n")
            file.setReadable(true, false)
        } catch (_: Exception) {}
    }
}
