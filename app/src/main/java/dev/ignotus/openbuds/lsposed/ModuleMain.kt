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
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        log("onPackageLoaded: ${param.packageName}, isFirst=${param.isFirstPackage}")

        if (!param.isFirstPackage) return

        val cl = param.defaultClassLoader ?: return

        when (param.packageName) {
            "com.android.bluetooth" -> BluetoothProcessHook(cl).probe()
            "com.xiaomi.bluetooth" -> {
                val hook = XiaomiBluetoothHook(cl)
                hook.probe()
                hook.hook()
            }
            "com.android.systemui" -> SystemUiHook(cl).probe()
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
