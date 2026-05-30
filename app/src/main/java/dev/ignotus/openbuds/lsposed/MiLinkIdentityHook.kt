package dev.ignotus.openbuds.lsposed

import android.bluetooth.BluetoothDevice
import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * LSPosed hook for MiLink Fusion Device Center identity spoofing.
 *
 * Target process: com.milink.service
 * Target class:   com.miui.circulate.api.protocol.bluetooth.BluetoothServiceClient
 *
 * Intercepts BluetoothServiceClient.isMiHeadset() before it calls
 * MxBluetoothManager.checkIsMiTWS(). For Sony headphones (detected by device name),
 * returns true immediately, triggering the first-party device path:
 *   - getDeviceType() → "Headset" (not "btHeadset")
 *   - onDeviceFound() → satellite sticker (not independent card)
 *   - getHeadsetType() → 2 (SINGER_BATTERY)
 *   - isCirculateDevice() → true
 */
class MiLinkIdentityHook(private val classLoader: ClassLoader) {

    // Class names to try (may differ across HyperOS versions)
    private val targetClassNames = listOf(
        "com.miui.circulate.api.protocol.bluetooth.BluetoothServiceClient",
    )

    private var probed = false
    private var resolvedClass: Class<*>? = null

    // ── Probe ──────────────────────────────────────────────

    fun probe(): Boolean {
        if (probed) return resolvedClass != null
        probed = true

        for (name in targetClassNames) {
            resolvedClass = try {
                classLoader.loadClass(name)
            } catch (_: Exception) { null }
            if (resolvedClass != null) {
                ProbeResultCache.markFound(name)
                log("found: $name")
                try {
                    resolvedClass!!.getDeclaredMethod("isMiHeadset", BluetoothDevice::class.java)
                    ProbeResultCache.markMethodFound(name, "isMiHeadset")
                    log("found: isMiHeadset(BluetoothDevice)")
                } catch (_: Exception) {
                    ProbeResultCache.markMethodNotFound(name, "isMiHeadset")
                    log("isMiHeadset(BluetoothDevice) not found — class is present but method missing")
                }
                try {
                    resolvedClass!!.getDeclaredMethod("getHeadsetType", BluetoothDevice::class.java)
                    ProbeResultCache.markMethodFound(name, "getHeadsetType")
                    log("found: getHeadsetType(BluetoothDevice)")
                } catch (_: Exception) {}
                try {
                    resolvedClass!!.getDeclaredMethod("isCirculateDevice", BluetoothDevice::class.java)
                    ProbeResultCache.markMethodFound(name, "isCirculateDevice")
                    log("found: isCirculateDevice(BluetoothDevice)")
                } catch (_: Exception) {}
                ProbeResultCache.persistShared()
                return true
            }
            ProbeResultCache.markNotFound(name)
            log("not found: $name")
        }
        ProbeResultCache.persistShared()
        log("no target class found in this process")
        return false
    }

    // ── Hook ───────────────────────────────────────────────

    fun hook() {
        val clazz = resolvedClass ?: return

        hookIsMiHeadset(clazz)
        hookGetHeadsetType(clazz)
        hookIsCirculateDevice(clazz)
    }

    private fun hookIsMiHeadset(clazz: Class<*>) {
        try {
            val method = clazz.getDeclaredMethod("isMiHeadset", BluetoothDevice::class.java)
            ModuleMain.instance.hook(method)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        val device = chain.args[0] as? BluetoothDevice
                        if (device != null && isSonyHeadphone(device)) {
                            log("isMiHeadset → TRUE for \"${device.name}\" (${device.address})")
                            cacheSonyDevice(device.address, device.name)
                            DeviceWhitelist.add(device.address)  // cross-process: share MAC via file
                            preconnectBle(device.address, device.name ?: "Sony")
                            return true  // skip original MxBluetoothManager.checkIsMiTWS()
                        }
                        return chain.proceed()
                    }
                })
            log("hooked: isMiHeadset")
        } catch (e: Exception) {
            log("FAILED to hook isMiHeadset: ${e.message}")
        }
    }

    private fun hookGetHeadsetType(clazz: Class<*>) {
        try {
            val method = clazz.getDeclaredMethod("getHeadsetType", BluetoothDevice::class.java)
            ModuleMain.instance.hook(method)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        val device = chain.args[0] as? BluetoothDevice
                        if (device != null && isSonyHeadphone(device)) {
                            // 2 = SINGER_BATTERY: standard TWS with per-ear battery
                            return 2
                        }
                        return chain.proceed()
                    }
                })
            log("hooked: getHeadsetType")
        } catch (e: Exception) {
            log("FAILED to hook getHeadsetType: ${e.message}")
        }
    }

    private fun hookIsCirculateDevice(clazz: Class<*>) {
        try {
            val method = clazz.getDeclaredMethod("isCirculateDevice", BluetoothDevice::class.java)
            ModuleMain.instance.hook(method)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        val device = chain.args[0] as? BluetoothDevice
                        if (device != null && isSonyHeadphone(device)) {
                            // Only keep sticker for connected devices
                            return isDeviceConnected(device)
                        }
                        return chain.proceed()
                    }
                })
            log("hooked: isCirculateDevice")
        } catch (e: Exception) {
            log("FAILED to hook isCirculateDevice: ${e.message}")
        }
    }

    // ── Sony device detection (name-based, no file dependency) ──

    private fun isSonyHeadphone(device: BluetoothDevice): Boolean {
        val name = device.name ?: return false
        return matchesSonyPattern(name)
    }

    companion object {
        /** Cached MAC of last Sony headphone seen by isMiHeadset hook. */
        @Volatile
        var lastSonyMac: String? = null
            private set

        @Volatile
        var lastSonyName: String? = null
            private set

        private val preconnectInProgress = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

        fun cacheSonyDevice(mac: String, name: String?) {
            lastSonyMac = mac
            lastSonyName = name
        }

        /**
         * Pre-connect BLE as soon as a Sony device is identified.
         * This gives the connection a head start before the MLCard appears,
         * so real BLE data is available by the time the card renders.
         */
        private fun preconnectBle(mac: String, name: String) {
            val normalizedMac = mac.trim().uppercase()
            if (!preconnectInProgress.add(normalizedMac)) return
            Thread({
                try {
                    val atClass = Class.forName("android.app.ActivityThread")
                    val app = atClass.getDeclaredMethod("currentApplication").invoke(null) as? Context
                    if (app == null) {
                        log("preconnect: no Application context")
                        preconnectInProgress.remove(normalizedMac)
                        return@Thread
                    }
                    log("preconnect: starting BLE for $name ($mac)")
                    val repo = dev.ignotus.openbuds.data.SonyHeadphoneRepository.getInstance(app)
                    repo.connect(mac, name)
                } catch (e: Exception) {
                    log("preconnect failed: ${e.message}")
                    preconnectInProgress.remove(normalizedMac)
                }
            }, "OpenBuds-Preconnect-${normalizedMac.take(8)}").start()
        }

        /**
         * Match Sony headphone name patterns.
         * Covers: WF-1000XM*, WH-1000XM*, LinkBuds*, WI-*, MDR-*, XBA-*
         */
        fun matchesSonyPattern(name: String): Boolean {
            val n = name.trim().lowercase()
            if (n.isEmpty()) return false
            if (n.startsWith("wf-")) return true
            if (n.startsWith("wh-")) return true
            if (n.startsWith("wi-")) return true
            if (n.startsWith("mdr-")) return true
            if (n.startsWith("xba-")) return true
            if (n.contains("linkbuds")) return true
            // Generic Sony: only match if name also contains a headphone model pattern
            if (n.contains("sony") && (n.startsWith("wf-") || n.startsWith("wh-") ||
                n.contains("linkbuds") || n.contains("1000x") || n.contains("h.ear"))) return true
            return false
        }

        /**
         * Check if a BluetoothDevice is currently connected via reflection.
         * Uses hidden API BluetoothDevice.isConnected().
         */
        private fun isDeviceConnected(device: BluetoothDevice): Boolean {
            return try {
                val method = device.javaClass.getMethod("isConnected")
                method.invoke(device) as? Boolean ?: false
            } catch (_: Exception) {
                // Fallback: check bond state — bonded implies was connected recently
                device.bondState == BluetoothDevice.BOND_BONDED
            }
        }

        private fun log(msg: String) {
            android.util.Log.i("OpenBuds", "[MiLink] $msg")
        }
    }
}
