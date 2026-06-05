package dev.ignotus.openbuds.lsposed.mitws

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.util.Log
import dev.ignotus.openbuds.integration.milink.MilinkBridgeContract
import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import dev.ignotus.openbuds.integration.milink.normalizeMac
import dev.ignotus.openbuds.lsposed.ModuleMain
import io.github.libxposed.api.XposedInterface

class MilinkMiTwsFacadeEntry(
    private val classLoader: ClassLoader,
) {
    @Volatile
    private var bridgeClient: MilinkBridgeClient? = null

    fun install() {
        val processName = currentProcessName()
        Log.i(
            TAG,
            "Installing MiLink MiTWS facade entry (M1) " +
                "pid=${Process.myPid()} process=$processName app=${currentApplication()?.packageName} " +
                "role=${MilinkRouteConfig.processRole(processName)}"
        )
        // Start bridge client immediately so snapshot is available before
        // MxBluetoothManager methods are first called. The Application.onCreate()
        // hook is a fallback if ActivityThread.currentApplication() isn't ready yet.
        val app = currentApplication()
        if (MilinkRouteConfig.shouldStartBridgeForProcess(processName)) {
            if (app != null) {
                startBridgeClient(app)
            } else {
                installApplicationOnCreateFallback()
            }
        } else {
            Log.i(
                TAG,
                "Skipping bridge client for process=$processName pid=${Process.myPid()} " +
                    "role=${MilinkRouteConfig.processRole(processName)}"
            )
        }
        MilinkMiTwsFacadeHook(classLoader, BridgeClientHolder).installTraceHooks()
        installVolumeControlHook()
        installAudioEffectControlHook()
    }

    private fun installVolumeControlHook() {
        val profileImpl = runCatching {
            classLoader.loadClass(PROFILE_IMPL)
        }.getOrNull() ?: run {
            Log.w(TAG, "M3+ volume control: $PROFILE_IMPL not found")
            return
        }
        val method = runCatching {
            profileImpl.getDeclaredMethod(
                "updateHeadsetVolume",
                String::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType ?: Int::class.java,
            )
        }.getOrNull() ?: run {
            Log.w(TAG, "M3+ volume control: ProfileImpl.updateHeadsetVolume not found")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val address = chain.args.getOrNull(1) as? String ?: ""
                    val volumeValue = (chain.args.getOrNull(3) as? Int) ?: -1
                    val mac = address.normalizeMac() ?: ""
                    val snapshot = BridgeClientHolder.snapshotFor(mac)
                    if (snapshot == null || !snapshot.supportsVolumeControl) {
                        return 201 // PROFILE_FAILURE_RESULT
                    }
                    // Scale MiLink slider (0-MI_LINK_MAX) → headphone protocol range (0-HEADPHONE_MAX).
                    // MiLink uses Android STREAM_MUSIC volume (typically 0-15); the headphone
                    // protocol expects 0-31 (Sony) or single-byte (QCY). Linear scaling ensures
                    // 50% on slider ≈ 50% of headphone's actual volume range.
                    val scaledVolume = (volumeValue.coerceIn(0, MI_LINK_VOLUME_MAX) * HEADPHONE_VOLUME_MAX / MI_LINK_VOLUME_MAX)
                        .coerceIn(0, HEADPHONE_VOLUME_MAX)
                    val command = Bundle().apply {
                        putString(MilinkBridgeContract.KEY_COMMAND_TYPE, MilinkBridgeContract.COMMAND_SET_VOLUME)
                        putInt(MilinkBridgeContract.KEY_VOLUME, scaledVolume)
                        putString(MilinkBridgeContract.KEY_REQUEST_ID, java.util.UUID.randomUUID().toString())
                    }
                    val result = BridgeClientHolder.executeCommand(mac, command, 50L)
                    Log.i(
                        TAG,
                        "[MiLinkMiTWS] updateHeadsetVolume mac=$mac raw=$volumeValue scaled=$scaledVolume " +
                            "accepted=${result.accepted} reason=${result.reason}"
                    )
                    return if (result.accepted) 1 else 201
                }
            })
        Log.i(TAG, "hooked M3+ volume control: ProfileImpl.updateHeadsetVolume")
    }

    private fun installAudioEffectControlHook() {
        val profileImpl = runCatching {
            classLoader.loadClass(PROFILE_IMPL)
        }.getOrNull() ?: run {
            Log.w(TAG, "M3+ audio effect control: $PROFILE_IMPL not found")
            return
        }
        val method = runCatching {
            profileImpl.getDeclaredMethod(
                "updateHeadsetAudioEffect",
                String::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType ?: Int::class.java,
            )
        }.getOrNull() ?: run {
            Log.w(TAG, "M3+ audio effect control: ProfileImpl.updateHeadsetAudioEffect not found")
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val address = chain.args.getOrNull(1) as? String ?: ""
                    val effectValue = (chain.args.getOrNull(3) as? Int) ?: -1
                    val mac = address.normalizeMac() ?: ""
                    val snapshot = BridgeClientHolder.snapshotFor(mac)
                    if (snapshot == null || !snapshot.supportsAudioEffect) {
                        return 201 // PROFILE_FAILURE_RESULT
                    }
                    val command = Bundle().apply {
                        putString(MilinkBridgeContract.KEY_COMMAND_TYPE, MilinkBridgeContract.COMMAND_SET_AUDIO_EFFECT)
                        putInt(MilinkBridgeContract.KEY_AUDIO_EFFECT_STATE, effectValue)
                        putString(MilinkBridgeContract.KEY_REQUEST_ID, java.util.UUID.randomUUID().toString())
                    }
                    val result = BridgeClientHolder.executeCommand(mac, command, 50L)
                    Log.i(
                        TAG,
                        "[MiLinkMiTWS] updateHeadsetAudioEffect mac=$mac value=$effectValue " +
                            "accepted=${result.accepted} reason=${result.reason}"
                    )
                    return if (result.accepted) 1 else 201
                }
            })
        Log.i(TAG, "hooked M3+ audio effect control: ProfileImpl.updateHeadsetAudioEffect")
    }

    private fun currentProcessName(): String = runCatching {
        val atClass = Class.forName("android.app.ActivityThread")
        val method = atClass.getDeclaredMethod("currentProcessName")
        method.invoke(null) as? String
    }.getOrNull().orEmpty()

    private fun currentApplication(): Application? = runCatching {
        val atClass = Class.forName("android.app.ActivityThread")
        val method = atClass.getDeclaredMethod("currentApplication")
        method.invoke(null) as? Application
    }.getOrNull()

    private fun installApplicationOnCreateFallback() {
        val method = runCatching {
            Application::class.java.getDeclaredMethod("onCreate")
                .also { it.isAccessible = true }
        }.getOrElse { error ->
            Log.w(TAG, "Missing Application.onCreate()", error)
            return
        }
        ModuleMain.instance.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val app = chain.thisObject as? Application
                    if (app != null) {
                        startBridgeClient(app)
                    }
                    return result
                }
            })
        Log.i(TAG, "hooked: Application.onCreate() fallback for MiTWS bridge")
    }

    private fun startBridgeClient(context: Context) {
        if (bridgeClient != null) return
        synchronized(this) {
            if (bridgeClient != null) return
            val client = MilinkBridgeClient(context)
            bridgeClient = client
            BridgeClientHolder.attach(client)
            client.start()
            Log.i(
                TAG,
                "MiLink MiTWS bridge client started (M1) " +
                    "pid=${Process.myPid()} context=${context.packageName} client=${System.identityHashCode(client)}"
            )
        }
    }

    private object BridgeClientHolder : MilinkBridgeClientFacade {
        private val snapshotListeners =
            mutableSetOf<(dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot) -> Unit>()

        @Volatile
        private var delegate: MilinkBridgeClient? = null

        fun attach(client: MilinkBridgeClient) {
            delegate = client
            Log.i(TAG, "BridgeClientHolder.attach pid=${Process.myPid()} client=${System.identityHashCode(client)}")
            synchronized(snapshotListeners) {
                snapshotListeners.forEach(client::addSnapshotListener)
            }
        }

        override val adapterEnabled: Boolean
            get() = delegate?.adapterEnabled == true

        override fun snapshotFor(mac: String?) = delegate?.snapshotFor(mac)

        override fun isAuthorized(mac: String?) = delegate?.isAuthorized(mac) == true

        override fun isClassificationEligible(mac: String?) =
            delegate?.isClassificationEligible(mac) == true

        override fun authorizedSnapshots() = delegate?.authorizedSnapshots().orEmpty()

        override fun addSnapshotListener(listener: (dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot) -> Unit) {
            synchronized(snapshotListeners) {
                snapshotListeners.add(listener)
            }
            delegate?.addSnapshotListener(listener)
        }

        override fun removeSnapshotListener(listener: (dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot) -> Unit) {
            synchronized(snapshotListeners) {
                snapshotListeners.remove(listener)
            }
            delegate?.removeSnapshotListener(listener)
        }

        override fun executeCommand(mac: String, command: android.os.Bundle, timeoutMs: Long): MiTwsBridgeCommandResult =
            delegate?.executeCommand(mac, command, timeoutMs)
                ?: MiTwsBridgeCommandResult.failed(
                    reason = dev.ignotus.openbuds.integration.milink.MilinkBridgeContract.REASON_BRIDGE_UNAVAILABLE,
                )
    }

    private companion object {
        private const val TAG = "OpenBuds"
        private const val PROFILE_IMPL = "com.miui.headset.runtime.ProfileImpl"
        private const val MI_LINK_VOLUME_MAX = 100 // MiLink slider percentage range
        private const val HEADPHONE_VOLUME_MAX = 15  // BLE headset volume steps (typical 0-15)
    }
}
