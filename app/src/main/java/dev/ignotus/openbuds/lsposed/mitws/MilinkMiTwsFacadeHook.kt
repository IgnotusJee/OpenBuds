package dev.ignotus.openbuds.lsposed.mitws

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Process
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
    private val runtimeProjection = MiTwsRuntimeProjection(
        classLoader = classLoader,
        bridgeClient = bridgeClient,
        deviceLookup = ::deviceForMac,
        deviceIdForMac = ::assignedDeviceIdFor,
    )
    private val bridgeSnapshotListener: (MilinkDeviceSnapshot) -> Unit = { snapshot ->
        log(
            "snapshotChanged mac=${snapshot.mac} revision=${snapshot.revision} " +
                "ancMode=${snapshot.ancMode} connected=${snapshot.connected} " +
                "protocolReady=${snapshot.protocolReady} supportsNoiseControl=${snapshot.supportsNoiseControl}"
        )
        callbackPump.dispatchSnapshot(snapshot)
    }

    fun installTraceHooks() {
        bridgeClient?.addSnapshotListener(bridgeSnapshotListener)

        val manager = loadClass(MX_BLUETOOTH_MANAGER)
        if (manager != null) {
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
        }
        installRuntimeProjectionHooks()
        installMiuiHeadsetTraceHooks()
        installRemoteProtocolTraceHooks()
        installAncControllerDiagnosticHooks()
        installProfileImplHooks()
        installQueryHooks()
        installFirstSnapshotBoostHooks()

        val callback = loadClass(MMA_CALLBACK)
        if (manager != null && callback != null) {
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
                    val classificationEligible = facadeClassificationEligible(mac)
                    val finalResult = if (original == MITWS_RESULT_TRUE || !classificationEligible) {
                        original
                    } else {
                        MITWS_RESULT_TRUE
                    }
                    log(
                        "checkIsMiTWS mac=$mac name=${safeName(device)} " +
                            "original=${resultSummary(original)} final=${resultSummary(finalResult)} " +
                            "classificationEligible=$classificationEligible gate=${facadeGateSummary()}"
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
                    val classificationEligible = facadeClassificationEligible(mac)
                    if (!classificationEligible) {
                        log(
                            "getDeviceId mac=$mac name=${safeName(device)} " +
                                "original=${resultSummary(original)} final=${resultSummary(original)} " +
                                "classificationEligible=false gate=${facadeGateSummary()}"
                        )
                        return original
                    }
                    val assignedDeviceId = assignedDeviceIdFor(mac)
                    log(
                        "getDeviceId mac=$mac name=${safeName(device)} " +
                            "original=${resultSummary(original)} " +
                            "final=$assignedDeviceId template=${MilinkRouteConfig.selectedDeviceIdTemplate().label} " +
                            "classificationEligible=true gate=${facadeGateSummary()}"
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
                        if (commandResult.accepted) {
                            val targetAncMode = MiTwsControlMapper.noiseModeForAncMethod(name)
                            if (targetAncMode != null && snapshot.ancMode != targetAncMode) {
                                val updatedSnapshot = snapshot.copy(ancMode = targetAncMode)
                                bridgeClient?.updateSnapshot(updatedSnapshot)
                                callbackPump.dispatchSnapshot(updatedSnapshot, force = true)
                                log(
                                    "$name mac=$mac name=${safeName(device)} " +
                                        "optimisticSnapshot=true oldAncMode=${snapshot.ancMode} " +
                                        "newAncMode=$targetAncMode requestId=${commandResult.requestId}"
                                )
                            } else {
                                log(
                                    "$name mac=$mac name=${safeName(device)} " +
                                        "optimisticSnapshot=skip currentAncMode=${snapshot.ancMode} " +
                                        "targetAncMode=$targetAncMode requestId=${commandResult.requestId}"
                                )
                            }
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

    private fun installRuntimeProjectionHooks() {
        hookDiscoveryProjection()
        hookProfileContextProjection()
    }

    private fun hookDiscoveryProjection() {
        val discoveryImpl = loadClass(DISCOVERY_IMPL) ?: return
        findMethod(discoveryImpl, "getActiveHeadset")?.let { method ->
            ModuleMain.instance.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val original = chain.proceed()
                        val originalMac = runtimeProjection.headsetDeviceAddress(original)
                        if (runtimeProjection.shouldSuppressRuntimeDevice(originalMac)) {
                            log(
                                "runtime getActiveHeadset suppress original=$originalMac " +
                                    "active=${runtimeProjection.activeSnapshot()?.mac} gate=${facadeGateSummary()}"
                            )
                            return null
                        }
                        if (!runtimeProjection.shouldProjectActiveHeadset(original)) {
                            return original
                        }
                        val projected = runtimeProjection.projectedActiveHeadset()
                        log(
                            "runtime getActiveHeadset original=${runtimeProjection.headsetDeviceAddress(original)} " +
                                "projected=${runtimeProjection.headsetDeviceAddress(projected)} " +
                                "active=${runtimeProjection.activeSnapshot()?.mac} gate=${facadeGateSummary()}"
                        )
                        return projected ?: original
                    }
                })
            log("hooked runtime projection: ${discoveryImpl.name}.${method.name}")
        } ?: log("missing runtime projection: ${discoveryImpl.name}.getActiveHeadset()")

        findMethod(discoveryImpl, "assembleHeadsetInfo")?.let { method ->
            ModuleMain.instance.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val original = chain.proceed()
                        val originalMac = runtimeProjection.headsetInfoAddress(original)
                        if (runtimeProjection.shouldSuppressRuntimeDevice(originalMac)) {
                            log(
                                "runtime assembleHeadsetInfo suppress original=$originalMac " +
                                    "gate=${facadeGateSummary()}"
                            )
                            return null
                        }
                        val snapshot = when {
                            originalMac != null -> runtimeProjection.snapshotFor(originalMac)
                            else -> runtimeProjection.activeSnapshot()
                        }
                        val projected = snapshot?.let { runtimeProjection.buildHeadsetInfo(it, original) }
                        log(
                            "runtime assembleHeadsetInfo original=$originalMac " +
                                "projected=${runtimeProjection.headsetInfoAddress(projected)} " +
                                "snapshot=${snapshot?.mac} gate=${facadeGateSummary()}"
                        )
                        return projected ?: original
                    }
                })
            log("hooked runtime projection: ${discoveryImpl.name}.${method.name}")
        } ?: log("missing runtime projection: ${discoveryImpl.name}.assembleHeadsetInfo()")

        val headsetInfo = loadClass(HEADSET_INFO)
        if (headsetInfo != null) {
            hookTrace(discoveryImpl, "notifyHeadsetInfoUpdate",
                Int::class.javaPrimitiveType ?: Int::class.java,
                headsetInfo,
                String::class.java,
            )
        }
    }

    private fun hookProfileContextProjection() {
        val profileContext = loadClass(PROFILE_CONTEXT) ?: return
        hookProfileContextConnectedDevices(profileContext)
        hookProfileContextActiveDevice(profileContext)
        hookProfileContextDeviceBoolean(profileContext, "isConnected")
        hookProfileContextDeviceBoolean(profileContext, "isActive")
        hookProfileContextDeviceId(profileContext)
        hookProfileContextBattery(profileContext)
        hookProfileContextAncState(profileContext)
        hookProfileContextVolume(profileContext)
        hookProfileContextAudioEffect(profileContext)
        hookProfileContextSwitchState(profileContext)
        hookProfileContextDeviceType(profileContext)
        hookProfileContextGetterTrace(profileContext, "getHeadsetProperty", BluetoothDevice::class.java)
        hookProfileContextGetterTrace(profileContext, "setAncState",
            BluetoothDevice::class.java,
            Int::class.javaPrimitiveType ?: Int::class.java,
        )
    }

    private fun hookProfileContextConnectedDevices(profileContext: Class<*>) {
        val method = findMethod(profileContext, "getConnectedDevices") ?: run {
            log("missing runtime projection: ${profileContext.name}.getConnectedDevices()")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val original = chain.proceed()
                    val projected = runtimeProjection.projectConnectedDevices(original)
                    log(
                        "runtime getConnectedDevices original=${iterableSize(original)} " +
                            "projected=${projected.size} active=${runtimeProjection.activeSnapshot()?.mac} " +
                            "gate=${facadeGateSummary()}"
                    )
                    return projected
                }
            })
        log("hooked runtime projection: ${profileContext.name}.${method.name}")
    }

    private fun hookProfileContextActiveDevice(profileContext: Class<*>) {
        val method = findMethod(profileContext, "getActiveDevice") ?: run {
            log("missing runtime projection: ${profileContext.name}.getActiveDevice()")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val original = chain.proceed()
                    val originalDevice = original as? BluetoothDevice
                    val originalMac = safeMac(originalDevice).normalizeMac()
                    if (runtimeProjection.shouldSuppressRuntimeDevice(originalMac)) {
                        log(
                            "runtime getActiveDevice suppress original=$originalMac " +
                                "gate=${facadeGateSummary()}"
                        )
                        return null
                    }
                    if (originalMac != null && runtimeProjection.snapshotFor(originalMac) == null) {
                        return original
                    }
                    val snapshot = runtimeProjection.activeSnapshot() ?: return original
                    val projected = deviceForMac(snapshot.mac) ?: return original
                    rememberDevice(projected)
                    log(
                        "runtime getActiveDevice original=$originalMac projected=${snapshot.mac} " +
                            "gate=${facadeGateSummary()}"
                    )
                    return projected
                }
            })
        log("hooked runtime projection: ${profileContext.name}.${method.name}")
    }

    private fun hookProfileContextDeviceBoolean(profileContext: Class<*>, name: String) {
        val method = findMethod(profileContext, name, BluetoothDevice::class.java) ?: run {
            log("missing runtime projection: ${profileContext.name}.$name(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    rememberDevice(device)
                    val mac = safeMac(device)
                    val snapshot = runtimeProjection.snapshotForDevice(device)
                    if (snapshot != null) {
                        val projected = when (name) {
                            "isConnected" -> runtimeProjection.isConnectedDevice(device)
                            "isActive" -> runtimeProjection.isActiveDevice(device)
                            else -> true
                        }
                        log("runtime $name mac=${snapshot.mac} projected=$projected gate=${facadeGateSummary()}")
                        return projected
                    }
                    if (runtimeProjection.isClassificationEligible(mac)) {
                        log("runtime $name mac=$mac classificationOnly=false gate=${facadeGateSummary()}")
                        return false
                    }
                    return chain.proceed()
                }
            })
        log("hooked runtime projection: ${profileContext.name}.${method.name}")
    }

    private fun hookProfileContextDeviceId(profileContext: Class<*>) {
        val method = findMethod(profileContext, "getDeviceId", BluetoothDevice::class.java) ?: run {
            log("missing runtime projection: ${profileContext.name}.getDeviceId(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    rememberDevice(device)
                    val snapshot = runtimeProjection.snapshotForDevice(device)
                    if (snapshot != null) {
                        val assigned = assignedDeviceIdFor(snapshot.mac)
                        log(
                            "runtime getDeviceId mac=${snapshot.mac} snapshot=${snapshot.deviceId} " +
                                "projected=$assigned gate=${facadeGateSummary()}"
                        )
                        return assigned
                    }
                    return chain.proceed()
                }
            })
        log("hooked runtime projection: ${profileContext.name}.${method.name}")
    }

    private fun hookProfileContextBattery(profileContext: Class<*>) {
        val method = findMethod(profileContext, "getBatteryLevel", BluetoothDevice::class.java) ?: run {
            log("missing runtime projection: ${profileContext.name}.getBatteryLevel(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    rememberDevice(device)
                    val snapshot = runtimeProjection.snapshotForDevice(device)
                    if (snapshot != null) {
                        val battery = MiTwsStateMapper.headsetInfoPowers(snapshot)
                        log("runtime getBatteryLevel mac=${snapshot.mac} projected=$battery gate=${facadeGateSummary()}")
                        return battery
                    }
                    return chain.proceed()
                }
            })
        log("hooked runtime projection: ${profileContext.name}.${method.name}")
    }

    private fun hookProfileContextAncState(profileContext: Class<*>) {
        val method = findMethod(profileContext, "getAncState", BluetoothDevice::class.java) ?: run {
            log("missing runtime projection: ${profileContext.name}.getAncState(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    rememberDevice(device)
                    val snapshot = runtimeProjection.snapshotForDevice(device)
                    if (snapshot != null) {
                        val ancState = MiTwsStateMapper.ancState(snapshot)
                        log("runtime getAncState mac=${snapshot.mac} projected=$ancState gate=${facadeGateSummary()}")
                        return ancState
                    }
                    return chain.proceed()
                }
            })
        log("hooked runtime projection: ${profileContext.name}.${method.name}")
    }

    private fun hookProfileContextVolume(profileContext: Class<*>) {
        val method = findMethod(profileContext, "getVolume") ?: run {
            log("missing runtime projection: ${profileContext.name}.getVolume()")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val snapshot = runtimeProjection.activeSnapshot()
                    if (snapshot?.supportsVolumeControl == true && snapshot.currentVolume != null) {
                        log("runtime getVolume mac=${snapshot.mac} projected=${snapshot.currentVolume} gate=${facadeGateSummary()}")
                        return snapshot.currentVolume
                    }
                    return chain.proceed()
                }
            })
        log("hooked runtime projection: ${profileContext.name}.${method.name}")
    }

    private fun hookProfileContextAudioEffect(profileContext: Class<*>) {
        val method = findMethod(profileContext, "getAudioSpatialEffectState", BluetoothDevice::class.java) ?: run {
            log("missing runtime projection: ${profileContext.name}.getAudioSpatialEffectState(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    rememberDevice(device)
                    val snapshot = runtimeProjection.snapshotForDevice(device)
                    if (snapshot?.supportsAudioEffect == true && snapshot.currentAudioEffectState != null) {
                        val state = snapshot.currentAudioEffectState
                        log("runtime getAudioSpatialEffectState mac=${snapshot.mac} projected=$state gate=${facadeGateSummary()}")
                        return state
                    }
                    return chain.proceed()
                }
            })
        log("hooked runtime projection: ${profileContext.name}.${method.name}")
    }

    private fun hookProfileContextSwitchState(profileContext: Class<*>) {
        val method = findMethod(profileContext, "getSwitchState", String::class.java) ?: run {
            log("missing runtime projection: ${profileContext.name}.getSwitchState(String)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val address = chain.args.firstOrNull() as? String
                    val snapshot = runtimeProjection.snapshotFor(address)
                    if (snapshot != null) {
                        log("runtime getSwitchState mac=${snapshot.mac} projected=0 gate=${facadeGateSummary()}")
                        return 0
                    }
                    return chain.proceed()
                }
            })
        log("hooked runtime projection: ${profileContext.name}.${method.name}")
    }

    private fun hookProfileContextDeviceType(profileContext: Class<*>) {
        val method = findMethod(profileContext, "getDeviceType", BluetoothDevice::class.java) ?: run {
            log("missing runtime projection: ${profileContext.name}.getDeviceType(BluetoothDevice)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val device = chain.args.firstOrNull() as? BluetoothDevice
                    val snapshot = runtimeProjection.snapshotForDevice(device)
                    if (snapshot != null) {
                        val projected = runtimeProjection.resolvedHeadsetInfoType(snapshot, original = null)
                        log("runtime getDeviceType mac=${snapshot.mac} projected=$projected formFactor=${snapshot.formFactor} gate=${facadeGateSummary()}")
                        return projected
                    }
                    return chain.proceed()
                }
            })
        log("hooked runtime projection: ${profileContext.name}.${method.name}")
    }

    private fun hookProfileContextGetterTrace(
        profileContext: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>,
    ) {
        val method = findMethod(profileContext, name, *parameterTypes) ?: run {
            log("missing runtime trace: ${profileContext.name}.$name")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val device = chain.args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                    rememberDevice(device)
                    val mac = safeMac(device)
                    log(
                        "runtime trace $name mac=$mac args=${argumentSummary(chain.args)} " +
                            "facadeTarget=${facadeSnapshot(mac) != null} result=${resultSummary(result)} " +
                            "gate=${facadeGateSummary()}"
                    )
                    return result
                }
            })
        log("hooked runtime trace: ${profileContext.name}.${method.name}")
    }

    private fun installProfileImplHooks() {
        // ProfileImpl.updateHeadsetMode(String hostId, String address, String deviceId, int opAncMode)
        // is the entry point for ANC switching from the Circulate API control center.
        // The Circulate layer can't find OpenBuds devices in its device registry, so
        // the call fails before reaching AncBatteryController.setAncStateBlock().
        // Hook here to intercept and route to our bridge command.
        val profileImpl = loadClass(PROFILE_IMPL) ?: return
        hookProfileGetHeadsetProperty(profileImpl)
        hookProfileUpdateHeadsetMode(profileImpl)
        hookProfileControlTrace(profileImpl, "updateHeadsetAudioEffect")
    }

    private fun hookProfileGetHeadsetProperty(profileImpl: Class<*>) {
        val method = findMethod(
            profileImpl,
            "getHeadsetProperty",
            String::class.java,
            String::class.java,
            String::class.java,
        ) ?: run {
            log("missing runtime profile: ${profileImpl.name}.getHeadsetProperty(String, String, String)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val hostId = chain.args.getOrNull(0) as? String ?: ""
                    val address = chain.args.getOrNull(1) as? String ?: ""
                    val deviceId = chain.args.getOrNull(2) as? String ?: ""
                    val mac = address.normalizeMac() ?: ""
                    val projectedTarget = runtimeProjection.targetMatchesActive(address, deviceId)
                    val result = chain.proceed()
                    if (facadeSnapshot(mac) != null &&
                        projectedTarget &&
                        result == MiTwsRuntimeProjection.PROFILE_TARGET_NOT_MATCH
                    ) {
                        log(
                            "getHeadsetProperty hostId=$hostId address=$address deviceId=$deviceId " +
                                "facadeTarget=true projectedTarget=true original=$result " +
                                "fallback=${MiTwsRuntimeProjection.PROFILE_SUCCESS}"
                        )
                        return MiTwsRuntimeProjection.PROFILE_SUCCESS
                    }
                    log(
                        "trace getHeadsetProperty hostId=$hostId address=$address deviceId=$deviceId " +
                            "facadeTarget=${facadeSnapshot(mac) != null} projectedTarget=$projectedTarget " +
                            "result=${resultSummary(result)} gate=${facadeGateSummary()}"
                    )
                    return result
                }
            })
        log("hooked runtime profile: ${profileImpl.name}.${method.name}")
    }

    private fun hookProfileUpdateHeadsetMode(profileImpl: Class<*>) {
        val method = findMethod(
            profileImpl,
            "updateHeadsetMode",
            String::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType ?: Int::class.java,
        ) ?: run {
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
                    if (snapshot == null && facadeClassificationEligible(mac)) {
                        log(
                            "updateHeadsetMode hostId=$hostId mac=$mac deviceId=$deviceId opAncMode=$opAncMode " +
                                "classificationOnly=true result=$PROFILE_FAILURE_RESULT gate=${facadeGateSummary()}"
                        )
                        return PROFILE_FAILURE_RESULT
                    }
                    if (snapshot != null) {
                        val projectedTarget = runtimeProjection.targetMatchesActive(address, deviceId)
                        val validationResult = if (MilinkRouteConfig.allowOpenBudsMmaPassthrough()) {
                            chain.proceed()
                        } else {
                            null
                        }
                        val commandResult = executeAncBridgeCommand(snapshot, opAncMode)
                        val facadeResult = if (commandResult.accepted) {
                            updateOptimisticAncSnapshot(snapshot, opAncMode, commandResult.requestId)
                            MiTwsRuntimeProjection.PROFILE_SUCCESS
                        } else {
                            PROFILE_FAILURE_RESULT
                        }
                        log(
                            "updateHeadsetMode hostId=$hostId mac=$mac name=${snapshot.name} " +
                                "deviceId=$deviceId opAncMode=$opAncMode facadeTarget=true " +
                                "projectedTarget=$projectedTarget nativeValidation=${resultSummary(validationResult)} " +
                                "accepted=${commandResult.accepted} reason=${commandResult.reason} " +
                                "requestId=${commandResult.requestId} result=$facadeResult gate=${facadeGateSummary()}"
                        )
                        return facadeResult
                    }
                    val result = chain.proceed()
                    log("trace updateHeadsetMode hostId=$hostId address=$address deviceId=$deviceId opAncMode=$opAncMode facadeTarget=false result=$result")
                    return result
                }
            })
        log("hooked M3 ANC control: ${profileImpl.name}.${method.name}")
    }

    private fun hookProfileControlTrace(profileImpl: Class<*>, name: String) {
        val method = findMethod(
            profileImpl,
            name,
            String::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType ?: Int::class.java,
        ) ?: run {
            log("missing runtime profile trace: ${profileImpl.name}.$name(String, String, String, int)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val hostId = chain.args.getOrNull(0) as? String ?: ""
                    val address = chain.args.getOrNull(1) as? String ?: ""
                    val deviceId = chain.args.getOrNull(2) as? String ?: ""
                    val value = (chain.args.getOrNull(3) as? Int) ?: -1
                    val mac = address.normalizeMac() ?: ""
                    val snapshot = facadeSnapshot(mac)
                    if (snapshot == null && facadeClassificationEligible(mac)) {
                        log(
                            "guard $name hostId=$hostId mac=$mac deviceId=$deviceId value=$value " +
                                "classificationOnly=true result=$PROFILE_FAILURE_RESULT gate=${facadeGateSummary()}"
                        )
                        return PROFILE_FAILURE_RESULT
                    }
                    if (snapshot != null) {
                        log(
                            "guard $name hostId=$hostId mac=$mac deviceId=$deviceId value=$value " +
                                "facadeTarget=true projectedTarget=${runtimeProjection.targetMatchesActive(address, deviceId)} " +
                                "capability(volume=${snapshot.supportsVolumeControl},audio=${snapshot.supportsAudioEffect}) " +
                                "result=$PROFILE_FAILURE_RESULT gate=${facadeGateSummary()}"
                        )
                        return PROFILE_FAILURE_RESULT
                    }
                    val result = chain.proceed()
                    log(
                        "trace $name hostId=$hostId address=$address deviceId=$deviceId value=$value " +
                            "facadeTarget=false result=${resultSummary(result)} gate=${facadeGateSummary()}"
                    )
                    return result
                }
            })
        log("hooked runtime profile trace: ${profileImpl.name}.${method.name}")
    }

    private fun executeAncBridgeCommand(
        snapshot: MilinkDeviceSnapshot,
        opAncMode: Int,
    ): MiTwsBridgeCommandResult {
        val command = MiTwsControlMapper.buildAncCommandForMode(opAncMode, snapshot)
            ?: return MiTwsBridgeCommandResult.failed(MilinkBridgeContract.REASON_UNSUPPORTED_CAPABILITY)
        return bridgeClient?.executeCommand(
            mac = snapshot.mac,
            command = command,
            timeoutMs = BRIDGE_COMMAND_TIMEOUT_MS,
        ) ?: MiTwsBridgeCommandResult.failed(
            reason = MilinkBridgeContract.REASON_BRIDGE_UNAVAILABLE,
            requestId = command.getString(MilinkBridgeContract.KEY_REQUEST_ID),
        )
    }

    private fun updateOptimisticAncSnapshot(
        snapshot: MilinkDeviceSnapshot,
        opAncMode: Int,
        requestId: String?,
    ) {
        if (snapshot.ancMode == opAncMode) {
            log(
                "updateHeadsetMode mac=${snapshot.mac} optimisticSnapshot=skip " +
                    "currentAncMode=${snapshot.ancMode} requestId=$requestId"
            )
            return
        }
        val updatedSnapshot = snapshot.copy(ancMode = opAncMode)
        bridgeClient?.updateSnapshot(updatedSnapshot)
        callbackPump.dispatchSnapshot(updatedSnapshot, force = true)
        log(
            "updateHeadsetMode mac=${snapshot.mac} optimisticSnapshot=true " +
                "oldAncMode=${snapshot.ancMode} newAncMode=$opAncMode requestId=$requestId"
        )
    }

    private fun installRemoteProtocolTraceHooks() {
        val proxy = loadClass(REMOTE_PROTOCOL_PROXY) ?: return
        hookTrace(proxy, "getHeadsetProperty", String::class.java, String::class.java, String::class.java)
        hookTrace(proxy, "updateHeadsetMode",
            String::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType ?: Int::class.java,
        )
        hookTrace(proxy, "updateHeadsetVolume",
            String::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType ?: Int::class.java,
        )
        hookTrace(proxy, "updateHeadsetAudioEffect",
            String::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType ?: Int::class.java,
        )
    }

    private fun installQueryHooks() {
        listOf(QUERY_LOCAL, QUERY_SERVER).forEach { className ->
            val queryClass = loadClass(className) ?: return@forEach
            hookGetSupportAncMode(queryClass)
            hookIsMmaHeadset(queryClass)
            hookGetBondStateWithTargetHost(queryClass)
        }
    }

    private fun hookGetSupportAncMode(queryClass: Class<*>) {
        val method = findMethod(queryClass, "getSupportAncMode", String::class.java, String::class.java) ?: run {
            log("missing query hook: ${queryClass.name}.getSupportAncMode(String, String)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val targetAddress = chain.args.getOrNull(0) as? String ?: ""
                    val deviceId = chain.args.getOrNull(1) as? String ?: ""
                    val mac = targetAddress.normalizeMac() ?: ""
                    val snapshot = facadeSnapshot(mac)
                    if (snapshot != null) {
                        val result = if (snapshot.supportsNoiseControl) {
                            QUERY_SUPPORT_ANC_MODE_THREE_STATE
                        } else {
                            QUERY_SUPPORT_ANC_MODE_TWO_STATE
                        }
                        log(
                            "query ${queryClass.simpleName}.getSupportAncMode target=$targetAddress deviceId=$deviceId " +
                                "facadeTarget=true supportsNoiseControl=${snapshot.supportsNoiseControl} result=$result"
                        )
                        return result
                    }
                    return chain.proceed()
                }
            })
        log("hooked query: ${queryClass.name}.${method.name}")
    }

    private fun hookIsMmaHeadset(queryClass: Class<*>) {
        val method = findMethod(queryClass, "isMmaHeadset", String::class.java, String::class.java) ?: run {
            log("missing query hook: ${queryClass.name}.isMmaHeadset(String, String)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val targetAddress = chain.args.getOrNull(0) as? String ?: ""
                    val targetHostId = chain.args.getOrNull(1) as? String ?: ""
                    val mac = targetAddress.normalizeMac() ?: ""
                    val snapshot = facadeSnapshot(mac)
                    if (snapshot != null) {
                        val result = targetHostId == LOCAL_DEVICE_ID ||
                            runtimeProjection.targetMatchesActive(targetAddress, assignedDeviceIdFor(mac))
                        log(
                            "query ${queryClass.simpleName}.isMmaHeadset target=$targetAddress hostId=$targetHostId " +
                                "facadeTarget=true result=$result"
                        )
                        return result
                    }
                    return chain.proceed()
                }
            })
        log("hooked query: ${queryClass.name}.${method.name}")
    }

    private fun hookGetBondStateWithTargetHost(queryClass: Class<*>) {
        val method = findMethod(queryClass, "getBondStateWithTargetHost", String::class.java, String::class.java) ?: run {
            log("missing query hook: ${queryClass.name}.getBondStateWithTargetHost(String, String)")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val targetAddress = chain.args.getOrNull(0) as? String ?: ""
                    val targetHostId = chain.args.getOrNull(1) as? String ?: ""
                    val mac = targetAddress.normalizeMac() ?: ""
                    val snapshot = facadeSnapshot(mac)
                    if (snapshot != null) {
                        val result = if (targetHostId == LOCAL_DEVICE_ID) {
                            QUERY_BOND_STATE_BONDED
                        } else {
                            QUERY_BOND_STATE_NOT_BONDED
                        }
                        log(
                            "query ${queryClass.simpleName}.getBondStateWithTargetHost target=$targetAddress hostId=$targetHostId " +
                                "facadeTarget=true result=$result"
                        )
                        return result
                    }
                    return chain.proceed()
                }
            })
        log("hooked query: ${queryClass.name}.${method.name}")
    }

    private fun installFirstSnapshotBoostHooks() {
        val discoveryImpl = loadClass(DISCOVERY_IMPL) ?: return
        val headsetInfoClass = loadClass(HEADSET_INFO) ?: return
        val method = findMethod(
            discoveryImpl,
            "notifyHeadsetInfoUpdate",
            Int::class.javaPrimitiveType ?: Int::class.java,
            headsetInfoClass,
            String::class.java,
        ) ?: return
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val status = chain.args.getOrNull(0) as? Int ?: -1
                    val headsetInfo = chain.args.getOrNull(1)
                    val mac = runtimeProjection.headsetInfoAddress(headsetInfo).orEmpty()
                    val snapshot = facadeSnapshot(mac)
                    if (snapshot != null && snapshot.supportsNoiseControl && status == HEADSET_NOTIFY_PROPERTY_CHANGED) {
                        callbackPump.dispatchSnapshot(snapshot, force = true)
                    }
                    return result
                }
            })
        log("hooked runtime boost: ${discoveryImpl.name}.${method.name}")
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

    private fun facadeClassificationEligible(mac: String): Boolean {
        if (!MilinkRouteConfig.canUseFacade(bridgeClient)) return false
        return bridgeClient?.isClassificationEligible(mac) == true
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

    private fun iterableSize(value: Any?): String =
        when (value) {
            is Collection<*> -> value.size.toString()
            is Iterable<*> -> value.count().toString()
            null -> "null"
            else -> value.javaClass.simpleName
        }

    companion object {
        const val MX_BLUETOOTH_MANAGER = "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager"
        const val MX_BLUETOOTH_SERVICE = "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService"
        const val MMA_CALLBACK = "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager\$MMACallback"
        const val MIUI_HEADSET_SERVICE_PROXY = "com.android.bluetooth.ble.app.IMiuiHeadsetService\$Stub\$Proxy"
        const val ANC_BATTERY_CONTROLLER = "com.miui.headset.runtime.AncBatteryController"
        const val DISCOVERY_IMPL = "com.miui.headset.runtime.DiscoveryImpl"
        const val HEADSET_INFO = "com.miui.headset.api.HeadsetInfo"
        const val PROFILE_CONTEXT = "com.miui.headset.runtime.ProfileContext"
        const val PROFILE_IMPL = "com.miui.headset.runtime.ProfileImpl"
        const val QUERY_LOCAL = "com.miui.headset.runtime.QueryLocal"
        const val QUERY_SERVER = "com.miui.headset.runtime.QueryServer"
        const val REMOTE_PROTOCOL_PROXY = "com.miui.headset.runtime.RemoteProtocol\$Proxy"

        private const val MITWS_RESULT_TRUE = 1
        private const val MMA_FACADE_SUCCESS_RESULT = 1
        private const val BATTERY_REQUEST_SUCCESS_RESULT = 1
        private const val CONTROL_SUCCESS_RESULT = 1
        private const val CONTROL_FAILURE_RESULT = 0
        private const val PROFILE_FAILURE_RESULT = 201
        private const val BRIDGE_COMMAND_TIMEOUT_MS = 50L
        private const val QUERY_SUPPORT_ANC_MODE_TWO_STATE = 3
        private const val QUERY_SUPPORT_ANC_MODE_THREE_STATE = 7
        private const val QUERY_BOND_STATE_BONDED = 306
        private const val QUERY_BOND_STATE_NOT_BONDED = 307
        private const val LOCAL_DEVICE_ID = "local_device_id"
        private const val HEADSET_NOTIFY_PROPERTY_CHANGED = 4
        private const val TAG = "OpenBuds"
        private val assignedDeviceIds = ConcurrentHashMap<String, String>()

        fun log(message: String) {
            val processName = runCatching {
                val atClass = Class.forName("android.app.ActivityThread")
                val currentProcessName = atClass.getDeclaredMethod("currentProcessName")
                currentProcessName.invoke(null) as? String
            }.getOrNull().orEmpty()
            Log.i(TAG, "[MiLinkMiTWS] process=$processName pid=${Process.myPid()} $message")
        }
    }
}
