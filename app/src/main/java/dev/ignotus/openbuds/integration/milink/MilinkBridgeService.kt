package dev.ignotus.openbuds.integration.milink

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import dev.ignotus.openbuds.data.HeadphoneRepository
import dev.ignotus.openbuds.data.HeadphoneUiState
import dev.ignotus.openbuds.data.settings.AppSettingsStore
import dev.ignotus.openbuds.service.SonyControlService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class MilinkBridgeService : Service() {
    private lateinit var repository: HeadphoneRepository
    private lateinit var settingsStore: AppSettingsStore
    private lateinit var callerVerifier: MilinkBridgeCallerVerifier

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val callbacks = RemoteCallbackList<IMilinkBridgeCallback>()
    private val secureRandom = SecureRandom()
    private val sessions = ConcurrentHashMap<String, Session>()

    @Volatile
    private var latestState: HeadphoneUiState = HeadphoneUiState()

    @Volatile
    private var latestSnapshot: MilinkDeviceSnapshot? = null

    @Volatile
    private var adapterEnabled: Boolean = false

    private var revision: Long = 0L

    override fun onCreate() {
        super.onCreate()
        repository = HeadphoneRepository.getInstance(applicationContext)
        settingsStore = AppSettingsStore(applicationContext)
        callerVerifier = MilinkBridgeCallerVerifier(applicationContext)

        // Auto-start BLE service so protocol data is available when milink queries.
        // This ensures the bridge has real battery/wearing/charging data without
        // requiring the user to open the app first.
        triggerAutoConnect()

        serviceScope.launch {
            combine(repository.state, settingsStore.settings) { state, settings ->
                state to settings.milinkAdapterEnabled
            }.collect { (state, enabled) ->
                latestState = state
                adapterEnabled = enabled
                revision += 1
                latestSnapshot = if (enabled) {
                    MilinkBridgeSnapshotMapper.fromUiState(
                        state = state,
                        revision = revision,
                        updatedAt = SystemClock.elapsedRealtime(),
                    )
                } else {
                    null
                }
                notifyClients()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun triggerAutoConnect() {
        val intent = Intent(this, SonyControlService::class.java).apply {
            action = SonyControlService.ACTION_AUTO_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        callbacks.kill()
        sessions.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private val binder = object : IMilinkBridgeService.Stub() {
        override fun openSession(): Bundle {
            val uid = verifyCaller()
            val token = newToken()
            sessions[token] = Session(uid = uid, lastSeenMs = SystemClock.elapsedRealtime())
            return Bundle().apply {
                putString(MilinkBridgeContract.KEY_TOKEN, token)
            }
        }

        override fun getAdapterStatus(token: String?): Bundle {
            verifySession(token)
            return statusBundle()
        }

        override fun getAuthorizedDevices(token: String?): Bundle {
            verifySession(token)
            return Bundle().apply {
                putStringArrayList(
                    MilinkBridgeContract.KEY_AUTHORIZED_MACS,
                    ArrayList(authorizedMacs()),
                )
            }
        }

        override fun getDeviceSnapshot(token: String?, mac: String?): Bundle {
            verifySession(token)
            val targetMac = mac?.normalizeMac() ?: return Bundle()
            val snapshot = latestSnapshot
            return if (snapshot != null && snapshot.mac == targetMac && isAuthorized(snapshot)) {
                snapshot.toBundle()
            } else {
                Bundle()
            }
        }

        override fun registerCallback(token: String?, callback: IMilinkBridgeCallback?) {
            verifySession(token)
            if (callback == null) return
            callbacks.register(callback)
            runCatching {
                callback.onAdapterStatusChanged(statusBundle())
                latestSnapshot?.takeIf(::isAuthorized)?.let { callback.onSnapshotChanged(it.toBundle()) }
            }.onFailure { error ->
                Log.w(TAG, "Initial callback dispatch failed", error)
            }
        }

        override fun unregisterCallback(token: String?, callback: IMilinkBridgeCallback?) {
            verifySession(token)
            if (callback != null) callbacks.unregister(callback)
        }
    }

    private fun verifyCaller(): Int {
        val uid = Binder.getCallingUid()
        callerVerifier.verify(uid)
        return uid
    }

    private fun verifySession(token: String?) {
        val uid = verifyCaller()
        val session = token?.takeIf { it.isNotBlank() }?.let(sessions::get)
        if (session == null || session.uid != uid) {
            throw SecurityException("MiLink bridge session is not open")
        }
        sessions[token] = session.copy(lastSeenMs = SystemClock.elapsedRealtime())
    }

    private fun newToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun statusBundle(): Bundle =
        Bundle().apply {
            val snapshot = latestSnapshot
            putBoolean(MilinkBridgeContract.KEY_ENABLED, adapterEnabled)
            putBoolean(MilinkBridgeContract.KEY_CONNECTED, snapshot?.connected == true)
            putBoolean(MilinkBridgeContract.KEY_PROTOCOL_READY, snapshot?.protocolReady == true)
            putStringArrayList(MilinkBridgeContract.KEY_AUTHORIZED_MACS, ArrayList(authorizedMacs()))
            putString(
                MilinkBridgeContract.KEY_REASON,
                when {
                    !adapterEnabled -> "disabled"
                    snapshot == null -> "no_connected_device"
                    !snapshot.connected -> "disconnected"
                    else -> "ready"
                },
            )
        }

    private fun authorizedMacs(): List<String> =
        latestSnapshot
            ?.takeIf(::isAuthorized)
            ?.let { listOf(it.mac) }
            .orEmpty()

    private fun isAuthorized(snapshot: MilinkDeviceSnapshot): Boolean =
        adapterEnabled && snapshot.connected && snapshot.mac.isNotBlank()

    private fun notifyClients() {
        val snapshot = latestSnapshot
        val status = statusBundle()
        val count = callbacks.beginBroadcast()
        try {
            repeat(count) { index ->
                val callback = callbacks.getBroadcastItem(index)
                try {
                    callback.onAdapterStatusChanged(status)
                    if (snapshot != null && isAuthorized(snapshot)) {
                        callback.onSnapshotChanged(snapshot.toBundle())
                    }
                } catch (error: RemoteException) {
                    Log.w(TAG, "MiLink callback failed", error)
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    private companion object {
        private const val TAG = "OpenBuds"
    }

    private data class Session(
        val uid: Int,
        val lastSeenMs: Long,
    )
}
