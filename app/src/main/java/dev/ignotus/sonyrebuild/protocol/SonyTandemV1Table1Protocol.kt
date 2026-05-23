package dev.ignotus.sonyrebuild.protocol

import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.DATA_MDR

object SonyTandemV1Table1Protocol {
    private const val COMMON_GET_BATTERY_LEVEL: Byte = 0x10
    private const val COMMON_RET_BATTERY_LEVEL: Byte = 0x11
    private const val COMMON_NTFY_BATTERY_LEVEL: Byte = 0x13
    private const val NCASM_GET_PARAM: Byte = 0x66
    private const val NCASM_RET_PARAM: Byte = 0x67
    private const val NCASM_SET_PARAM: Byte = 0x68
    private const val NCASM_NTFY_PARAM: Byte = 0x69
    private const val NCASM_EFFECT_OFF: Byte = 0x00
    private const val NCASM_EFFECT_ADJUSTMENT_COMPLETION: Byte = 0x11
    private const val NCASM_SETTING_DUAL_SINGLE_OFF: Byte = 0x02
    private const val NCASM_ASM_SETTING_LEVEL_ADJUSTMENT: Byte = 0x01
    private const val NC_VALUE_OFF: Byte = 0x00
    private const val NC_VALUE_ON_SINGLE: Byte = 0x01
    private const val NC_VALUE_ON_DUAL: Byte = 0x02

    fun buildGetBatteryStatus(type: PowerInquiredType = PowerInquiredType.BATTERY): ByteArray =
        SonyTandemFrame.message(COMMON_GET_BATTERY_LEVEL, byteArrayOf(type.code))

    fun buildGetNcAsmParam(): ByteArray =
        SonyTandemFrame.message(
            NCASM_GET_PARAM,
            byteArrayOf(NcAsmInquiredType.V1_TABLE_SET1_NC_ASM.code),
        )

    fun buildSetNoiseControlMode(
        controlMode: NoiseControlMode,
        ambientLevel: Int = 10,
        ambientMode: AmbientSoundMode = AmbientSoundMode.NORMAL,
    ): ByteArray {
        val effect = if (controlMode == NoiseControlMode.OFF) {
            NCASM_EFFECT_OFF
        } else {
            NCASM_EFFECT_ADJUSTMENT_COMPLETION
        }
        val ncValue = when (controlMode) {
            NoiseControlMode.NOISE_CANCELLING -> NC_VALUE_ON_DUAL
            NoiseControlMode.AMBIENT_SOUND,
            NoiseControlMode.OFF -> NC_VALUE_OFF
        }
        val asmLevel = if (controlMode == NoiseControlMode.AMBIENT_SOUND) {
            ambientLevel.coerceIn(1, 20).toByte()
        } else {
            0x00
        }

        return SonyTandemFrame.message(
            NCASM_SET_PARAM,
            byteArrayOf(
                NcAsmInquiredType.V1_TABLE_SET1_NC_ASM.code,
                effect,
                NCASM_SETTING_DUAL_SINGLE_OFF,
                ncValue,
                NCASM_ASM_SETTING_LEVEL_ADJUSTMENT,
                ambientMode.code,
                asmLevel,
            ),
        )
    }

    fun parse(raw: ByteArray): ParsedTandemResponse {
        val normalized = if (raw.firstOrNull() == DATA_MDR) raw else byteArrayOf(DATA_MDR) + raw
        val command = normalized.getOrNull(1)
        val payload = if (normalized.size > 2) normalized.copyOfRange(2, normalized.size) else byteArrayOf()
        return when (command) {
            COMMON_RET_BATTERY_LEVEL,
            COMMON_NTFY_BATTERY_LEVEL -> parseBattery(payload, raw)
            NCASM_RET_PARAM,
            NCASM_NTFY_PARAM -> parseNoiseControl(command, payload, raw)
            else -> ParsedTandemResponse.Unknown(
                dataType = normalized.firstOrNull()?.unsigned,
                command = command?.unsigned,
                payload = payload,
                raw = raw,
            )
        }
    }

    private fun parseBattery(payload: ByteArray, raw: ByteArray): ParsedTandemResponse.Battery {
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

    private fun parseNoiseControl(command: Byte, payload: ByteArray, raw: ByteArray): ParsedTandemResponse {
        val type = payload.firstOrNull()?.let { code ->
            NcAsmInquiredType.entries.firstOrNull { it.code == code }
        }
        if (type != NcAsmInquiredType.V1_TABLE_SET1_NC_ASM) {
            return ParsedTandemResponse.Unknown(
                dataType = DATA_MDR.unsigned,
                command = command.unsigned,
                payload = payload,
                raw = raw,
            )
        }
        val controlMode = when {
            payload.getOrNull(1) == NCASM_EFFECT_OFF -> NoiseControlMode.OFF
            payload.getOrNull(3) == NC_VALUE_ON_SINGLE ||
                payload.getOrNull(3) == NC_VALUE_ON_DUAL -> NoiseControlMode.NOISE_CANCELLING
            payload.getOrNull(3) == NC_VALUE_OFF &&
                payload.getOrNull(1) != NCASM_EFFECT_OFF -> NoiseControlMode.AMBIENT_SOUND
            else -> null
        }
        val ambientMode = payload.getOrNull(5)?.let { byte ->
            AmbientSoundMode.entries.firstOrNull { it.code == byte }
        }
        return ParsedTandemResponse.NoiseControl(
            type = type,
            values = payload.drop(1).map { it.unsigned },
            enabled = controlMode == NoiseControlMode.NOISE_CANCELLING,
            ambientSoundEnabled = controlMode == NoiseControlMode.AMBIENT_SOUND,
            ambientLevel = payload.getOrNull(6)?.unsigned,
            ambientMode = ambientMode,
            controlMode = controlMode,
            raw = raw,
        )
    }
}
