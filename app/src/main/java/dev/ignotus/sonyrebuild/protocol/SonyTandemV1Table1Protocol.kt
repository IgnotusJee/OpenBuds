package dev.ignotus.sonyrebuild.protocol

import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.COMMON_GET_BATTERY_LEVEL
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.COMMON_NTFY_BATTERY_LEVEL
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.COMMON_RET_BATTERY_LEVEL
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.DATA_MDR
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.EQEBB_GET_PARAM
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.EQEBB_GET_STATUS
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_EFFECT_ADJUSTMENT_COMPLETION
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_EFFECT_OFF
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_GET_PARAM
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_SETTING_DUAL_SINGLE_OFF
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_SET_PARAM
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NC_VALUE_OFF
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NC_VALUE_ON_DUAL
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_ASM_SETTING_LEVEL_ADJUSTMENT

object SonyTandemV1Table1Protocol {

    fun buildGetBatteryStatus(type: PowerInquiredType = PowerInquiredType.BATTERY): ByteArray =
        SonyTandemFrame.message(COMMON_GET_BATTERY_LEVEL, byteArrayOf(type.code))

    fun buildGetEqEbbStatus(type: EqEbbInquiredType): ByteArray =
        SonyTandemFrame.message(EQEBB_GET_STATUS, byteArrayOf(type.code))

    fun buildGetEqEbbParam(type: EqEbbInquiredType): ByteArray =
        SonyTandemFrame.message(EQEBB_GET_PARAM, byteArrayOf(type.code))

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
}
