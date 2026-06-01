package dev.ignotus.openbuds.lsposed.milink

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import dev.ignotus.openbuds.integration.milink.IMilinkBridgeCallback
import dev.ignotus.openbuds.integration.milink.IMilinkBridgeService
import dev.ignotus.openbuds.integration.milink.MilinkBridgeContract
import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot

class MilinkBridgeClient(
    private val context: Context,
    private val cache: MilinkBridgeCache = MilinkBridgeCache(),
    private val notifyPump: NotifyChangePump = NotifyChangePump(context),
) {
    private val worker = HandlerThread("OpenBuds-MiLinkBridge").also { it.start() }
    private val handler = Handler(worker.looper)

    @Volatile
    private var service: IMilinkBridgeService? = null

    @Volatile
    private var token: String? = null

    private var retryMs = MIN_RETRY_MS
    private var bound = false

    private val callback = object : IMilinkBridgeCallback.Stub() {
        override fun onSnapshotChanged(snapshot: Bundle?) {
            val parsed = MilinkDeviceSnapshot.fromBundle(snapshot) ?: return
            cache.updateSnapshot(parsed)
            notifyPump.requestNotify()
        }

        override fun onAdapterStatusChanged(status: Bundle?) {
            applyStatus(status)
            notifyPump.requestNotify()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            handler.post {
                service = IMilinkBridgeService.Stub.asInterface(binder)
                retryMs = MIN_RETRY_MS
                openSessionAndRegister()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            handler.post {
                bound = false
                service = null
                token = null
                cache.markError("bridge_disconnected")
                scheduleBind()
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            handler.post {
                bound = false
                service = null
                token = null
                cache.markError("bridge_binding_died")
                scheduleBind()
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            handler.post {
                bound = false
                service = null
                token = null
                cache.markError("bridge_null_binding")
                retryLater()
            }
        }
    }

    fun start() {
        handler.post { scheduleBind(delayMs = 0L) }
    }

    fun snapshotFor(mac: String?): MilinkDeviceSnapshot? {
        val snapshot = cache.snapshotFor(mac)
        if (snapshot == null) {
            handler.post { refreshFromBridge(mac) }
        }
        return snapshot
    }

    fun isAuthorized(mac: String?): Boolean =
        snapshotFor(mac) != null || fallbackAuthorized(mac)

    /**
     * Fallback authorization via system property allowlist.
     *
     * Used when the bridge is active but has no connected-device snapshots
     * (e.g. App process was just started by milink's bind but
     * [dev.ignotus.openbuds.service.SonyControlService] hasn't re-established
     * the BLE protocol connection yet).
     *
     * Set via:
     * ```powershell
     * adb shell setprop debug.openbuds.milink_m1_macs "F8:4E:17:D1:32:27,AA:BB:CC:DD:EE:FF"
     * ```
     */
    private fun fallbackAuthorized(mac: String?): Boolean {
        val normalized = MilinkAirpodsTargetMatcher.normalizeMac(mac) ?: return false
        val allowlist = readSystemProp("debug.openbuds.milink_m1_macs", "")
        if (allowlist.isBlank()) return false
        val allowMacs = allowlist.split(",").mapNotNull(MilinkAirpodsTargetMatcher::normalizeMac).toSet()
        return normalized in allowMacs
    }

    private fun scheduleBind(delayMs: Long = retryMs) {
        handler.removeCallbacksAndMessages(BIND_TOKEN)
        handler.postAtTime({
            bind()
        }, BIND_TOKEN, android.os.SystemClock.uptimeMillis() + delayMs)
    }

    private fun bind() {
        if (bound) return
        val intent = Intent(MilinkBridgeContract.ACTION_BIND).apply {
            component = ComponentName(
                MilinkBridgeContract.SERVICE_PACKAGE,
                MilinkBridgeContract.SERVICE_CLASS,
            )
        }
        val success = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrElse { error ->
            Log.w(TAG, "Bridge bind failed", error)
            false
        }
        if (success) {
            bound = true
            cache.markError(null)
        } else {
            cache.markError("bridge_bind_failed")
            retryLater()
        }
    }

    private fun retryLater() {
        unbindBridge()
        retryMs = (retryMs * 2).coerceAtMost(MAX_RETRY_MS)
        scheduleBind()
    }

    private fun openSessionAndRegister() {
        val bridge = service ?: return
        runCatching {
            val session = bridge.openSession()
            val openedToken = session.getString(MilinkBridgeContract.KEY_TOKEN)
                ?: error("Bridge did not return token")
            token = openedToken
            applyStatus(bridge.getAdapterStatus(openedToken))
            refreshAuthorizedSnapshots(bridge, openedToken)
            bridge.registerCallback(openedToken, callback)
            cache.markError(null)
            Log.i(TAG, "MiLink bridge session opened")
        }.onFailure { error ->
            Log.w(TAG, "Bridge session failed", error)
            token = null
            cache.markError(error.message ?: "bridge_session_failed")
            retryLater()
        }
    }

    private fun refreshFromBridge(mac: String?) {
        val bridge = service ?: return
        val openedToken = token ?: return
        val normalized = MilinkAirpodsTargetMatcher.normalizeMac(mac) ?: return
        runCatching {
            val snapshot = MilinkDeviceSnapshot.fromBundle(
                bridge.getDeviceSnapshot(openedToken, normalized),
            )
            if (snapshot != null) {
                cache.updateSnapshot(snapshot)
            }
        }.onFailure { error ->
            handleBridgeError(error)
        }
    }

    private fun refreshAuthorizedSnapshots(bridge: IMilinkBridgeService, openedToken: String) {
        val authorized = bridge.getAuthorizedDevices(openedToken)
            .getStringArrayList(MilinkBridgeContract.KEY_AUTHORIZED_MACS)
            .orEmpty()
        cache.updateStatus(cache.adapterEnabled, authorized)
        authorized.forEach { mac ->
            MilinkDeviceSnapshot.fromBundle(bridge.getDeviceSnapshot(openedToken, mac))
                ?.let(cache::updateSnapshot)
        }
    }

    private fun applyStatus(status: Bundle?) {
        if (status == null) return
        val authorized = status
            .getStringArrayList(MilinkBridgeContract.KEY_AUTHORIZED_MACS)
            .orEmpty()
        cache.updateStatus(
            enabled = status.getBoolean(MilinkBridgeContract.KEY_ENABLED, false),
            authorized = authorized,
        )
    }

    private fun handleBridgeError(error: Throwable) {
        Log.w(TAG, "Bridge call failed", error)
        if (error is RemoteException || error is SecurityException) {
            token = null
            service = null
            cache.markError(error.message ?: "bridge_call_failed")
            retryLater()
        }
    }

    private fun unbindBridge() {
        if (bound) {
            runCatching {
                context.unbindService(connection)
            }.onFailure { error ->
                Log.w(TAG, "Bridge unbind failed", error)
            }
        }
        bound = false
        service = null
        token = null
    }

    private companion object {
        private const val TAG = "OpenBuds"
        private const val MIN_RETRY_MS = 1_000L
        private const val MAX_RETRY_MS = 30_000L
        private val BIND_TOKEN = Any()

        private fun readSystemProp(key: String, default: String): String =
            runCatching {
                val clazz = Class.forName("android.os.SystemProperties")
                val method = clazz.getMethod("get", String::class.java, String::class.java)
                method.invoke(null, key, default) as? String ?: default
            }.getOrDefault(default)
    }
}
