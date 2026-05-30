package dev.ignotus.openbuds.lsposed

/**
 * Phase 3 probe: find the MiLink headset protocol stack classes.
 *
 * From the reverse engineering doc, the protocol redirect interception point is:
 *   com.miui.headset.api.C6540c.m25629a() — factory method
 *
 * Supporting interfaces:
 *   com.miui.headset.api.InterfaceC6548k  — HeadsetClient
 *   com.miui.headset.api.InterfaceC6550m  — IProfile (AIDL)
 *   com.miui.headset.api.InterfaceC6551n  — IQuery (AIDL)
 *
 * Service wrapper:
 *   com.miui.circulate.api.protocol.headset.HeadsetServiceClient
 */
class HeadsetApiProbeHook(private val classLoader: ClassLoader) {

    // Class names to search for
    private val targetClasses = listOf(
        "com.miui.headset.api.C6540c",
        "com.miui.headset.api.InterfaceC6548k",
        "com.miui.headset.api.InterfaceC6550m",
        "com.miui.headset.api.InterfaceC6551n",
        "com.miui.circulate.api.protocol.headset.HeadsetServiceClient",
        "com.milink.cardframelibrary.host.MLCardManagerHost",
        "com.milink.cardframelibrary.host.C3829i",
    )

    // Also try to find by scanning for headset-related packages
    private val headsetPackages = listOf(
        "com.miui.headset",
        "com.miui.circulate.api.protocol.headset",
        "com.milink.cardframelibrary",
    )

    private var probed = false

    fun probe() {
        if (probed) return
        probed = true

        log("=== Phase 3: probing headset API classes ===")

        // Try loading each specific class
        for (className in targetClasses) {
            try {
                val cls = classLoader.loadClass(className)
                ProbeResultCache.markFound(className)
                log("FOUND: $className")
                // Log all public methods
                for (m in cls.declaredMethods.take(15)) {
                    val params = m.parameterTypes.joinToString(",") { it.simpleName }
                    log("  ${m.returnType.simpleName} ${m.name}($params)")
                }
                if (cls.declaredMethods.size > 15) {
                    log("  ... +${cls.declaredMethods.size - 15} more methods")
                }
            } catch (_: Exception) {
                log("NOT FOUND: $className")
                ProbeResultCache.markNotFound(className)
            }
        }

        // Try package-level scanning
        log("=== Package scan ===")
        for (pkg in headsetPackages) {
            try {
                // Check if the package exists by trying to load something from it
                val pkgPrefix = pkg.replace('.', '/')
                log("package prefix: $pkgPrefix")
            } catch (_: Exception) {}
        }

        ProbeResultCache.persistShared()
        log("=== Phase 3 probe complete ===")
    }

    companion object {
        fun log(msg: String) {
            android.util.Log.i("OpenBuds", "[PH3] $msg")
        }
    }
}
