package dev.ignotus.openbuds.headphones.qcydevices

import dev.ignotus.openbuds.headphones.EqDeviceConfig
import dev.ignotus.openbuds.headphones.HeadphoneCapabilities
import dev.ignotus.openbuds.headphones.HeadphoneFeature
import dev.ignotus.openbuds.headphones.HeadphoneFormFactor
import dev.ignotus.openbuds.headphones.HeadphoneProtocolVariant
import dev.ignotus.openbuds.headphones.InfoLayoutHint
import dev.ignotus.openbuds.headphones.ProfileTemplate
import dev.ignotus.openbuds.protocol.EqEbbInquiredType
import dev.ignotus.openbuds.protocol.EqPresetId

/**
 * Device profile for QCY C30S true wireless earbuds.
 *
 * Reference:
 *   QCY_C30S_PROTOCOL.md
 *   com.qcymall.qcylibrary.QCYHeadsetClient.java
 */
object QcyC30SProfile {
    private val features = setOf(
        HeadphoneFeature.DEVICE_INFO,
        HeadphoneFeature.BATTERY,
        HeadphoneFeature.NOISE_CONTROL,
        HeadphoneFeature.AMBIENT_LEVEL,
        HeadphoneFeature.EQ,
        HeadphoneFeature.PLAYBACK_CONTROL,
        // VOLUME: deferred to P3 — CMD 8 write path + UI state not wired yet.
    )

    val template = ProfileTemplate(
        modelName = "C30S",
        series = null,
        infoLayoutHint = InfoLayoutHint.BRAND_MODEL,
        capabilities = HeadphoneCapabilities(
            features = features,
            formFactor = HeadphoneFormFactor.TRUE_WIRELESS,
            batteryQueries = emptyList(),       // QCY reads battery characteristic directly
            noiseControlQueryTypes = emptyList(), // QCY uses CMD-based queries
            writableNoiseControlTypes = emptySet(),
            eqConfig = EqDeviceConfig(
                availablePresets = listOf(
                    EqPresetId.OFF,
                    EqPresetId.BASS,
                    EqPresetId.BRIGHT,
                    EqPresetId.POP,
                    EqPresetId.JAZZ,
                    EqPresetId.VOCAL,
                    EqPresetId.CUSTOM,
                ),
                writeInquiredType = EqEbbInquiredType.PRESET_EQ, // placeholder, not used for QCY
                statusQueryTypes = emptyList(),
                paramQueryTypes = emptyList(),
                bandCount = 10,
                hasClearBass = false,
            ),
            queryProtocolInfo = false,
            queryNoiseControlParams = false,
        ),
        featureProtocolMap = features.associateWith { HeadphoneProtocolVariant.QCY },
    )
}
