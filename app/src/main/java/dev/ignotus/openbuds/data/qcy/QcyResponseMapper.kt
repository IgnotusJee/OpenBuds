package dev.ignotus.openbuds.data.qcy

import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dev.ignotus.openbuds.protocol.ParsedHeadphoneResponse
import dev.ignotus.openbuds.protocol.QcyProtocol

/**
 * Maps QCY parsed responses into [SonyHeadphoneUiState] mutations.
 *
 * Kept as a standalone object so the repository can delegate QCY-specific
 * state transforms without polluting `SonyHeadphoneRepository`.
 */
object QcyResponseMapper {
    private const val EQ_BAND_STEP_CENTER = 10

    fun apply(
        state: SonyHeadphoneUiState,
        response: ParsedHeadphoneResponse.Qcy,
    ): SonyHeadphoneUiState = when (response) {
        is ParsedHeadphoneResponse.Qcy.Battery -> applyBattery(state, response)
        is ParsedHeadphoneResponse.Qcy.NoiseControl -> applyNoise(state, response)
        is ParsedHeadphoneResponse.Qcy.EqData -> applyEq(state, response)
        is ParsedHeadphoneResponse.Qcy.DeviceInfo -> applyDeviceInfo(state, response)
        is ParsedHeadphoneResponse.Qcy.Volume -> applyVolume(state, response)
        is ParsedHeadphoneResponse.Qcy.FunctionStatus -> applyFunctionStatus(state, response)
    }

    fun applyBattery(
        state: SonyHeadphoneUiState,
        response: ParsedHeadphoneResponse.Qcy.Battery,
    ): SonyHeadphoneUiState = state.copy(
        batteryState = state.batteryState.copy(
            left = response.leftLevel,
            right = response.rightLevel,
            cradle = response.caseLevel,
            raw = listOf(response.leftLevel, response.rightLevel, response.caseLevel),
        )
    )

    fun applyNoise(
        state: SonyHeadphoneUiState,
        response: ParsedHeadphoneResponse.Qcy.NoiseControl,
    ): SonyHeadphoneUiState {
        val mode = response.mode?.let { rawMode ->
            when (rawMode) {
                QcyProtocol.NOISE_MODE_ANC.toInt() -> NoiseControlMode.NOISE_CANCELLING
                QcyProtocol.NOISE_MODE_OUTDOOR.toInt(),
                QcyProtocol.NOISE_MODE_TRANSPARENCY.toInt() -> NoiseControlMode.AMBIENT_SOUND
                QcyProtocol.NOISE_MODE_OFF.toInt() -> NoiseControlMode.OFF
                else -> NoiseControlMode.OFF
            }
        }
        val currentNoise = state.noiseControlState
        return state.copy(
            noiseControlState = currentNoise.copy(
                controlMode = mode ?: currentNoise.controlMode,
                ambientLevel = response.noiseValue?.let { v ->
                    if (v > 0) (v / 12).coerceIn(1, 20) else currentNoise.ambientLevel
                } ?: currentNoise.ambientLevel,
                raw = listOfNotNull(response.mode, response.noiseValue),
            )
        )
    }

    fun applyEq(
        state: SonyHeadphoneUiState,
        response: ParsedHeadphoneResponse.Qcy.EqData,
    ): SonyHeadphoneUiState {
        val bandSteps = response.bands.map { band ->
            val effectiveGain = band.gain + response.masterGain
            ((effectiveGain / 1.2f) + EQ_BAND_STEP_CENTER).toInt().coerceIn(0, 20)
        }
        return state.copy(
            eqState = state.eqState.copy(
                preset = if (response.eqType == 0) EqPresetId.CUSTOM else EqPresetId.OFF,
                bandSteps = bandSteps.map { it - EQ_BAND_STEP_CENTER },
                rawBandSteps = bandSteps,
                bandStepCenter = EQ_BAND_STEP_CENTER,
                raw = response.bands.flatMap { listOf(it.frequency, (it.gain * 100).toInt(), (it.q * 100).toInt()) },
            )
        )
    }

    fun applyDeviceInfo(
        state: SonyHeadphoneUiState,
        response: ParsedHeadphoneResponse.Qcy.DeviceInfo,
    ): SonyHeadphoneUiState = state.copy(
        deviceInfo = state.deviceInfo.copy(
            firmwareVersion = "L:${response.leftFirmware} R:${response.rightFirmware}",
        )
    )

    fun applyVolume(
        state: SonyHeadphoneUiState,
        response: ParsedHeadphoneResponse.Qcy.Volume,
    ): SonyHeadphoneUiState = state // P3 follow-up

    fun applyFunctionStatus(
        state: SonyHeadphoneUiState,
        response: ParsedHeadphoneResponse.Qcy.FunctionStatus,
    ): SonyHeadphoneUiState = state // P3 follow-up
}
