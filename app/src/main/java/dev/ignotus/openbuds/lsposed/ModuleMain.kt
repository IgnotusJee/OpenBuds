package dev.ignotus.openbuds.lsposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class ModuleMain : XposedModule() {

    init {
        val processName = try {
            val atClass = Class.forName("android.app.ActivityThread")
            val method = atClass.getDeclaredMethod("currentProcessName")
            method.invoke(null) as? String ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
        log("ModuleMain loaded in process: $processName")
        instance = this
        try {
            val marker = java.io.File("/sdcard/openbuds_lsposed_startup.txt")
            marker.writeText("process=$processName\ntime=${System.currentTimeMillis()}\n")
            marker.setReadable(true, false)
        } catch (_: Exception) {}
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        log("onPackageLoaded: ${param.packageName}, isFirst=${param.isFirstPackage}")

        if (!param.isFirstPackage) return

        val cl = param.defaultClassLoader ?: return

        when (param.packageName) {
            // Primary: com.android.bluetooth — proven approach from HyperPods/OppoPods
            "com.android.bluetooth" -> {
                // A2DP detection → whitelist MAC + MiLink identity spoofing
                val hook = BluetoothProcessHook(cl)
                hook.probe()
                hook.hook()
            }
            // Fallback: com.milink.service — if BluetoothServiceClient runs here
            "com.milink.service" -> {
                val hook = MiLinkServiceHook(cl)
                hook.probe()
                hook.hook()
            }
        }
        ProbeResultCache.persistShared()
    }

    fun log(msg: String) {
        Log.i("OpenBuds", "[LSPosed] $msg")
    }

    companion object {
        lateinit var instance: ModuleMain
            private set
    }
}
