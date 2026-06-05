package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import android.util.Log

class XiaomiBluetoothTraceEntry(
    private val classLoader: ClassLoader,
) {
    private val logger = XiaomiBluetoothTraceLogger()

    fun installIfEnabled() {
        if (!XiaomiBluetoothTraceConfig.isTraceEnabled()) {
            Log.i(TAG, "[XIAOMI_BT_TRACE] disabled property=${XiaomiBluetoothTraceConfig.TRACE_ENABLE_PROPERTY}")
            return
        }
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

        fastConnectTraceHook.install()
        notificationTraceHook.install()
        peripheralTraceHook.install()
        DeferredClassLoadTraceInstaller(
            fastConnectTraceHook = fastConnectTraceHook,
            notificationTraceHook = notificationTraceHook,
            peripheralTraceHook = peripheralTraceHook,
            logger = logger,
        ).install()
    }

    private companion object {
        private const val TAG = "OpenBuds"
    }
}
