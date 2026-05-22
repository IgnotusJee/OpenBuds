package dev.ignotus.sonyrebuild.protocol

object SonyTandemConstants {
    const val DATA_MDR: Byte = 0x0E

    // ── Connect (0x00-0x07) ──
    const val CONNECT_GET_PROTOCOL_INFO: Byte = 0x00
    const val CONNECT_RET_PROTOCOL_INFO: Byte = 0x01
    const val CONNECT_GET_DEVICE_INFO: Byte = 0x04
    const val CONNECT_RET_DEVICE_INFO: Byte = 0x05

    // ── Common (0x10-0x1F) ──
    const val COMMON_GET_BATTERY_LEVEL: Byte = 0x10
    const val COMMON_RET_BATTERY_LEVEL: Byte = 0x11
    const val COMMON_GET_STATUS: Byte = 0x12
    const val COMMON_RET_STATUS: Byte = 0x13
    const val COMMON_NTFY_BATTERY_LEVEL: Byte = 0x13
    const val COMMON_NTFY_STATUS: Byte = 0x15

    // ── Power (0x20-0x29) ──
    const val POWER_GET_STATUS: Byte = 0x22
    const val POWER_RET_STATUS: Byte = 0x23
    const val POWER_NTFY_STATUS: Byte = 0x25

    // ── System (0x30-0x3F) ──
    const val SYSTEM_GET_PARAM: Byte = 0x36
    const val SYSTEM_RET_PARAM: Byte = 0x37

    // ── LE Audio (0x40-0x4F) ──
    const val LEA_GET_STATUS: Byte = 0x42
    const val LEA_RET_STATUS: Byte = 0x43
    const val LEA_NTFY_STATUS: Byte = 0x45
    const val LEA_GET_PARAM: Byte = 0x46
    const val LEA_RET_PARAM: Byte = 0x47
    const val LEA_NTFY_PARAM: Byte = 0x49

    // ── EQ/EBB (0x50-0x5B) ──
    const val EQEBB_GET_STATUS: Byte = 0x52
    const val EQEBB_RET_STATUS: Byte = 0x53
    const val EQEBB_NTFY_STATUS: Byte = 0x55
    const val EQEBB_GET_PARAM: Byte = 0x56
    const val EQEBB_RET_PARAM: Byte = 0x57
    const val EQEBB_SET_PARAM: Byte = 0x58
    const val EQEBB_NTFY_PARAM: Byte = 0x59

    // ── NC/ASM (0x60-0x69) ──
    const val NCASM_GET_STATUS: Byte = 0x62
    const val NCASM_RET_STATUS: Byte = 0x63
    const val NCASM_SET_STATUS: Byte = 0x64
    const val NCASM_NTFY_STATUS: Byte = 0x65
    const val NCASM_GET_PARAM: Byte = 0x66
    const val NCASM_RET_PARAM: Byte = 0x67
    const val NCASM_SET_PARAM: Byte = 0x68
    const val NCASM_NTFY_PARAM: Byte = 0x69

    // ── Playback (0xA0-0xA9) ──
    const val PLAY_GET_STATUS: Byte = 0xA2.toByte()
    const val PLAY_RET_STATUS: Byte = 0xA3.toByte()
    const val PLAY_SET_STATUS: Byte = 0xA4.toByte()
    const val PLAY_NTFY_STATUS: Byte = 0xA5.toByte()

    // ── Shared payload constants ──
    const val VALUE_ENABLE: Byte = 0x00
    const val VALUE_DISABLE: Byte = 0x01
    const val VALUE_CHANGED: Byte = 0x01

    const val NCASM_EFFECT_OFF: Byte = 0x00
    const val NCASM_EFFECT_ADJUSTMENT_COMPLETION: Byte = 0x11
    const val NCASM_SETTING_DUAL_SINGLE_OFF: Byte = 0x02
    const val NCASM_ASM_SETTING_LEVEL_ADJUSTMENT: Byte = 0x01
    const val NCASM_ON: Byte = 0x01
    const val NCASM_OFF: Byte = 0x00
    const val NCASM_MODE_NC: Byte = 0x00
    const val NCASM_MODE_ASM: Byte = 0x01
    const val NC_VALUE_OFF: Byte = 0x00
    const val NC_VALUE_ON_SINGLE: Byte = 0x01
    const val NC_VALUE_ON_DUAL: Byte = 0x02

    const val PLAYBACK_CONTROL_WITH_FUNCTION_CHANGE: Byte = 0x03
}
