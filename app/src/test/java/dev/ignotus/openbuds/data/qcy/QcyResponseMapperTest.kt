package dev.ignotus.openbuds.data.qcy

import dev.ignotus.openbuds.data.HeadphoneUiState
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dev.ignotus.openbuds.protocol.ParsedHeadphoneResponse
import dev.ignotus.openbuds.protocol.QcyEqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QcyResponseMapperTest {
    private val initial = HeadphoneUiState()

    @Test
    fun applyBattery_setsAllLevelsAndChargingFlags() {
        val r = ParsedHeadphoneResponse.Qcy.Battery(80, 90, 50, true, false, true, byteArrayOf())
        val s = QcyResponseMapper.applyBattery(initial, r)
        assertEquals(80, s.batteryState.left)
        assertEquals(90, s.batteryState.right)
        assertEquals(50, s.batteryState.cradle)
        assertEquals(true, s.batteryState.leftCharging)
        assertEquals(false, s.batteryState.rightCharging)
        assertEquals(true, s.batteryState.cradleCharging)
    }

    @Test
    fun applyNoise_modeOnly_mergesWithExisting() {
        val r = ParsedHeadphoneResponse.Qcy.NoiseControl(mode = 1, noiseValue = null, raw = byteArrayOf())
        val s = QcyResponseMapper.applyNoise(initial, r)
        assertEquals(NoiseControlMode.NOISE_CANCELLING, s.noiseControlState.controlMode)
    }

    @Test
    fun applyNoise_valueOnly_mergesWithExisting() {
        val r = ParsedHeadphoneResponse.Qcy.NoiseControl(mode = null, noiseValue = 84, raw = byteArrayOf())
        val s = QcyResponseMapper.applyNoise(initial, r)
        assertEquals(7, s.noiseControlState.ambientLevel) // 84/12 = 7
    }

    @Test
    fun applyEq_mapsStepsCorrectly() {
        val band = QcyEqBand(frequency = 1000, gain = 1.2f, q = 1.0f, bandType = 0)
        val r = ParsedHeadphoneResponse.Qcy.EqData(0, 0f, listOf(band), byteArrayOf())
        val s = QcyResponseMapper.applyEq(initial, r)
        assertEquals(EqPresetId.CUSTOM, s.eqState.preset)
        assertEquals(11, s.eqState.rawBandSteps[0]) // 1.2/1.2 + 10 = 11
    }

    @Test
    fun applyDeviceInfo_setsFirmwareLabel() {
        val r = ParsedHeadphoneResponse.Qcy.DeviceInfo("1.2.3", "4.5.6", byteArrayOf())
        val s = QcyResponseMapper.applyDeviceInfo(initial, r)
        assertEquals("L:1.2.3 R:4.5.6", s.deviceInfo.firmwareVersion)
    }

    @Test
    fun applyVolume_setsMusicVolumeFromLeftVolume() {
        val r = ParsedHeadphoneResponse.Qcy.Volume(10, 12, byteArrayOf())
        val s = QcyResponseMapper.applyVolume(initial, r)
        assertEquals(10, s.volumeState.musicVolume)
    }

    @Test
    fun applyFunctionStatus_logsButNoop_returnsSame() {
        val r = ParsedHeadphoneResponse.Qcy.FunctionStatus(true, false, byteArrayOf())
        assertTrue(QcyResponseMapper.applyFunctionStatus(initial, r) === initial)
    }
}
