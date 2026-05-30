package dev.ignotus.openbuds.headphones.sony

import dev.ignotus.openbuds.headphones.ClearBassWriteMode
import dev.ignotus.openbuds.headphones.EqDeviceConfig
import dev.ignotus.openbuds.headphones.EqUiCapability
import dev.ignotus.openbuds.headphones.HeadphoneCommand
import dev.ignotus.openbuds.headphones.HeadphoneFeature
import dev.ignotus.openbuds.headphones.HeadphoneProtocolVariant
import dev.ignotus.openbuds.headphones.eqUiCapability
import dev.ignotus.openbuds.protocol.sony.EqEbbInquiredType
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.ParsedHeadphoneResponse

class EqProtocolEngine(
    private val config: EqDeviceConfig,
    private val codec: TandemCodec,
) {
    constructor(config: EqDeviceConfig, variant: HeadphoneProtocolVariant) : this(
        config,
        TandemCodecRegistry.codecFor(variant),
    )

    // ── Refresh ──

    fun buildRefreshCommands(
        buildCommand: (String, ByteArray) -> HeadphoneCommand,
    ): List<HeadphoneCommand> = buildList {
        config.statusQueryTypes.forEach { type ->
            codec.buildGetEqEbbStatus(type)?.let { bytes ->
                add(buildCommand("GET EQ status $type", bytes))
            }
        }
        config.paramQueryTypes.forEach { type ->
            codec.buildGetEqEbbParam(type)?.let { bytes ->
                add(buildCommand("GET EQ param $type", bytes))
            }
        }
        config.extendedInfoQueryTypes.forEach { type ->
            codec.buildGetEqEbbExtendedInfo(type)?.let { bytes ->
                add(buildCommand("GET EQ extended $type", bytes))
            }
        }
    }

    // ── Writes ──

    fun buildSetPreset(preset: EqPresetId): ByteArray =
        requireNotNull(codec.buildSetEqPreset(preset, config.writeInquiredType)) {
            "Codec ${codec.variant} does not support EQ preset writes"
        }

    fun buildSetBands(bands: List<Int>, preset: EqPresetId): ByteArray =
        requireNotNull(codec.buildSetEqBands(preset, config.writeInquiredType, bands)) {
            "Codec ${codec.variant} does not support EQ band writes"
        }

    fun buildSetClearBass(level: Int): ByteArray =
        requireNotNull(codec.buildSetClearBass(level)) {
            "Codec ${codec.variant} does not support Clear Bass writes"
        }

    // ── Parse ──

    /** Parse delegates to the selected codec so EQ routing stays protocol-variant local. */
    fun parseResponse(raw: ByteArray): ParsedHeadphoneResponse.SonyTandem.EqEbb? {
        val result = codec.parse(raw)
        return result as? ParsedHeadphoneResponse.SonyTandem.EqEbb
    }

    companion object {
        const val BAND_STEP_CENTER: Int = 10

        private val DEFAULT_BAND_LABELS = listOf("400 Hz", "1 kHz", "2.5 kHz", "6.3 kHz", "16 kHz")

        fun uiCapability(config: EqDeviceConfig): EqUiCapability = EqUiCapability(
            availablePresets = config.availablePresets,
            visibleBandCount = config.bandCount - 1,
            bandLabels = DEFAULT_BAND_LABELS,
            bandDisplayRange = -10..10,
            hasClearBass = config.hasClearBass,
            clearBassDisplayRange = -10..10,
            bandStepCenter = BAND_STEP_CENTER,
        )
    }
}
