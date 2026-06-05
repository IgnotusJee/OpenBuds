package dev.ignotus.openbuds.lsposed.mitws

import android.os.Bundle
import dev.ignotus.openbuds.integration.milink.MilinkBridgeContract
import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import java.util.UUID

data class MiTwsBridgeCommandResult(
    val accepted: Boolean,
    val reason: String,
    val requestId: String?,
) {
    companion object {
        fun failed(
            reason: String,
            requestId: String? = null,
        ): MiTwsBridgeCommandResult =
            MiTwsBridgeCommandResult(
                accepted = false,
                reason = reason,
                requestId = requestId,
            )
    }
}

data class MiTwsAncCommand(
    val commandType: String,
    val noiseMode: Int,
    val requestId: String,
)

object MiTwsControlMapper {
    const val METHOD_OPEN_ANC = "openAnc"
    const val METHOD_OPEN_TRANSPARENT = "openTransparent"
    const val METHOD_CLOSE_ANC = "closeAnc"

    fun noiseModeForAncMethod(methodName: String): Int? =
        when (methodName) {
            METHOD_OPEN_ANC -> 1
            METHOD_OPEN_TRANSPARENT -> 2
            METHOD_CLOSE_ANC -> 0
            else -> null
        }

    fun buildAncCommand(
        methodName: String,
        snapshot: MilinkDeviceSnapshot,
        requestId: String = newRequestId(),
    ): Bundle? {
        val command = buildAncCommandValue(methodName, snapshot, requestId) ?: return null
        return toBundle(command)
    }

    fun buildAncCommandValue(
        methodName: String,
        snapshot: MilinkDeviceSnapshot,
        requestId: String,
    ): MiTwsAncCommand? {
        if (!snapshot.supportsNoiseControl) return null
        val noiseMode = noiseModeForAncMethod(methodName) ?: return null
        return MiTwsAncCommand(
            commandType = MilinkBridgeContract.COMMAND_SET_NOISE_CONTROL,
            noiseMode = noiseMode,
            requestId = requestId,
        )
    }

    fun buildAncCommandForMode(
        noiseMode: Int,
        snapshot: MilinkDeviceSnapshot,
        requestId: String = newRequestId(),
    ): Bundle? {
        val methodName = when (noiseMode) {
            0 -> METHOD_CLOSE_ANC
            1 -> METHOD_OPEN_ANC
            2 -> METHOD_OPEN_TRANSPARENT
            else -> return null
        }
        return buildAncCommand(methodName, snapshot, requestId)
    }

    fun commandType(command: Bundle): String? =
        command.getString(MilinkBridgeContract.KEY_COMMAND_TYPE)

    fun noiseMode(command: Bundle): Int? =
        if (command.containsKey(MilinkBridgeContract.KEY_NOISE_MODE)) {
            command.getInt(MilinkBridgeContract.KEY_NOISE_MODE)
        } else {
            null
        }

    fun volume(command: Bundle): Int? =
        if (command.containsKey(MilinkBridgeContract.KEY_VOLUME)) {
            command.getInt(MilinkBridgeContract.KEY_VOLUME)
        } else {
            null
        }

    fun buildSetVolumeCommand(
        volumeValue: Int,
        snapshot: MilinkDeviceSnapshot,
        requestId: String = newRequestId(),
    ): Bundle? {
        if (!snapshot.supportsVolumeControl) return null
        val clamped = volumeValue.coerceIn(0, 255)
        return Bundle().apply {
            putString(MilinkBridgeContract.KEY_COMMAND_TYPE, MilinkBridgeContract.COMMAND_SET_VOLUME)
            putInt(MilinkBridgeContract.KEY_VOLUME, clamped)
            putString(MilinkBridgeContract.KEY_REQUEST_ID, requestId)
        }
    }

    fun parseResult(result: Bundle?): MiTwsBridgeCommandResult =
        if (result == null) {
            MiTwsBridgeCommandResult.failed(MilinkBridgeContract.REASON_BRIDGE_UNAVAILABLE)
        } else {
            MiTwsBridgeCommandResult(
                accepted = result.getBoolean(MilinkBridgeContract.KEY_SUCCESS, false),
                reason = result.getString(MilinkBridgeContract.KEY_REASON)
                    ?: MilinkBridgeContract.REASON_REMOTE_ERROR,
                requestId = result.getString(MilinkBridgeContract.KEY_REQUEST_ID),
            )
        }

    fun failureResult(reason: String, requestId: String? = null): Bundle =
        Bundle().apply {
            putBoolean(MilinkBridgeContract.KEY_SUCCESS, false)
            putString(MilinkBridgeContract.KEY_REASON, reason)
            requestId?.let { putString(MilinkBridgeContract.KEY_REQUEST_ID, it) }
        }

    private fun toBundle(command: MiTwsAncCommand): Bundle =
        Bundle().apply {
            putString(MilinkBridgeContract.KEY_COMMAND_TYPE, command.commandType)
            putInt(MilinkBridgeContract.KEY_NOISE_MODE, command.noiseMode)
            putString(MilinkBridgeContract.KEY_REQUEST_ID, command.requestId)
        }

    private fun newRequestId(): String = UUID.randomUUID().toString()
}
