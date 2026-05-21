package dev.ignotus.sonyrebuild.protocol

enum class DeviceInfoType(val code: Byte) {
    MODEL_NAME(0x01),
    FW_VERSION(0x02),
    SERIES_AND_COLOR_INFO(0x03),
    INSTRUCTION_GUIDE(0x04),
}

enum class CommonInquiredType(val code: Byte) {
    DISPLAY_FW_VERSION(0x09),
}

enum class PowerInquiredType(val code: Byte) {
    BATTERY(0x00),
    LEFT_RIGHT_BATTERY(0x01),
    CRADLE_BATTERY(0x02),
    AUTO_POWER_OFF(0x04),
    POWER_SAVE_MODE(0x06),
    STAMINA(0x0E),
}

enum class EqEbbInquiredType(val code: Byte) {
    PRESET_EQ(0x00),
    EBB(0x01),
    PRESET_EQ_NONCUSTOMIZABLE(0x02),
    PRESET_EQ_AND_ULT_MODE(0x03),
    PRESET_EQ_AND_ERRORCODE(0x04),
    SOUND_EFFECT(0x30),
    CUSTOM_EQ(0x31),
    TURN_KEY_EQ(0x32),
}

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
    CUSTOM(0xA0.toByte(), "手动"),
    USER_SETTING1(0xA1.toByte(), "自定义1"),
    USER_SETTING2(0xA2.toByte(), "自定义2"),
}

enum class NcAsmInquiredType(val code: Byte) {
    V1_TABLE_SET1_NC_ASM(0x02),
    NC_ON_OFF(0x01),
    NC_ON_OFF_AND_ASM_ON_OFF(0x11),
    NC_MODE_SWITCH_AND_ASM_ON_OFF(0x12),
    NC_ON_OFF_AND_ASM_SEAMLESS(0x13),
    NC_MODE_SWITCH_AND_ASM_SEAMLESS(0x14),
    MODE_NC_ASM_AUTO_NC_MODE_SWITCH_AND_ASM_SEAMLESS(0x15),
    MODE_NC_ASM_DUAL_SINGLE_NC_MODE_SWITCH_AND_ASM_SEAMLESS(0x16),
    MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS(0x17),
    MODE_NC_NCSS_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS(0x18),
    MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS_NA(0x19),
    ASM_ON_OFF(0x21),
    ASM_SEAMLESS(0x22),
    NC_AMB_TOGGLE(0x30),
}

enum class PlaybackControl(val code: Byte) {
    PAUSE(0x01),
    TRACK_UP(0x02),
    TRACK_DOWN(0x03),
    STOP(0x06),
    PLAY(0x07),
}

enum class PlayInquiredType(val code: Byte) {
    PLAYBACK_CONTROL_WITH_CALL_VOLUME_ADJUSTMENT(0x01),
    PLAYBACK_CONTROL_WITH_CALL_VOLUME_ADJUSTMENT_AND_FUNCTION_CHANGE(0x02),
    PLAYBACK_CONTROL_WITH_FUNCTION_CHANGE(0x03),
    PLAY_MODE(0x40),
}

enum class LeaInquiredType(val code: Byte) {
    TWS_SUPPORTS_A2DP_LEA_UNI_LEA_BROAD_WITH_CTKD(0x00),
    HBS_SUPPORTS_A2DP_LEA_UNI_LEA_BROAD_WITH_CTKD(0x01),
    TWS_SUPPORTS_LEA_UNI_LEA_BROAD(0x02),
}

enum class LeaConnectionType(val code: Byte) {
    SPP(0x00),
    BLE_GATT(0x01),
    OUT_OF_RANGE(0xFF.toByte()),
}

enum class LeaStreamingStatus(val code: Byte) {
    POWER_OFF(0x00),
    NONE(0x01),
    VIA_A2DP(0x02),
    VIA_LE_AUDIO_UNICAST(0x03),
    OUT_OF_RANGE(0xFF.toByte()),
}

enum class LeaPairedHistory(val code: Byte) {
    BOTH_CLASSIC_BT_BLE(0x00),
    ONLY_CLASSIC_BT(0x01),
    ONLY_BLE(0x02),
    OUT_OF_RANGE(0xFF.toByte()),
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

data class TandemMessage(
    val dataType: Byte,
    val command: Byte,
    val payload: ByteArray = byteArrayOf(),
) {
    fun toByteArray(): ByteArray = byteArrayOf(dataType, command) + payload

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TandemMessage) return false
        return dataType == other.dataType &&
            command == other.command &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = dataType.toInt()
        result = 31 * result + command
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

object SonyTandemFrame {
    const val DATA_MDR: Byte = 0x0E

    fun message(command: Byte, payload: ByteArray = byteArrayOf()): ByteArray =
        TandemMessage(DATA_MDR, command, payload).toByteArray()
}

sealed interface ParsedTandemResponse {
    val raw: ByteArray

    data class DeviceInfo(
        val type: DeviceInfoType?,
        val text: String?,
        override val raw: ByteArray,
    ) : ParsedTandemResponse

    data class CommonStatus(
        val type: CommonInquiredType?,
        val text: String?,
        val values: List<Int>,
        override val raw: ByteArray,
    ) : ParsedTandemResponse

    data class Battery(
        val kind: PowerInquiredType?,
        val values: List<Int>,
        override val raw: ByteArray,
    ) : ParsedTandemResponse

    data class EqEbb(
        val type: EqEbbInquiredType?,
        val enabled: Boolean? = null,
        val preset: EqPresetId? = null,
        val clearBass: Int? = null,
        val bandSteps: List<Int> = emptyList(),
        val values: List<Int>,
        override val raw: ByteArray,
    ) : ParsedTandemResponse

    data class NoiseControl(
        val type: NcAsmInquiredType?,
        val values: List<Int>,
        val enabled: Boolean? = null,
        val ambientSoundEnabled: Boolean? = null,
        val ambientLevel: Int? = null,
        val ambientMode: AmbientSoundMode? = null,
        val controlMode: NoiseControlMode? = null,
        override val raw: ByteArray,
    ) : ParsedTandemResponse

    data class PlaybackAck(
        val values: List<Int>,
        val status: PlaybackStatus = PlaybackStatus.UNKNOWN,
        override val raw: ByteArray,
    ) : ParsedTandemResponse

    data class LeaStatus(
        val type: LeaInquiredType?,
        val values: List<Int>,
        val connectionType: LeaConnectionType? = null,
        val streamingStatus: LeaStreamingStatus? = null,
        val pairedHistory: LeaPairedHistory? = null,
        override val raw: ByteArray,
    ) : ParsedTandemResponse

    data class Unknown(
        val dataType: Int?,
        val command: Int?,
        val payload: ByteArray,
        override val raw: ByteArray,
    ) : ParsedTandemResponse
}

object SonyTandemV2Table1Protocol {
    const val CONNECT_GET_PROTOCOL_INFO: Byte = 0x00
    const val CONNECT_RET_PROTOCOL_INFO: Byte = 0x01
    const val CONNECT_GET_DEVICE_INFO: Byte = 0x04
    const val CONNECT_RET_DEVICE_INFO: Byte = 0x05
    const val COMMON_GET_STATUS: Byte = 0x12
    const val COMMON_RET_STATUS: Byte = 0x13
    const val COMMON_NTFY_STATUS: Byte = 0x15
    const val POWER_GET_STATUS: Byte = 0x22
    const val POWER_RET_STATUS: Byte = 0x23
    const val POWER_NTFY_STATUS: Byte = 0x25
    const val EQEBB_GET_STATUS: Byte = 0x52
    const val EQEBB_RET_STATUS: Byte = 0x53
    const val EQEBB_NTFY_STATUS: Byte = 0x55
    const val EQEBB_GET_PARAM: Byte = 0x56
    const val EQEBB_RET_PARAM: Byte = 0x57
    const val EQEBB_SET_PARAM: Byte = 0x58
    const val EQEBB_NTFY_PARAM: Byte = 0x59
    const val NCASM_GET_STATUS: Byte = 0x62
    const val NCASM_RET_STATUS: Byte = 0x63
    const val NCASM_SET_STATUS: Byte = 0x64
    const val NCASM_NTFY_STATUS: Byte = 0x65
    const val NCASM_GET_PARAM: Byte = 0x66
    const val NCASM_RET_PARAM: Byte = 0x67
    const val NCASM_SET_PARAM: Byte = 0x68
    const val NCASM_NTFY_PARAM: Byte = 0x69
    const val PLAY_GET_STATUS: Byte = 0xA2.toByte()
    const val PLAY_RET_STATUS: Byte = 0xA3.toByte()
    const val PLAY_SET_STATUS: Byte = 0xA4.toByte()
    const val PLAY_NTFY_STATUS: Byte = 0xA5.toByte()
    const val LEA_GET_STATUS: Byte = 0x42
    const val LEA_RET_STATUS: Byte = 0x43
    const val LEA_NTFY_STATUS: Byte = 0x45

    private const val PLAYBACK_CONTROL_WITH_FUNCTION_CHANGE: Byte = 0x03
    private const val ENABLE: Byte = 0x00
    private const val DISABLE: Byte = 0x01
    private const val VALUE_CHANGED: Byte = 0x01
    private const val NCASM_OFF: Byte = 0x00
    private const val NCASM_ON: Byte = 0x01
    private const val NCASM_MODE_NC: Byte = 0x00
    private const val NCASM_MODE_ASM: Byte = 0x01
    private const val NC_VALUE_OFF: Byte = 0x00
    private const val NC_VALUE_ON_SINGLE: Byte = 0x01
    private const val NC_VALUE_ON_DUAL: Byte = 0x02

    fun buildGetProtocolInfo(): ByteArray =
        SonyTandemFrame.message(CONNECT_GET_PROTOCOL_INFO)

    fun buildGetDeviceInfo(type: DeviceInfoType): ByteArray =
        SonyTandemFrame.message(CONNECT_GET_DEVICE_INFO, byteArrayOf(type.code))

    fun buildGetDisplayFirmwareVersion(): ByteArray =
        SonyTandemFrame.message(COMMON_GET_STATUS, byteArrayOf(CommonInquiredType.DISPLAY_FW_VERSION.code))

    fun buildGetBatteryStatus(type: PowerInquiredType): ByteArray =
        SonyTandemFrame.message(POWER_GET_STATUS, byteArrayOf(type.code))

    fun buildGetEqEbbStatus(type: EqEbbInquiredType): ByteArray =
        SonyTandemFrame.message(EQEBB_GET_STATUS, byteArrayOf(type.code))

    fun buildGetEqEbbParam(type: EqEbbInquiredType): ByteArray =
        SonyTandemFrame.message(EQEBB_GET_PARAM, byteArrayOf(type.code))

    fun buildSetEqPreset(
        preset: EqPresetId,
        type: EqEbbInquiredType = EqEbbInquiredType.PRESET_EQ,
        bandSteps: List<Int> = emptyList(),
    ): ByteArray =
        SonyTandemFrame.message(
            EQEBB_SET_PARAM,
            byteArrayOf(type.code, preset.code, bandSteps.size.toByte()) +
                bandSteps.map { it.coerceIn(0, 255).toByte() }.toByteArray(),
        )

    fun buildSetCustomEqBandSteps(bandSteps: List<Int>): ByteArray =
        SonyTandemFrame.message(
            EQEBB_SET_PARAM,
            byteArrayOf(EqEbbInquiredType.CUSTOM_EQ.code, bandSteps.size.toByte()) +
                bandSteps.map { it.coerceIn(0, 255).toByte() }.toByteArray(),
        )

    fun buildSetClearBass(level: Int): ByteArray =
        SonyTandemFrame.message(
            EQEBB_SET_PARAM,
            byteArrayOf(EqEbbInquiredType.EBB.code, level.coerceIn(-127, 127).toByte()),
        )

    fun buildGetNcAsmStatus(type: NcAsmInquiredType): ByteArray =
        SonyTandemFrame.message(NCASM_GET_STATUS, byteArrayOf(type.code))

    fun buildGetNcAsmParam(type: NcAsmInquiredType): ByteArray =
        SonyTandemFrame.message(NCASM_GET_PARAM, byteArrayOf(type.code))

    fun buildSetNoiseControlMode(
        controlMode: NoiseControlMode,
        ambientLevel: Int = 10,
        ambientMode: AmbientSoundMode = AmbientSoundMode.NORMAL,
    ): ByteArray {
        val enabled = controlMode != NoiseControlMode.OFF
        val ncAsmMode = if (controlMode == NoiseControlMode.AMBIENT_SOUND) NCASM_MODE_ASM else NCASM_MODE_NC
        return SonyTandemFrame.message(
            NCASM_SET_PARAM,
            byteArrayOf(
                NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS.code,
                VALUE_CHANGED,
                if (enabled) NCASM_ON else NCASM_OFF,
                ncAsmMode,
                ambientMode.code,
                ambientLevel.coerceIn(1, 20).toByte(),
            ),
        )
    }

    fun buildSetNcModeSwitchAndAmbientLevel(
        controlMode: NoiseControlMode,
        ambientLevel: Int = 10,
        ambientMode: AmbientSoundMode = AmbientSoundMode.NORMAL,
    ): ByteArray {
        val totalEffect = if (controlMode == NoiseControlMode.OFF) NCASM_OFF else NCASM_ON
        val ncValue = when (controlMode) {
            NoiseControlMode.NOISE_CANCELLING -> NC_VALUE_ON_DUAL
            NoiseControlMode.AMBIENT_SOUND,
            NoiseControlMode.OFF -> NC_VALUE_OFF
        }
        val rawAmbientLevel = if (controlMode == NoiseControlMode.AMBIENT_SOUND) {
            (ambientLevel.coerceIn(1, 20) - 1).toByte()
        } else {
            0x00
        }
        return SonyTandemFrame.message(
            NCASM_SET_PARAM,
            byteArrayOf(
                NcAsmInquiredType.NC_MODE_SWITCH_AND_ASM_SEAMLESS.code,
                VALUE_CHANGED,
                totalEffect,
                ncValue,
                ambientMode.code,
                rawAmbientLevel,
            ),
        )
    }

    fun buildGetPlaybackStatus(
        type: PlayInquiredType = PlayInquiredType.PLAYBACK_CONTROL_WITH_CALL_VOLUME_ADJUSTMENT,
    ): ByteArray =
        SonyTandemFrame.message(PLAY_GET_STATUS, byteArrayOf(type.code))

    fun buildGetLeaStatus(type: LeaInquiredType): ByteArray =
        SonyTandemFrame.message(LEA_GET_STATUS, byteArrayOf(type.code))

    fun buildSetNcOnOff(enabled: Boolean): ByteArray =
        SonyTandemFrame.message(
            NCASM_SET_PARAM,
            byteArrayOf(
                NcAsmInquiredType.NC_ON_OFF.code,
                VALUE_CHANGED,
                if (enabled) NCASM_ON else NCASM_OFF,
                if (enabled) NCASM_ON else NCASM_OFF,
            ),
        )

    fun buildSetAmbientSound(
        enabled: Boolean,
        mode: AmbientSoundMode = AmbientSoundMode.NORMAL,
    ): ByteArray =
        SonyTandemFrame.message(
            NCASM_SET_PARAM,
            byteArrayOf(
                NcAsmInquiredType.ASM_ON_OFF.code,
                VALUE_CHANGED,
                if (enabled) NCASM_ON else NCASM_OFF,
                mode.code,
                if (enabled) NCASM_ON else NCASM_OFF,
            ),
        )

    fun buildSetAmbientLevel(
        level: Int,
        enabled: Boolean = true,
        mode: AmbientSoundMode = AmbientSoundMode.NORMAL,
    ): ByteArray =
        SonyTandemFrame.message(
            NCASM_SET_PARAM,
            byteArrayOf(
                NcAsmInquiredType.ASM_SEAMLESS.code,
                VALUE_CHANGED,
                if (enabled) NCASM_ON else NCASM_OFF,
                mode.code,
                level.coerceIn(0, 255).toByte(),
            ),
        )

    fun buildPlayback(control: PlaybackControl): ByteArray =
        SonyTandemFrame.message(
            PLAY_SET_STATUS,
            byteArrayOf(PLAYBACK_CONTROL_WITH_FUNCTION_CHANGE, ENABLE, control.code),
        )

    fun parse(raw: ByteArray): ParsedTandemResponse {
        val normalized = if (raw.firstOrNull() == SonyTandemFrame.DATA_MDR) raw else byteArrayOf(SonyTandemFrame.DATA_MDR) + raw
        if (normalized.size < 2) {
            return ParsedTandemResponse.Unknown(null, null, byteArrayOf(), raw)
        }
        val dataType = normalized[0]
        val command = normalized[1]
        val payload = normalized.drop(2).map { it }.toByteArray()
        if (dataType != SonyTandemFrame.DATA_MDR) {
            return ParsedTandemResponse.Unknown(dataType.unsigned, command.unsigned, payload, raw)
        }

        return when (command) {
            CONNECT_RET_DEVICE_INFO -> parseDeviceInfo(payload, raw)
            COMMON_RET_STATUS, COMMON_NTFY_STATUS -> parseCommonStatus(payload, raw)
            POWER_RET_STATUS, POWER_NTFY_STATUS -> parseBattery(payload, raw)
            EQEBB_RET_STATUS, EQEBB_NTFY_STATUS,
            EQEBB_RET_PARAM, EQEBB_NTFY_PARAM -> parseEqEbb(command, payload, raw)
            NCASM_RET_STATUS, NCASM_NTFY_STATUS -> parseNoiseControl(command, payload, raw)
            NCASM_RET_PARAM, NCASM_NTFY_PARAM -> parseNoiseControl(command, payload, raw)
            PLAY_RET_STATUS, PLAY_NTFY_STATUS -> ParsedTandemResponse.PlaybackAck(
                values = payload.unsignedList(),
                status = parsePlaybackStatus(payload),
                raw = raw,
            )
            LEA_RET_STATUS, LEA_NTFY_STATUS -> parseLeaStatus(payload, raw)
            else -> ParsedTandemResponse.Unknown(dataType.unsigned, command.unsigned, payload, raw)
        }
    }

    private fun parseDeviceInfo(payload: ByteArray, raw: ByteArray): ParsedTandemResponse {
        val type = payload.firstOrNull()?.let { code ->
            DeviceInfoType.entries.firstOrNull { it.code == code }
        }
        val text = when (type) {
            DeviceInfoType.MODEL_NAME,
            DeviceInfoType.FW_VERSION,
            DeviceInfoType.INSTRUCTION_GUIDE -> parseLengthPrefixedString(payload, offset = 1)
            DeviceInfoType.SERIES_AND_COLOR_INFO -> parseSeriesAndColor(payload)
            null -> null
        }
        return ParsedTandemResponse.DeviceInfo(type, text, raw)
    }

    private fun parseCommonStatus(payload: ByteArray, raw: ByteArray): ParsedTandemResponse {
        val type = payload.firstOrNull()?.let { code ->
            CommonInquiredType.entries.firstOrNull { it.code == code }
        }
        val text = when (type) {
            CommonInquiredType.DISPLAY_FW_VERSION -> parseLengthPrefixedString(payload, offset = 1)
            null -> null
        }
        return ParsedTandemResponse.CommonStatus(
            type = type,
            text = text,
            values = payload.drop(1).map { it.unsigned },
            raw = raw,
        )
    }

    private fun parseLengthPrefixedString(payload: ByteArray, offset: Int): String? {
        val length = payload.getOrNull(offset)?.unsigned ?: return fallbackDeviceInfoString(payload)
        val start = offset + 1
        if (length <= 0 || payload.size < start + length) {
            return fallbackDeviceInfoString(payload)
        }
        return payload.copyOfRange(start, start + length)
            .decodeToString()
            .trimEnd('\u0000')
            .takeIf { it.isNotBlank() }
    }

    private fun fallbackDeviceInfoString(payload: ByteArray): String? =
        payload.drop(1)
            .takeIf { it.isNotEmpty() }
            ?.toByteArray()
            ?.decodeToString()
            ?.trimEnd('\u0000')
            ?.takeIf { it.isNotBlank() }

    private fun parseSeriesAndColor(payload: ByteArray): String? {
        val series = payload.getOrNull(1)?.unsigned ?: return null
        val color = payload.getOrNull(2)?.unsigned ?: return null
        return "${modelSeriesLabel(series)} / ${modelColorLabel(color)}"
    }

    private fun modelSeriesLabel(code: Int): String =
        when (code) {
            0x00 -> "NO_SERIES"
            0x10 -> "EXTRA_BASS"
            0x11 -> "ULT_POWER_SOUND"
            0x20 -> "HEAR"
            0x30 -> "PREMIUM"
            0x40 -> "SPORTS"
            0x50 -> "CASUAL"
            0x60 -> "LINK_BUDS"
            0x70 -> "NECKBAND"
            0x80 -> "LINKPOD"
            0x90 -> "GAMING"
            else -> "UNKNOWN_SERIES_0x%02X".format(code)
        }

    private fun modelColorLabel(code: Int): String =
        when (code) {
            0x00 -> "Default"
            0x01 -> "Black"
            0x02 -> "White"
            0x03 -> "Silver"
            0x04 -> "Red"
            0x05 -> "Blue"
            0x06 -> "Pink"
            0x07 -> "Yellow"
            0x08 -> "Green"
            0x09 -> "Gray"
            0x0A -> "Gold"
            0x0B -> "Cream"
            0x0C -> "Orange"
            0x0D -> "Brown"
            0x0E -> "Violet"
            0x11 -> "Black-I"
            0x12 -> "White-I"
            0x13 -> "Silver-I"
            0x14 -> "Red-I"
            0x15 -> "Blue-I"
            0x16 -> "Pink-I"
            0x17 -> "Yellow-I"
            0x18 -> "Green-I"
            0x19 -> "Gray-I"
            0x1A -> "Gold-I"
            0x1B -> "Cream-I"
            0x1C -> "Orange-I"
            0x1D -> "Brown-I"
            0x1E -> "Violet-I"
            else -> "Unknown color 0x%02X".format(code)
        }

    private fun parseBattery(payload: ByteArray, raw: ByteArray): ParsedTandemResponse {
        val kind = payload.firstOrNull()?.let { code ->
            PowerInquiredType.entries.firstOrNull { it.code == code }
        }
        val values = when (kind) {
            PowerInquiredType.BATTERY,
            PowerInquiredType.CRADLE_BATTERY -> listOfNotNull(payload.getOrNull(1)?.percentageOrNull())
            PowerInquiredType.LEFT_RIGHT_BATTERY -> listOfNotNull(
                payload.getOrNull(1)?.percentageOrNull(),
                payload.getOrNull(3)?.percentageOrNull(),
            )
            else -> payload.drop(1).map { it.unsigned }
        }
        return ParsedTandemResponse.Battery(kind, values, raw)
    }

    private fun parseEqEbb(command: Byte, payload: ByteArray, raw: ByteArray): ParsedTandemResponse {
        val type = payload.firstOrNull()?.let { code ->
            EqEbbInquiredType.entries.firstOrNull { it.code == code }
        }
        val values = payload.drop(1).map { it.unsigned }
        val isParamResponse = command == EQEBB_RET_PARAM || command == EQEBB_NTFY_PARAM
        val enabled = if (command == EQEBB_RET_STATUS || command == EQEBB_NTFY_STATUS) {
            payload.getOrNull(1)?.let { it == ENABLE }
        } else {
            null
        }
        val ebbCombinedPreset = if (isParamResponse && type == EqEbbInquiredType.EBB &&
            payload.size >= 4 &&
            payload.getOrNull(2)?.unsigned?.let { count -> payload.size == count + 3 } == true
        ) {
            payload.getOrNull(1)?.let { code -> EqPresetId.entries.firstOrNull { it.code == code } }
        } else {
            null
        }
        val preset = if (isParamResponse) when (type) {
            EqEbbInquiredType.PRESET_EQ,
            EqEbbInquiredType.PRESET_EQ_NONCUSTOMIZABLE,
            EqEbbInquiredType.PRESET_EQ_AND_ERRORCODE,
            EqEbbInquiredType.PRESET_EQ_AND_ULT_MODE -> payload.getOrNull(1)?.let { code ->
                EqPresetId.entries.firstOrNull { it.code == code }
            }
            EqEbbInquiredType.EBB -> ebbCombinedPreset
            else -> null
        } else {
            null
        }
        val bandCountOffset = when (type) {
            EqEbbInquiredType.CUSTOM_EQ -> 1
            EqEbbInquiredType.EBB -> if (ebbCombinedPreset != null) 2 else 1
            EqEbbInquiredType.PRESET_EQ_AND_ULT_MODE -> 3
            null -> 0
            else -> 2
        }
        val bandSteps = if (isParamResponse) payload.getOrNull(bandCountOffset)?.unsigned?.let { count ->
            payload.drop(bandCountOffset + 1).take(count).map { it.unsigned }
        }.orEmpty() else emptyList()
        return ParsedTandemResponse.EqEbb(
            type = type,
            enabled = enabled,
            preset = preset,
            clearBass = if (type == EqEbbInquiredType.EBB && isParamResponse && ebbCombinedPreset == null) {
                payload.getOrNull(1)?.toInt()
            } else {
                null
            },
            bandSteps = bandSteps,
            values = values,
            raw = raw,
        )
    }

    private fun parseNoiseControl(command: Byte, payload: ByteArray, raw: ByteArray): ParsedTandemResponse {
        val type = payload.firstOrNull()?.let { code ->
            NcAsmInquiredType.entries.firstOrNull { it.code == code }
        }
        val values = payload.drop(1).map { it.unsigned }
        val isParamResponse = command == NCASM_RET_PARAM || command == NCASM_NTFY_PARAM
        if (!isParamResponse) {
            return ParsedTandemResponse.NoiseControl(
                type = type,
                values = values,
                raw = raw,
            )
        }
        val ambientMode = when (type) {
            NcAsmInquiredType.V1_TABLE_SET1_NC_ASM -> payload.getOrNull(5)
            NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS,
            NcAsmInquiredType.NC_ON_OFF_AND_ASM_SEAMLESS,
            NcAsmInquiredType.NC_MODE_SWITCH_AND_ASM_SEAMLESS -> payload.getOrNull(4)
            else -> payload.getOrNull(3)
        }?.let { byte ->
            AmbientSoundMode.entries.firstOrNull { it.code == byte }
        }
        val combinedEnabled = payload.getOrNull(2)?.let { it == NCASM_ON }
        val combinedMode = payload.getOrNull(3)
        val combinedControlMode = when (type) {
            NcAsmInquiredType.V1_TABLE_SET1_NC_ASM -> when {
                payload.getOrNull(1) == SonyTandemV1Table1Protocol.NCASM_EFFECT_OFF -> NoiseControlMode.OFF
                payload.getOrNull(3) == NC_VALUE_ON_SINGLE ||
                    payload.getOrNull(3) == NC_VALUE_ON_DUAL -> NoiseControlMode.NOISE_CANCELLING
                payload.getOrNull(3) == NC_VALUE_OFF &&
                    payload.getOrNull(1) != SonyTandemV1Table1Protocol.NCASM_EFFECT_OFF -> NoiseControlMode.AMBIENT_SOUND
                else -> null
            }
            NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS -> when {
                combinedEnabled == false -> NoiseControlMode.OFF
                combinedMode == NCASM_MODE_ASM -> NoiseControlMode.AMBIENT_SOUND
                combinedMode == NCASM_MODE_NC -> NoiseControlMode.NOISE_CANCELLING
                else -> null
            }
            NcAsmInquiredType.NC_ON_OFF -> when (payload.getOrNull(3) ?: payload.getOrNull(1)) {
                NCASM_ON -> NoiseControlMode.NOISE_CANCELLING
                NCASM_OFF -> NoiseControlMode.OFF
                else -> null
            }
            NcAsmInquiredType.ASM_ON_OFF -> when (payload.getOrNull(4) ?: payload.getOrNull(1)) {
                NCASM_ON -> NoiseControlMode.AMBIENT_SOUND
                NCASM_OFF -> NoiseControlMode.OFF
                else -> null
            }
            NcAsmInquiredType.ASM_SEAMLESS -> when (payload.getOrNull(2)) {
                NCASM_ON -> NoiseControlMode.AMBIENT_SOUND
                NCASM_OFF -> NoiseControlMode.OFF
                else -> null
            }
            NcAsmInquiredType.NC_ON_OFF_AND_ASM_SEAMLESS -> when {
                payload.getOrNull(2) == NCASM_OFF -> NoiseControlMode.OFF
                payload.getOrNull(3) == NCASM_OFF -> NoiseControlMode.AMBIENT_SOUND
                payload.getOrNull(2) == NCASM_ON -> NoiseControlMode.NOISE_CANCELLING
                else -> null
            }
            NcAsmInquiredType.NC_MODE_SWITCH_AND_ASM_SEAMLESS -> when {
                payload.getOrNull(2) == NCASM_OFF -> NoiseControlMode.OFF
                payload.getOrNull(3) == NC_VALUE_ON_SINGLE ||
                    payload.getOrNull(3) == NC_VALUE_ON_DUAL -> NoiseControlMode.NOISE_CANCELLING
                payload.getOrNull(3) == NC_VALUE_OFF &&
                    payload.getOrNull(2) == NCASM_ON -> NoiseControlMode.AMBIENT_SOUND
                else -> null
            }
            else -> null
        }
        return ParsedTandemResponse.NoiseControl(
            type = type,
            values = values,
            enabled = when (type) {
                NcAsmInquiredType.V1_TABLE_SET1_NC_ASM -> combinedControlMode == NoiseControlMode.NOISE_CANCELLING
                NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS ->
                    combinedControlMode == NoiseControlMode.NOISE_CANCELLING
                NcAsmInquiredType.NC_ON_OFF -> payload.getOrNull(3)?.let { it == NCASM_ON }
                    ?: payload.getOrNull(1)?.let { it == ENABLE }
                NcAsmInquiredType.NC_ON_OFF_AND_ASM_SEAMLESS,
                NcAsmInquiredType.NC_MODE_SWITCH_AND_ASM_SEAMLESS -> combinedControlMode == NoiseControlMode.NOISE_CANCELLING
                else -> null
            },
            ambientSoundEnabled = when (type) {
                NcAsmInquiredType.V1_TABLE_SET1_NC_ASM -> combinedControlMode == NoiseControlMode.AMBIENT_SOUND
                NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS ->
                    combinedControlMode == NoiseControlMode.AMBIENT_SOUND
                NcAsmInquiredType.ASM_ON_OFF -> payload.getOrNull(4)?.let { it == NCASM_ON }
                    ?: payload.getOrNull(1)?.let { it == ENABLE }
                NcAsmInquiredType.ASM_SEAMLESS -> payload.getOrNull(2)?.let { it == NCASM_ON }
                NcAsmInquiredType.NC_ON_OFF_AND_ASM_SEAMLESS,
                NcAsmInquiredType.NC_MODE_SWITCH_AND_ASM_SEAMLESS -> combinedControlMode == NoiseControlMode.AMBIENT_SOUND
                else -> null
            },
            ambientLevel = when (type) {
                NcAsmInquiredType.V1_TABLE_SET1_NC_ASM -> payload.getOrNull(6)?.unsigned
                NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS -> payload.getOrNull(5)?.unsigned
                NcAsmInquiredType.NC_ON_OFF_AND_ASM_SEAMLESS -> payload.getOrNull(5)?.unsigned
                NcAsmInquiredType.NC_MODE_SWITCH_AND_ASM_SEAMLESS -> payload.getOrNull(5)?.unsigned?.plus(1)
                NcAsmInquiredType.ASM_SEAMLESS -> payload.getOrNull(4)?.unsigned
                else -> null
            },
            ambientMode = ambientMode,
            controlMode = combinedControlMode,
            raw = raw,
        )
    }

    private fun parsePlaybackStatus(payload: ByteArray): PlaybackStatus =
        when (payload.getOrNull(2)?.unsigned) {
            1 -> PlaybackStatus.PLAYING
            2 -> PlaybackStatus.PAUSED
            3 -> PlaybackStatus.STOPPED
            else -> PlaybackStatus.UNKNOWN
        }

    private fun parseLeaStatus(payload: ByteArray, raw: ByteArray): ParsedTandemResponse {
        val typeCode = payload.firstOrNull()
        val type = LeaInquiredType.entries.firstOrNull { it.code == typeCode }
        val values = payload.unsignedList()
        val connectionType = payload.getOrNull(1)?.let { ct ->
            LeaConnectionType.entries.firstOrNull { it.code == ct }
        }
        val streamingStatus = payload.getOrNull(2)?.let { ss ->
            LeaStreamingStatus.entries.firstOrNull { it.code == ss }
        }
        val pairedHistory = payload.getOrNull(1)?.let { ph ->
            LeaPairedHistory.entries.firstOrNull { it.code == ph }
        }
        return ParsedTandemResponse.LeaStatus(
            type = type,
            values = values,
            connectionType = connectionType,
            streamingStatus = streamingStatus,
            pairedHistory = pairedHistory,
            raw = raw,
        )
    }
}

val Byte.unsigned: Int
    get() = toInt() and 0xFF

fun ByteArray.hexString(): String = joinToString(" ") { "%02X".format(it.unsigned) }

private fun ByteArray.unsignedList(): List<Int> = map { it.unsigned }

private fun Byte.percentageOrNull(): Int? = unsigned.takeIf { it in 0..100 }
