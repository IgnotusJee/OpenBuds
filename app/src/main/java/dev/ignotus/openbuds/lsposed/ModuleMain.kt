package dev.ignotus.openbuds.lsposed

import android.util.Log
import dev.ignotus.openbuds.lsposed.mitws.MilinkMiTwsFacadeEntry
import dev.ignotus.openbuds.lsposed.mitws.MilinkMiTwsTraceEntry
import dev.ignotus.openbuds.lsposed.mitws.MilinkRouteConfig
import dev.ignotus.openbuds.lsposed.mitws.MilinkRouteMode
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * LSPosed module entry for OpenBuds.
 *
 * ## Scope
 *
 * Configured via LSPosed Manager to inject into **`com.milink.service` only**.
 * No other packages are in scope.
 *
 * ## Lifecycle
 *
 * 1. [init] — writes a startup marker to `/sdcard/openbuds_lsposed_startup.txt`
 *    for diagnostics (process name + timestamp).
 * 2. [onPackageLoaded] — when `com.milink.service` loads, installs MiTWS
 *    facade hooks (M1) or trace-only hooks according to [MilinkRouteConfig].
 *
 * ## Hook architecture
 *
 * ```
 * com.milink.service process
 *   └── MxBluetoothManager MiTWS methods ← M1 facade mutation + trace
 * ```
 *
 * No hooks target `com.android.bluetooth`, `com.android.systemui`, or any
 * other system process. All interception happens **before** milink makes
 * binder calls to the Bluetooth stack.
 *
 * @see docs/plan/MILINK_FIRST_PARTY_ADAPTER_PLAN_V2.md
 */
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

    /**
     * Called by LSPosed when a package in scope is loaded.
     *
     * Dispatches only on the first package load (`isFirstPackage`).
     * Currently only handles `com.milink.service`.
     */
    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        log("onPackageLoaded: ${param.packageName} isFirst=${param.isFirstPackage}")

        if (!param.isFirstPackage) return

        val cl = param.defaultClassLoader

        when (param.packageName) {
            "com.milink.service" -> {
                when (MilinkRouteConfig.mode()) {
                    MilinkRouteMode.MITWS -> MilinkMiTwsFacadeEntry(cl).install()
                    MilinkRouteMode.TRACE_ONLY -> MilinkMiTwsTraceEntry(cl).install()
                }
            }
        }
    }

    fun log(msg: String) {
        Log.i("OpenBuds", "[LSPosed] $msg")
    }

    companion object {
        lateinit var instance: ModuleMain
            private set
    }
}
