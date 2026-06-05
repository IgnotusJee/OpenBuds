package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import android.app.Application
import android.content.Context
import android.util.Log
import dev.ignotus.openbuds.lsposed.ModuleMain
import io.github.libxposed.api.XposedInterface

class XiaomiBluetoothTraceEntry(
    private val classLoader: ClassLoader,
) {
    private val logger = XiaomiBluetoothTraceLogger(macAllowlist = combinedMacAllowlist())
    private val sonySppProbe = SonySppProbe(logger)
    private val sonySppProxy = SonySppProxy(classLoader, logger)

    fun installIfEnabled() {
        val traceEnabled = XiaomiBluetoothTraceConfig.isTraceEnabled()
        val sppProbeEnabled = XiaomiBluetoothTraceConfig.isSppProbeEnabled()
        val sppProxyEnabled = XiaomiBluetoothTraceConfig.isSppProxyEnabled()
        if (!traceEnabled && !sppProbeEnabled && !sppProxyEnabled) {
            Log.i(
                TAG,
                "[XIAOMI_BT_TRACE] disabled traceProperty=${XiaomiBluetoothTraceConfig.TRACE_ENABLE_PROPERTY} " +
                    "sppProbeProperty=${XiaomiBluetoothTraceConfig.SPP_PROBE_ENABLE_PROPERTY} " +
                    "sppProxyProperty=${XiaomiBluetoothTraceConfig.SPP_PROXY_ENABLE_PROPERTY}",
            )
            return
        }
        if (traceEnabled) installTraceHooks()
        if (!traceEnabled && sppProxyEnabled && XiaomiBluetoothTraceConfig.sppProxyTransport() == MiuiSppProxyTransport.PC) {
            installProxyClassLoadHook()
        }
        if (sppProbeEnabled || sppProxyEnabled) installContextHook()
    }

    private fun installTraceHooks() {
        logger.info(
            event = "diagnostic_template",
            mac = null,
            details = "scan_seen=? assembled_adv=? check_adv_passed=? cached_device_id_seen=? " +
                "notification_state_seen=? toast_called=? detail_notification_gate_passed=? peripheral_path_seen=?",
            force = true,
        )
        val fastConnectTraceHook = FastConnectTraceHook(classLoader, logger)
        val notificationTraceHook = NotificationTraceHook(classLoader, logger)
        val peripheralTraceHook = PeripheralTraceHook(classLoader, logger)
        val mmaTraceHook = MmaTraceHook(classLoader, logger)

        fastConnectTraceHook.install()
        notificationTraceHook.install()
        peripheralTraceHook.install()
        mmaTraceHook.install()
        DeferredClassLoadTraceInstaller(
            fastConnectTraceHook = fastConnectTraceHook,
            notificationTraceHook = notificationTraceHook,
            peripheralTraceHook = peripheralTraceHook,
            mmaTraceHook = mmaTraceHook,
            onLoadedClass = sonySppProxy::onLoadedClass,
            logger = logger,
        ).install()
    }

    private fun installProxyClassLoadHook() {
        DeferredClassLoadTraceInstaller(
            onLoadedClass = sonySppProxy::onLoadedClass,
            logger = logger,
        ).install()
    }

    private fun installContextHook() {
        val method = runCatching {
            Application::class.java.getDeclaredMethod("attach", Context::class.java)
                .also { it.isAccessible = true }
        }.onFailure {
            logger.warn("xiaomi_bt_attach_hook_missing", "Application.attach(Context)", it)
        }.getOrNull() ?: return

        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    runCatching {
                        val context = (chain.args.firstOrNull() as? Context)
                            ?: (chain.thisObject as? Application)
                        if (context != null) {
                            val appContext = context.applicationContext ?: context
                            sonySppProbe.startOnce(appContext)
                            sonySppProxy.startOnce(appContext)
                        }
                    }.onFailure { logger.warn("xiaomi_bt_attach_error", "Application.attach(Context)", it) }
                    return result
                }
            })
        logger.methodHooked(Application::class.java.name, "attach(Context)")
    }

    private companion object {
        private const val TAG = "OpenBuds"

        private fun combinedMacAllowlist(): Set<String> =
            XiaomiBluetoothTraceConfig.macAllowlist() +
                setOfNotNull(
                    XiaomiBluetoothTraceConfig.sppProbeTargetMac(),
                    XiaomiBluetoothTraceConfig.sppProxyTargetMac(),
                )
    }
}
