package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import dev.ignotus.openbuds.integration.milink.normalizeMac
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class SonySppProxy(
    private val classLoader: ClassLoader,
    private val logger: XiaomiBluetoothTraceLogger,
) {
    private val started = AtomicBoolean(false)
    private val loadedTargetClasses = ConcurrentHashMap<String, Class<*>>()
    @Volatile private var featureClassLoader: ClassLoader? = null

    fun onLoadedClass(className: String, clazz: Class<*>) {
        if (className !in targetClassNames) return
        loadedTargetClasses[className] = clazz
        logger.info(
            event = "spp_proxy_target_class_loaded",
            mac = null,
            details = "class=$className loader=${clazz.classLoader?.javaClass?.name}",
            force = true,
        )
    }

    fun startOnce(context: Context) {
        if (!XiaomiBluetoothTraceConfig.isSppProxyEnabled()) return
        if (!started.compareAndSet(false, true)) return

        val targetMac = XiaomiBluetoothTraceConfig.sppProxyTargetMac()
        if (targetMac == null) {
            logger.info(
                event = "spp_proxy_config_invalid",
                mac = null,
                details = "reason=missing_or_invalid_mac property=${XiaomiBluetoothTraceConfig.SPP_PROXY_MAC_PROPERTY}",
                force = true,
            )
            return
        }

        Thread({
            runCatching {
                runProxy(context.applicationContext ?: context, targetMac)
            }.onFailure { logger.warn("spp_proxy_crash_guard", "proxy thread failed", it) }
        }, "OpenBuds-SonySppProxy").start()
    }

    @SuppressLint("MissingPermission")
    private fun runProxy(context: Context, targetMac: String) {
        Thread.sleep(PROXY_START_DELAY_MS)
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            logger.info(
                event = "spp_proxy_unavailable",
                mac = targetMac,
                details = "reason=bluetooth_adapter_unavailable_or_disabled",
                force = true,
            )
            return
        }
        val device = adapter.bondedDevices.orEmpty()
            .firstOrNull { candidate ->
                candidate.address.normalizeMac() == targetMac &&
                    candidate.type != BluetoothDevice.DEVICE_TYPE_LE
            }
        if (device == null) {
            logger.info(
                event = "spp_proxy_target_missing",
                mac = targetMac,
                details = "bonded_count=${adapter.bondedDevices.orEmpty().size}",
                force = true,
            )
            return
        }
        runCatching { adapter.cancelDiscovery() }
        logger.info(
            event = "spp_proxy_start",
            mac = targetMac,
            details = "transport=${XiaomiBluetoothTraceConfig.sppProxyTransport().propertyValue} " +
                "name=${safeName(device)} command_enabled=${XiaomiBluetoothTraceConfig.isSppProxyCommandEnabled()}",
            force = true,
        )

        val bridgeClientRef = arrayOfNulls<MilinkTransportProxyClient>(1)
        val strategyRef = arrayOfNulls<MiuiSppProxyStrategy>(1)
        val wire = SonySppWireSession(
            targetMac = targetMac,
            logger = logger,
            writer = { bytes -> strategyRef[0]?.let { strategyWrite(it, bytes) } == true },
            onStateChanged = { state ->
                bridgeClientRef[0]?.publishSnapshot(state, safeName(device))
            },
        )
        val bridgeClient = MilinkTransportProxyClient(
            context = context,
            targetMac = targetMac,
            logger = logger,
            onNoiseControlCommand = { mode ->
                strategyRef[0]?.sendNoiseControlMode(mode) == true
            },
        )
        bridgeClientRef[0] = bridgeClient
        val strategy = when (XiaomiBluetoothTraceConfig.sppProxyTransport()) {
            MiuiSppProxyTransport.PC -> PcMiuiSppProxyStrategy(
                context = context,
                classResolver = { name, preferredLoader ->
                    resolveTargetClass(context, name, preferredLoader)
                },
                device = device,
                targetMac = targetMac,
                logger = logger,
                wire = wire,
            )
            MiuiSppProxyTransport.DIRECT -> DirectMiuiSppProxyStrategy(
                device = device,
                targetMac = targetMac,
                logger = logger,
                wire = wire,
            )
        }
        strategyRef[0] = strategy

        bridgeClient.start()
        if (!strategy.start()) {
            logger.info(
                event = "spp_proxy_failed",
                mac = targetMac,
                details = "transport=${strategy.transport.propertyValue}",
                force = true,
            )
            bridgeClient.stop()
        }
    }

    private fun strategyWrite(strategy: MiuiSppProxyStrategy, bytes: ByteArray): Boolean =
        when (strategy) {
            is DirectMiuiSppProxyStrategy -> strategy.writeRaw(bytes)
            is PcMiuiSppProxyStrategy -> strategy.writeRaw(bytes)
            else -> false
        }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String =
        runCatching { device.name.orEmpty() }.getOrDefault("")

    private fun resolveTargetClass(context: Context, name: String, preferredLoader: ClassLoader?): Class<*>? {
        loadedTargetClasses[name]?.let { return it }
        preferredLoader?.loadTargetClassOrNull(name)?.let { clazz ->
            loadedTargetClasses[name] = clazz
            return clazz
        }
        context.classLoader?.loadTargetClassOrNull(name)?.let { clazz ->
            loadedTargetClasses[name] = clazz
            return clazz
        }
        classLoader.loadTargetClassOrNull(name)?.let { clazz ->
            loadedTargetClasses[name] = clazz
            return clazz
        }
        loadFeatureClassLoader(context)?.loadTargetClassOrNull(name)?.let { clazz ->
            loadedTargetClasses[name] = clazz
            logger.info(
                event = "spp_proxy_feature_class_loaded",
                mac = null,
                details = "class=$name loader=${clazz.classLoader?.javaClass?.name}",
                force = true,
            )
            return clazz
        }

        val deadline = System.currentTimeMillis() + PROXY_CLASS_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            loadedTargetClasses[name]?.let { return it }
            preferredLoader?.loadTargetClassOrNull(name)?.let { clazz ->
                loadedTargetClasses[name] = clazz
                return clazz
            }
            context.classLoader?.loadTargetClassOrNull(name)?.let { clazz ->
                loadedTargetClasses[name] = clazz
                return clazz
            }
            loadFeatureClassLoader(context)?.loadTargetClassOrNull(name)?.let { clazz ->
                loadedTargetClasses[name] = clazz
                logger.info(
                    event = "spp_proxy_feature_class_loaded",
                    mac = null,
                    details = "class=$name loader=${clazz.classLoader?.javaClass?.name}",
                    force = true,
                )
                return clazz
            }
            Thread.sleep(PROXY_CLASS_RETRY_MS)
        }
        logger.warn(
            event = "spp_proxy_target_class_unavailable",
            details = "class=$name waited_ms=$PROXY_CLASS_WAIT_MS",
        )
        return null
    }

    private fun loadFeatureClassLoader(context: Context): ClassLoader? {
        featureClassLoader?.let { return it }
        return synchronized(this) {
            featureClassLoader?.let { return@synchronized it }
            runCatching {
                val root = File(context.codeCacheDir, "openbuds_m5_qigsaw").also { it.mkdirs() }
                val featureZip = File(root, QIGSAW_PRELOADED_FEATURE_FILE)
                context.assets.open(QIGSAW_PRELOADED_FEATURE_ASSET).use { input ->
                    featureZip.outputStream().use { output -> input.copyTo(output) }
                }
                val optDir = File(root, "opt").also { it.mkdirs() }
                DexClassLoader(
                    featureZip.absolutePath,
                    optDir.absolutePath,
                    null,
                    context.classLoader,
                )
            }.onSuccess { loader ->
                logger.info(
                    event = "spp_proxy_feature_loader_ready",
                    mac = null,
                    details = "asset=$QIGSAW_PRELOADED_FEATURE_ASSET loader=${loader.javaClass.name}",
                    force = true,
                )
            }.onFailure { error ->
                logger.warn(
                    event = "spp_proxy_feature_loader_failed",
                    details = "asset=$QIGSAW_PRELOADED_FEATURE_ASSET",
                    error = error,
                )
            }.getOrNull()
                .also { featureClassLoader = it }
        }
    }

    companion object {
        const val PC_PERIPHERAL_SERVICE = "com.xiaomi.bluetooth.peripheral.MiuiPeripheralConnectionServiceReal"
        const val PC_CALLBACK_INTERFACE = "com.xiaomi.bluetooth.peripheral.IPCServiceEventCallback"
        const val PC_CALLBACK_STUB = "com.xiaomi.bluetooth.peripheral.IPCServiceEventCallback\$Stub"
        const val PC_SPP_PERIPHERAL = "com.xiaomi.bluetooth.peripheral.MiuiSppPeripheral"
        const val PC_SPP_PERIPHERAL_CALLBACK =
            "com.xiaomi.bluetooth.peripheral.MiuiSppPeripheral\$IPeripheralConnectionCallback"
        val targetClassNames: Set<String> = setOf(
            PC_PERIPHERAL_SERVICE,
            PC_CALLBACK_INTERFACE,
            PC_CALLBACK_STUB,
            PC_SPP_PERIPHERAL,
            PC_SPP_PERIPHERAL_CALLBACK,
        )

        private const val PROXY_START_DELAY_MS = 1_000L
        private const val PROXY_CLASS_WAIT_MS = 60_000L
        private const val PROXY_CLASS_RETRY_MS = 250L
        private const val QIGSAW_PRELOADED_FEATURE_ASSET = "qigsaw/preloadedFeature-master.zip"
        private const val QIGSAW_PRELOADED_FEATURE_FILE = "preloadedFeature-master.zip"
    }
}

private class DirectMiuiSppProxyStrategy(
    private val device: BluetoothDevice,
    private val targetMac: String,
    private val logger: XiaomiBluetoothTraceLogger,
    private val wire: SonySppWireSession,
) : MiuiSppProxyStrategy {
    override val transport: MiuiSppProxyTransport = MiuiSppProxyTransport.DIRECT
    private val closed = AtomicBoolean(false)
    private var socket: BluetoothSocket? = null

    override fun start(): Boolean {
        Thread({
            runCatching { runDirect() }
                .onFailure { logger.warn("spp_proxy_direct_failed", "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)}", it) }
        }, "OpenBuds-M5DirectSpp").start()
        return true
    }

    @SuppressLint("MissingPermission")
    private fun runDirect() {
        for (uuid in SonySppProbeUuid.EXPLICIT_UUIDS.map { it.uuid }) {
            val connected = runCatching {
                logger.info(
                    event = "spp_proxy_direct_connect_attempt",
                    mac = targetMac,
                    details = "uuid=$uuid",
                    force = true,
                )
                val candidate = device.createRfcommSocketToServiceRecord(uuid)
                socket = candidate
                candidate.connect()
                logger.info(
                    event = "spp_proxy_direct_connected",
                    mac = targetMac,
                    details = "uuid=$uuid",
                    force = true,
                )
                readLoop(candidate)
                true
            }.onFailure {
                logger.warn(
                    event = "spp_proxy_direct_connect_failed",
                    details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)} uuid=$uuid",
                    error = it,
                )
                runCatching { socket?.close() }
                socket = null
            }.getOrDefault(false)
            if (connected) return
        }
    }

    private fun readLoop(socket: BluetoothSocket) {
        wire.sendReadonlyBatteryQuery(waitForAck = false)
        val input = socket.inputStream
        val buffer = ByteArray(512)
        while (!closed.get()) {
            val read = runCatching { input.read(buffer) }.getOrDefault(-1)
            if (read < 0) break
            wire.ingest(buffer.copyOf(read))
        }
    }

    fun writeRaw(bytes: ByteArray): Boolean =
        runCatching {
            val output = socket?.outputStream ?: return false
            output.write(bytes)
            output.flush()
            true
        }.getOrDefault(false)

    override fun sendNoiseControlMode(mode: NoiseControlMode): Boolean =
        wire.sendNoiseControlMode(mode)

    override fun close() {
        closed.set(true)
        wire.close()
        runCatching { socket?.close() }
    }
}

private class PcMiuiSppProxyStrategy(
    private val context: Context,
    private val classResolver: (String, ClassLoader?) -> Class<*>?,
    private val device: BluetoothDevice,
    private val targetMac: String,
    private val logger: XiaomiBluetoothTraceLogger,
    private val wire: SonySppWireSession,
) : MiuiSppProxyStrategy {
    override val transport: MiuiSppProxyTransport = MiuiSppProxyTransport.PC
    private var pcService: Any? = null
    private var miuiSppPeripheral: Any? = null
    private var sendDataMethod: Method? = null
    private var peripheralSendDataMethod: Method? = null
    private var unregisterMethod: Method? = null
    private var serviceConnection: ServiceConnection? = null
    private var serviceBound: Boolean = false
    private val fallbackReadonlySent = AtomicBoolean(false)

    override fun start(): Boolean {
        ensurePcServiceCreated()
        val serviceClass = classResolver(SonySppProxy.PC_PERIPHERAL_SERVICE, null) ?: return false
        val serviceClassLoader = serviceClass.classLoader ?: context.classLoader
        val callback = createCallback(serviceClassLoader) ?: return false
        val service = waitForPcService(serviceClass)
            ?: return startMiuiSppPeripheralFallback(serviceClassLoader, callback)
        pcService = service
        val register = serviceClass.findMethodsByName("registerPCService")
            .firstOrNull { it.parameterTypes.size == 6 }
            ?: return false.also {
                logger.methodMissing(
                    SonySppProxy.PC_PERIPHERAL_SERVICE,
                    "registerPCService(device,type,uuid,action,package,callback)",
                )
            }
        sendDataMethod = serviceClass.findMethodsByName("sendData")
            .firstOrNull { it.parameterTypes.size == 2 }
        unregisterMethod = serviceClass.findMethodsByName("unRegisterPCService")
            .firstOrNull { it.parameterTypes.size == 2 }

        val uuid = SonySppProbeUuid.MDR_UUID_1.uuid.toString()
        val result = runCatching {
            register.invoke(
                service,
                device,
                PC_TYPE_SPP,
                uuid,
                XiaomiBluetoothTraceConfig.pcRegisterAction(),
                XiaomiBluetoothTraceConfig.pcRegisterPackage(),
                callback,
            ) as? Boolean == true
        }.onFailure { error ->
            logger.warn(
                event = "spp_proxy_pc_register_failed",
                details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)} uuid=$uuid " +
                    "package=${XiaomiBluetoothTraceConfig.pcRegisterPackage()} action=${XiaomiBluetoothTraceConfig.pcRegisterAction()}",
                error = error,
            )
        }.getOrDefault(false)
        logger.info(
            event = "spp_proxy_pc_register_result",
            mac = targetMac,
            details = "success=$result uuid=$uuid package=${XiaomiBluetoothTraceConfig.pcRegisterPackage()} " +
                "action=${XiaomiBluetoothTraceConfig.pcRegisterAction()}",
            force = true,
        )
        return result
    }

    private fun startMiuiSppPeripheralFallback(serviceClassLoader: ClassLoader, callback: Any): Boolean {
        val sppClass = classResolver(SonySppProxy.PC_SPP_PERIPHERAL, serviceClassLoader) ?: return false
        val callbackClass = classResolver(SonySppProxy.PC_SPP_PERIPHERAL_CALLBACK, sppClass.classLoader)
        val constructor = sppClass.declaredConstructors
            .firstOrNull { it.parameterTypes.size == 4 }
            ?.also { it.isAccessible = true }
            ?: return false.also {
                logger.methodMissing(SonySppProxy.PC_SPP_PERIPHERAL, "<init>(Context,device,uuid,callback)")
            }
        val connectMethod = sppClass.findMethodByNameAndArity("connect", 1)
            ?: return false.also { logger.methodMissing(SonySppProxy.PC_SPP_PERIPHERAL, "connect(boolean)") }
        val getStateMethod = sppClass.findMethodByNameAndArity("getConnectionState", 0)
        peripheralSendDataMethod = sppClass.findMethodByNameAndArity("sendData", 1)
            ?: return false.also { logger.methodMissing(SonySppProxy.PC_SPP_PERIPHERAL, "sendData(byte[])") }
        val clearMethod = sppClass.findMethodByNameAndArity("clear", 0)
        val uuid = SonySppProbeUuid.MDR_UUID_1.uuid.toString()

        val peripheral = runCatching {
            constructor.newInstance(context, device, uuid, callback)
        }.onFailure { error ->
            logger.warn(
                event = "spp_proxy_pc_miui_spp_create_failed",
                details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)} uuid=$uuid",
                error = error,
            )
        }.getOrNull() ?: return false
        miuiSppPeripheral = peripheral

        if (callbackClass != null) {
            val listener = createPeripheralListener(callbackClass, clearMethod)
            val registerListener = sppClass.findMethodByNameAndArity("registerPeripheralConnectionListener", 1)
            if (listener != null && registerListener != null) {
                runCatching { registerListener.invoke(peripheral, listener) }
                    .onFailure { error ->
                        logger.warn("spp_proxy_pc_miui_spp_listener_failed", SonySppProxy.PC_SPP_PERIPHERAL_CALLBACK, error)
                    }
            }
        }

        val accepted = runCatching { connectMethod.invoke(peripheral, false) as? Boolean == true }
            .onFailure { error ->
                logger.warn(
                    event = "spp_proxy_pc_miui_spp_connect_failed",
                    details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)} uuid=$uuid",
                    error = error,
                )
                runCatching { clearMethod?.invoke(peripheral) }
            }
            .getOrDefault(false)
        logger.info(
            event = "spp_proxy_pc_miui_spp_fallback",
            mac = targetMac,
            details = "accepted=$accepted uuid=$uuid note=pc_service_unavailable_using_miui_spp_peripheral",
            force = true,
        )
        if (!accepted) return false

        Thread({
            val deadline = System.currentTimeMillis() + PERIPHERAL_CONNECT_WAIT_MS
            while (System.currentTimeMillis() < deadline) {
                val state = runCatching { getStateMethod?.invoke(peripheral) as? Int }
                    .getOrNull()
                if (state == 2) {
                    logger.info(
                        event = "spp_proxy_pc_miui_spp_connected",
                        mac = targetMac,
                        details = "state=$state",
                        force = true,
                    )
                    sendFallbackReadonlyQueryOnce()
                    return@Thread
                }
                Thread.sleep(PERIPHERAL_CONNECT_RETRY_MS)
            }
            logger.warn(
                event = "spp_proxy_pc_miui_spp_connect_timeout",
                details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)} waited_ms=$PERIPHERAL_CONNECT_WAIT_MS",
            )
        }, "OpenBuds-MiuiSppFallbackWait").start()
        return true
    }

    private fun createPeripheralListener(callbackClass: Class<*>, clearMethod: Method?): Any? =
        runCatching {
            Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { _, method, args ->
                val values = args.orEmpty().toList()
                when (method.name) {
                    "onConnectionState" -> {
                        val state = values.getOrNull(2) as? Int
                        logger.info(
                            event = "spp_proxy_pc_miui_spp_status",
                            mac = logger.macFromArgs(values) ?: targetMac,
                            details = "type=${values.getOrNull(1)} state=$state last=${values.getOrNull(4)}",
                            force = true,
                        )
                        if (state == 2) sendFallbackReadonlyQueryOnce()
                    }
                    "onErrorCode" -> {
                        logger.info(
                            event = "spp_proxy_pc_miui_spp_error",
                            mac = logger.macFromArgs(values) ?: targetMac,
                            details = "type=${values.getOrNull(1)} sub=${values.getOrNull(2)} message=${values.getOrNull(3)}",
                            force = true,
                        )
                        runCatching { clearMethod?.invoke(miuiSppPeripheral) }
                    }
                    "toString" -> return@newProxyInstance "OpenBudsMiuiSppPeripheralCallback"
                    "hashCode" -> return@newProxyInstance System.identityHashCode(this)
                    "equals" -> return@newProxyInstance false
                }
                null
            }
        }.getOrElse { error ->
            logger.warn("spp_proxy_pc_miui_spp_listener_create_failed", callbackClass.name, error)
            null
        }

    private fun sendFallbackReadonlyQueryOnce() {
        if (fallbackReadonlySent.compareAndSet(false, true)) {
            wire.sendReadonlyBatteryQuery(waitForAck = false)
        }
    }

    private fun ensurePcServiceCreated() {
        val intent = Intent().setClassName(context.packageName, SonySppProxy.PC_PERIPHERAL_SERVICE)
        val startResult = runCatching { context.startService(intent) }
            .onFailure { error ->
                logger.warn(
                    event = "spp_proxy_pc_service_start_failed",
                    details = "component=${SonySppProxy.PC_PERIPHERAL_SERVICE}",
                    error = error,
                )
            }
            .getOrNull()

        if (serviceConnection == null) {
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    logger.info(
                        event = "spp_proxy_pc_service_bound",
                        mac = targetMac,
                        details = "component=${name.flattenToShortString()} binder=${service.javaClass.name}",
                        force = true,
                    )
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    serviceBound = false
                    logger.info(
                        event = "spp_proxy_pc_service_disconnected",
                        mac = targetMac,
                        details = "component=${name.flattenToShortString()}",
                        force = true,
                    )
                }
            }
            val bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
                .onFailure { error ->
                    logger.warn(
                        event = "spp_proxy_pc_service_bind_failed",
                        details = "component=${SonySppProxy.PC_PERIPHERAL_SERVICE}",
                        error = error,
                    )
                }
                .getOrDefault(false)
            if (bound) {
                serviceConnection = connection
                serviceBound = true
            }
        }

        logger.info(
            event = "spp_proxy_pc_service_create_requested",
            mac = targetMac,
            details = "start=${startResult?.flattenToShortString()} bound=$serviceBound",
            force = true,
        )
    }

    private fun waitForPcService(serviceClass: Class<*>): Any? {
        val getter = runCatching {
            serviceClass.getDeclaredMethod("getPeripheralConnectionServiceReal")
                .also { it.isAccessible = true }
        }.getOrElse { error ->
            logger.warn(
                event = "spp_proxy_pc_service_lookup_failed",
                details = "class=${SonySppProxy.PC_PERIPHERAL_SERVICE} reason=getter_missing",
                error = error,
            )
            return null
        }
        val deadline = System.currentTimeMillis() + PC_SERVICE_WAIT_MS
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            val service = runCatching { getter.invoke(null) }
                .onFailure { lastError = it }
                .getOrNull()
            if (service != null) return service
            Thread.sleep(PC_SERVICE_RETRY_MS)
        }
        logger.warn(
            event = "spp_proxy_pc_service_unavailable",
            details = "class=${SonySppProxy.PC_PERIPHERAL_SERVICE} waited_ms=$PC_SERVICE_WAIT_MS",
            error = lastError,
        )
        return null
    }

    private fun createCallback(serviceClassLoader: ClassLoader): Any? {
        val callbackInterface = classResolver(SonySppProxy.PC_CALLBACK_INTERFACE, serviceClassLoader) ?: return null
        val binder = Binder()
        return runCatching {
            Proxy.newProxyInstance(
                serviceClassLoader,
                arrayOf(callbackInterface),
            ) { _, method, args ->
                if (method.name == "asBinder") return@newProxyInstance binder
                handleCallbackInvocation(method.name, args.orEmpty().toList())
            }
        }.getOrElse { error ->
            logger.warn("spp_proxy_pc_callback_failed", SonySppProxy.PC_CALLBACK_INTERFACE, error)
            null
        }
    }

    private fun handleCallbackInvocation(name: String, args: List<Any?>): Any? {
        when (name) {
            "onPCServiceStatus" -> {
                logger.info(
                    event = "spp_proxy_pc_status",
                    mac = logger.macFromArgs(args) ?: targetMac,
                    details = "type=${args.getOrNull(1)} state=${args.getOrNull(2)} last=${args.getOrNull(3)}",
                    force = true,
                )
                if ((args.getOrNull(2) as? Int) == 2) {
                    wire.sendReadonlyBatteryQuery(waitForAck = false)
                }
            }
            "onPCServiceData" -> {
                val bytes = args.getOrNull(1) as? ByteArray ?: return null
                logger.info(
                    event = "spp_proxy_pc_rx",
                    mac = logger.macFromArgs(args) ?: targetMac,
                    details = "len=${bytes.size} data=${bytes.toHex()}",
                    force = true,
                )
                wire.ingest(bytes)
            }
            "onError" -> logger.info(
                event = "spp_proxy_pc_error",
                mac = targetMac,
                details = "type=${args.getOrNull(0)} sub=${args.getOrNull(1)} message=${args.getOrNull(2)}",
                force = true,
            )
            "asBinder" -> return null
        }
        return defaultReturn(name)
    }

    fun writeRaw(bytes: ByteArray): Boolean {
        val service = pcService
        val send = sendDataMethod
        if (service != null && send != null) {
            return runCatching {
                send.invoke(service, device, bytes) as? Boolean == true
            }.onFailure { error ->
                logger.warn(
                    event = "spp_proxy_pc_send_failed",
                    details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)} bytes=${bytes.toHex()}",
                    error = error,
                )
            }.getOrDefault(false)
        }
        val peripheral = miuiSppPeripheral ?: return false
        val peripheralSend = peripheralSendDataMethod ?: return false
        return runCatching {
            peripheralSend.invoke(peripheral, bytes) as? Boolean == true
        }.onFailure { error ->
            logger.warn(
                event = "spp_proxy_pc_miui_spp_send_failed",
                details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)} bytes=${bytes.toHex()}",
                error = error,
            )
        }.getOrDefault(false)
    }

    override fun sendNoiseControlMode(mode: NoiseControlMode): Boolean =
        wire.sendNoiseControlMode(mode)

    override fun close() {
        wire.close()
        val service = pcService
        val unregister = unregisterMethod
        if (service != null && unregister != null) {
            runCatching { unregister.invoke(service, device, PC_TYPE_SPP) }
        }
        miuiSppPeripheral?.let { peripheral ->
            runCatching {
                peripheral.javaClass.findMethodByNameAndArity("clear", 0)?.invoke(peripheral)
            }
        }
        val connection = serviceConnection
        if (connection != null && serviceBound) {
            runCatching { context.unbindService(connection) }
            serviceBound = false
        }
    }

    private fun defaultReturn(name: String): Any? =
        when (name) {
            "toString" -> "OpenBudsSonySppProxyCallback"
            "hashCode" -> System.identityHashCode(this)
            "equals" -> false
            else -> null
        }

    private companion object {
        private const val PC_TYPE_SPP = 1
        private const val PC_SERVICE_WAIT_MS = 15_000L
        private const val PC_SERVICE_RETRY_MS = 500L
        private const val PERIPHERAL_CONNECT_WAIT_MS = 15_000L
        private const val PERIPHERAL_CONNECT_RETRY_MS = 250L
    }
}

private fun ClassLoader.loadTargetClassOrNull(name: String): Class<*>? =
    runCatching { loadClass(name) }.getOrNull()
