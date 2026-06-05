package dev.ignotus.openbuds.lsposed.mitws

import android.bluetooth.BluetoothDevice
import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import dev.ignotus.openbuds.integration.milink.normalizeMac

class MiTwsRuntimeProjection(
    private val classLoader: ClassLoader,
    private val bridgeClient: MilinkBridgeClientFacade?,
    private val deviceLookup: (String) -> BluetoothDevice?,
    private val deviceIdForMac: (String) -> String,
    private val projectionGate: (MilinkBridgeClientFacade?) -> Boolean = MilinkRouteConfig::canUseFacade,
) {
    private val headsetDeviceClass: Class<*>? by lazy {
        runCatching { classLoader.loadClass(HEADSET_DEVICE) }.getOrNull()
    }
    private val headsetInfoClass: Class<*>? by lazy {
        runCatching { classLoader.loadClass(HEADSET_INFO) }.getOrNull()
    }
    private val audioManager: android.media.AudioManager? by lazy {
        runCatching {
            val app = Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? android.app.Application
            app?.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        }.getOrNull()
    }

    fun canUseProjection(): Boolean =
        projectionGate(bridgeClient)

    fun snapshotFor(mac: String?): MilinkDeviceSnapshot? {
        if (!canUseProjection()) return null
        return bridgeClient?.snapshotFor(mac)
    }

    fun isClassificationEligible(mac: String?): Boolean {
        if (!canUseProjection()) return false
        return bridgeClient?.isClassificationEligible(mac) == true
    }

    fun activeSnapshot(): MilinkDeviceSnapshot? {
        if (!canUseProjection()) return null
        return bridgeClient
            ?.authorizedSnapshots()
            .orEmpty()
            .firstOrNull { it.connected }
    }

    fun snapshotForDevice(device: BluetoothDevice?): MilinkDeviceSnapshot? =
        snapshotFor(safeMac(device))

    fun shouldSuppressRuntimeDevice(mac: String?): Boolean =
        isClassificationEligible(mac) && snapshotFor(mac) == null

    fun isConnectedDevice(device: BluetoothDevice?): Boolean =
        snapshotForDevice(device)?.connected == true

    fun isActiveDevice(device: BluetoothDevice?): Boolean {
        val snapshot = snapshotForDevice(device) ?: return false
        return activeSnapshot()?.mac == snapshot.mac
    }

    fun shouldProjectActiveHeadset(original: Any?): Boolean {
        val originalMac = headsetDeviceAddress(original)
        if (originalMac != null && snapshotFor(originalMac) == null) {
            return false
        }
        return activeSnapshot() != null
    }

    fun projectedActiveHeadset(): Any? {
        val snapshot = activeSnapshot() ?: return null
        return buildHeadsetDevice(snapshot)
    }

    fun projectConnectedDevices(original: Any?): List<BluetoothDevice> {
        val result = mutableListOf<BluetoothDevice>()
        if (original is Iterable<*>) {
            original.filterIsInstance<BluetoothDevice>().forEach { result.add(it) }
        }
        activeSnapshot()?.let { snapshot ->
            val alreadyPresent = result.any { safeMac(it).normalizeMac() == snapshot.mac }
            if (!alreadyPresent) {
                deviceLookup(snapshot.mac)?.let(result::add)
            }
        }
        return result
    }

    fun targetSnapshot(address: String, deviceId: String): MilinkDeviceSnapshot? {
        val snapshot = snapshotFor(address) ?: return null
        val assignedDeviceId = deviceIdForMac(snapshot.mac)
        return snapshot.takeIf {
            deviceId.isBlank() ||
                deviceId == snapshot.deviceId ||
                deviceId == assignedDeviceId
        }
    }

    fun targetMatchesActive(address: String, deviceId: String): Boolean {
        val snapshot = targetSnapshot(address, deviceId) ?: return false
        val active = activeSnapshot() ?: return false
        return active.mac == snapshot.mac
    }

    fun buildHeadsetInfo(snapshot: MilinkDeviceSnapshot, original: Any? = null): Any? {
        val clazz = headsetInfoClass ?: return null
        val powers = MiTwsStateMapper.headsetInfoPowers(snapshot)
        return runCatching {
            clazz.getConstructor(
                String::class.java,
                String::class.java,
                String::class.java,
                List::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).newInstance(
                snapshot.mac,
                snapshot.name.orEmpty().ifBlank { snapshot.model.orEmpty().ifBlank { "OpenBuds" } },
                deviceIdForMac(snapshot.mac),
                powers,
                MiTwsStateMapper.ancState(snapshot),
                resolvedHeadsetInfoVolume(snapshot, original),
                resolvedHeadsetInfoType(snapshot, original),
                SWITCH_STATE_DEFAULT,
                WIRED_STATE_DEFAULT,
                resolvedHeadsetInfoAudioEffect(snapshot, original),
            )
        }.getOrNull()
    }

    fun resolvedHeadsetInfoVolume(snapshot: MilinkDeviceSnapshot, original: Any?): Int {
        if (!snapshot.supportsVolumeControl) {
            return intGetter(original, "getVolume") ?: DEFAULT_VOLUME
        }
        // Use AudioManager percentage, same as hookProfileContextVolume.
        // The headset protocol raw value (snapshot.currentVolume) does NOT
        // match MiLink's slider range (0-100%). Using AudioManager ensures
        // HeadsetInfo.headsetVolume matches the getter on first card open.
        val am = audioManager ?: return intGetter(original, "getVolume") ?: DEFAULT_VOLUME
        val streamVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val min = am.getStreamMinVolume(android.media.AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        if (max <= min) return DEFAULT_VOLUME
        return kotlin.math.round(((streamVol - min).toFloat() / (max - min).toFloat()) * 100f).toInt()
    }

    fun resolvedHeadsetInfoAudioEffect(snapshot: MilinkDeviceSnapshot, original: Any?): Int =
        if (snapshot.supportsAudioEffect && snapshot.currentAudioEffectState != null) {
            snapshot.currentAudioEffectState
        } else {
            intGetter(original, "getAudioEffectState") ?: AUDIO_EFFECT_UNSUPPORTED
        }

    fun resolvedHeadsetInfoType(snapshot: MilinkDeviceSnapshot, original: Any?): Int =
        when (snapshot.formFactor) {
            FORM_FACTOR_TRUE_WIRELESS -> HEADSET_INFO_TYPE_EARBUD
            FORM_FACTOR_HEADSET -> HEADSET_INFO_TYPE_HEADSET
            else -> intGetter(original, "getType") ?: HEADSET_INFO_TYPE_HEADSET
        }

    private fun buildHeadsetDevice(snapshot: MilinkDeviceSnapshot): Any? {
        val clazz = headsetDeviceClass ?: return null
        val bluetoothDevice = deviceLookup(snapshot.mac) ?: return null
        return runCatching {
            clazz.getConstructor(
                String::class.java,
                String::class.java,
                String::class.java,
                BluetoothDevice::class.java,
            ).newInstance(
                snapshot.mac,
                snapshot.name.orEmpty().ifBlank { snapshot.model.orEmpty().ifBlank { "OpenBuds" } },
                deviceIdForMac(snapshot.mac),
                bluetoothDevice,
            )
        }.getOrNull()
    }

    fun headsetDeviceAddress(headsetDevice: Any?): String? =
        stringGetter(headsetDevice, "getAddress")?.normalizeMac()

    fun headsetInfoAddress(headsetInfo: Any?): String? =
        stringGetter(headsetInfo, "getAddress")?.normalizeMac()

    private fun stringGetter(target: Any?, methodName: String): String? =
        runCatching {
            target?.javaClass?.getDeclaredMethod(methodName)
                ?.apply { isAccessible = true }
                ?.invoke(target) as? String
        }.getOrNull()

    private fun intGetter(target: Any?, methodName: String): Int? =
        runCatching {
            target?.javaClass?.getDeclaredMethod(methodName)
                ?.apply { isAccessible = true }
                ?.invoke(target) as? Int
        }.getOrNull()

    fun safeMac(device: BluetoothDevice?): String =
        runCatching { device?.address?.normalizeMac() ?: device?.address.orEmpty() }
            .getOrDefault("")

    companion object {
        const val PROFILE_SUCCESS = 100
        const val PROFILE_TARGET_NOT_MATCH = 206
        const val DEFAULT_VOLUME = 0
        const val AUDIO_EFFECT_UNSUPPORTED = -1
        const val FORM_FACTOR_HEADSET = 0
        const val FORM_FACTOR_TRUE_WIRELESS = 1

        private const val HEADSET_DEVICE = "com.miui.headset.runtime.HeadsetDevice"
        private const val HEADSET_INFO = "com.miui.headset.api.HeadsetInfo"
        private const val HEADSET_INFO_TYPE_HEADSET = 2
        private const val HEADSET_INFO_TYPE_EARBUD = 0
        private const val SWITCH_STATE_DEFAULT = 0
        private const val WIRED_STATE_DEFAULT = 0
    }
}
