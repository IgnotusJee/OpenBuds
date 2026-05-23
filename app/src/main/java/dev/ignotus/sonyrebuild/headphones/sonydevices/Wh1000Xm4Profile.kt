package dev.ignotus.sonyrebuild.headphones.sonydevices

import dev.ignotus.sonyrebuild.headphones.EqDeviceConfig
import dev.ignotus.sonyrebuild.headphones.HeadphoneCapabilities
import dev.ignotus.sonyrebuild.headphones.HeadphoneFeature
import dev.ignotus.sonyrebuild.headphones.HeadphoneFormFactor
import dev.ignotus.sonyrebuild.headphones.HeadphoneProtocolVariant
import dev.ignotus.sonyrebuild.headphones.ProfileTemplate
import dev.ignotus.sonyrebuild.protocol.EqEbbInquiredType
import dev.ignotus.sonyrebuild.protocol.EqPresetId
import dev.ignotus.sonyrebuild.protocol.NcAsmInquiredType
import dev.ignotus.sonyrebuild.protocol.PowerInquiredType

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
                bandCount = 6,
                hasClearBass = true,
            ),
            queryProtocolInfo = false,
            queryNoiseControlParams = true,
        ),
        featureProtocolMap = features.associateWith { HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1 },
    )
}
