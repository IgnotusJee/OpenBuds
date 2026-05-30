package dev.ignotus.openbuds.headphones

import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.sony.EqEbbInquiredType

data class EqDeviceConfig(
    val availablePresets: List<EqPresetId>,
    val writeInquiredType: EqEbbInquiredType,
    val statusQueryTypes: List<EqEbbInquiredType>,
    val paramQueryTypes: List<EqEbbInquiredType>,
    val extendedInfoQueryTypes: List<EqEbbInquiredType> = emptyList(),
    val bandCount: Int,
    val hasClearBass: Boolean,
    val clearBassWriteMode: ClearBassWriteMode = ClearBassWriteMode.EBB_PARAM,
)

enum class ClearBassWriteMode {
    EBB_PARAM,
    PRESET_EQ_BANDS,
}

data class EqUiCapability(
    val availablePresets: List<EqPresetId>,
    val visibleBandCount: Int,
    val bandLabels: List<String>,
    val bandDisplayRange: IntRange,
    val hasClearBass: Boolean,
    val clearBassDisplayRange: IntRange,
    val bandStepCenter: Int,
)
