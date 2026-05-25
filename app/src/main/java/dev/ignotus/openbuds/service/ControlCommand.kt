package dev.ignotus.openbuds.service

sealed class ControlCommand {
    data class SetNoiseControl(val mode: dev.ignotus.openbuds.protocol.NoiseControlMode) : ControlCommand()
    data class SetAmbientLevel(val level: Int) : ControlCommand()
    data class SetAmbientVoiceMode(val enabled: Boolean) : ControlCommand()
    data class Playback(val action: PlaybackAction) : ControlCommand()
    data object Refresh : ControlCommand()

    enum class PlaybackAction { PREVIOUS, PLAY_PAUSE, NEXT }
}
