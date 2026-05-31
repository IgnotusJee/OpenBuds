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
 * M1/M2 AirPods adapter hooks for `com.milink.service`.
 *
 * ## Hooks installed
 *
 * | Hook target | Type | Effect |
 * |------------|------|--------|
 * | `MxBluetoothManager.checkIsAirPods(String)` | Intercept | Returns `true` for allowlisted MACs |
 * | `MxBluetoothManager.getAirPodsState(String)` | Intercept | Returns a 9-element fixed state array for allowlisted MACs |
 * | `BluetoothServiceClient.isAirPods(BluetoothDevice)` | Intercept | Fallback if MxBluetoothManager signature changes |
 * | `BluetoothServiceClient.getAirpodsDeviceId(...)` | Trace | Logs deviceId resolution (no modification) |
 * | `BluetoothServiceClient.getAirpodsHeadsetType(...)` | Trace | Logs headset type mapping (no modification) |
 * | `ContentResolver.call(getAirpodsState, mac)` | Intercept | Returns fake 11-field Bundle so the HEADSET card renders |
 *
 * ## Safety
 *
 * - All hooks use `ExceptionMode.PROTECTIVE` — any hook exception falls through
 *   to the original method, preventing milink crashes.
 * - Original results of `true` (genuine AirPods) are always transparently preserved
 *   via [MilinkAirpodsTargetMatcher.airpodsDecision].
 * - The ContentResolver hook only intercepts the specific URI/method/MAC combination;
 *   all other ContentResolver calls are passed through unchanged.
 *
 * ## ContentResolver hook rationale
 *
 * When `checkIsAirPods` returns `true`, milink classifies the device as `HEADSET`
 * and expects state data via:
 * ```
 * ContentResolver.call(
 *     content://com.android.bluetooth.ble.app.headsetdata.provider/airpodsstate,
 *     "getAirpodsState",
 *     "XX:XX:XX:XX:XX:XX",
 *     null
 * )
 * ```
 *
 * Without this data, the HEADSET rendering path fails silently and the device
 * card disappears from the control center. This hook supplies a minimal 11-field
 * Bundle so the card renders with placeholder values until real state (M3) is
 * provided by the App bridge.
 *
 * @see MilinkAirpodsAdapterEntry
 * @see MilinkAirpodsTargetMatcher
 * @see docs/plan/MILINK_FIRST_PARTY_ADAPTER_PLAN.md
 */
class MilinkAirpodsM1Hook(
    private val classLoader: ClassLoader,
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

    /**
     * Hooks `MxBluetoothManager.checkIsAirPods(String mac): boolean` and
     * `MxBluetoothManager.getAirPodsState(String mac): String[]`.
     *
     * This is the primary entry point for AirPods classification. Milink calls
     * this to determine if a MAC belongs to an AirPods device.
     *
     * ## Operating modes
     *
     * | `debug.openbuds.milink_m1_intercept` | Behavior |
     * |---|---|
     * | `"false"` or unset (default) | **Trace-only**: logs the decision that *would* be made, returns original result. Safe — card rendering unaffected. |
     * | `"true"` | **Intercept**: applies [MilinkAirpodsTargetMatcher.airpodsDecision] and returns the overridden result. Requires ContentProvider data hooks (from M2) to render. |
     *
     * When trace-only (default), genuine AirPods still pass through unchanged
     * (their original result is already `true`).
     */
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
                    val macListValue = readDebugMacProperty()
                    val target = MilinkAirpodsTargetMatcher.isTargetMac(mac, macListValue)
                    val overridden = MilinkAirpodsTargetMatcher.airpodsDecision(
                        original, mac, macListValue
                    )
                    val mode = if (shouldIntercept()) "INTERCEPT" else "TRACE"
                    val result = if (shouldIntercept()) overridden else original
                    log(
                        "[$mode] MxBluetoothManager.checkIsAirPods mac=${safeMac(mac)} " +
                            "original=$original target=$target overridden=$overridden result=$result"
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
     * and `AncBatteryController.getAirpodsStatus`. M2 supplies fixed state for
     * allowlisted MACs only when the original method did not already return a
     * valid 9-element AirPods state, preserving genuine AirPods behavior.
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
                    val macListValue = readDebugMacProperty()
                    val target = MilinkAirpodsTargetMatcher.isTargetMac(mac, macListValue)
                    val intercept = shouldIntercept()
                    val originalValid = isValidAirpodsStateArray(original)
                    val result = if (intercept && target && !originalValid) {
                        AirpodsStateMapper.toStateArray(AirpodsStateMapper.placeholder(mac))
                    } else {
                        original
                    }
                    val mode = if (intercept) "INTERCEPT" else "TRACE"
                    log(
                        "[$mode] MxBluetoothManager.getAirPodsState mac=${safeMac(mac)} " +
                            "target=$target originalValid=$originalValid result=${stateArraySummary(result)}"
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
     * versions. Uses the same trace/intercept gating and
     * [MilinkAirpodsTargetMatcher.airpodsDecision] logic as the primary hook.
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
                    val macListValue = readDebugMacProperty()
                    val target = MilinkAirpodsTargetMatcher.isTargetMac(mac, macListValue)
                    val overridden = MilinkAirpodsTargetMatcher.airpodsDecision(
                        original, mac, macListValue
                    )
                    val mode = if (shouldIntercept()) "INTERCEPT" else "TRACE"
                    val result = if (shouldIntercept()) overridden else original
                    log(
                        "[$mode] BluetoothServiceClient.isAirPods fallback mac=${safeMac(mac)} " +
                            "name=${safeName(device)} original=$original target=$target overridden=$overridden result=$result"
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
     * - `call()`: intercepted for `getAirpodsState` → returns fake Bundle
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
     * Returns a synthetic 11-field [Bundle] when milink queries
     * `getAirpodsState` for an allowlisted MAC. All other calls fall through.
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

                    if (!shouldIntercept()
                        || callMethod != "getAirpodsState"
                        || uri == null
                        || uri.authority != AIRPODS_PROVIDER_AUTHORITY
                        || uri.path != AIRPODS_STATE_PATH
                    ) {
                        return chain.proceed()
                    }

                    val propertyValue = readDebugMacProperty()
                    if (!MilinkAirpodsTargetMatcher.isTargetMac(arg, propertyValue)) {
                        return chain.proceed()
                    }

                    val original = chain.proceed()
                    if (original is Bundle) {
                        log("ContentResolver.call getAirpodsState mac=$arg -> original Bundle")
                        return original
                    }

                    val bundle = createFakeAirpodsStateBundle(arg)
                    log("ContentResolver.call getAirpodsState mac=$arg -> fake Bundle")
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
     * Creates a minimal 11-field Bundle matching the AirPods state format
     * expected by milink's `AncBatteryController.registerAirpodsStateCallback`.
     *
     * Field mapping per the decompiled `airpodsBatteryParse` (line 919-963):
     *
     * | Key | Purpose | Placeholder | Real source (M3) |
     * |-----|---------|------------|------------------|
     * | device | MAC address | query arg | Bridge MAC |
     * | connectState | Connection flag ("2" = connected) | "2" | Bridge state |
     * | isLeftWearing | Left ear wearing status | "true" | Snapshot |
     * | leftBattery | Left battery 0–100 | "75" | Snapshot |
     * | isRightWearing | Right ear wearing status | "true" | Snapshot |
     * | rightBattery | Right battery 0–100 | "80" | Snapshot |
     * | boxBattery | Case battery 0–100 | "90" | Snapshot |
     * | isLeftCharging | Left ear in case/charging | "false" | Snapshot |
     * | isRightCharging | Right ear in case/charging | "false" | Snapshot |
     * | isBoxCharging | Case on charger | "false" | Snapshot |
     * | modelName | Device ID for icon selection | "01010101" | DeviceIdRegistry |
     */
    private fun createFakeAirpodsStateBundle(mac: String?): Bundle =
        Bundle().apply {
            AirpodsStateMapper.toBundleFields(AirpodsStateMapper.placeholder(mac)).forEach { (key, value) ->
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

    // ── Debug utilities ─────────────────────────────────────────────────

    /**
     * Reads the intercept mode flag from `SystemProperties`.
     *
     * When `"true"`: hooks change return values (device becomes HEADSET).
     * When `"false"` or unset (default): trace-only, return original results.
     */
    private fun shouldIntercept(): Boolean =
        runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod("get", String::class.java, String::class.java)
            (method.invoke(null, INTERCEPT_PROPERTY, "false") as? String) == "true"
        }.getOrDefault(false)

    /**
     * Reads the M1 debug MAC override via `SystemProperties.get()`.
     *
     * Uses reflection to access `android.os.SystemProperties` which is a
     * hidden API. Returns `null` if reflection fails (SELinux denial,
     * API removed, etc.) — matcher gracefully falls back to [hardcoded
     * defaults][MilinkAirpodsTargetMatcher.configuredTargets].
     */
    private fun readDebugMacProperty(): String? =
        runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod("get", String::class.java, String::class.java)
            method.invoke(null, MilinkAirpodsTargetMatcher.DEBUG_PROPERTY, "") as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Normalizes a MAC for safe logging. Returns trimmed uppercase or empty. */
    private fun safeMac(mac: String?): String =
        MilinkAirpodsTargetMatcher.normalizeMac(mac) ?: mac?.trim().orEmpty()

    /** Extracts Bluetooth device name for logging, defaulting to empty on failure. */
    private fun safeName(device: BluetoothDevice?): String =
        runCatching { device?.name.orEmpty() }.getOrDefault("")

    // ── Constants ───────────────────────────────────────────────────────

    private companion object {
        private const val TAG = "OpenBuds"

        /** System property key for the intercept/trace-only mode toggle. */
        private const val INTERCEPT_PROPERTY = "debug.openbuds.milink_m1_intercept"

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
