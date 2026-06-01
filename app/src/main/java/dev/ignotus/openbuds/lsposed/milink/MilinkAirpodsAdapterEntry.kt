package dev.ignotus.openbuds.lsposed.milink

import android.app.Application
import android.content.Context
import android.util.Log
import dev.ignotus.openbuds.lsposed.ModuleMain
import io.github.libxposed.api.XposedInterface

/**
 * Top-level entry point for the MiLink first-party adapter path.
 *
 * ## Architecture
 *
 * This entry is invoked by [ModuleMain][dev.ignotus.openbuds.lsposed.ModuleMain]
 * when the LSPosed module loads inside `com.milink.service`. It installs hooks
 * that intercept milink's AirPods classification chain **before** any binder
 * call reaches `com.android.bluetooth`.
 *
 * ## Key principle
 *
 * **Never cross the process boundary.** All hooks live inside `com.milink.service`.
 * The Bluetooth process (`com.android.bluetooth`) is never touched, hooked, or
 * reverse-engineered.
 *
 * @see MilinkAirpodsM1Hook for hook implementation details
 * @see docs/plan/MILINK_FIRST_PARTY_ADAPTER_PLAN.md
 */
class MilinkAirpodsAdapterEntry(
    private val classLoader: ClassLoader,
) {
    @Volatile
    private var bridgeClient: MilinkBridgeClient? = null

    fun install() {
        Log.i(TAG, "Installing MiLink AirPods bridge hooks")
        installApplicationAttachHook()
        val client = MilinkBridgeClientHolder
        MilinkAirpodsM1Hook(classLoader, client).install()
    }

    private fun installApplicationAttachHook() {
        val method = runCatching {
            Application::class.java.getDeclaredMethod("onCreate")
                .also { it.isAccessible = true }
        }.getOrElse { error ->
            Log.w(TAG, "Missing Application.onCreate()", error)
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val app = chain.thisObject as? Application
                    if (app != null) {
                        startBridgeClient(app)
                    }
                    return result
                }
            })
        Log.i(TAG, "hooked: Application.onCreate()")
    }

    private fun startBridgeClient(context: Context) {
        if (bridgeClient != null) return
        synchronized(this) {
            if (bridgeClient != null) return
            val client = MilinkBridgeClient(context)
            bridgeClient = client
            MilinkBridgeClientHolder.delegate = client
            client.start()
            Log.i(TAG, "MiLink bridge client started")
        }
    }

    /**
     * Hook callbacks may run before Application.attach has supplied a Context.
     * Until then this delegate returns no snapshots, preserving strict fallback.
     */
    private object MilinkBridgeClientHolder : MilinkBridgeClientFacade {
        @Volatile
        var delegate: MilinkBridgeClient? = null

        override fun snapshotFor(mac: String?) = delegate?.snapshotFor(mac)
        override fun isAuthorized(mac: String?) = delegate?.isAuthorized(mac) == true
    }

    private companion object {
        private const val TAG = "OpenBuds"
    }
}

interface MilinkBridgeClientFacade {
    fun snapshotFor(mac: String?): dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot?
    fun isAuthorized(mac: String?): Boolean
}
