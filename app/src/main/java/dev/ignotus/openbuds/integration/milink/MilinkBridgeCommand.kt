package dev.ignotus.openbuds.integration.milink

import android.os.Bundle
import dev.ignotus.openbuds.protocol.NoiseControlMode

data class MilinkBridgeCommandEnvelope(
    val commandType: String? = null,
    val noiseMode: Int? = null,
    val volume: Int? = null,
    val audioEffectState: Int? = null,
    val ringEnabled: Boolean? = null,
    val requestId: String? = null,
)

sealed class MilinkBridgeCommandAction {
    data class SetNoiseControl(val mode: NoiseControlMode) : MilinkBridgeCommandAction()
    data class SetVolume(val volume: Int) : MilinkBridgeCommandAction()
    data class SetAudioEffect(val state: Int) : MilinkBridgeCommandAction()
    object StartRing : MilinkBridgeCommandAction()
    object StopRing : MilinkBridgeCommandAction()
}

data class MilinkBridgeCommandDecision(
    val success: Boolean,
    val reason: String,
    val requestId: String?,
    val action: MilinkBridgeCommandAction? = null,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putBoolean(MilinkBridgeContract.KEY_SUCCESS, success)
        putString(MilinkBridgeContract.KEY_REASON, reason)
        requestId?.let { putString(MilinkBridgeContract.KEY_REQUEST_ID, it) }
    }

    companion object {
        fun accepted(
            requestId: String?,
            action: MilinkBridgeCommandAction,
        ): MilinkBridgeCommandDecision =
            MilinkBridgeCommandDecision(
                success = true,
                reason = MilinkBridgeContract.REASON_OK,
                requestId = requestId,
                action = action,
            )

        fun rejected(
            reason: String,
            requestId: String?,
        ): MilinkBridgeCommandDecision =
            MilinkBridgeCommandDecision(
                success = false,
                reason = reason,
                requestId = requestId,
            )
    }
}

object MilinkBridgeCommandProcessor {
    fun evaluate(
        adapterEnabled: Boolean,
        snapshot: MilinkDeviceSnapshot?,
        command: MilinkBridgeCommandEnvelope,
    ): MilinkBridgeCommandDecision {
        if (!adapterEnabled) {
            return MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_DISABLED,
                command.requestId,
            )
        }
        if (snapshot == null) {
            return MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_UNAUTHORIZED_DEVICE,
                command.requestId,
            )
        }
        if (!snapshot.connected) {
            return MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_NOT_CONNECTED,
                command.requestId,
            )
        }
        if (!snapshot.protocolReady) {
            return MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_PROTOCOL_NOT_READY,
                command.requestId,
            )
        }

        return when (command.commandType) {
            MilinkBridgeContract.COMMAND_SET_NOISE_CONTROL -> evaluateNoiseControl(snapshot, command)
            MilinkBridgeContract.COMMAND_SET_VOLUME -> evaluateVolume(snapshot, command)
            MilinkBridgeContract.COMMAND_SET_AUDIO_EFFECT -> evaluateAudioEffect(snapshot, command)
            MilinkBridgeContract.COMMAND_START_RING -> evaluateRing(snapshot, command, start = true)
            MilinkBridgeContract.COMMAND_STOP_RING,
            MilinkBridgeContract.COMMAND_RING_FIND -> evaluateRing(
                snapshot = snapshot,
                command = command,
                start = command.ringEnabled ?: false,
            )
            null, "" -> MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_INVALID_COMMAND,
                command.requestId,
            )
            else -> MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_UNSUPPORTED_COMMAND,
                command.requestId,
            )
        }
    }

    private fun evaluateNoiseControl(
        snapshot: MilinkDeviceSnapshot,
        command: MilinkBridgeCommandEnvelope,
    ): MilinkBridgeCommandDecision {
        if (!snapshot.supportsNoiseControl) {
            return MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_UNSUPPORTED_CAPABILITY,
                command.requestId,
            )
        }
        val mode = when (command.noiseMode) {
            0 -> NoiseControlMode.OFF
            1 -> NoiseControlMode.NOISE_CANCELLING
            2 -> NoiseControlMode.AMBIENT_SOUND
            else -> return MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_INVALID_NOISE_MODE,
                command.requestId,
            )
        }
        return MilinkBridgeCommandDecision.accepted(
            requestId = command.requestId,
            action = MilinkBridgeCommandAction.SetNoiseControl(mode),
        )
    }

    private fun evaluateVolume(
        snapshot: MilinkDeviceSnapshot,
        command: MilinkBridgeCommandEnvelope,
    ): MilinkBridgeCommandDecision {
        if (!snapshot.supportsVolumeControl) {
            return MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_UNSUPPORTED_CAPABILITY,
                command.requestId,
            )
        }
        val volume = command.volume?.takeIf { it in 0..100 }
            ?: return MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_INVALID_VOLUME,
                command.requestId,
            )
        return MilinkBridgeCommandDecision.accepted(
            requestId = command.requestId,
            action = MilinkBridgeCommandAction.SetVolume(volume),
        )
    }

    private fun evaluateAudioEffect(
        snapshot: MilinkDeviceSnapshot,
        command: MilinkBridgeCommandEnvelope,
    ): MilinkBridgeCommandDecision {
        if (!snapshot.supportsAudioEffect) {
            return MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_UNSUPPORTED_CAPABILITY,
                command.requestId,
            )
        }
        val state = command.audioEffectState?.takeIf { it >= 0 }
            ?: return MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_INVALID_AUDIO_EFFECT,
                command.requestId,
            )
        return MilinkBridgeCommandDecision.accepted(
            requestId = command.requestId,
            action = MilinkBridgeCommandAction.SetAudioEffect(state),
        )
    }

    private fun evaluateRing(
        snapshot: MilinkDeviceSnapshot,
        command: MilinkBridgeCommandEnvelope,
        start: Boolean,
    ): MilinkBridgeCommandDecision {
        if (!snapshot.supportsRing) {
            return MilinkBridgeCommandDecision.rejected(
                MilinkBridgeContract.REASON_UNSUPPORTED_CAPABILITY,
                command.requestId,
            )
        }
        return MilinkBridgeCommandDecision.accepted(
            requestId = command.requestId,
            action = if (start) {
                MilinkBridgeCommandAction.StartRing
            } else {
                MilinkBridgeCommandAction.StopRing
            },
        )
    }
}

fun Bundle?.toMilinkBridgeCommandEnvelope(): MilinkBridgeCommandEnvelope =
    MilinkBridgeCommandEnvelope(
        commandType = this?.getString(MilinkBridgeContract.KEY_COMMAND_TYPE),
        noiseMode = this?.takeIf { it.containsKey(MilinkBridgeContract.KEY_NOISE_MODE) }
            ?.getInt(MilinkBridgeContract.KEY_NOISE_MODE),
        volume = this?.takeIf { it.containsKey(MilinkBridgeContract.KEY_VOLUME) }
            ?.getInt(MilinkBridgeContract.KEY_VOLUME),
        audioEffectState = this?.takeIf { it.containsKey(MilinkBridgeContract.KEY_AUDIO_EFFECT_STATE) }
            ?.getInt(MilinkBridgeContract.KEY_AUDIO_EFFECT_STATE),
        ringEnabled = this?.takeIf { it.containsKey(MilinkBridgeContract.KEY_RING_ENABLED) }
            ?.getBoolean(MilinkBridgeContract.KEY_RING_ENABLED),
        requestId = this?.getString(MilinkBridgeContract.KEY_REQUEST_ID),
    )
