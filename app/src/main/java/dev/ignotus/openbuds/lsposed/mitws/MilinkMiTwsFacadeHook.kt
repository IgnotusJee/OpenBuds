package dev.ignotus.openbuds.lsposed.mitws

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
import dev.ignotus.openbuds.integration.milink.MilinkBridgeContract
import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import dev.ignotus.openbuds.integration.milink.normalizeMac
import dev.ignotus.openbuds.lsposed.ModuleMain
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * MiTWS hook surface for `com.milink.service`.
 *
 * M1 mutates only the MiTWS classification and deviceId for bridge-authorized
 * OpenBuds devices. Real MiTWS devices and all non-authorized devices stay on
 * the original Xiaomi path.
 */
class MilinkMiTwsFacadeHook(
    private val classLoader: ClassLoader,
    private val bridgeClient: MilinkBridgeClientFacade?,
) {
    private val devicesByMac = ConcurrentHashMap<String, BluetoothDevice>()
    private val callbackPump = MiTwsCallbackPump(
        deviceLookup = { mac -> deviceForMac(mac) },
        deviceIdForMac = ::assignedDeviceIdFor,
        allowNullDevice = true,
        mainHandler = runCatching { android.os.Handler(android.os.Looper.getMainLooper()) }.getOrNull(),
    )
    private val bridgeSnapshotListener: (MilinkDeviceSnapshot) -> Unit = { snapshot ->
        callbackPump.dispatchSnapshot(snapshot)
    }

    fun installTraceHooks() {
        val manager = loadClass(MX_BLUETOOTH_MANAGER) ?: return
        bridgeClient?.addSnapshotListener(bridgeSnapshotListener)

        hookCheckIsMiTws(manager)
        hookGetDeviceId(manager)
        hookMmaConnection(manager, "connectMma")
        hookMmaConnection(manager, "disconnectMma")
        hookBatteryLevel(manager)
        hookAncState(manager)
        hookWearStatus(manager)
        hookAncControl(manager, MiTwsControlMapper.METHOD_OPEN_ANC)
        hookAncControl(manager, MiTwsControlMapper.METHOD_OPEN_TRANSPARENT)
        hookAncControl(manager, MiTwsControlMapper.METHOD_CLOSE_ANC)
        installMiuiHeadsetTraceHooks()
        installAncControllerDiagnosticHooks()
        installProfileImplHooks()

        val callback = loadClass(MMA_CALLBACK)
        if (callback != null) {
            hookRegisterCallback(manager, callback)
            hookUnregisterCallback(manager, callback)
        } else {
            log("missing: $MMA_CALLBACK")
        }
    }

    private fun hookCheckIsMiTws(manager: Class<*>) {
        val method = findMethod(manager, "checkIsMiTWS", BluetoothDevice::class.java)
        if (method == null) {
            log("missing M1 facade: ${manager.name}.checkIsMiTWS(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val original = chain.proceed()
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    val mac = safeMac(device)
                    val snapshot = facadeSnapshot(mac)
                    val finalResult = if (original == MITWS_RESULT_TRUE || snapshot == null) {
                        original
                    } else {
                        MITWS_RESULT_TRUE
                    }
                    log(
                        "checkIsMiTWS mac=$mac name=${safeName(device)} " +
                            "original=${resultSummary(original)} final=${resultSummary(finalResult)} " +
                            "facadeTarget=${snapshot != null} gate=${facadeGateSummary()}"
                    )
                    return finalResult
                }
            })
        log("hooked M1 facade: ${manager.name}.${method.name}")
    }

    private fun hookGetDeviceId(manager: Class<*>) {
        val method = findMethod(manager, "getDeviceId", BluetoothDevice::class.java)
        if (method == null) {
            log("missing M1 facade: ${manager.name}.getDeviceId(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val original = chain.proceed()
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    val mac = safeMac(device)
                    val snapshot = facadeSnapshot(mac)
                    if (snapshot == null) {
                        log(
                            "getDeviceId mac=$mac name=${safeName(device)} " +
                                "original=${resultSummary(original)} final=${resultSummary(original)} " +
                                "facadeTarget=false gate=${facadeGateSummary()}"
                        )
                        return original
                    }
                    val assignedDeviceId = assignedDeviceIdFor(mac)
                    log(
                        "getDeviceId mac=$mac name=${safeName(device)} " +
                            "original=${resultSummary(original)} snapshot=${snapshot.deviceId} " +
                            "final=$assignedDeviceId template=${MilinkRouteConfig.selectedDeviceIdTemplate().label} " +
                            "facadeTarget=true gate=${facadeGateSummary()}"
                    )
                    return assignedDeviceId
                }
            })
        log("hooked M1 facade: ${manager.name}.${method.name}")
    }

    private fun hookMmaConnection(manager: Class<*>, name: String) {
        val method = findMethod(manager, name, BluetoothDevice::class.java)
        if (method == null) {
            log("missing M1 facade: ${manager.name}.$name(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    rememberDevice(device)
                    val mac = safeMac(device)
                    val snapshot = facadeSnapshot(mac)
                    val passthrough = MilinkRouteConfig.allowOpenBudsMmaPassthrough()
                    if (snapshot != null && !passthrough) {
                        // Only dispatch for connectMma. disconnectMma fires constantly
                        // (every ~100ms as the controller polls), and dispatching
                        // onConnectMmaStateChanged(false) each time would cause the UI
                        // to hide battery/ANC controls thinking MMA is disconnected.
                        if (name == "connectMma") {
                            callbackPump.dispatchConnection(
                                snapshot = snapshot,
                                connected = true,
                            )
                        }
                        log(
                            "$name mac=$mac name=${safeName(device)} " +
                                "facadeTarget=true passthrough=false facadeResult=$MMA_FACADE_SUCCESS_RESULT " +
                                "gate=${facadeGateSummary()}"
                        )
                        return MMA_FACADE_SUCCESS_RESULT
                    }
                    val result = chain.proceed()
                    log(
                        "$name mac=$mac name=${safeName(device)} " +
                            "facadeTarget=${snapshot != null} passthrough=$passthrough " +
                            "result=${resultSummary(result)} gate=${facadeGateSummary()}"
                    )
                    return result
                }
            })
        log("hooked M1 facade: ${manager.name}.${method.name}")
    }

    private fun hookBatteryLevel(manager: Class<*>) {
        val method = findMethod(manager, "getBatteryLevel", BluetoothDevice::class.java)
        if (method == null) {
            log("missing M2 facade: ${manager.name}.getBatteryLevel(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    rememberDevice(device)
                    val mac = safeMac(device)
                    val snapshot = facadeSnapshot(mac)
                    if (snapshot != null) {
                        callbackPump.dispatchSnapshot(snapshot, force = true)
                        log(
                            "getBatteryLevel mac=$mac name=${safeName(device)} " +
                                "facadeTarget=true facadeResult=$BATTERY_REQUEST_SUCCESS_RESULT " +
                                "battery=${MiTwsStateMapper.batteryArray(snapshot).joinToString(prefix = "[", postfix = "]")} " +
                                "gate=${facadeGateSummary()}"
                        )
                        return BATTERY_REQUEST_SUCCESS_RESULT
                    }
                    val result = chain.proceed()
                    log(
                        "trace getBatteryLevel mac=$mac name=${safeName(device)} " +
                            "facadeTarget=false result=${resultSummary(result)} gate=${facadeGateSummary()}"
                    )
                    return result
                }
            })
        log("hooked M2 facade: ${manager.name}.${method.name}")
    }

    private fun hookAncState(manager: Class<*>) {
        val method = findMethod(manager, "getAncState", BluetoothDevice::class.java)
        if (method == null) {
            log("missing M2 facade: ${manager.name}.getAncState(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    rememberDevice(device)
                    val mac = safeMac(device)
                    val snapshot = facadeSnapshot(mac)
                    if (snapshot != null) {
                        val ancState = MiTwsStateMapper.ancState(snapshot)
                        callbackPump.dispatchSnapshot(snapshot, force = true)
                        log(
                            "getAncState mac=$mac name=${safeName(device)} " +
                                "facadeTarget=true facadeResult=$ancState gate=${facadeGateSummary()}"
                        )
                        return ancState
                    }
                    val result = chain.proceed()
                    log(
                        "trace getAncState mac=$mac name=${safeName(device)} " +
                            "facadeTarget=false result=${resultSummary(result)} gate=${facadeGateSummary()}"
                    )
                    return result
                }
            })
        log("hooked M2 facade: ${manager.name}.${method.name}")
    }

    private fun hookWearStatus(manager: Class<*>) {
        val method = findMethod(manager, "getWearStatus", BluetoothDevice::class.java)
        if (method == null) {
            log("missing M2 facade: ${manager.name}.getWearStatus(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    rememberDevice(device)
                    val mac = safeMac(device)
                    val snapshot = facadeSnapshot(mac)
                    if (snapshot != null) {
                        val wearStatus = MiTwsStateMapper.wearStatus(snapshot)
                        log(
                            "getWearStatus mac=$mac name=${safeName(device)} " +
                                "facadeTarget=true facadeResult=$wearStatus " +
                                "leftWearing=${snapshot.leftWearing} rightWearing=${snapshot.rightWearing} " +
                                "gate=${facadeGateSummary()}"
                        )
                        return wearStatus
                    }
                    val result = chain.proceed()
                    log(
                        "trace getWearStatus mac=$mac name=${safeName(device)} " +
                            "facadeTarget=false result=${resultSummary(result)} gate=${facadeGateSummary()}"
                    )
                    return result
                }
            })
        log("hooked M2 facade: ${manager.name}.${method.name}")
    }

    private fun hookRegisterCallback(manager: Class<*>, callbackClass: Class<*>) {
        val method = findMethod(manager, "registerCallback", callbackClass)
        if (method == null) {
            log("missing M2 facade: ${manager.name}.registerCallback(${callbackClass.simpleName})")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val callback = chain.args.firstOrNull()
                    val result = chain.proceed()
                    val registered = callbackPump.register(callback, bridgeClient?.authorizedSnapshots().orEmpty())
                    log(
                        "registerCallback callback=${callback?.javaClass?.name} " +
                            "original=${resultSummary(result)} captured=$registered callbacks=${callbackPump.callbackCount()} " +
                            "gate=${facadeGateSummary()}"
                    )
                    return result
                }
            })
        log("hooked M2 callback capture: ${manager.name}.${method.name}")
    }

    private fun hookUnregisterCallback(manager: Class<*>, callbackClass: Class<*>) {
        val method = findMethod(manager, "unregisterCallback", callbackClass)
        if (method == null) {
            log("missing M2 facade: ${manager.name}.unregisterCallback(${callbackClass.simpleName})")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val callback = chain.args.firstOrNull()
                    val result = chain.proceed()
                    val removed = callbackPump.unregister(callback)
                    log(
                        "unregisterCallback callback=${callback?.javaClass?.name} " +
                            "original=${resultSummary(result)} removed=$removed callbacks=${callbackPump.callbackCount()} " +
                            "gate=${facadeGateSummary()}"
                    )
                    return result
                }
            })
        log("hooked M2 callback release: ${manager.name}.${method.name}")
    }

    private fun hookAncControl(manager: Class<*>, name: String) {
        val method = findMethod(manager, name, BluetoothDevice::class.java)
        if (method == null) {
            log("missing M3 ANC control: ${manager.name}.$name(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    val mac = safeMac(device)
                    val snapshot = facadeSnapshot(mac)
                    if (snapshot != null) {
                        val command = MiTwsControlMapper.buildAncCommand(name, snapshot)
                        if (command == null) {
                            log(
                                "$name mac=$mac name=${safeName(device)} " +
                                    "facadeTarget=true commandAccepted=false reason=${MilinkBridgeContract.REASON_UNSUPPORTED_CAPABILITY} " +
                                    "result=$CONTROL_FAILURE_RESULT supportsNoiseControl=${snapshot.supportsNoiseControl} " +
                                    "gate=${facadeGateSummary()}"
                            )
                            return CONTROL_FAILURE_RESULT
                        }
                        val commandResult = bridgeClient?.executeCommand(
                            mac = snapshot.mac,
                            command = command,
                            timeoutMs = BRIDGE_COMMAND_TIMEOUT_MS,
                        ) ?: MiTwsBridgeCommandResult.failed(
                            reason = MilinkBridgeContract.REASON_BRIDGE_UNAVAILABLE,
                            requestId = command.getString(MilinkBridgeContract.KEY_REQUEST_ID),
                        )
                        val facadeResult = if (commandResult.accepted) {
                            CONTROL_SUCCESS_RESULT
                        } else {
                            CONTROL_FAILURE_RESULT
                        }
                        log(
                            "$name mac=$mac name=${safeName(device)} " +
                                "facadeTarget=true commandType=${MiTwsControlMapper.commandType(command)} " +
                                "noiseMode=${MiTwsControlMapper.noiseMode(command)} " +
                                "requestId=${commandResult.requestId} accepted=${commandResult.accepted} " +
                                "reason=${commandResult.reason} result=$facadeResult " +
                                "gate=${facadeGateSummary()}"
                        )
                        return facadeResult
                    }
                    // Facade not active — fall through to original
                    val result = chain.proceed()
                    log(
                        "trace $name mac=$mac name=${safeName(device)} " +
                            "facadeTarget=false gate=${facadeGateSummary()} " +
                            "result=${resultSummary(result)}"
                    )
                    return result
                }
            })
        log("hooked M3 ANC control: ${manager.name}.${method.name}")
    }

    private fun installMiuiHeadsetTraceHooks() {
        val headsetService = loadClass(MIUI_HEADSET_SERVICE_PROXY) ?: return
        hookTrace(headsetService, "changeAncMode", Int::class.javaPrimitiveType ?: Int::class.java, BluetoothDevice::class.java)
        hookTrace(headsetService, "changeAncLevel", String::class.java, BluetoothDevice::class.java)
        hookTrace(headsetService, "changePlayStatus", Int::class.javaPrimitiveType ?: Int::class.java, BluetoothDevice::class.java)
        hookTrace(headsetService, "setCommonCommand", Int::class.javaPrimitiveType ?: Int::class.java, String::class.java, BluetoothDevice::class.java)
        hookTrace(headsetService, "ringFindForAirPods", String::class.java, Boolean::class.javaPrimitiveType ?: Boolean::class.java)
    }

    private fun installProfileImplHooks() {
        // ProfileImpl.updateHeadsetMode(String hostId, String address, String deviceId, int opAncMode)
        // is the entry point for ANC switching from the Circulate API control center.
        // The Circulate layer can't find OpenBuds devices in its device registry, so
        // the call fails before reaching AncBatteryController.setAncStateBlock().
        // Hook here to intercept and route to our bridge command.
        val profileImpl = loadClass(PROFILE_IMPL) ?: return
        val method = findMethod(profileImpl, "updateHeadsetMode",
            String::class.java, String::class.java, String::class.java, Int::class.javaPrimitiveType ?: Int::class.java)
        if (method == null) {
            log("missing M3 ANC control: ${profileImpl.name}.updateHeadsetMode(String, String, String, int)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val args = chain.args
                    val hostId = args.getOrNull(0) as? String ?: ""
                    val address = args.getOrNull(1) as? String ?: ""
                    val deviceId = args.getOrNull(2) as? String ?: ""
                    val opAncMode = (args.getOrNull(3) as? Int) ?: -1
                    val mac = address.normalizeMac() ?: ""
                    val snapshot = facadeSnapshot(mac)
                    if (snapshot != null) {
                        val methodName = when (opAncMode) {
                            0 -> MiTwsControlMapper.METHOD_CLOSE_ANC
                            1 -> MiTwsControlMapper.METHOD_OPEN_ANC
                            2 -> MiTwsControlMapper.METHOD_OPEN_TRANSPARENT
                            else -> null
                        }
                        if (methodName != null) {
                            val command = MiTwsControlMapper.buildAncCommand(methodName, snapshot)
                            if (command != null) {
                                val commandResult = bridgeClient?.executeCommand(
                                    mac = snapshot.mac,
                                    command = command,
                                    timeoutMs = BRIDGE_COMMAND_TIMEOUT_MS,
                                ) ?: MiTwsBridgeCommandResult.failed(
                                    reason = MilinkBridgeContract.REASON_BRIDGE_UNAVAILABLE,
                                )
                                if (commandResult.accepted) {
                                    log("updateHeadsetMode mac=$mac name=${snapshot.name} opAncMode=$opAncMode facadeTarget=true result=100 (accepted)")
                                    return 100
                                }
                                log("updateHeadsetMode mac=$mac name=${snapshot.name} opAncMode=$opAncMode facadeTarget=true result=201 (command rejected: ${commandResult.reason})")
                                return 201
                            }
                        }
                        log("updateHeadsetMode mac=$mac name=${snapshot.name} opAncMode=$opAncMode facadeTarget=true result=201 (unsupported mode)")
                        return 201
                    }
                    val result = chain.proceed()
                    log("trace updateHeadsetMode hostId=$hostId address=$address deviceId=$deviceId opAncMode=$opAncMode facadeTarget=false result=$result")
                    return result
                }
            })
        log("hooked M3 ANC control: ${profileImpl.name}.${method.name}")
    }

    private fun installAncControllerDiagnosticHooks() {
        // Hook AncBatteryController.setAncStateBlock to trace the ANC switching entry point
        val controller = loadClass(ANC_BATTERY_CONTROLLER) ?: return
        // setAncStateBlock(BluetoothDevice, int) → int
        hookTrace(controller, "setAncStateBlock", BluetoothDevice::class.java, Int::class.javaPrimitiveType ?: Int::class.java)
        // isSupportOpAnc(BluetoothDevice, int) → int (private, use declared method)
        val isSupportMethod = findMethod(controller, "isSupportOpAnc", BluetoothDevice::class.java, Int::class.javaPrimitiveType ?: Int::class.java)
        if (isSupportMethod != null) {
            hookTraceMethod(isSupportMethod)
            log("hooked diagnostic: ${controller.name}.isSupportOpAnc")
        } else {
            log("missing diagnostic: ${controller.name}.isSupportOpAnc(BluetoothDevice, int)")
        }
        // getAncState(BluetoothDevice) on AncBatteryController (not MxBluetoothManager)
        val getAncMethod = findMethod(controller, "getAncState", BluetoothDevice::class.java)
        if (getAncMethod != null) {
            hookTraceMethod(getAncMethod)
            log("hooked diagnostic: ${controller.name}.getAncState")
        } else {
            log("missing diagnostic: ${controller.name}.getAncState(BluetoothDevice)")
        }
        // getWearStatus(BluetoothDevice) on AncBatteryController
        val getWearMethod = findMethod(controller, "getWearStatus", BluetoothDevice::class.java)
        if (getWearMethod != null) {
            hookTraceMethod(getWearMethod)
            log("hooked diagnostic: ${controller.name}.getWearStatus")
        } else {
            log("missing diagnostic: ${controller.name}.getWearStatus(BluetoothDevice)")
        }
    }

    private fun hookTrace(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>) {
        val method = findMethod(clazz, name, *parameterTypes)
        if (method == null) {
            log("missing trace: ${clazz.name}.$name(${parameterTypes.joinToString { it.simpleName }})")
            return
        }
        hookTraceMethod(method)
        log("hooked trace: ${clazz.name}.${method.name}")
    }

    private fun hookTraceMethod(method: Method) {
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val device = chain.args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                    rememberDevice(device)
                    val mac = safeMac(device)
                    log(
                        "trace ${method.name} mac=$mac name=${safeName(device)} " +
                            "args=${argumentSummary(chain.args)} facadeTarget=${facadeSnapshot(mac) != null} " +
                            "gate=${facadeGateSummary()} result=${resultSummary(result)}"
                    )
                    return result
                }
            })
    }

    private fun loadClass(name: String): Class<*>? =
        runCatching {
            classLoader.loadClass(name).also { log("found: $name") }
        }.getOrElse {
            log("missing: $name")
            null
        }

    private fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? =
        runCatching {
            clazz.getDeclaredMethod(name, *parameterTypes).also { it.isAccessible = true }
        }.getOrNull()

    private fun safeMac(device: BluetoothDevice?): String =
        runCatching { device?.address?.normalizeMac() ?: device?.address.orEmpty() }
            .getOrDefault("")

    private fun safeName(device: BluetoothDevice?): String =
        runCatching { device?.name.orEmpty() }.getOrDefault("")

    private fun rememberDevice(device: BluetoothDevice?) {
        val mac = safeMac(device).normalizeMac() ?: return
        if (device != null) {
            devicesByMac[mac] = device
        }
    }

    private fun deviceForMac(mac: String): BluetoothDevice? {
        val normalized = mac.normalizeMac() ?: return null
        devicesByMac[normalized]?.let { return it }
        return runCatching {
            BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(normalized)
        }.getOrNull()?.also { devicesByMac[normalized] = it }
    }

    private fun facadeSnapshot(mac: String): MilinkDeviceSnapshot? {
        if (!MilinkRouteConfig.canUseFacade(bridgeClient)) return null
        return bridgeClient?.snapshotFor(mac)
    }

    private fun facadeGateSummary(): String =
        "system=${MilinkRouteConfig.isSystemFacadeEnabled()},adapter=${bridgeClient?.adapterEnabled == true}"

    private fun assignedDeviceIdFor(mac: String): String {
        val template = MilinkRouteConfig.selectedDeviceIdTemplate()
        val normalized = mac.normalizeMac() ?: return template.deviceId
        return assignedDeviceIds.computeIfAbsent(normalized) { template.deviceId }
    }

    private fun argumentSummary(args: List<Any?>): String =
        args.joinToString(prefix = "[", postfix = "]") { arg ->
            when (arg) {
                null -> "null"
                is BluetoothDevice -> "BluetoothDevice(${safeMac(arg)})"
                else -> arg.javaClass.name
            }
        }

    private fun resultSummary(result: Any?): String =
        when (result) {
            null -> "null"
            is IntArray -> result.joinToString(prefix = "[", postfix = "]")
            is Array<*> -> "Array(size=${result.size})"
            else -> result.toString()
        }

    companion object {
        const val MX_BLUETOOTH_MANAGER = "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager"
        const val MX_BLUETOOTH_SERVICE = "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService"
        const val MMA_CALLBACK = "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager\$MMACallback"
        const val MIUI_HEADSET_SERVICE_PROXY = "com.android.bluetooth.ble.app.IMiuiHeadsetService\$Stub\$Proxy"
        const val ANC_BATTERY_CONTROLLER = "com.miui.headset.runtime.AncBatteryController"
        const val PROFILE_IMPL = "com.miui.headset.runtime.ProfileImpl"

        private const val MITWS_RESULT_TRUE = 1
        private const val MMA_FACADE_SUCCESS_RESULT = 1
        private const val BATTERY_REQUEST_SUCCESS_RESULT = 1
        private const val CONTROL_SUCCESS_RESULT = 1
        private const val CONTROL_FAILURE_RESULT = 0
        private const val BRIDGE_COMMAND_TIMEOUT_MS = 50L
        private const val TAG = "OpenBuds"
        private val assignedDeviceIds = ConcurrentHashMap<String, String>()

        fun log(message: String) {
            Log.i(TAG, "[MiLinkMiTWS] $message")
        }
    }
}
