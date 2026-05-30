package dev.ignotus.openbuds.protocol

enum class EqPresetId(val code: Byte, val displayName: String) {
    OFF(0x00, "Off"),
    ROCK(0x01, "Rock"),
    POP(0x02, "Pop"),
    JAZZ(0x03, "Jazz"),
    DANCE(0x04, "Dance"),
    EDM(0x05, "EDM"),
    R_AND_B_HIP_HOP(0x06, "R&B / Hip-Hop"),
    ACOUSTIC(0x07, "Acoustic"),
    BRIGHT(0x10, "Bright"),
    EXCITED(0x11, "Excited"),
    MELLOW(0x12, "Mellow"),
    RELAXED(0x13, "Relaxed"),
    VOCAL(0x14, "Vocal"),
    TREBLE(0x15, "Treble"),
    BASS(0x16, "Bass"),
    SPEECH(0x17, "Speech"),
    HEAVY(0x30, "Heavy"),
    CLEAR(0x31, "Clear"),
    HARD(0x32, "Hard"),
    SOFT(0x33, "Soft"),
    CUSTOM(0xA0.toByte(), "Custom"),
    USER_SETTING1(0xA1.toByte(), "User Setting 1"),
    USER_SETTING2(0xA2.toByte(), "User Setting 2"),
    UNSPECIFIED(0xFF.toByte(), "Unspecified"),
}

enum class AmbientSoundMode(val code: Byte) {
    NORMAL(0x00),
    VOICE(0x01),
}

enum class NoiseControlMode {
    OFF,
    NOISE_CANCELLING,
    AMBIENT_SOUND,
}

enum class PlaybackStatus {
    UNKNOWN,
    PLAYING,
    PAUSED,
    STOPPED,
}
