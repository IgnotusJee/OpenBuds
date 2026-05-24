package dev.ignotus.openbuds.headphones.sonydevices

import dev.ignotus.openbuds.headphones.ClearBassWriteMode
import dev.ignotus.openbuds.headphones.EqDeviceConfig
import dev.ignotus.openbuds.headphones.HeadphoneCapabilities
import dev.ignotus.openbuds.headphones.HeadphoneFeature
import dev.ignotus.openbuds.headphones.HeadphoneFormFactor
import dev.ignotus.openbuds.headphones.HeadphoneProtocolVariant
import dev.ignotus.openbuds.headphones.ProfileTemplate
import dev.ignotus.openbuds.protocol.EqEbbInquiredType
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.NcAsmInquiredType
import dev.ignotus.openbuds.protocol.PowerInquiredType

object Wh1000Xm4Profile {
    private val features = setOf(
        HeadphoneFeature.DEVICE_INFO,
        HeadphoneFeature.BATTERY,
        HeadphoneFeature.NOISE_CONTROL,
        HeadphoneFeature.AMBIENT_LEVEL,
        HeadphoneFeature.AMBIENT_VOICE_MODE,
        HeadphoneFeature.PLAYBACK_CONTROL,
        HeadphoneFeature.EQ,
        HeadphoneFeature.CLEAR_BASS,
    )

    val template = ProfileTemplate(
        modelName = "WH-1000XM4",
        series = "PREMIUM",
        capabilities = HeadphoneCapabilities(
            features = features,
            formFactor = HeadphoneFormFactor.HEADSET,
            batteryQueries = listOf(PowerInquiredType.BATTERY),
            noiseControlQueryTypes = listOf(
                NcAsmInquiredType.V1_TABLE_SET1_NC_ASM,
            ),
            writableNoiseControlTypes = setOf(
                NcAsmInquiredType.V1_TABLE_SET1_NC_ASM,
            ),
            eqConfig = EqDeviceConfig(
                availablePresets = listOf(
                    EqPresetId.OFF,
                    EqPresetId.BRIGHT,
                    EqPresetId.EXCITED,
                    EqPresetId.MELLOW,
                    EqPresetId.RELAXED,
                    EqPresetId.VOCAL,
                    EqPresetId.TREBLE,
                    EqPresetId.BASS,
                    EqPresetId.SPEECH,
                    EqPresetId.CUSTOM,
                    EqPresetId.USER_SETTING1,
                    EqPresetId.USER_SETTING2,
                ),
                writeInquiredType = EqEbbInquiredType.PRESET_EQ,
                statusQueryTypes = listOf(EqEbbInquiredType.PRESET_EQ),
                paramQueryTypes = listOf(EqEbbInquiredType.PRESET_EQ),
                extendedInfoQueryTypes = listOf(EqEbbInquiredType.PRESET_EQ),
                bandCount = 6,
                hasClearBass = true,
                clearBassWriteMode = ClearBassWriteMode.PRESET_EQ_BANDS,
            ),
            queryProtocolInfo = false,
            queryNoiseControlParams = true,
        ),
        featureProtocolMap = features.associateWith { HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1 },
    )
}
