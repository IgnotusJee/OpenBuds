package dev.ignotus.openbuds.lsposed

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import dev.ignotus.openbuds.protocol.NoiseControlMode
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.runBlocking

/**
 * Redirects MiLink's third-party headset MLCard path to the native first-party
 * headset layout and supplies the minimal first-party headset data it expects.
 */
class MiLinkHeadsetCardHook(private val classLoader: ClassLoader) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var probed = false
    private var hooked = false

    private var mlCardServiceClass: Class<*>? = null
    private var controllerClass: Class<*>? = null
    private var factoryClass: Class<*>? = null
    private var headsetDeviceInfoClass: Class<*>? = null
    private var headsetInfoClass: Class<*>? = null
    private var headsetHostClass: Class<*>? = null
    private var headsetClientInterface: Class<*>? = null
    private var profileInterface: Class<*>? = null
    private var queryInterface: Class<*>? = null
    private var multipointClass: Class<*>? = null

    // Cached hostListener from headset factory proxy — used to push HeadsetHost updates
    // proactively when controller data is requested (before initialize() fires)
    @Volatile
    private var cachedHostListener: Any? = null

    // Real BLE data bridge
    private var repository: dev.ignotus.openbuds.data.SonyHeadphoneRepository? = null
    private var dataBridgeStarted = false

    fun probe(): Boolean {
        if (probed) return mlCardServiceClass != null || controllerClass != null || factoryClass != null
        probed = true

        mlCardServiceClass = load("com.miui.circulate.world.MLCardViewHostService")
        controllerClass = load("com.miui.circulate.api.protocol.headset.b0")
        factoryClass = load("com.miui.headset.api.c")
        headsetDeviceInfoClass = load("com.miui.circulate.api.protocol.headset.HeadsetDeviceInfo")
        headsetInfoClass = load("com.miui.headset.api.HeadsetInfo")
        headsetHostClass = load("com.miui.headset.api.HeadsetHost")
        headsetClientInterface = load("com.miui.headset.api.k")
        profileInterface = load("com.miui.headset.api.m")
        queryInterface = load("com.miui.headset.api.n")
        multipointClass = load("com.miui.headset.api.l")

        return mlCardServiceClass != null || controllerClass != null || factoryClass != null
    }

    fun hook() {
        if (hooked) return
        hooked = true
        hookMlCardStrategy()
        hookControllerData()
        hookHeadsetClientFactory()
        ProbeResultCache.persistShared()
    }

    private fun hookMlCardStrategy() {
        val clazz = mlCardServiceClass ?: return
        val deviceInfoClass = load("com.miui.circulate.device.api.DeviceInfo") ?: return
        val method = try {
            findDeclaredMethod(clazz, methodNames("mo16375v"), arrayOf(deviceInfoClass, Integer.TYPE))
        } catch (e: Exception) {
            log("MLCard strategy method missing: ${e.message}")
            return
        } ?: return

        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val args = chain.args
                    val deviceInfo = args.getOrNull(0) ?: return chain.proceed()
                    val cardId = args.getOrNull(1) as? Int ?: return chain.proceed()
                    val deviceType = runCatching { invokeString(deviceInfo, "getDeviceType") }.getOrNull() ?: "?"
                    log("MLCardViewHostService.v() called: deviceType=$deviceType cardId=$cardId")
                    if (!isTargetThirdHeadsetDevice(deviceInfo)) return chain.proceed()

                    val service = chain.thisObject ?: return chain.proceed()
                    if (installNativeHeadsetStrategy(service, deviceInfo, cardId)) {
                        log("third_headset MLCard redirected to native headset strategy")
                        // Push HeadsetHost update when hostListener is available (may be delayed)
                        scheduleHostUpdateWhenReady(300)
                        return null
                    }
                    return chain.proceed()
                }
            })
        ProbeResultCache.markMethodFound(clazz.name, method.name)
        log("hooked: MLCardViewHostService.${method.name}")
    }

    private fun scheduleHostUpdateWhenReady(delayMs: Long) {
        if (delayMs > 5000) {
            log("scheduleHostUpdateWhenReady: giving up after 5s")
            return
        }
        val listener = cachedHostListener
        if (listener != null) {
            sendSyntheticHostUpdate(listener, "v().retry")
            return
        }
        mainHandler.postDelayed({
            scheduleHostUpdateWhenReady(delayMs + 500)
        }, delayMs)
    }

    private fun hookControllerData() {
        val clazz = controllerClass ?: return
        val serviceInfoClass = load("com.miui.circulate.api.service.CirculateServiceInfo") ?: return
        val deviceInfoClass = load("com.miui.circulate.api.service.CirculateDeviceInfo")

        hookServiceGetter(clazz, serviceInfoClass, "m19869B") { service -> syntheticDeviceInfo(service) }
        hookServiceGetter(clazz, serviceInfoClass, "m19868A") { service -> syntheticStateForService(service)?.powers }
        hookServiceGetter(clazz, serviceInfoClass, "m19870C") { service -> syntheticStateForService(service)?.mode }
        hookServiceGetter(clazz, serviceInfoClass, "m19871D") { service -> syntheticStateForService(service)?.name }
        hookServiceGetter(clazz, serviceInfoClass, "m19872F") { service -> if (isTargetService(service)) 0 else null }
        hookServiceGetter(clazz, serviceInfoClass, "m19873G") { service -> syntheticStateForService(service)?.volume }
        log("hooked: controller data getters")
        hookFutureGetter(clazz, serviceInfoClass, "m19877L") { 2 }
        hookFutureGetter(clazz, serviceInfoClass, "m19881X") { SUCCESS }
        hookFutureGetter(clazz, serviceInfoClass, "m19888e0") { SUCCESS }
        log("hooked: controller future getters (L/X/e0)")

        if (deviceInfoClass != null) {
            hookFutureGetter(
                clazz,
                arrayOf(deviceInfoClass, serviceInfoClass),
                "m19878M",
            ) { service -> if (isTargetService(service)) BOND_BONDED else null }
            hookFutureGetter(
                clazz,
                arrayOf(deviceInfoClass, serviceInfoClass),
                "m19880O",
            ) { service -> if (isTargetService(service)) false else null }
            log("hooked: controller future getters (M/O) with DeviceInfo")
        }

        hookSetOperation(clazz, serviceInfoClass, "m19883Z") { state, value ->
            state.mode = value.coerceIn(0, 2)
            // Forward ANC mode change to real BLE device
            forwardAncModeToDevice(value)
        }
        hookSetOperation(clazz, serviceInfoClass, "m19885b0") { state, value ->
            state.volume = value.coerceIn(0, 100)
            // Forward volume change to system Bluetooth volume via AudioManager
            forwardVolumeToSystem(value.coerceIn(0, 100))
        }
        hookSetOperation(clazz, serviceInfoClass, "m19882Y") { state, value ->
            state.audioEffect = value
        }
        log("hooked: controller set operations")

        // Start bridging real BLE data into synthetic state (retry until repository is ready)
        scheduleDataBridgeWhenReady(300)
    }

    private fun hookHeadsetClientFactory() {
        val clazz = factoryClass ?: return
        val clientInterface = headsetClientInterface ?: return
        val contextClass = try {
            android.content.Context::class.java
        } catch (_: Exception) {
            return
        }
        val serviceListenerClass = load("com.miui.headset.api.j") ?: return
        val hostListenerClass = load("com.miui.headset.api.i") ?: return
        val method = try {
            findDeclaredMethod(
                clazz,
                methodNames("m25629a"),
                arrayOf(contextClass, serviceListenerClass, hostListenerClass, String::class.java),
            )
        } catch (e: Exception) {
            log("headset factory method missing: ${e.message}")
            return
        } ?: return

        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val realClient = chain.proceed() ?: return null
                    val hostListener = chain.args.getOrNull(2)
                    cachedHostListener = hostListener  // store for proactive updates
                    val proxy = createClientProxy(clientInterface, realClient, hostListener)
                    log("wrapped headset client")
                    // Immediately push HeadsetHost — card may request data before initialize()
                    hostListener?.let { sendSyntheticHostUpdate(it, "factory.create") }
                    return proxy
                }
            })
        ProbeResultCache.markMethodFound(clazz.name, method.name)
        log("hooked: ${clazz.name}.${method.name}")
    }

    private fun installNativeHeadsetStrategy(service: Any, deviceInfo: Any, cardId: Int): Boolean {
        return runCatching {
            log("installNative: setting cardId=$cardId")
            setField(service, listOf("mCardId", "I"), cardId)
            setField(service, listOf("mDeviceInfo", "J"), deviceInfo)
            log("installNative: getting plugin via Z()")
            val plugin = invokeNoArg(service, "m21480Z", "Z") ?: run { log("plugin Z() returned null"); return false }
            log("installNative: calling plugin.n(service)")
            val strategy = invokeMethod(plugin, arrayOf("mo25186n", "n"), arrayOf(service))
                ?: run { log("plugin.n() returned null"); return false }
            log("installNative: setting mDeviceStrategy")
            setField(service, listOf("mDeviceStrategy", "R"), strategy)
            log("installNative: getting manager via b0()")
            val manager = invokeNoArg(service, "m21475b0", "b0") ?: run { log("manager b0() returned null"); return false }
            log("installNative: calling manager.g(strategy)")
            if (!invokeVoidMethod(manager, arrayOf("m21636g", "g"), arrayOf(strategy))) {
                log("manager.g() failed")
                return false
            }
            log("installNative: success")
            true
        }.onFailure {
            log("native strategy install failed: ${it.message}")
        }.getOrDefault(false)
    }

    private fun createClientProxy(clientInterface: Class<*>, realClient: Any, hostListener: Any?): Any {
        val profileProxy by lazy { createProfileProxy(realClient) }
        val queryProxy by lazy { createQueryProxy(realClient) }
        return Proxy.newProxyInstance(
            classLoader,
            arrayOf(clientInterface),
            InvocationHandler { _, method, args ->
                when (method.name) {
                    "getProfile" -> profileProxy ?: invokeOriginal(realClient, method, args)
                    "getQuery" -> queryProxy ?: invokeOriginal(realClient, method, args)
                    "initialize", "startDiscovery" -> {
                        val result = invokeOriginal(realClient, method, args)
                        scheduleSyntheticHostUpdate(hostListener, "client.${method.name}")
                        result
                    }
                    "circulateStart", "circulateEnd" -> {
                        if (args?.any { isTargetAddress(it as? String) } == true) SUCCESS
                        else invokeOriginal(realClient, method, args)
                    }
                    else -> invokeOriginal(realClient, method, args)
                }
            },
        )
    }

    private fun createProfileProxy(realClient: Any): Any? {
        val iface = profileInterface ?: return null
        val realProfile = runCatching { invokeNoArg(realClient, "getProfile") }.getOrNull()
        return Proxy.newProxyInstance(
            classLoader,
            arrayOf(iface),
            InvocationHandler { _, method, args ->
                val address = args?.getOrNull(1) as? String
                if (address != null && isTargetAddress(address)) {
                    when (method.name) {
                        "connect", "disconnect", "getHeadsetProperty" -> return@InvocationHandler SUCCESS
                        "updateHeadsetMode" -> {
                            stateFor(address).mode = miuiModeToUiMode(args.getOrNull(3) as? Int)
                            return@InvocationHandler SUCCESS
                        }
                        "updateHeadsetVolume" -> {
                            stateFor(address).volume =
                                (args.getOrNull(3) as? Int)?.coerceIn(0, 100) ?: stateFor(address).volume
                            return@InvocationHandler SUCCESS
                        }
                        "updateHeadsetAudioEffect" -> {
                            stateFor(address).audioEffect =
                                (args.getOrNull(3) as? Int) ?: stateFor(address).audioEffect
                            return@InvocationHandler SUCCESS
                        }
                    }
                }
                if (realProfile != null) invokeOriginal(realProfile, method, args) else defaultValue(method.returnType)
            },
        )
    }

    private fun createQueryProxy(realClient: Any): Any? {
        val iface = queryInterface ?: return null
        val realQuery = runCatching { invokeNoArg(realClient, "getQuery") }.getOrNull()
        return Proxy.newProxyInstance(
            classLoader,
            arrayOf(iface),
            InvocationHandler { _, method, args ->
                val address = args?.getOrNull(0) as? String
                if (isTargetAddress(address)) {
                    return@InvocationHandler when (method.name) {
                        "getBondStateWithTargetHost" -> BOND_BONDED
                        "getSupportAncMode" -> 7
                        "isMmaHeadset" -> false
                        "switchToHeadsetActivity" -> SUCCESS
                        "getMultipointInfo" -> newMultipointInfo()
                        else -> defaultValue(method.returnType)
                    }
                }
                if (realQuery != null) invokeOriginal(realQuery, method, args) else defaultValue(method.returnType)
            },
        )
    }

    private fun hookServiceGetter(
        clazz: Class<*>,
        serviceInfoClass: Class<*>,
        methodName: String,
        resultForService: (Any) -> Any?,
    ) {
        val method = runCatching {
            findDeclaredMethod(clazz, methodNames(methodName), arrayOf(serviceInfoClass))
        }.getOrNull()
        if (method == null) {
            ProbeResultCache.markMethodNotFound(clazz.name, methodName)
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val service = chain.args.getOrNull(0) ?: return chain.proceed()
                    val state = syntheticStateForService(service)
                    if (state == null) return chain.proceed()
                    val result = resultForService(service) ?: chain.proceed()
                    if (method.name in listOf("A", "B", "C", "D", "F", "G")) {
                        log("controller ${method.name} → ${result?.toString()?.take(80)}")
                    }
                    return result
                }
            })
        ProbeResultCache.markMethodFound(clazz.name, method.name)
    }

    private fun hookFutureGetter(
        clazz: Class<*>,
        serviceInfoClass: Class<*>,
        methodName: String,
        value: () -> Any?,
    ) {
        hookFutureGetter(clazz, arrayOf(serviceInfoClass), methodName) { service ->
            if (isTargetService(service)) value() else null
        }
    }

    private fun hookFutureGetter(
        clazz: Class<*>,
        parameterTypes: Array<Class<*>>,
        methodName: String,
        valueForService: (Any) -> Any?,
    ) {
        val method = runCatching {
            findDeclaredMethod(clazz, methodNames(methodName), parameterTypes)
        }.getOrNull()
        if (method == null) {
            ProbeResultCache.markMethodNotFound(clazz.name, methodName)
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val service = chain.args.lastOrNull() ?: return chain.proceed()
                    val value = valueForService(service) ?: return chain.proceed()
                    log("controller future ${method.name} → CompletableFuture(${value?.toString()?.take(60)})")
                    return CompletableFuture.completedFuture(value)
                }
            })
        ProbeResultCache.markMethodFound(clazz.name, method.name)
    }

    private fun hookSetOperation(
        clazz: Class<*>,
        serviceInfoClass: Class<*>,
        methodName: String,
        update: (SyntheticHeadsetState, Int) -> Unit,
    ) {
        val method = runCatching {
            findDeclaredMethod(clazz, methodNames(methodName), arrayOf(serviceInfoClass, Integer.TYPE))
        }.getOrNull()
        if (method == null) {
            ProbeResultCache.markMethodNotFound(clazz.name, methodName)
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val service = chain.args.getOrNull(0) ?: return chain.proceed()
                    val value = chain.args.getOrNull(1) as? Int ?: return chain.proceed()
                    if (!isTargetService(service)) return chain.proceed()
                    val state = syntheticStateForService(service) ?: return chain.proceed()
                    state.serviceRef = service
                    log("controller set ${methodName}($value)")
                    update(state, value)
                    notifyForSet(chain.thisObject, methodName, service, state)
                    return CompletableFuture.completedFuture(SUCCESS)
                }
            })
        ProbeResultCache.markMethodFound(clazz.name, method.name)
    }

    private fun notifyForSet(controller: Any?, methodName: String, service: Any, state: SyntheticHeadsetState) {
        val (notifyMethod, value) = when (methodName) {
            "m19883Z" -> "mo19898g" to state.mode
            "m19885b0" -> "mo19897d" to state.volume
            "m19882Y" -> "mo19899h" to state.audioEffect
            else -> return
        }
        notifyHeadsetListeners(controller, notifyMethod, service, value)
    }

    private fun notifyHeadsetListeners(controller: Any?, methodName: String, service: Any?, value: Int) {
        controller ?: return
        val listeners = runCatching { invokeNoArg(controller, "m50711b", "b") as? Iterable<*> }.getOrNull() ?: return
        val names = methodNames(methodName)
        mainHandler.post {
            for (listener in listeners) {
                if (listener == null || service == null) continue
                runCatching {
                    val method = listener.javaClass.methods.firstOrNull { m ->
                        m.name in names && m.parameterTypes.size == 2
                    } ?: listener.javaClass.declaredMethods.firstOrNull { m ->
                        m.name in names && m.parameterTypes.size == 2
                    } ?: return@runCatching
                    method.isAccessible = true
                    method.invoke(listener, service, value)
                }
            }
        }
    }

    private fun scheduleSyntheticHostUpdate(hostListener: Any?, reason: String) {
        if (hostListener == null) return
        // Send immediately on main thread — no delay needed since hooks provide data
        mainHandler.post {
            sendSyntheticHostUpdate(hostListener, reason)
        }
    }

    private fun sendSyntheticHostUpdate(hostListener: Any?, reason: String) {
        if (hostListener == null) return
        val mac = MiLinkIdentityHook.lastSonyMac ?: return
        val state = stateFor(mac)
        // Always send update — multiple cards may need it
        val headsetInfo = newHeadsetInfo(state) ?: return
        val host = newHeadsetHost(headsetInfo) ?: return
        runCatching {
            hostListener.javaClass.methods.first {
                it.name == "onHeadsetHostUpdate" && it.parameterTypes.size == 2
            }.invoke(hostListener, HEADSET_ACTIVE_CHANGE, host)
            log("synthetic HeadsetHost update sent ($reason)")
        }.onFailure {
            log("synthetic host update failed: ${it.message}")
        }
    }

    // ── Real BLE data bridge ─────────────────────────────

    private fun scheduleDataBridgeWhenReady(delayMs: Long) {
        if (dataBridgeStarted) return
        if (delayMs > 10_000) {
            log("DataBridge: giving up after 10s — repository never ready")
            return
        }
        val repo = MiLinkIdentityHook.preconnectRepository
        if (repo != null) {
            repository = repo
            dataBridgeStarted = true
            log("DataBridge: repository ready, starting collector")
            startDataBridgeCollector()
            return
        }
        mainHandler.postDelayed({
            scheduleDataBridgeWhenReady(delayMs + 500)
        }, delayMs)
    }

    private fun startDataBridgeCollector() {
        val repo = repository ?: return
        Thread({
            try {
                runBlocking {
                    repo.state.collect { uiState ->
                        applyRealState(uiState)
                    }
                }
            } catch (e: Exception) {
                log("DataBridge: collection stopped — ${e.message}")
                dataBridgeStarted = false
            }
        }, "OpenBuds-DataBridge").start()
    }

    private fun applyRealState(uiState: dev.ignotus.openbuds.data.SonyHeadphoneUiState) {
        val mac = MiLinkIdentityHook.lastSonyMac ?: return
        val state = stateFor(mac)
        var changed = false

        // ── Battery ──
        val bs = uiState.batteryState
        val newPowers = mutableListOf<Int>()
        if (bs.left != null || bs.right != null) {
            newPowers.add(bs.left ?: 0)
            newPowers.add(bs.right ?: 0)
            newPowers.add(bs.cradle ?: 0)
        } else {
            newPowers.add(bs.single ?: 0)
            newPowers.add(0)
            newPowers.add(0)
        }
        // Pad to 6 elements (MiUI expects [L, R, Case, ?, ?, ?])
        while (newPowers.size < 6) newPowers.add(0)
        if (state.powers != newPowers) {
            state.powers = newPowers
            changed = true
        }

        // ── ANC mode (reverse-sync: BLE → UI) ──
        val ncMode = uiState.noiseControlState.controlMode
        val uiMode = when (ncMode) {
            NoiseControlMode.NOISE_CANCELLING -> 0
            NoiseControlMode.AMBIENT_SOUND -> 1
            NoiseControlMode.OFF -> 2
            null -> state.mode // no data yet, keep current
        }
        if (state.mode != uiMode) {
            state.mode = uiMode
            changed = true
        }

        // ── Device name ──
        val repoName = uiState.connectedDevice?.name
            ?: uiState.deviceInfo.modelName
        if (!repoName.isNullOrBlank() && repoName != state.name) {
            state.name = repoName
            MiLinkIdentityHook.lastSonyName = repoName
            changed = true
        }

        if (changed) {
            mainHandler.post {
                val listener = cachedHostListener
                if (listener != null) {
                    sendSyntheticHostUpdate(listener, "dataBridge")
                }
            }
        }
    }

    // ── ANC / Volume forwarding ──────────────────────────

    private fun forwardAncModeToDevice(uiValue: Int) {
        val repo = MiLinkIdentityHook.preconnectRepository ?: return
        val ncMode = when (uiValue) {
            0 -> NoiseControlMode.NOISE_CANCELLING
            1 -> NoiseControlMode.AMBIENT_SOUND
            else -> NoiseControlMode.OFF
        }
        Thread({
            try {
                repo.setNoiseControlMode(ncMode)
                log("DataBridge: forwarded ANC mode $ncMode to device")
            } catch (e: Exception) {
                log("DataBridge: ANC forward failed — ${e.message}")
            }
        }, "OpenBuds-ANC").start()
    }

    private fun forwardVolumeToSystem(percent: Int) {
        try {
            val app = getApplicationContext() ?: return
            val am = app.getSystemService(AudioManager::class.java) ?: return
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVol <= 0) return
            val targetVol = ((percent / 100.0) * maxVol).toInt().coerceIn(0, maxVol)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            log("DataBridge: system volume → $targetVol/$maxVol ($percent%)")
        } catch (e: Exception) {
            log("DataBridge: volume forward failed — ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun getApplicationContext(): Context? {
        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val method = atClass.getDeclaredMethod("currentApplication")
            method.invoke(null) as? Context
        } catch (_: Exception) {
            null
        }
    }

    // ── Synthetic data builders ──────────────────────────

    private fun syntheticDeviceInfo(service: Any): Any? {
        val state = syntheticStateForService(service) ?: return null
        val clazz = headsetDeviceInfoClass ?: return null
        return runCatching {
            clazz.getDeclaredConstructor().newInstance().also { info ->
                setField(info, "mac", state.address)
                setField(info, "deviceId", state.address)
                setField(info, "name", state.name)
                setField(info, "vidPid", SYNTHETIC_VID_PID)
                setField(info, "headsetVolume", state.volume)
                setField(info, "power", state.powers)
                setField(info, "mode", state.mode)
                setField(info, "type", HEADSET_TYPE_DEFAULT)
                setField(info, "wiredState", 0)
                setField(info, "audioEffectState", state.audioEffect)
                setField(info, "isOutput", true)
            }
        }.onFailure {
            log("build HeadsetDeviceInfo failed: ${it.message}")
        }.getOrNull()
    }

    private fun newHeadsetInfo(state: SyntheticHeadsetState): Any? {
        val clazz = headsetInfoClass ?: return null
        return runCatching {
            clazz.getConstructor(
                String::class.java,
                String::class.java,
                String::class.java,
                List::class.java,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE,
            ).newInstance(
                state.address,
                state.name,
                SYNTHETIC_VID_PID,
                state.powers,
                uiModeToMiuiMode(state.mode),
                state.volume,
                HEADSET_TYPE_DEFAULT,
                0,
                0,
                state.audioEffect,
            )
        }.onFailure {
            log("build HeadsetInfo failed: ${it.message}")
        }.getOrNull()
    }

    private fun newHeadsetHost(headsetInfo: Any): Any? {
        val clazz = headsetHostClass ?: return null
        val infoClass = headsetInfoClass ?: return null
        return runCatching {
            val extra = Bundle().apply {
                putString("device_type", "")
                putString("id", LOCAL_DEVICE_ID)
            }
            clazz.getConstructor(
                String::class.java,
                infoClass,
                String::class.java,
                Bundle::class.java,
            ).newInstance(LOCAL_DEVICE_ID, headsetInfo, MiLinkIdentityHook.lastSonyName, extra)
        }.onFailure {
            log("build HeadsetHost failed: ${it.message}")
        }.getOrNull()
    }

    private fun newMultipointInfo(): Any? {
        val clazz = multipointClass ?: return null
        return runCatching {
            clazz.getConstructor(
                java.lang.Boolean.TYPE,
                String::class.java,
                List::class.java,
            ).newInstance(false, "", Collections.emptyList<String>())
        }.getOrNull()
    }

    private fun syntheticStateForService(service: Any): SyntheticHeadsetState? {
        val address = getStringField(service, "deviceId")
        if (address == null) {
            log("syntheticStateForService: no deviceId on ${service.javaClass.simpleName}")
            return null
        }
        val target = isTargetAddress(address)
        // Fallback: match by device name if MAC not cached yet
        val nameField = if (!target) getStringField(service, "devicesName") else null
        val nameMatch = nameField != null && MiLinkIdentityHook.matchesSonyPattern(nameField)
        if (!target && !nameMatch) {
            log("syntheticStateForService: not target addr=$address name=$nameField")
            return null
        }
        if (nameMatch && MiLinkIdentityHook.lastSonyMac == null) {
            MiLinkIdentityHook.cacheSonyDevice(address, nameField!!)
            DeviceWhitelist.add(address)
        }
        return stateFor(address).also {
            it.serviceRef = service
            val cachedName = MiLinkIdentityHook.lastSonyName
            if (!cachedName.isNullOrBlank()) it.name = cachedName
        }
    }

    private fun stateFor(address: String): SyntheticHeadsetState {
        val key = normalize(address)
        return stateByAddress.getOrPut(key) {
            SyntheticHeadsetState(
                address = address,
                name = MiLinkIdentityHook.lastSonyName ?: "Sony Headphones",
            )
        }
    }

    private fun isTargetThirdHeadsetDevice(deviceInfo: Any): Boolean {
        val type = invokeString(deviceInfo, "getDeviceType")
        if (type != "third_headset") return false
        val id = invokeString(deviceInfo, "getId")
        val mac = invokeString(deviceInfo, "getMac")
        val byMac = isTargetAddress(id) || isTargetAddress(mac)
        if (byMac) return true
        // Fallback: match by name if MAC not cached yet (cross-process race)
        val name = runCatching { invokeString(deviceInfo, "getName") }.getOrNull()
            ?: runCatching { invokeString(deviceInfo, "getDeviceName") }.getOrNull()
        if (name != null && MiLinkIdentityHook.matchesSonyPattern(name)) {
            // Cache this MAC for future calls (cross-process via whitelist)
            val addr = id ?: mac ?: return false
            MiLinkIdentityHook.cacheSonyDevice(addr, name)
            DeviceWhitelist.add(addr)
            return true
        }
        log("third_headset skipped: id=$id mac=$mac name=$name (no match)")
        return false
    }

    private fun isTargetService(service: Any): Boolean {
        val addr = getStringField(service, "deviceId")
        if (isTargetAddress(addr)) return true
        // Fallback: match by name if MAC not cached yet
        val name = getStringField(service, "devicesName")
        return name != null && MiLinkIdentityHook.matchesSonyPattern(name)
    }

    private fun isTargetAddress(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val normalized = normalize(address)
        val cached = MiLinkIdentityHook.lastSonyMac
        if (cached != null && normalize(cached) == normalized) return true
        return DeviceWhitelist.contains(address)
    }

    private fun load(name: String): Class<*>? {
        return try {
            classLoader.loadClass(name).also {
                ProbeResultCache.markFound(name)
                log("found: $name")
            }
        } catch (_: Exception) {
            ProbeResultCache.markNotFound(name)
            null
        }
    }

    private fun invokeOriginal(target: Any, method: Method, args: Array<Any?>?): Any? {
        return runCatching {
            method.invoke(target, *(args ?: emptyArray()))
        }.getOrElse {
            log("delegate ${method.name} failed: ${it.message}")
            defaultValue(method.returnType)
        }
    }

    private fun invokeNoArg(target: Any, vararg methodNames: String): Any? {
        return runCatching {
            val method = findNoArgMethod(target.javaClass, methodNames.toList()) ?: return null
            method.isAccessible = true
            method.invoke(target)
        }.getOrNull()
    }

    private fun invokeMethod(target: Any, methodNames: Array<String>, args: Array<Any?>): Any? {
        return runCatching {
            val method = findMethod(target.javaClass, methodNames.toList(), args.size) ?: return null
            method.isAccessible = true
            method.invoke(target, *args)
        }.getOrNull()
    }

    private fun invokeVoidMethod(target: Any, methodNames: Array<String>, args: Array<Any?>): Boolean {
        return runCatching {
            val method = findMethod(target.javaClass, methodNames.toList(), args.size) ?: return false
            method.isAccessible = true
            method.invoke(target, *args)
            true
        }.getOrDefault(false)
    }

    private fun invokeString(target: Any, methodName: String): String? = invokeNoArg(target, methodName) as? String

    private fun getStringField(target: Any, fieldName: String): String? {
        return runCatching {
            var c: Class<*>? = target.javaClass
            while (c != null) {
                val field = c.declaredFields.firstOrNull { it.name == fieldName }
                if (field != null) {
                    field.isAccessible = true
                    return@runCatching field.get(target) as? String
                }
                c = c.superclass
            }
            null
        }.getOrNull()
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        setField(target, listOf(fieldName), value)
    }

    private fun setField(target: Any, fieldNames: List<String>, value: Any?) {
        var c: Class<*>? = target.javaClass
        while (c != null) {
            val field = c.declaredFields.firstOrNull { it.name in fieldNames }
            if (field != null) {
                field.isAccessible = true
                field.set(target, value)
                return
            }
            c = c.superclass
        }
    }

    private fun findNoArgMethod(clazz: Class<*>, names: List<String>): Method? = findMethod(clazz, names, 0)

    private fun findMethod(clazz: Class<*>, names: List<String>, parameterCount: Int): Method? {
        var c: Class<*>? = clazz
        while (c != null) {
            val method = c.declaredMethods.firstOrNull {
                it.name in names && it.parameterTypes.size == parameterCount
            }
            if (method != null) return method
            c = c.superclass
        }
        return null
    }

    private fun findDeclaredMethod(clazz: Class<*>, names: List<String>, parameterTypes: Array<Class<*>>): Method? {
        return names.firstNotNullOfOrNull { name ->
            runCatching { clazz.getDeclaredMethod(name, *parameterTypes) }.getOrNull()
        }
    }

    private fun methodNames(decompiledName: String): List<String> {
        val actual = when (decompiledName) {
            "mo16375v" -> "v"
            "m25629a" -> "a"
            "m19868A" -> "A"
            "m19869B" -> "B"
            "m19870C" -> "C"
            "m19871D" -> "D"
            "m19872F" -> "F"
            "m19873G" -> "G"
            "m19877L" -> "L"
            "m19878M" -> "M"
            "m19880O" -> "O"
            "m19881X" -> "X"
            "m19882Y" -> "Y"
            "m19883Z" -> "Z"
            "m19885b0" -> "b0"
            "m19888e0" -> "e0"
            "m50711b" -> "b"
            "mo19897d" -> "d"
            "mo19898g" -> "g"
            "mo19899h" -> "h"
            "m21480Z" -> "Z"
            "mo25174D" -> "D"
            "m21475b0" -> "b0"
            "m21477f0" -> "f0"
            "mo25186n" -> "n"
            else -> null
        }
        return if (actual == null || actual == decompiledName) {
            listOf(decompiledName)
        } else {
            listOf(decompiledName, actual)
        }
    }

    private fun defaultValue(type: Class<*>): Any? {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            java.lang.Void.TYPE -> null
            else -> null
        }
    }

    private fun uiModeToMiuiMode(uiMode: Int): Int {
        return when (uiMode) {
            0 -> 1
            1 -> 2
            2 -> 0
            else -> -1
        }
    }

    private fun miuiModeToUiMode(miuiMode: Int?): Int {
        return when (miuiMode) {
            1 -> 0
            2 -> 1
            0 -> 2
            else -> 2
        }
    }

    private fun normalize(address: String): String = address.trim().uppercase()

    private data class SyntheticHeadsetState(
        val address: String,
        var name: String,
        var powers: List<Int> = listOf(85, 85, 85, 0, 0, 0),
        var mode: Int = 2,
        var volume: Int = 60,
        var audioEffect: Int = -1,
        var serviceRef: Any? = null
    )

    companion object {
        private const val SUCCESS = 100
        private const val BOND_BONDED = 306
        private const val HEADSET_ACTIVE_CHANGE = 2
        private const val HEADSET_TYPE_DEFAULT = 0
        private const val LOCAL_DEVICE_ID = "local_device_id"
        private const val SYNTHETIC_VID_PID = "1010101"

        private val stateByAddress = mutableMapOf<String, SyntheticHeadsetState>()

        private fun log(msg: String) {
            android.util.Log.i("OpenBuds", "[MiLinkCard] $msg")
        }
    }
}
