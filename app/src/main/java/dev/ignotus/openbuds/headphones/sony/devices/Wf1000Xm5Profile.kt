package dev.ignotus.openbuds.headphones.sony.devices

import dev.ignotus.openbuds.headphones.ClearBassWriteMode
import dev.ignotus.openbuds.headphones.EqDeviceConfig
import dev.ignotus.openbuds.headphones.HeadphoneCapabilities
import dev.ignotus.openbuds.headphones.HeadphoneFeature
import dev.ignotus.openbuds.headphones.HeadphoneFormFactor
import dev.ignotus.openbuds.headphones.HeadphoneProtocolVariant
import dev.ignotus.openbuds.headphones.ProfileTemplate
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.sony.EqEbbInquiredType
import dev.ignotus.openbuds.protocol.sony.NcAsmInquiredType
import dev.ignotus.openbuds.protocol.sony.PowerInquiredType

object Wf1000Xm5Profile {
    private val features = setOf(
        HeadphoneFeature.DEVICE_INFO,
        HeadphoneFeature.BATTERY,
        HeadphoneFeature.NOISE_CONTROL,
        HeadphoneFeature.AMBIENT_LEVEL,
        HeadphoneFeature.AMBIENT_VOICE_MODE,
        HeadphoneFeature.PLAYBACK_CONTROL,
        HeadphoneFeature.VOLUME,
        HeadphoneFeature.EQ,
        HeadphoneFeature.CLEAR_BASS,
        HeadphoneFeature.LEA_STATUS,
        HeadphoneFeature.QUICK_ACCESS,
        HeadphoneFeature.WEARING_STATUS,
    )

    val template = ProfileTemplate(
        modelName = "WF-1000XM5",
        series = "PREMIUM",
        capabilities = HeadphoneCapabilities(
            features = features,
            formFactor = HeadphoneFormFactor.TRUE_WIRELESS,
            batteryQueries = listOf(
                PowerInquiredType.LEFT_RIGHT_BATTERY,
                PowerInquiredType.CRADLE_BATTERY,
            ),
            noiseControlQueryTypes = listOf(
                NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS,
            ),
            writableNoiseControlTypes = setOf(
                NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS,
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
                bandCount = 6,
                hasClearBass = true,
                clearBassWriteMode = ClearBassWriteMode.PRESET_EQ_BANDS,
            ),
        ),
        featureProtocolMap = features.associateWith { HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1 },
    )
}
