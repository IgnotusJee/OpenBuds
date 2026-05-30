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
        log("loaded in process: $processName")
        instance = this
        try {
            val marker = java.io.File("/sdcard/openbuds_lsposed_startup.txt")
            marker.writeText("process=$processName\ntime=${System.currentTimeMillis()}\n")
            marker.setReadable(true, false)
        } catch (_: Exception) {}
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        log("onPackageLoaded: ${param.packageName} isFirst=${param.isFirstPackage}")

        if (!param.isFirstPackage) return

        val cl = param.defaultClassLoader ?: return

        when (param.packageName) {
            "com.milink.service" -> {
                // Phase 1: Identity spoofing (satellite sticker)
                val identityHook = MiLinkIdentityHook(cl)
                if (identityHook.probe()) identityHook.hook()

                // Phase 2: Protocol-level redirect — third_headset → native headset (AUDIOGLASSES path)
                val headsetCardHook = MiLinkHeadsetCardHook(cl)
                if (headsetCardHook.probe()) headsetCardHook.hook()

                // Phase 3: trace HeadsetServiceClient + fix name at framework level
                val hsTrace = HeadsetClientTraceHook(cl)
                if (hsTrace.probe()) hsTrace.hook()
                TextViewNameFixHook(cl).hook()
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
