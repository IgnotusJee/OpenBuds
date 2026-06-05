package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import dev.ignotus.openbuds.integration.milink.IMilinkBridgeService
import dev.ignotus.openbuds.integration.milink.IMilinkTransportProxyCallback
import dev.ignotus.openbuds.integration.milink.MilinkBridgeContract
import dev.ignotus.openbuds.integration.milink.MilinkTransportProxyCoordinator
import dev.ignotus.openbuds.protocol.NoiseControlMode
import java.util.concurrent.atomic.AtomicBoolean

class MilinkTransportProxyClient(
    private val context: Context,
    private val targetMac: String,
    private val logger: XiaomiBluetoothTraceLogger,
    private val onNoiseControlCommand: (NoiseControlMode) -> Boolean,
) {
    private val worker = HandlerThread("OpenBuds-M5ProxyBridge").also { it.start() }
    private val handler = Handler(worker.looper)
    private val started = AtomicBoolean(false)

    @Volatile
    private var service: IMilinkBridgeService? = null

    @Volatile
    private var token: String? = null

    private var bound = false
    private var retryMs = MIN_RETRY_MS

    private val callback = object : IMilinkTransportProxyCallback.Stub() {
        override fun executeProxyCommand(mac: String?, command: Bundle?): Bundle {
            val mode = command
                ?.takeIf { it.getString(MilinkBridgeContract.KEY_COMMAND_TYPE) == MilinkBridgeContract.COMMAND_SET_NOISE_CONTROL }
                ?.takeIf { it.containsKey(MilinkBridgeContract.KEY_NOISE_MODE) }
                ?.getInt(MilinkBridgeContract.KEY_NOISE_MODE)
                ?.toNoiseControlMode()
            val requestId = command?.getString(MilinkBridgeContract.KEY_REQUEST_ID)
            if (mode == null) {
                return MilinkTransportProxyCoordinator.rejectedBundle(
                    MilinkBridgeContract.REASON_UNSUPPORTED_COMMAND,
                    requestId,
                )
            }
            val accepted = onNoiseControlCommand(mode)
            return if (accepted) {
                MilinkTransportProxyCoordinator.acceptedBundle(requestId)
            } else {
                MilinkTransportProxyCoordinator.rejectedBundle(
                    MilinkBridgeContract.REASON_REMOTE_ERROR,
                    requestId,
                )
            }
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
                service = null
                token = null
                bound = false
                scheduleBind()
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            handler.post {
                service = null
                token = null
                bound = false
                scheduleBind()
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            handler.post {
                service = null
                token = null
                bound = false
                retryLater()
            }
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        handler.post { scheduleBind(delayMs = 0L) }
    }

    fun publishSnapshot(state: SonySppWireState, deviceName: String?) {
        val bridge = service ?: return
        val openedToken = token ?: return
        val snapshot = state.toMilinkSnapshot(
            mac = targetMac,
            name = deviceName,
            updatedAt = SystemClock.elapsedRealtime(),
        )
        handler.post {
            runCatching {
                bridge.publishTransportProxySnapshot(openedToken, snapshot.toBundle())
            }.onFailure { error ->
                logger.warn(
                    event = "spp_proxy_bridge_publish_failed",
                    details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)}",
                    error = error,
                )
            }
        }
    }

    fun stop() {
        handler.post {
            val bridge = service
            val openedToken = token
            if (bridge != null && openedToken != null) {
                runCatching { bridge.unregisterTransportProxy(openedToken, targetMac) }
            }
            if (bound) {
                runCatching { context.unbindService(connection) }
            }
            bound = false
            service = null
            token = null
            worker.quitSafely()
        }
    }

    private fun scheduleBind(delayMs: Long = retryMs) {
        handler.removeCallbacksAndMessages(BIND_TOKEN)
        handler.postAtTime(
            { bind() },
            BIND_TOKEN,
            SystemClock.uptimeMillis() + delayMs,
        )
    }

    private fun bind() {
        if (bound) return
        val intent = Intent(MilinkBridgeContract.ACTION_BIND).apply {
            component = ComponentName(
                MilinkBridgeContract.SERVICE_PACKAGE,
                MilinkBridgeContract.SERVICE_CLASS,
            )
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val success = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrElse { error ->
            logger.warn(
                event = "spp_proxy_bridge_bind_failed",
                details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)}",
                error = error,
            )
            false
        }
        if (success) {
            bound = true
        } else {
            retryLater()
        }
    }

    private fun retryLater() {
        retryMs = (retryMs * 2).coerceAtMost(MAX_RETRY_MS)
        scheduleBind()
    }

    private fun openSessionAndRegister() {
        val bridge = service ?: return
        runCatching {
            val session = bridge.openTransportProxySession()
            val openedToken = session.getString(MilinkBridgeContract.KEY_TOKEN)
                ?: error("missing proxy token")
            token = openedToken
            val capabilities = Bundle().apply {
                putBoolean(MilinkBridgeContract.KEY_SUPPORTS_BATTERY, true)
                putBoolean(MilinkBridgeContract.KEY_SUPPORTS_NOISE_CONTROL, true)
                putBoolean(MilinkBridgeContract.KEY_SUPPORTS_AMBIENT_LEVEL, true)
                putString(
                    MilinkBridgeContract.KEY_PROXY_TRANSPORT,
                    XiaomiBluetoothTraceConfig.sppProxyTransport().propertyValue,
                )
            }
            val registration = bridge.registerTransportProxy(openedToken, targetMac, capabilities, callback)
            if (!registration.getBoolean(MilinkBridgeContract.KEY_SUCCESS, false)) {
                error(
                    "proxy registration rejected reason=" +
                        registration.getString(MilinkBridgeContract.KEY_REASON, "unknown"),
                )
            }
            logger.info(
                event = "spp_proxy_bridge_registered",
                mac = targetMac,
                details = "transport=${XiaomiBluetoothTraceConfig.sppProxyTransport().propertyValue}",
                force = true,
            )
        }.onFailure { error ->
            logger.warn(
                event = "spp_proxy_bridge_session_failed",
                details = "mac=${XiaomiBluetoothTraceConfig.maskMac(targetMac)}",
                error = error,
            )
            if (bound) {
                runCatching { context.unbindService(connection) }
            }
            bound = false
            service = null
            token = null
            retryLater()
        }
    }

    private companion object {
        private const val MIN_RETRY_MS = 1_000L
        private const val MAX_RETRY_MS = 30_000L
        private val BIND_TOKEN = Any()
    }
}
