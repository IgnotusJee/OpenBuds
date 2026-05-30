package dev.ignotus.openbuds.service

import android.os.Bundle
import dev.ignotus.openbuds.data.HeadphoneUiState
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dev.ignotus.openbuds.protocol.PlaybackStatus

data class DeviceStateSnapshot(
    val deviceName: String?,
    val deviceMac: String?,
    val batterySingle: Int?,
    val batteryLeft: Int?,
    val batteryRight: Int?,
    val batteryCradle: Int?,
    val noiseControlMode: NoiseControlMode?,
    val noiseCancellingEnabled: Boolean?,
    val ambientSoundEnabled: Boolean?,
    val ambientLevel: Int?,
    val ambientVoiceMode: Boolean,
    val eqPresetName: String?,
    val eqClearBass: Int?,
    val playbackStatus: PlaybackStatus,
    val isConnected: Boolean,
    val isProtocolReady: Boolean,
) {
    fun toBundle(): Bundle = Bundle().apply {
        deviceName?.let { putString(KEY_DEVICE_NAME, it) }
        deviceMac?.let { putString(KEY_DEVICE_MAC, it) }
        batterySingle?.let { putInt(KEY_BATTERY_SINGLE, it) }
        batteryLeft?.let { putInt(KEY_BATTERY_LEFT, it) }
        batteryRight?.let { putInt(KEY_BATTERY_RIGHT, it) }
        batteryCradle?.let { putInt(KEY_BATTERY_CRADLE, it) }
        noiseControlMode?.let { putString(KEY_NC_MODE, it.name) }
        noiseCancellingEnabled?.let { putBoolean(KEY_NC_ENABLED, it) }
        ambientSoundEnabled?.let { putBoolean(KEY_ASM_ENABLED, it) }
        ambientLevel?.let { putInt(KEY_AMBIENT_LEVEL, it) }
        putBoolean(KEY_AMBIENT_VOICE, ambientVoiceMode)
        eqPresetName?.let { putString(KEY_EQ_PRESET, it) }
        eqClearBass?.let { putInt(KEY_EQ_CLEAR_BASS, it) }
        putString(KEY_PLAYBACK_STATUS, playbackStatus.name)
        putBoolean(KEY_IS_CONNECTED, isConnected)
        putBoolean(KEY_IS_PROTOCOL_READY, isProtocolReady)
    }

    companion object {
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_DEVICE_MAC = "device_mac"
        private const val KEY_BATTERY_SINGLE = "battery_single"
        private const val KEY_BATTERY_LEFT = "battery_left"
        private const val KEY_BATTERY_RIGHT = "battery_right"
        private const val KEY_BATTERY_CRADLE = "battery_cradle"
        private const val KEY_NC_MODE = "nc_mode"
        private const val KEY_NC_ENABLED = "nc_enabled"
        private const val KEY_ASM_ENABLED = "asm_enabled"
        private const val KEY_AMBIENT_LEVEL = "ambient_level"
        private const val KEY_AMBIENT_VOICE = "ambient_voice"
        private const val KEY_EQ_PRESET = "eq_preset"
        private const val KEY_EQ_CLEAR_BASS = "eq_clear_bass"
        private const val KEY_PLAYBACK_STATUS = "playback_status"
        private const val KEY_IS_CONNECTED = "is_connected"
        private const val KEY_IS_PROTOCOL_READY = "is_protocol_ready"

        val EMPTY = DeviceStateSnapshot(
            deviceName = null, deviceMac = null,
            batterySingle = null, batteryLeft = null, batteryRight = null, batteryCradle = null,
            noiseControlMode = null, noiseCancellingEnabled = null, ambientSoundEnabled = null,
            ambientLevel = null, ambientVoiceMode = false,
            eqPresetName = null, eqClearBass = null,
            playbackStatus = PlaybackStatus.UNKNOWN,
            isConnected = false, isProtocolReady = false,
        )

        fun fromBundle(bundle: Bundle): DeviceStateSnapshot = DeviceStateSnapshot(
            deviceName = bundle.getString(KEY_DEVICE_NAME),
            deviceMac = bundle.getString(KEY_DEVICE_MAC),
            batterySingle = if (bundle.containsKey(KEY_BATTERY_SINGLE)) bundle.getInt(KEY_BATTERY_SINGLE) else null,
            batteryLeft = if (bundle.containsKey(KEY_BATTERY_LEFT)) bundle.getInt(KEY_BATTERY_LEFT) else null,
            batteryRight = if (bundle.containsKey(KEY_BATTERY_RIGHT)) bundle.getInt(KEY_BATTERY_RIGHT) else null,
            batteryCradle = if (bundle.containsKey(KEY_BATTERY_CRADLE)) bundle.getInt(KEY_BATTERY_CRADLE) else null,
            noiseControlMode = bundle.getString(KEY_NC_MODE)?.let { runCatching { NoiseControlMode.valueOf(it) }.getOrNull() },
            noiseCancellingEnabled = if (bundle.containsKey(KEY_NC_ENABLED)) bundle.getBoolean(KEY_NC_ENABLED) else null,
            ambientSoundEnabled = if (bundle.containsKey(KEY_ASM_ENABLED)) bundle.getBoolean(KEY_ASM_ENABLED) else null,
            ambientLevel = if (bundle.containsKey(KEY_AMBIENT_LEVEL)) bundle.getInt(KEY_AMBIENT_LEVEL) else null,
            ambientVoiceMode = bundle.getBoolean(KEY_AMBIENT_VOICE, false),
            eqPresetName = bundle.getString(KEY_EQ_PRESET),
            eqClearBass = if (bundle.containsKey(KEY_EQ_CLEAR_BASS)) bundle.getInt(KEY_EQ_CLEAR_BASS) else null,
            playbackStatus = bundle.getString(KEY_PLAYBACK_STATUS)?.let { runCatching { PlaybackStatus.valueOf(it) }.getOrNull() } ?: PlaybackStatus.UNKNOWN,
            isConnected = bundle.getBoolean(KEY_IS_CONNECTED, false),
            isProtocolReady = bundle.getBoolean(KEY_IS_PROTOCOL_READY, false),
        )

        fun fromUiState(state: HeadphoneUiState): DeviceStateSnapshot = DeviceStateSnapshot(
            deviceName = state.connectedDevice?.name ?: state.deviceInfo.modelName,
            deviceMac = state.connectedDevice?.address,
            batterySingle = state.batteryState.single,
            batteryLeft = state.batteryState.left,
            batteryRight = state.batteryState.right,
            batteryCradle = state.batteryState.cradle,
            noiseControlMode = state.noiseControlState.controlMode,
            noiseCancellingEnabled = state.noiseControlState.noiseCancellingEnabled,
            ambientSoundEnabled = state.noiseControlState.ambientSoundEnabled,
            ambientLevel = state.noiseControlState.ambientLevel,
            ambientVoiceMode = state.noiseControlState.ambientVoiceMode,
            eqPresetName = state.eqState.preset?.name,
            eqClearBass = state.eqState.clearBass,
            playbackStatus = state.playbackStatus,
            isConnected = state.connectedDevice != null,
            isProtocolReady = state.deviceInfo.protocolReady,
        )
    }
}
