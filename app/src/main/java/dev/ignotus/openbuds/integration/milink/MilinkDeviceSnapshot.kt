package dev.ignotus.openbuds.integration.milink

import android.os.Bundle
import dev.ignotus.openbuds.data.HeadphoneUiState
import dev.ignotus.openbuds.headphones.HeadphoneFeature
import dev.ignotus.openbuds.headphones.HeadphoneFormFactor
import dev.ignotus.openbuds.lsposed.mitws.MiTwsDeviceIdPolicy
import dev.ignotus.openbuds.protocol.NoiseControlMode

data class MilinkDeviceSnapshot(
    val mac: String,
    val name: String?,
    val brand: String?,
    val model: String?,
    val deviceId: String,
    val formFactor: Int?,
    val connected: Boolean,
    val protocolReady: Boolean,
    val leftBattery: Int?,
    val rightBattery: Int?,
    val caseBattery: Int?,
    val singleBattery: Int?,
    val leftWearing: Boolean?,
    val rightWearing: Boolean?,
    val leftCharging: Boolean?,
    val rightCharging: Boolean?,
    val caseCharging: Boolean?,
    val ancMode: Int?,
    val ringing: Boolean,
    val currentVolume: Int?,
    val currentAudioEffectState: Int?,
    val supportsBattery: Boolean,
    val supportsNoiseControl: Boolean,
    val supportsWearing: Boolean,
    val supportsRing: Boolean,
    val supportsVolumeControl: Boolean,
    val supportsAudioEffect: Boolean,
    val revision: Long,
    val updatedAt: Long,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(MilinkBridgeContract.KEY_MAC, mac)
        name?.let { putString(MilinkBridgeContract.KEY_NAME, it) }
        brand?.let { putString(MilinkBridgeContract.KEY_BRAND, it) }
        model?.let { putString(MilinkBridgeContract.KEY_MODEL, it) }
        putString(MilinkBridgeContract.KEY_DEVICE_ID, deviceId)
        formFactor?.let { putInt(MilinkBridgeContract.KEY_FORM_FACTOR, it) }
        putBoolean(MilinkBridgeContract.KEY_CONNECTED, connected)
        putBoolean(MilinkBridgeContract.KEY_PROTOCOL_READY, protocolReady)
        leftBattery?.let { putInt(MilinkBridgeContract.KEY_LEFT_BATTERY, it) }
        rightBattery?.let { putInt(MilinkBridgeContract.KEY_RIGHT_BATTERY, it) }
        caseBattery?.let { putInt(MilinkBridgeContract.KEY_CASE_BATTERY, it) }
        singleBattery?.let { putInt(MilinkBridgeContract.KEY_SINGLE_BATTERY, it) }
        leftWearing?.let { putBoolean(MilinkBridgeContract.KEY_LEFT_WEARING, it) }
        rightWearing?.let { putBoolean(MilinkBridgeContract.KEY_RIGHT_WEARING, it) }
        leftCharging?.let { putBoolean(MilinkBridgeContract.KEY_LEFT_CHARGING, it) }
        rightCharging?.let { putBoolean(MilinkBridgeContract.KEY_RIGHT_CHARGING, it) }
        caseCharging?.let { putBoolean(MilinkBridgeContract.KEY_CASE_CHARGING, it) }
        ancMode?.let { putInt(MilinkBridgeContract.KEY_ANC_MODE, it) }
        putBoolean(MilinkBridgeContract.KEY_RINGING, ringing)
        currentVolume?.let { putInt(MilinkBridgeContract.KEY_CURRENT_VOLUME, it) }
        currentAudioEffectState?.let { putInt(MilinkBridgeContract.KEY_CURRENT_AUDIO_EFFECT_STATE, it) }
        putBoolean(MilinkBridgeContract.KEY_SUPPORTS_BATTERY, supportsBattery)
        putBoolean(MilinkBridgeContract.KEY_SUPPORTS_NOISE_CONTROL, supportsNoiseControl)
        putBoolean(MilinkBridgeContract.KEY_SUPPORTS_WEARING, supportsWearing)
        putBoolean(MilinkBridgeContract.KEY_SUPPORTS_RING, supportsRing)
        putBoolean(MilinkBridgeContract.KEY_SUPPORTS_VOLUME_CONTROL, supportsVolumeControl)
        putBoolean(MilinkBridgeContract.KEY_SUPPORTS_AUDIO_EFFECT, supportsAudioEffect)
        putLong(MilinkBridgeContract.KEY_REVISION, revision)
        putLong(MilinkBridgeContract.KEY_UPDATED_AT, updatedAt)
    }

    companion object {
        fun fromBundle(bundle: Bundle?): MilinkDeviceSnapshot? {
            if (bundle == null) return null
            val mac = bundle.getString(MilinkBridgeContract.KEY_MAC)?.normalizeMac() ?: return null
            return MilinkDeviceSnapshot(
                mac = mac,
                name = bundle.getString(MilinkBridgeContract.KEY_NAME),
                brand = bundle.getString(MilinkBridgeContract.KEY_BRAND),
                model = bundle.getString(MilinkBridgeContract.KEY_MODEL),
                deviceId = bundle.getString(MilinkBridgeContract.KEY_DEVICE_ID)
                    ?: MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
                formFactor = bundle.intOrNull(MilinkBridgeContract.KEY_FORM_FACTOR, min = 0, max = 2),
                connected = bundle.getBoolean(MilinkBridgeContract.KEY_CONNECTED, false),
                protocolReady = bundle.getBoolean(MilinkBridgeContract.KEY_PROTOCOL_READY, false),
                leftBattery = bundle.intOrNull(MilinkBridgeContract.KEY_LEFT_BATTERY),
                rightBattery = bundle.intOrNull(MilinkBridgeContract.KEY_RIGHT_BATTERY),
                caseBattery = bundle.intOrNull(MilinkBridgeContract.KEY_CASE_BATTERY),
                singleBattery = bundle.intOrNull(MilinkBridgeContract.KEY_SINGLE_BATTERY),
                leftWearing = bundle.booleanOrNull(MilinkBridgeContract.KEY_LEFT_WEARING),
                rightWearing = bundle.booleanOrNull(MilinkBridgeContract.KEY_RIGHT_WEARING),
                leftCharging = bundle.booleanOrNull(MilinkBridgeContract.KEY_LEFT_CHARGING),
                rightCharging = bundle.booleanOrNull(MilinkBridgeContract.KEY_RIGHT_CHARGING),
                caseCharging = bundle.booleanOrNull(MilinkBridgeContract.KEY_CASE_CHARGING),
                ancMode = bundle.intOrNull(MilinkBridgeContract.KEY_ANC_MODE, min = 0, max = 2),
                ringing = bundle.getBoolean(MilinkBridgeContract.KEY_RINGING, false),
                currentVolume = bundle.intOrNull(MilinkBridgeContract.KEY_CURRENT_VOLUME),
                currentAudioEffectState = bundle.intOrNull(
                    MilinkBridgeContract.KEY_CURRENT_AUDIO_EFFECT_STATE,
                    min = -1,
                    max = 10,
                ),
                supportsBattery = bundle.getBoolean(MilinkBridgeContract.KEY_SUPPORTS_BATTERY, false),
                supportsNoiseControl = bundle.getBoolean(MilinkBridgeContract.KEY_SUPPORTS_NOISE_CONTROL, false),
                supportsWearing = bundle.getBoolean(MilinkBridgeContract.KEY_SUPPORTS_WEARING, false),
                supportsRing = bundle.getBoolean(MilinkBridgeContract.KEY_SUPPORTS_RING, false),
                supportsVolumeControl = bundle.getBoolean(MilinkBridgeContract.KEY_SUPPORTS_VOLUME_CONTROL, false),
                supportsAudioEffect = bundle.getBoolean(MilinkBridgeContract.KEY_SUPPORTS_AUDIO_EFFECT, false),
                revision = bundle.getLong(MilinkBridgeContract.KEY_REVISION, 0L),
                updatedAt = bundle.getLong(MilinkBridgeContract.KEY_UPDATED_AT, 0L),
            )
        }
    }
}

object MilinkBridgeSnapshotMapper {
    fun fromUiState(
        state: HeadphoneUiState,
        revision: Long,
        updatedAt: Long,
    ): MilinkDeviceSnapshot? {
        val device = state.connectedDevice ?: return null
        val mac = device.address.normalizeMac() ?: return null
        val battery = state.batteryState
        val profile = state.connectedProfile
        val noiseControl = state.noiseControlState
        return MilinkDeviceSnapshot(
            mac = mac,
            name = device.name.takeIf { it.isNotBlank() } ?: profile?.displayName,
            brand = profile?.brand,
            model = state.deviceInfo.modelName ?: profile?.modelName,
            deviceId = MiTwsDeviceIdPolicy.deviceIdForMac(mac),
            formFactor = when (profile?.capabilities?.formFactor) {
                HeadphoneFormFactor.HEADSET -> 0
                HeadphoneFormFactor.TRUE_WIRELESS -> 1
                HeadphoneFormFactor.UNKNOWN, null -> null
            },
            connected = true,
            protocolReady = state.deviceInfo.protocolReady,
            leftBattery = battery.left,
            rightBattery = battery.right,
            caseBattery = battery.cradle,
            singleBattery = battery.single,
            leftWearing = state.wearingState.leftWearing,
            rightWearing = state.wearingState.rightWearing,
            leftCharging = battery.leftCharging,
            rightCharging = battery.rightCharging,
            caseCharging = battery.cradleCharging,
            ancMode = when (noiseControl.controlMode) {
                NoiseControlMode.OFF -> 0
                NoiseControlMode.NOISE_CANCELLING -> 1
                NoiseControlMode.AMBIENT_SOUND -> 2
                null -> null
            },
            ringing = false,
            currentVolume = state.volumeState.musicVolume,
            currentAudioEffectState = if (state.audioEffectState.enabled) 1 else 0,
            supportsBattery = profile.supports(HeadphoneFeature.BATTERY),
            supportsNoiseControl = profile.supports(HeadphoneFeature.NOISE_CONTROL),
            supportsWearing = profile.supports(HeadphoneFeature.WEARING_STATUS),
            // OpenBuds does not yet expose a true find-earbud command/state to MiLink.
            supportsRing = false,
            supportsVolumeControl = profile.supports(HeadphoneFeature.VOLUME),
            supportsAudioEffect = profile.supports(HeadphoneFeature.AUDIO_EFFECT),
            revision = revision,
            updatedAt = updatedAt,
        )
    }
}

fun String.normalizeMac(): String? {
    val normalized = trim().uppercase()
    return normalized.takeIf { MAC_REGEX.matches(it) }
}

private fun Bundle.intOrNull(key: String, min: Int = 0, max: Int = 100): Int? =
    if (containsKey(key)) getInt(key).coerceIn(min, max) else null

private fun Bundle.booleanOrNull(key: String): Boolean? =
    if (containsKey(key)) getBoolean(key) else null

private val MAC_REGEX = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")

private fun dev.ignotus.openbuds.headphones.ConnectedHeadphoneProfile?.supports(
    feature: HeadphoneFeature,
): Boolean =
    this?.supports(feature) == true
