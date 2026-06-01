package dev.ignotus.openbuds.lsposed.milink

import android.bluetooth.BluetoothDevice
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import dev.ignotus.openbuds.lsposed.ModuleMain
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * MiLink AirPods-path hooks for `com.milink.service`.
 *
 * The hooks preserve genuine AirPods results and only synthesize OpenBuds
 * responses when the app-side bridge has an authorized, connected snapshot in
 * memory. With no bridge snapshot, every hook falls through to MiLink's original
 * behavior.
 */
class MilinkAirpodsM1Hook(
    private val classLoader: ClassLoader,
    private val bridgeClient: MilinkBridgeClientFacade,
) {
    /**
     * Installs all hooks. Called once per milink sub-process load.
     */
    fun install() {
        hookMxBluetoothManager()
        hookBluetoothServiceClient()
        hookContentResolverCall()
    }

    // ── MxBluetoothManager hooks ────────────────────────────────────────

    private fun hookMxBluetoothManager() {
        val clazz = loadClass(MX_BLUETOOTH_MANAGER) ?: return
        hookMxBluetoothManagerCheckIsAirPods(clazz)
        hookMxBluetoothManagerGetAirPodsState(clazz)
    }

    private fun hookMxBluetoothManagerCheckIsAirPods(clazz: Class<*>) {
        val method = findMethod(clazz, "checkIsAirPods", String::class.java)
        if (method == null) {
            log("missing: $MX_BLUETOOTH_MANAGER.checkIsAirPods(String)")
            return
        }

        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val mac = chain.args.getOrNull(0) as? String
                    val original = chain.proceed() as? Boolean ?: false
                    val target = bridgeClient.isAuthorized(mac)
                    val result = original || target
                    log(
                        "[BRIDGE] MxBluetoothManager.checkIsAirPods mac=${safeMac(mac)} " +
                            "original=$original target=$target result=$result"
                    )
                    return result
                }
            })
        log("hooked: $MX_BLUETOOTH_MANAGER.${method.name}(String)")
    }

    /**
     * Hooks `MxBluetoothManager.getAirPodsState(String mac): String[]`.
     *
     * This is the read path used by `BluetoothServiceClient.getAirpodsDeviceId`
     * and `AncBatteryController.getAirpodsStatus`. Bridge snapshots are used
     * only when the original method did not already return a valid 9-element
     * AirPods state, preserving genuine AirPods behavior.
     */
    private fun hookMxBluetoothManagerGetAirPodsState(clazz: Class<*>) {
        val method = findMethod(clazz, "getAirPodsState", String::class.java)
        if (method == null) {
            log("missing: $MX_BLUETOOTH_MANAGER.getAirPodsState(String)")
            return
        }

        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val mac = chain.args.getOrNull(0) as? String
                    val original = chain.proceed()
                    val originalValid = isValidAirpodsStateArray(original)
                    val snapshot = bridgeClient.snapshotFor(mac)
                    val result = when {
                        originalValid -> original
                        snapshot != null -> AirpodsStateMapper.toStateArray(AirpodsStateMapper.fromMilinkSnapshot(snapshot))
                        else -> original
                    }
                    log(
                        "[BRIDGE] MxBluetoothManager.getAirPodsState mac=${safeMac(mac)} " +
                            "snapshot=${snapshot != null} originalValid=$originalValid result=${stateArraySummary(result)}"
                    )
                    return result
                }
            })
        log("hooked: $MX_BLUETOOTH_MANAGER.${method.name}(String)")
    }

    // ── BluetoothServiceClient hooks ────────────────────────────────────

    /**
     * Installs all `BluetoothServiceClient` hooks: the `isAirPods` fallback
     * intercept plus two trace-only hooks.
     */
    private fun hookBluetoothServiceClient() {
        val clazz = loadClass(BLUETOOTH_SERVICE_CLIENT) ?: return

        hookBluetoothServiceIsAirPods(clazz)
        hookAirpodsDeviceIdTrace(clazz)
        hookAirpodsHeadsetTypeTrace(clazz)
    }

    /**
     * Fallback hook for `BluetoothServiceClient.isAirPods(BluetoothDevice): boolean`.
     *
     * Guards against [MxBluetoothManager] signature changes between HyperOS
     * versions. Uses the same bridge-cache gating as the primary hook.
     */
    private fun hookBluetoothServiceIsAirPods(clazz: Class<*>) {
        val method = findMethod(clazz, "isAirPods", BluetoothDevice::class.java)
        if (method == null) {
            log("missing: $BLUETOOTH_SERVICE_CLIENT.isAirPods(BluetoothDevice)")
            return
        }

        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val device = chain.args.getOrNull(0) as? BluetoothDevice
                    val mac = device?.address
                    val original = chain.proceed() as? Boolean ?: false
                    val target = bridgeClient.isAuthorized(mac)
                    val result = original || target
                    log(
                        "[BRIDGE] BluetoothServiceClient.isAirPods fallback mac=${safeMac(mac)} " +
                            "name=${safeName(device)} original=$original target=$target result=$result"
                    )
                    return result
                }
            })
        log("hooked: $BLUETOOTH_SERVICE_CLIENT.${method.name}(BluetoothDevice)")
    }

    /**
     * Trace-only hook for `getAirpodsDeviceId` / `getDeviceIdForAirpods`.
     *
     * Tries both `BluetoothDevice` and `String` parameter overloads. Logs the
     * deviceId that milink resolves for our device — critical data for the
     * deviceId → headsetType → icon mapping in later milestones.
     *
     * **Does not modify the return value.**
     */
    private fun hookAirpodsDeviceIdTrace(clazz: Class<*>) {
        // Try BluetoothDevice overload first
        val bluetoothMethod = findFirstMethod(
            clazz,
            listOf("getAirpodsDeviceId", "getDeviceIdForAirpods"),
            BluetoothDevice::class.java,
        )
        if (bluetoothMethod != null) {
            hookTraceMethod(bluetoothMethod) { args, result ->
                val device = args.getOrNull(0) as? BluetoothDevice
                "BluetoothServiceClient.${bluetoothMethod.name} mac=${safeMac(device?.address)} " +
                    "name=${safeName(device)} result=${result?.toString().orEmpty()}"
            }
            log("hooked trace: $BLUETOOTH_SERVICE_CLIENT.${bluetoothMethod.name}(BluetoothDevice)")
            return
        }

        // Fallback to String overload
        val stringMethod = findFirstMethod(
            clazz,
            listOf("getAirpodsDeviceId", "getDeviceIdForAirpods"),
            String::class.java,
        )
        if (stringMethod != null) {
            hookTraceMethod(stringMethod) { args, result ->
                "BluetoothServiceClient.${stringMethod.name} mac=${safeMac(args.getOrNull(0) as? String)} " +
                    "result=${result?.toString().orEmpty()}"
            }
            log("hooked trace: $BLUETOOTH_SERVICE_CLIENT.${stringMethod.name}(String)")
            return
        }

        log("missing trace: $BLUETOOTH_SERVICE_CLIENT.getAirpodsDeviceId/getDeviceIdForAirpods")
    }

    /**
     * Trace-only hook for `getAirpodsHeadsetType` / `getAirpodsHeadsetTypeForDeviceId`.
     *
     * Logs the headset type integer that milink resolves from a deviceId.
     * Key values per decompiled code:
     * - 0 = generic earbuds
     * - 4 = open-wear
     * - 5 = AirPods headset
     * - 6 = AirPods headphones
     *
     * **Does not modify the return value.**
     */
    private fun hookAirpodsHeadsetTypeTrace(clazz: Class<*>) {
        val method = findFirstMethod(
            clazz,
            listOf("getAirpodsHeadsetType", "getAirpodsHeadsetTypeForDeviceId"),
            String::class.java,
        )
        if (method == null) {
            log("missing trace: $BLUETOOTH_SERVICE_CLIENT.getAirpodsHeadsetType(String)")
            return
        }

        hookTraceMethod(method) { args, result ->
            "BluetoothServiceClient.${method.name} deviceId=${args.getOrNull(0)?.toString().orEmpty()} " +
                "result=${result?.toString().orEmpty()}"
        }
        log("hooked trace: $BLUETOOTH_SERVICE_CLIENT.${method.name}(String)")
    }

    // ── ContentResolver hooks ───────────────────────────────────────────

    /**
     * Hooks `ContentResolver.call()` and `ContentResolver.query()` to monitor
     * (and intercept) milink's access to the AirPods ContentProvider.
     *
     * ## Why both call() and query()
     *
     * The plan documents `call("getAirpodsState")` as the state query path,
     * but milink may also use `query()` for `/deviceinfo` or other paths.
     * Until we confirm which path milink actually hits, both are hooked.
     *
     * - `call()`: intercepted for `getAirpodsState` → returns bridge Bundle
     * - `query()`: **trace-only** — logs all headsetdata provider queries
     *   so we discover the actual URI/columns milink expects
     *
     * All other ContentResolver traffic passes through unchanged.
     */
    private fun hookContentResolverCall() {
        val crClazz = try {
            Class.forName("android.content.ContentResolver")
        } catch (_: Exception) {
            log("missing: android.content.ContentResolver")
            return
        }

        hookContentResolverCallMethod(crClazz)
        hookContentResolverQueryTrace(crClazz)
    }

    /**
     * Intercepts `ContentResolver.call(Uri, String, String, Bundle)`.
     *
     * Returns a bridge-backed 11-field [Bundle] when milink queries
     * `getAirpodsState` for an authorized MAC. All other calls fall through.
     */
    private fun hookContentResolverCallMethod(crClazz: Class<*>) {
        val method = findMethod(crClazz, "call", Uri::class.java, String::class.java, String::class.java, Bundle::class.java)
        if (method == null) {
            log("missing: ContentResolver.call(Uri, String, String, Bundle)")
            return
        }

        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val uri = chain.args.getOrNull(0) as? Uri
                    val callMethod = chain.args.getOrNull(1) as? String
                    val arg = chain.args.getOrNull(2) as? String

                    if (callMethod != "getAirpodsState"
                        || uri == null
                        || uri.authority != AIRPODS_PROVIDER_AUTHORITY
                        || uri.path != AIRPODS_STATE_PATH
                    ) {
                        return chain.proceed()
                    }

                    val snapshot = bridgeClient.snapshotFor(arg)
                    if (snapshot == null) {
                        return chain.proceed()
                    }

                    val bundle = createAirpodsStateBundle(snapshot)
                    log("ContentResolver.call getAirpodsState mac=$arg -> bridge Bundle")
                    return bundle
                }
            })
        log("hooked: ContentResolver.call(getAirpodsState)")
    }

    /**
     * Trace-only hook for `ContentResolver.query(Uri, ...)`.
     *
     * Logs every query targeting the `headsetdata` authority so we can
     * discover which URIs, projections, and selection args milink uses
     * after classifying a device as HEADSET.
     *
     * **Does not modify the return value.** Used solely for discovery.
     */
    private fun hookContentResolverQueryTrace(crClazz: Class<*>) {
        val projectionType = Array<String>::class.java
        val method = findFirstMethod(
            crClazz,
            listOf("query"),
            Uri::class.java,
            projectionType,
            Bundle::class.java,
            CancellationSignal::class.java,
        )
        if (method == null) {
            // Fallback to simpler query(Uri, String[], String, String[], String)
            val simpleMethod = findMethod(
                crClazz, "query",
                Uri::class.java, projectionType, String::class.java, projectionType, String::class.java,
            )
            if (simpleMethod == null) {
                log("missing trace: ContentResolver.query")
                return
            }
            hookQueryTraceMethod(simpleMethod)
            log("hooked trace: ContentResolver.query (5-arg)")
            return
        }

        hookQueryTraceMethod(method)
        log("hooked trace: ContentResolver.query (5-arg + Bundle + CancellationSignal)")
    }

    private fun hookQueryTraceMethod(method: Method) {
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val uri = chain.args.getOrNull(0) as? Uri
                    if (uri?.authority == AIRPODS_PROVIDER_AUTHORITY) {
                        val selection = chain.args.getOrNull(3)?.toString().orEmpty()
                        log(
                            "ContentResolver.query uri=$uri " +
                                "selection=$selection"
                        )
                    }
                    return chain.proceed()
                }
            })
    }

    /**
     * Creates an 11-field Bundle matching the AirPods state format
     * expected by milink's `AncBatteryController.registerAirpodsStateCallback`.
     */
    private fun createAirpodsStateBundle(snapshot: dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot): Bundle =
        Bundle().apply {
            AirpodsStateMapper.toBundleFields(AirpodsStateMapper.fromMilinkSnapshot(snapshot)).forEach { (key, value) ->
                putString(key, value)
            }
        }

    // ── Trace helper ────────────────────────────────────────────────────

    /**
     * Installs a pass-through trace hook on [method].
     *
     * Calls the original method, logs the result with [message], then returns
     * the original value unchanged. Used for deviceId and headsetType discovery
     * without affecting milink behavior.
     */
    private fun hookTraceMethod(
        method: Method,
        message: (List<Any?>, Any?) -> String,
    ) {
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    log(message(chain.args, result))
                    return result
                }
            })
    }

    // ── Reflection utilities ───────────────────────────────────────────

    /**
     * Loads a class via the milink [classLoader]. Returns `null` and logs if
     * the class is not found (e.g., different HyperOS version).
     */
    private fun loadClass(name: String): Class<*>? =
        runCatching {
            classLoader.loadClass(name).also { log("found: $name") }
        }.getOrElse {
            log("missing: $name")
            null
        }

    /**
     * Finds a declared method by name and exact parameter types.
     * Makes the method accessible on success.
     */
    private fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? =
        runCatching {
            clazz.getDeclaredMethod(name, *parameterTypes).also { it.isAccessible = true }
        }.getOrNull()

    /**
     * Finds the first declared method from [names] that matches [parameterTypes].
     * Used for methods that have multiple known aliases across HyperOS versions.
     */
    private fun findFirstMethod(
        clazz: Class<*>,
        names: List<String>,
        vararg parameterTypes: Class<*>,
    ): Method? =
        names.firstNotNullOfOrNull { name -> findMethod(clazz, name, *parameterTypes) }

    private fun isValidAirpodsStateArray(value: Any?): Boolean =
        (value as? Array<*>)?.size?.let { it >= AIRPODS_STATE_ARRAY_SIZE } == true

    private fun stateArraySummary(value: Any?): String {
        val array = value as? Array<*> ?: return value?.javaClass?.simpleName ?: "null"
        return "len=${array.size} deviceId=${array.getOrNull(8)?.toString().orEmpty()}"
    }

    /** Normalizes a MAC for safe logging. Returns trimmed uppercase or empty. */
    private fun safeMac(mac: String?): String =
        MilinkAirpodsTargetMatcher.normalizeMac(mac) ?: mac?.trim().orEmpty()

    /** Extracts Bluetooth device name for logging, defaulting to empty on failure. */
    private fun safeName(device: BluetoothDevice?): String =
        runCatching { device?.name.orEmpty() }.getOrDefault("")

    // ── Constants ───────────────────────────────────────────────────────

    private companion object {
        private const val TAG = "OpenBuds"

        /** Fully-qualified class names as found in HyperOS 3.0 dex. */
        private const val MX_BLUETOOTH_MANAGER =
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager"
        private const val BLUETOOTH_SERVICE_CLIENT =
            "com.miui.circulate.api.protocol.bluetooth.BluetoothServiceClient"

        /** ContentProvider URI components for AirPods state queries. */
        private const val AIRPODS_PROVIDER_AUTHORITY =
            "com.android.bluetooth.ble.app.headsetdata.provider"
        private const val AIRPODS_STATE_PATH = "/airpodsstate"
        private const val AIRPODS_STATE_ARRAY_SIZE = 9

        private fun log(message: String) {
            Log.i(TAG, "[MiLinkAirPodsM1] $message")
        }
    }
}
