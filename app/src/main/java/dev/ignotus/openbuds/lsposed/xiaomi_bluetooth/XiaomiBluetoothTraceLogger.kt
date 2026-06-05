package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.os.SystemClock
import android.util.Log
import dev.ignotus.openbuds.integration.milink.normalizeMac
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Array as ReflectArray
import java.util.concurrent.ConcurrentHashMap

class XiaomiBluetoothTraceLogger(
    private val sampleWindowMs: Long = XiaomiBluetoothTraceConfig.sampleWindowMs(),
    private val macAllowlist: Set<String> = XiaomiBluetoothTraceConfig.macAllowlist(),
) {
    private val lastLogAt = ConcurrentHashMap<String, Long>()

    fun info(event: String, mac: String?, details: String, force: Boolean = false) {
        if (mac != null && !XiaomiBluetoothTraceConfig.shouldTraceMac(mac, macAllowlist)) return
        if (mac == null && macAllowlist.isNotEmpty() && !force) return
        if (!force && !shouldSample(event, mac)) return
        Log.i(TAG, "$PREFIX event=$event mac=${XiaomiBluetoothTraceConfig.maskMac(mac)} $details")
    }

    fun warn(event: String, details: String, error: Throwable? = null) {
        if (error == null) {
            Log.w(TAG, "$PREFIX event=$event $details")
        } else {
            Log.w(TAG, "$PREFIX event=$event $details", error)
        }
    }

    fun methodHooked(className: String, methodName: String) {
        Log.i(TAG, "$PREFIX hooked=$className.$methodName")
    }

    fun methodMissing(className: String, methodName: String) {
        Log.i(TAG, "$PREFIX missing=$className.$methodName")
    }

    fun macFromArgs(args: List<Any?>): String? =
        args.firstNotNullOfOrNull(::macFromAny)

    fun macFromAny(value: Any?): String? {
        when (value) {
            is BluetoothDevice -> return safeDeviceAddress(value)
            is String -> return value.normalizeMac()
        }
        val scanDevice = noArgInvoke(value, "getDevice") as? BluetoothDevice
        if (scanDevice != null) return safeDeviceAddress(scanDevice)
        val deviceField = (fieldValue(value, "device") ?: fieldValue(value, "mDevice")) as? BluetoothDevice
        if (deviceField != null) return safeDeviceAddress(deviceField)
        return null
    }

    fun describe(value: Any?): String =
        when (value) {
            null -> "null"
            is String -> value.normalizeMac()?.let(XiaomiBluetoothTraceConfig::maskMac) ?: value
            is BluetoothDevice -> "BluetoothDevice(mac=${XiaomiBluetoothTraceConfig.maskMac(safeDeviceAddress(value))},name=${safeName(value)},bond=${safeBondState(value)})"
            is ByteArray -> "bytes(len=${value.size},hex=${value.toHex(MAX_HEX_BYTES)})"
            is IntArray -> "ints(${value.joinToString(limit = 8)})"
            is BooleanArray -> "booleans(${value.joinToString(limit = 8)})"
            is Array<*> -> "array(${value.joinToString(limit = 6) { describe(it) }})"
            is Collection<*> -> "collection(size=${value.size})"
            else -> describeObject(value)
        }

    fun describeArgs(args: List<Any?>): String =
        args.mapIndexed { index, arg -> "arg$index=${describe(arg)}" }
            .joinToString(separator = " ", limit = 8)

    fun scanSummary(scanResult: Any?): String {
        val device = noArgInvoke(scanResult, "getDevice") as? BluetoothDevice
        val rssi = noArgInvoke(scanResult, "getRssi")
        val isLegacy = noArgInvoke(scanResult, "isLegacy")
        val scanRecord = noArgInvoke(scanResult, "getScanRecord")
        val bytes = noArgInvoke(scanRecord, "getBytes") as? ByteArray
        val serviceData = serviceDataSummary(scanRecord)
        return "scan_device=${XiaomiBluetoothTraceConfig.maskMac(safeDeviceAddress(device))} rssi=$rssi legacy=$isLegacy record_len=${bytes?.size ?: -1} $serviceData"
    }

    fun characteristicSummary(characteristic: Any?): String {
        val typed = characteristic as? BluetoothGattCharacteristic
        val serviceUuid = runCatching { typed?.service?.uuid?.toString() }.getOrNull()
        val charUuid = runCatching { typed?.uuid?.toString() }.getOrNull()
        val value = runCatching { typed?.value }.getOrNull()
        return "service=$serviceUuid characteristic=$charUuid value=${describe(value)}"
    }

    private fun shouldSample(event: String, mac: String?): Boolean {
        if (sampleWindowMs <= 0L) return true
        val key = "$event:${mac?.normalizeMac().orEmpty()}"
        val now = SystemClock.uptimeMillis()
        val last = lastLogAt[key] ?: 0L
        if (now - last < sampleWindowMs) return false
        lastLogAt[key] = now
        return true
    }

    private fun describeObject(value: Any): String {
        val className = value.javaClass.name
        if (value.javaClass.isArray) {
            return "array(type=${value.javaClass.componentType?.simpleName},len=${ReflectArray.getLength(value)})"
        }
        return when {
            className.startsWith("java.") -> value.toString()
            className.startsWith("android.") -> className.substringAfterLast('.')
            className.startsWith("com.android.bluetooth") -> className.substringAfterLast('.')
            className.startsWith("com.xiaomi.bluetooth") -> className.substringAfterLast('.')
            else -> className.substringAfterLast('.')
        }
    }

    private fun serviceDataSummary(scanRecord: Any?): String {
        val map = fieldValue(scanRecord, "mServiceData")
        if (map != null) return "serviceData=$map"
        return "serviceData=unknown"
    }

    private fun safeDeviceAddress(device: BluetoothDevice?): String? =
        runCatching { device?.address }.getOrNull()

    private fun safeName(device: BluetoothDevice?): String =
        runCatching { device?.name.orEmpty() }.getOrDefault("")

    private fun safeBondState(device: BluetoothDevice?): Int =
        runCatching { device?.bondState ?: -1 }.getOrDefault(-1)

    private fun noArgInvoke(target: Any?, name: String): Any? =
        runCatching {
            target?.javaClass?.methods?.firstOrNull { method ->
                method.name == name && method.parameterTypes.isEmpty()
            }?.invoke(target)
        }.getOrNull()

    private fun fieldValue(target: Any?, name: String): Any? =
        runCatching {
            var clazz: Class<*>? = target?.javaClass
            while (clazz != null) {
                val field: Field? = clazz.declaredFields.firstOrNull { it.name == name }
                if (field != null) {
                    field.isAccessible = true
                    return@runCatching field.get(target)
                }
                clazz = clazz.superclass
            }
            null
        }.getOrNull()

    private fun ByteArray.toHex(maxBytes: Int): String =
        take(maxBytes).joinToString(separator = "") { "%02X".format(it) } +
            if (size > maxBytes) "..." else ""

    companion object {
        private const val TAG = "OpenBuds"
        private const val PREFIX = "[XIAOMI_BT_TRACE]"
        private const val MAX_HEX_BYTES = 24
    }
}

internal fun ClassLoader.loadTargetClass(name: String, logger: XiaomiBluetoothTraceLogger): Class<*>? =
    runCatching { loadClass(name) }
        .onFailure { logger.methodMissing(name, "<class>") }
        .getOrNull()

internal fun Class<*>.findMethodsByName(name: String): List<Method> =
    declaredMethods.filter { it.name == name }.onEach { it.isAccessible = true }

internal fun Class<*>.findMethodByNameAndArity(name: String, arity: Int): Method? =
    findMethodsByName(name).firstOrNull { it.parameterTypes.size == arity }
