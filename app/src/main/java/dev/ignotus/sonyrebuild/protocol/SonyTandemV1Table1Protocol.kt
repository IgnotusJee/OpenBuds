package dev.ignotus.sonyrebuild.protocol

object SonyTandemV1Table1Protocol {
    const val COMMON_GET_BATTERY_LEVEL: Byte = 0x10
    const val COMMON_RET_BATTERY_LEVEL: Byte = 0x11
    const val COMMON_NTFY_BATTERY_LEVEL: Byte = 0x13

    const val NCASM_EFFECT_OFF: Byte = 0x00

    private const val NCASM_EFFECT_ADJUSTMENT_COMPLETION: Byte = 0x11
    private const val NCASM_SETTING_DUAL_SINGLE_OFF: Byte = 0x02
    private const val ASM_SETTING_LEVEL_ADJUSTMENT: Byte = 0x01
    private const val NC_VALUE_OFF: Byte = 0x00
    private const val NC_VALUE_ON_DUAL: Byte = 0x02

    fun buildGetBatteryStatus(type: PowerInquiredType = PowerInquiredType.BATTERY): ByteArray =
        SonyTandemFrame.message(COMMON_GET_BATTERY_LEVEL, byteArrayOf(type.code))

    fun buildGetEqEbbStatus(type: EqEbbInquiredType): ByteArray =
        SonyTandemV2Table1Protocol.buildGetEqEbbStatus(type)

    fun buildGetEqEbbParam(type: EqEbbInquiredType): ByteArray =
        SonyTandemV2Table1Protocol.buildGetEqEbbParam(type)

    fun buildGetNcAsmParam(): ByteArray =
        SonyTandemFrame.message(
            SonyTandemV2Table1Protocol.NCASM_GET_PARAM,
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

        // Reverse/source parity:
        // - export/03-tandem-protocol-v1/Command.java: NCASM_GET_PARAM=0x66, NCASM_SET_PARAM=0x68.
        // - Reverse db0.C12802i3: V1 NCASM_SET_PARAM payload.
        // - capture/btsnoop_hci_260518_212713.log: WH-1000XM4 uses 0E 68 02 ...
        return SonyTandemFrame.message(
            SonyTandemV2Table1Protocol.NCASM_SET_PARAM,
            byteArrayOf(
                NcAsmInquiredType.V1_TABLE_SET1_NC_ASM.code,
                effect,
                NCASM_SETTING_DUAL_SINGLE_OFF,
                ncValue,
                ASM_SETTING_LEVEL_ADJUSTMENT,
                ambientMode.code,
                asmLevel,
            ),
        )
    }

    fun parse(raw: ByteArray): ParsedTandemResponse {
        val normalized = if (raw.firstOrNull() == SonyTandemFrame.DATA_MDR) raw else byteArrayOf(SonyTandemFrame.DATA_MDR) + raw
        val command = normalized.getOrNull(1)
        val payload = if (normalized.size > 2) normalized.copyOfRange(2, normalized.size) else byteArrayOf()
        return when (command) {
            COMMON_RET_BATTERY_LEVEL,
            COMMON_NTFY_BATTERY_LEVEL -> parseBattery(payload, raw)
            else -> SonyTandemV2Table1Protocol.parse(raw)
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

    private fun Byte.percentageOrNull(): Int? = unsigned.takeIf { it in 0..100 }
}
