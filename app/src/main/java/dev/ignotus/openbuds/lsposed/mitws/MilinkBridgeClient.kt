package dev.ignotus.openbuds.lsposed.mitws

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import dev.ignotus.openbuds.integration.milink.IMilinkBridgeCallback
import dev.ignotus.openbuds.integration.milink.IMilinkBridgeService
import dev.ignotus.openbuds.integration.milink.MilinkBridgeContract
import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import dev.ignotus.openbuds.integration.milink.normalizeMac

class MilinkBridgeClient(
    private val context: Context,
    private val cache: MiTwsBridgeCache = MiTwsBridgeCache(),
) : MilinkBridgeClientFacade {
    private val worker = HandlerThread("OpenBuds-MiTwsBridge").also { it.start() }
    private val handler = Handler(worker.looper)

    @Volatile
    private var service: IMilinkBridgeService? = null

    @Volatile
    private var token: String? = null

    private var retryMs = MIN_RETRY_MS
    private var bound = false
    private val snapshotListeners = mutableSetOf<(MilinkDeviceSnapshot) -> Unit>()

    override val adapterEnabled: Boolean
        get() = cache.adapterEnabled

    private val callback = object : IMilinkBridgeCallback.Stub() {
        override fun onSnapshotChanged(snapshot: Bundle?) {
            val parsed = MilinkDeviceSnapshot.fromBundle(snapshot) ?: return
            cache.updateSnapshot(parsed)
            notifySnapshotListeners(parsed)
        }

        override fun onAdapterStatusChanged(status: Bundle?) {
            applyStatus(status)
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

    override fun snapshotFor(mac: String?): MilinkDeviceSnapshot? {
        val snapshot = cache.snapshotFor(mac)
        if (snapshot == null) {
            handler.post { refreshFromBridge(mac) }
        }
        return snapshot
    }

    override fun updateSnapshot(snapshot: MilinkDeviceSnapshot) {
        cache.updateSnapshot(snapshot)
        notifySnapshotListeners(snapshot)
    }

    override fun isAuthorized(mac: String?): Boolean = snapshotFor(mac) != null

    override fun authorizedSnapshots(): List<MilinkDeviceSnapshot> = cache.authorizedSnapshots()

    override fun addSnapshotListener(listener: (MilinkDeviceSnapshot) -> Unit) {
        handler.post {
            snapshotListeners.add(listener)
            cache.authorizedSnapshots().forEach(listener)
        }
    }

    override fun removeSnapshotListener(listener: (MilinkDeviceSnapshot) -> Unit) {
        handler.post {
            snapshotListeners.remove(listener)
        }
    }

    override fun executeCommand(
        mac: String,
        command: Bundle,
        timeoutMs: Long,
    ): MiTwsBridgeCommandResult {
        val normalized = mac.normalizeMac()
            ?: return MiTwsBridgeCommandResult.failed(MilinkBridgeContract.REASON_INVALID_MAC)
        val bridge = service
            ?: return MiTwsBridgeCommandResult.failed(MilinkBridgeContract.REASON_BRIDGE_UNAVAILABLE)
                .also { Log.w(TAG, "[ANC_CMD] executeCommand failed: bridge service not bound") }
        val openedToken = token
            ?: return MiTwsBridgeCommandResult.failed(MilinkBridgeContract.REASON_BRIDGE_UNAVAILABLE)
                .also { Log.w(TAG, "[ANC_CMD] executeCommand failed: no session token") }
        if (android.os.Looper.myLooper() == worker.looper) {
            return runCatching {
                MiTwsControlMapper.parseResult(
                    bridge.executeCommand(openedToken, normalized, command),
                ).also { result ->
                    Log.i(TAG, "[ANC_CMD] bridge call result accepted=${result.accepted} reason=${result.reason} requestId=${result.requestId}")
                }
            }.getOrElse { error ->
                handleBridgeError(error)
                MiTwsBridgeCommandResult.failed(
                    reason = error.message ?: MilinkBridgeContract.REASON_REMOTE_ERROR,
                    requestId = command.getString(MilinkBridgeContract.KEY_REQUEST_ID),
                ).also { Log.w(TAG, "[ANC_CMD] bridge call exception: ${error.message}") }
            }
        }
        val deadline = SystemClock.uptimeMillis() + timeoutMs.coerceAtLeast(1L)
        val result = arrayOfNulls<MiTwsBridgeCommandResult>(1)
        val complete = java.util.concurrent.CountDownLatch(1)
        handler.post {
            result[0] = runCatching {
                MiTwsControlMapper.parseResult(
                    bridge.executeCommand(openedToken, normalized, command),
                )
            }.getOrElse { error ->
                handleBridgeError(error)
                val reason = if (error is RemoteException) {
                    MilinkBridgeContract.REASON_REMOTE_ERROR
                } else {
                    error.message ?: MilinkBridgeContract.REASON_REMOTE_ERROR
                }
                MiTwsBridgeCommandResult.failed(
                    reason = reason,
                    requestId = command.getString(MilinkBridgeContract.KEY_REQUEST_ID),
                )
            }.also { r ->
                Log.i(TAG, "[ANC_CMD] bridge call result accepted=${r.accepted} reason=${r.reason} requestId=${r.requestId}")
            }
            complete.countDown()
        }
        val waitMs = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(1L)
        return if (complete.await(waitMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            result[0] ?: MiTwsBridgeCommandResult.failed(MilinkBridgeContract.REASON_REMOTE_ERROR)
        } else {
            MiTwsBridgeCommandResult.failed(
                reason = MilinkBridgeContract.REASON_TIMEOUT,
                requestId = command.getString(MilinkBridgeContract.KEY_REQUEST_ID),
            )
        }
    }

    private fun scheduleBind(delayMs: Long = retryMs) {
        handler.removeCallbacksAndMessages(BIND_TOKEN)
        handler.postAtTime(
            { bind() },
            BIND_TOKEN,
            android.os.SystemClock.uptimeMillis() + delayMs,
        )
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
            Log.w(TAG, "MiTWS bridge bind failed", error)
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
            Log.i(TAG, "MiLink MiTWS bridge session opened")
        }.onFailure { error ->
            Log.w(TAG, "MiTWS bridge session failed", error)
            token = null
            cache.markError(error.message ?: "bridge_session_failed")
            retryLater()
        }
    }

    private fun refreshFromBridge(mac: String?) {
        val bridge = service ?: return
        val openedToken = token ?: return
        val normalized = mac?.normalizeMac() ?: return
        runCatching {
            val snapshot = MilinkDeviceSnapshot.fromBundle(
                bridge.getDeviceSnapshot(openedToken, normalized),
            )
            if (snapshot != null) {
                cache.updateSnapshot(snapshot)
                notifySnapshotListeners(snapshot)
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
                ?.let { snapshot ->
                    cache.updateSnapshot(snapshot)
                    notifySnapshotListeners(snapshot)
                }
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
        Log.w(TAG, "MiTWS bridge call failed", error)
        if (error is RemoteException || error is SecurityException) {
            token = null
            service = null
            cache.markError(error.message ?: "bridge_call_failed")
            retryLater()
        }
    }

    private fun notifySnapshotListeners(snapshot: MilinkDeviceSnapshot) {
        snapshotListeners.toList().forEach { listener ->
            runCatching { listener(snapshot) }
                .onFailure { error -> Log.w(TAG, "MiTWS snapshot listener failed", error) }
        }
    }

    private fun unbindBridge() {
        if (bound) {
            runCatching {
                context.unbindService(connection)
            }.onFailure { error ->
                Log.w(TAG, "MiTWS bridge unbind failed", error)
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
    }
}

interface MilinkBridgeClientFacade {
    val adapterEnabled: Boolean
    fun snapshotFor(mac: String?): MilinkDeviceSnapshot?
    fun isAuthorized(mac: String?): Boolean
    fun authorizedSnapshots(): List<MilinkDeviceSnapshot> = emptyList()
    fun addSnapshotListener(listener: (MilinkDeviceSnapshot) -> Unit) = Unit
    fun removeSnapshotListener(listener: (MilinkDeviceSnapshot) -> Unit) = Unit
    fun updateSnapshot(snapshot: MilinkDeviceSnapshot) = Unit
    fun executeCommand(
        mac: String,
        command: Bundle,
        timeoutMs: Long = 50L,
    ): MiTwsBridgeCommandResult =
        MiTwsBridgeCommandResult.failed(
            reason = MilinkBridgeContract.REASON_BRIDGE_UNAVAILABLE,
            requestId = command.getString(MilinkBridgeContract.KEY_REQUEST_ID),
        )
}
