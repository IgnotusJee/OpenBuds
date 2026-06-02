package dev.ignotus.openbuds.lsposed.mitws

import android.app.Application
import android.content.Context
import android.util.Log
import dev.ignotus.openbuds.lsposed.ModuleMain
import io.github.libxposed.api.XposedInterface

class MilinkMiTwsFacadeEntry(
    private val classLoader: ClassLoader,
) {
    @Volatile
    private var bridgeClient: MilinkBridgeClient? = null

    fun install() {
        Log.i(TAG, "Installing MiLink MiTWS facade entry (M1)")
        installApplicationOnCreateHook()
        MilinkMiTwsFacadeHook(classLoader, BridgeClientHolder).installTraceHooks()
    }

    private fun installApplicationOnCreateHook() {
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
        Log.i(TAG, "hooked: Application.onCreate() for MiTWS bridge")
    }

    private fun startBridgeClient(context: Context) {
        if (bridgeClient != null) return
        synchronized(this) {
            if (bridgeClient != null) return
            val client = MilinkBridgeClient(context)
            bridgeClient = client
            BridgeClientHolder.delegate = client
            client.start()
            Log.i(TAG, "MiLink MiTWS bridge client started (M1)")
        }
    }

    private object BridgeClientHolder : MilinkBridgeClientFacade {
        @Volatile
        var delegate: MilinkBridgeClient? = null

        override val adapterEnabled: Boolean
            get() = delegate?.adapterEnabled == true

        override fun snapshotFor(mac: String?) = delegate?.snapshotFor(mac)

        override fun isAuthorized(mac: String?) = delegate?.isAuthorized(mac) == true
    }

    private companion object {
        private const val TAG = "OpenBuds"
    }
}
