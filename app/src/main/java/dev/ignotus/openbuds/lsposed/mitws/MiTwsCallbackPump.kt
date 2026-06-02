package dev.ignotus.openbuds.lsposed.mitws

import android.bluetooth.BluetoothDevice
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import dev.ignotus.openbuds.integration.milink.normalizeMac
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class MiTwsCallbackPump(
    private val deviceLookup: (String) -> BluetoothDevice?,
    private val deviceIdForMac: (String) -> String,
    private val asyncDispatcher: AsyncDispatcher = HandlerThreadDispatcher("OpenBuds-MiTwsCallbackPump"),
    private val allowNullDevice: Boolean = false,
    private val mainHandler: Handler? = null,
) {
    private val callbacks = mutableSetOf<Any>()
    private val lastRevisionByCallback = ConcurrentHashMap<CallbackMacKey, Long>()

    fun register(callback: Any?, snapshots: List<MilinkDeviceSnapshot> = emptyList()): Boolean {
        if (callback == null) return false
        synchronized(callbacks) {
            callbacks.add(callback)
        }
        snapshots.forEach { dispatchSnapshotLocked(callback, it, force = true) }
        return true
    }

    fun unregister(callback: Any?): Boolean {
        if (callback == null) return false
        asyncDispatcher.dispatch {
            synchronized(callbacks) {
                callbacks.remove(callback)
            }
            val identity = System.identityHashCode(callback)
            lastRevisionByCallback.keys.removeAll { it.callbackIdentity == identity }
        }
        return true
    }

    fun dispatchSnapshot(snapshot: MilinkDeviceSnapshot, force: Boolean = false) {
        val targets = snapshotTargets()
        if (targets.isEmpty()) return
        // dispatchSnapshot is called from the bridge handler thread (Binder).
        // MMACallback methods must be invoked on the main thread — the original
        // MiaoXiangCallbackProxy uses mHandler.post. UI updates from a background
        // thread are silently ignored.
        if (mainHandler != null && Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { targets.forEach { dispatchSnapshotLocked(it, snapshot, force) } }
        } else {
            targets.forEach { dispatchSnapshotLocked(it, snapshot, force) }
        }
    }

    fun dispatchConnection(snapshot: MilinkDeviceSnapshot, connected: Boolean) {
        snapshotTargets().forEach { callback ->
            val device = deviceLookup(snapshot.mac)
            if (device == null && !allowNullDevice) return@forEach
            invoke(callback, "onConnectMmaStateChanged", device, connected)
        }
    }

    fun callbackCount(): Int =
        synchronized(callbacks) { callbacks.size }

    private fun snapshotTargets(): List<Any> =
        synchronized(callbacks) { callbacks.toList() }

    private fun dispatchSnapshotLocked(callback: Any, snapshot: MilinkDeviceSnapshot, force: Boolean) {
        val mac = snapshot.mac.normalizeMac() ?: return
        val key = CallbackMacKey(System.identityHashCode(callback), mac)
        if (!force && lastRevisionByCallback[key] == snapshot.revision) return
        val device = deviceLookup(mac)
        if (device == null && !allowNullDevice) return

        invoke(callback, "onConnectMmaStateChanged", device, MiTwsStateMapper.connected(snapshot))
        if (snapshot.supportsBattery) {
            invoke(callback, "onBatteryLevel", device, MiTwsStateMapper.batteryArray(snapshot))
        }
        if (snapshot.supportsNoiseControl) {
            val anc = MiTwsStateMapper.ancState(snapshot)
            invoke(callback, "onAncStateChanged", device, anc)
            invoke(callback, "onReportAncState", device, anc)
        }
        invoke(callback, "onDeviceIdUpdate", device, deviceIdForMac(mac))
        if (snapshot.supportsRing) {
            invoke(callback, "onRingStateChanged", device, MiTwsStateMapper.ringing(snapshot))
        }
        lastRevisionByCallback[key] = snapshot.revision
    }

    private fun invoke(callback: Any, methodName: String, device: BluetoothDevice?, value: Any) {
        val method = callback.javaClass.findMiTwsCallbackMethod(methodName, value)
        if (method == null) {
            Log.w(TAG, "MiTWS callback method not found: $methodName(${value.javaClass.name}) on ${callback.javaClass.name}")
            return
        }
        runCatching {
            method.invoke(callback, device, value)
        }.onFailure { error ->
            Log.w(TAG, "MiTWS callback ${method.name} failed", error)
        }
    }

    private data class CallbackMacKey(
        val callbackIdentity: Int,
        val mac: String,
    )

    companion object {
        private const val TAG = "OpenBuds"
    }
}

interface AsyncDispatcher {
    fun dispatch(block: () -> Unit)
}

class HandlerThreadDispatcher(name: String) : AsyncDispatcher {
    private val worker = HandlerThread(name).also { it.start() }
    private val handler = Handler(worker.looper)

    override fun dispatch(block: () -> Unit) {
        handler.post(block)
    }
}

object DirectDispatcher : AsyncDispatcher {
    override fun dispatch(block: () -> Unit) = block()
}

private fun Class<*>.findMiTwsCallbackMethod(name: String, value: Any): Method? {
    var current: Class<*>? = this
    while (current != null) {
        val method = current.declaredMethods.firstOrNull { method ->
            method.name == name &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0].matchesDeviceParameter() &&
                method.parameterTypes[1].matchesCallbackValue(value)
        }
        if (method != null) {
            method.isAccessible = true
            return method
        }
        current = current.superclass
    }
    return null
}

private fun Class<*>.matchesDeviceParameter(): Boolean =
    BluetoothDevice::class.java.isAssignableFrom(this) || this == Any::class.java

private fun Class<*>.matchesCallbackValue(value: Any): Boolean =
    when (value) {
        is Int -> this == Integer.TYPE || this == Integer::class.java
        is Boolean -> this == java.lang.Boolean.TYPE || this == java.lang.Boolean::class.java
        is String -> this == String::class.java
        is IntArray -> this == IntArray::class.java
        else -> isAssignableFrom(value.javaClass)
    }
