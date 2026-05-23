package dev.ignotus.sonyrebuild.headphones

import dev.ignotus.sonyrebuild.protocol.EqEbbInquiredType
import dev.ignotus.sonyrebuild.protocol.EqPresetId
import dev.ignotus.sonyrebuild.protocol.ParsedTandemResponse
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.DATA_MDR

data class EqDeviceConfig(
    val availablePresets: List<EqPresetId>,
    val writeInquiredType: EqEbbInquiredType,
    val includeBandsOnPresetWrite: Boolean,
    val statusQueryTypes: List<EqEbbInquiredType>,
    val paramQueryTypes: List<EqEbbInquiredType>,
    val bandCount: Int,
    val hasClearBass: Boolean,
)

data class EqUiCapability(
    val availablePresets: List<EqPresetId>,
    val visibleBandCount: Int,
    val bandLabels: List<String>,
    val bandDisplayRange: IntRange,
    val hasClearBass: Boolean,
    val clearBassDisplayRange: IntRange,
    val bandStepCenter: Int,
)

class EqProtocolEngine(private val config: EqDeviceConfig) {

    // ── Refresh ──

    fun buildRefreshCommands(
        buildCommand: (String, ByteArray) -> HeadphoneCommand,
    ): List<HeadphoneCommand> = buildList {
        config.statusQueryTypes.forEach { type ->
            add(buildCommand("GET EQ status $type", SonyTandemV2Table1Codec.buildGetEqEbbStatus(type)))
        }
        config.paramQueryTypes.forEach { type ->
            add(buildCommand("GET EQ param $type", SonyTandemV2Table1Codec.buildGetEqEbbParam(type)))
        }
    }

    // ── Writes ──

    fun buildSetPreset(preset: EqPresetId, currentBands: List<Int>): ByteArray {
        val bands = if (config.includeBandsOnPresetWrite) {
            currentBands.ifEmpty { List(config.bandCount) { BAND_STEP_CENTER } }
        } else {
            emptyList()
        }
        return SonyTandemV2Table1Codec.buildSetEqPreset(preset, config.writeInquiredType, bands)
    }

    fun buildSetBands(bands: List<Int>, preset: EqPresetId): ByteArray =
        SonyTandemV2Table1Codec.buildSetEqPreset(preset, config.writeInquiredType, bands)

    fun buildSetClearBass(level: Int): ByteArray =
        SonyTandemV2Table1Codec.buildSetClearBass(level)

    // ── Parse ──

    fun parseResponse(raw: ByteArray): ParsedTandemResponse.EqEbb? {
        val result = SonyTandemV2Table1Codec.parse(raw)
        return result as? ParsedTandemResponse.EqEbb
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
