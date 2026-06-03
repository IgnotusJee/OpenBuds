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
        logI("[DIAG] register callback=${callback.javaClass.name} snapshotCount=${snapshots.size}")
        synchronized(callbacks) {
            callbacks.add(callback)
        }
        snapshots.forEach { snapshot ->
            logI("[DIAG] register dispatch mac=${snapshot.mac} supportsBattery=${snapshot.supportsBattery} supportsNoiseControl=${snapshot.supportsNoiseControl} connected=${snapshot.connected} ancMode=${snapshot.ancMode} leftBattery=${snapshot.leftBattery} isPlaceholder=${snapshot.protocolReady == false && snapshot.name == ""}")
            dispatchSnapshotLocked(callback, snapshot, force = true)
        }
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
        logI("[DIAG] dispatchSnapshot mac=${snapshot.mac} supportsBattery=${snapshot.supportsBattery} supportsNoiseControl=${snapshot.supportsNoiseControl} callbacks=${targets.size} thread=${Thread.currentThread().name}")
        // dispatchSnapshot is called from the bridge handler thread (Binder).
        // MMACallback methods must be invoked on the main thread — the original
        // MiaoXiangCallbackProxy uses mHandler.post. UI updates from a background
        // thread are silently ignored.
        if (mainHandler != null && Looper.myLooper() != Looper.getMainLooper()) {
            logI("[DIAG] dispatchSnapshot posting to main thread")
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
        if (!force && lastRevisionByCallback[key] == snapshot.revision) {
            logI("[DIAG] skip dispatch: same revision=${snapshot.revision}")
            return
        }
        val device = deviceLookup(mac)
        if (device == null && !allowNullDevice) {
            logI("[DIAG] skip dispatch: device null for mac=$mac")
            return
        }
        logI("[DIAG] dispatchSnapshotLocked mac=$mac revision=${snapshot.revision} force=$force device=${device != null}")

        logI("[DIAG] -> onConnectMmaStateChanged(${MiTwsStateMapper.connected(snapshot)})")
        invoke(callback, "onConnectMmaStateChanged", device, MiTwsStateMapper.connected(snapshot))
        // Ensure ancBatteryModel is created on the AncBatteryController.
        // The controller only creates the model in onConnectMmaStateChanged(true)
        // when pendingConnectMmaAddress matches, but register() dispatches the
        // callback before connectMma sets the address. Force-create via reflection.
        if (snapshot.connected && snapshot.protocolReady && !snapshot.mac.isBlank()) {
            ensureAncBatteryModel(callback, device, snapshot.mac)
        }
        if (snapshot.supportsBattery) {
            val batt = MiTwsStateMapper.batteryArray(snapshot)
            logI("[DIAG] -> onBatteryLevel(${batt.joinToString()})")
            invoke(callback, "onBatteryLevel", device, batt)
        } else {
            logI("[DIAG] -> SKIP onBatteryLevel: supportsBattery=false")
        }
        if (snapshot.supportsNoiseControl) {
            val anc = MiTwsStateMapper.ancState(snapshot)
            logI("[DIAG] -> onAncStateChanged($anc) onReportAncState($anc)")
            invoke(callback, "onAncStateChanged", device, anc)
            invoke(callback, "onReportAncState", device, anc)
        } else {
            logI("[DIAG] -> SKIP onAncStateChanged: supportsNoiseControl=false")
        }
        logI("[DIAG] -> onDeviceIdUpdate(${deviceIdForMac(mac)})")
        invoke(callback, "onDeviceIdUpdate", device, deviceIdForMac(mac))
        if (snapshot.supportsRing) {
            invoke(callback, "onRingStateChanged", device, MiTwsStateMapper.ringing(snapshot))
        }
        lastRevisionByCallback[key] = snapshot.revision
    }

    private fun invoke(callback: Any, methodName: String, device: BluetoothDevice?, value: Any) {
        val method = callback.javaClass.findMiTwsCallbackMethod(methodName, value)
        if (method == null) {
            logW("[DIAG] callback method not found: $methodName(${value.javaClass.name}) on ${callback.javaClass.name}")
            return
        }
        logI("[DIAG] invoke $methodName value=$value on ${callback.javaClass.name}")
        runCatching {
            method.invoke(callback, device, value)
            logI("[DIAG] invoke $methodName SUCCESS")
        }.onFailure { error ->
            logW("[DIAG] invoke ${method.name} FAILED", error)
        }
    }

    /**
     * AncBatteryController only creates ancBatteryModel in onConnectMmaStateChanged(true)
     * when pendingConnectMmaAddress matches. Our register() dispatches this callback before
     * connectMma is called, so the model never gets created. Force-create it via reflection.
     */
    private fun ensureAncBatteryModel(callback: Any, device: BluetoothDevice?, mac: String) {
        runCatching {
            // callback is AncBatteryController$mmaCallback$1 → this$0 is AncBatteryController
            val this0Field = callback.javaClass.getDeclaredField("this$0")
            this0Field.isAccessible = true
            val controller = this0Field.get(callback)

            val ancModelField = controller.javaClass.getDeclaredField("ancBatteryModel")
            ancModelField.isAccessible = true
            val existing = ancModelField.get(controller)
            if (existing != null) {
                logI("[DIAG] ancBatteryModel already exists: $existing")
                return@runCatching
            }

            // Load AncBatteryModel using milink's classloader (not ours)
            val milinkCl = callback.javaClass.classLoader ?: return@runCatching
            val modelClass = milinkCl.loadClass("com.miui.headset.runtime.AncBatteryModel")
            val constructor = modelClass.declaredConstructors.firstOrNull { it.parameterTypes.size == 5 }
                ?: return@runCatching
            constructor.isAccessible = true
            val model = constructor.newInstance(device, 0, null, 6, null)

            // Also set pendingConnectMmaAddress so subsequent connectMma callback doesn't
            // need to recreate it
            runCatching {
                val pendingField = controller.javaClass.getDeclaredField("pendingConnectMmaAddress")
                pendingField.isAccessible = true
                pendingField.set(controller, mac.normalizeMac() ?: mac)
            }

            ancModelField.set(controller, model)
            logI("[DIAG] ancBatteryModel force-created: $model")
        }.onFailure { error ->
            logW("[DIAG] ensureAncBatteryModel failed", error)
        }
    }

    private fun logI(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun logW(message: String, error: Throwable? = null) {
        runCatching {
            if (error == null) {
                Log.w(TAG, message)
            } else {
                Log.w(TAG, message, error)
            }
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
