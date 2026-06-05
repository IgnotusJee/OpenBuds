package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import android.bluetooth.BluetoothDevice
import dev.ignotus.openbuds.lsposed.ModuleMain
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class FastConnectTraceHook(
    private val classLoader: ClassLoader,
    private val logger: XiaomiBluetoothTraceLogger,
) {
    private val installedClassNames = ConcurrentHashMap.newKeySet<String>()

    fun install() {
        targetClassNames.forEach { installByName(it) }
    }

    fun installLoadedClass(clazz: Class<*>): Boolean =
        when (clazz.name) {
            FAST_CONNECT_SERVICE -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookFastConnectService(clazz)
                true
            }
            STATE_MACHINE -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookStateMachine(clazz)
                true
            }
            CONTROLLER_FACTORY -> {
                if (!installedClassNames.add(clazz.name)) return true
                hookControllerFactory(clazz)
                true
            }
            else -> false
        }

    private fun installByName(className: String) {
        val clazz = classLoader.loadTargetClass(className, logger) ?: return
        installLoadedClass(clazz)
    }

    private fun hookFastConnectService(clazz: Class<*>) {
        hookAfter(clazz, "handleScanCallBack") { method, args, result ->
            val scanResult = args.getOrNull(1)
            logScan(
                event = "fastconnect_scan",
                method = method,
                scanResult = scanResult,
                result = result,
                extra = "scan_seen=true",
            )
        }
        hookAfter(clazz, "getServiceAssembleAdvData") { method, args, result ->
            val scanResult = args.firstOrNull()
            logScan(
                event = "fastconnect_service_assemble_adv",
                method = method,
                scanResult = scanResult,
                result = result,
                extra = "assembled_adv=${result is ByteArray} adv=${logger.describe(result)}",
            )
        }
        hookAfter(clazz, "checkAdvData") { method, args, result ->
            val advData = args.firstOrNull() as? ByteArray
            logger.info(
                event = "fastconnect_check_adv",
                mac = null,
                details = "check_adv_passed=${result == true} protocol=${args.getOrNull(1)} adv=${logger.describe(advData)} result=${logger.describe(result)} method=${method.name}",
                force = true,
            )
        }
        hookAfter(clazz, "saveBattery") { method, args, result ->
            val scanResult = args.firstOrNull()
            logScan(
                event = "fastconnect_save_battery",
                method = method,
                scanResult = scanResult,
                result = result,
                extra = "notification_state_seen=true",
            )
        }
        hookAfter(clazz, "saveDeviceId") { method, args, result ->
            val scanResult = args.getOrNull(1)
            logScan(
                event = "fastconnect_save_device_id",
                method = method,
                scanResult = scanResult,
                result = result,
                extra = "cached_device_id_seen=true protocol=${args.firstOrNull()}",
            )
        }
        hookAfter(clazz, "getCachedDeviceId") { method, args, result ->
            val mac = args.firstOrNull() as? String
            val cachedDeviceId = result as? String
            logger.info(
                event = "fastconnect_get_cached_device_id",
                mac = mac,
                details = "cached_device_id_seen=${!cachedDeviceId.isNullOrBlank()} requested=${XiaomiBluetoothTraceConfig.maskMac(mac)} result=${logger.describe(result)} method=${method.name}",
                force = true,
            )
        }
        hookAfter(clazz, "handleActionConnectionStateChange") { method, args, result ->
            val device = args.firstOrNull() as? BluetoothDevice
            logger.info(
                event = "fastconnect_connection_state",
                mac = safeAddress(device),
                details = "notification_state_seen=true state=${args.getOrNull(1)} result=${logger.describe(result)} method=${method.name}",
                force = true,
            )
        }
        hookAfter(clazz, "sendMessageDelayObject") { method, args, result ->
            val what = args.getOrNull(0) as? Int ?: -1
            if (what == 33 || what == 34 || what == 35) {
                val mac = logger.macFromArgs(args)
                logger.info(
                    event = "fastconnect_notification_message",
                    mac = mac,
                    details = "notification_state_seen=true message=$what arg1=${args.getOrNull(1)} arg2=${args.getOrNull(2)} delay=${args.getOrNull(4)} result=${logger.describe(result)} method=${method.name}",
                    force = true,
                )
            }
        }
    }

    private fun hookStateMachine(clazz: Class<*>) {
        hookAfter(clazz, "getAssembleAdvData") { method, args, result ->
            val scanResult = args.firstOrNull()
            logScan(
                event = "fastconnect_sm_assemble_adv",
                method = method,
                scanResult = scanResult,
                result = result,
                extra = "assembled_adv=${result is ByteArray} adv=${logger.describe(result)}",
            )
        }
        hookAfter(clazz, "checkAdvData") { method, args, result ->
            logger.info(
                event = "fastconnect_sm_check_adv",
                mac = null,
                details = "check_adv_passed=${result == true} adv=${logger.describe(args.firstOrNull())} result=${logger.describe(result)} method=${method.name}",
                force = true,
            )
        }
        hookAfter(clazz, "checkAndStartConnecting") { method, args, result ->
            val scanResult = args.firstOrNull()
            logScan(
                event = "fastconnect_start_connecting",
                method = method,
                scanResult = scanResult,
                result = result,
                extra = "check_adv_passed=${result == true}",
                force = true,
            )
        }
        hookAfter(clazz, "startProductActivity") { method, args, result ->
            val scanResult = args.getOrNull(2)
            logScan(
                event = "fastconnect_start_product_activity",
                method = method,
                scanResult = scanResult,
                result = result,
                extra = "activity_started_attempt=true connectFlags=${args.getOrNull(0)} name=${args.getOrNull(1)}",
                force = true,
            )
        }
    }

    private fun hookControllerFactory(clazz: Class<*>) {
        hookAfter(clazz, "getController") { method, args, result ->
            logger.info(
                event = "fastconnect_controller_factory",
                mac = logger.macFromArgs(args),
                details = "controller=${result?.javaClass?.name} result=${logger.describe(result)} method=${method.name}",
                force = true,
            )
        }
    }

    private fun hookAfter(
        clazz: Class<*>,
        methodName: String,
        onAfter: (Method, List<Any?>, Any?) -> Unit,
    ) {
        val methods = clazz.findMethodsByName(methodName)
        if (methods.isEmpty()) {
            logger.methodMissing(clazz.name, methodName)
            return
        }
        methods.forEach { method ->
            ModuleMain.instance.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        runCatching { onAfter(method, chain.args, result) }
                            .onFailure { logger.warn("trace_error", "${clazz.name}.${method.name}", it) }
                        return result
                    }
                })
            logger.methodHooked(clazz.name, method.name)
        }
    }

    private fun logScan(
        event: String,
        method: Method,
        scanResult: Any?,
        result: Any?,
        extra: String,
        force: Boolean = false,
    ) {
        val mac = logger.macFromAny(scanResult)
        logger.info(
            event = event,
            mac = mac,
            details = "$extra ${logger.scanSummary(scanResult)} result=${logger.describe(result)} method=${method.name}",
            force = force,
        )
    }

    private fun safeAddress(device: BluetoothDevice?): String? =
        runCatching { device?.address }.getOrNull()

    companion object {
        const val FAST_CONNECT_SERVICE = "com.android.bluetooth.ble.app.fastconnect.MiuiFastConnectService"
        const val STATE_MACHINE = "com.android.bluetooth.ble.app.fastconnect.MiuiFastConnectStateMachine"
        const val CONTROLLER_FACTORY = "com.android.bluetooth.ble.app.fastconnect.MiuiFastConnectControllerFactory"

        val targetClassNames: Set<String> = setOf(
            FAST_CONNECT_SERVICE,
            STATE_MACHINE,
            CONTROLLER_FACTORY,
        )
    }
}
