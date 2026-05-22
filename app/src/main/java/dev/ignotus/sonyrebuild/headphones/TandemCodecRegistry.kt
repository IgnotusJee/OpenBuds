package dev.ignotus.sonyrebuild.headphones

import dev.ignotus.sonyrebuild.protocol.AmbientSoundMode
import dev.ignotus.sonyrebuild.protocol.DeviceInfoType
import dev.ignotus.sonyrebuild.protocol.EqEbbInquiredType
import dev.ignotus.sonyrebuild.protocol.EqPresetId
import dev.ignotus.sonyrebuild.protocol.LeaInquiredType
import dev.ignotus.sonyrebuild.protocol.NcAsmInquiredType
import dev.ignotus.sonyrebuild.protocol.NoiseControlMode
import dev.ignotus.sonyrebuild.protocol.ParsedTandemResponse
import dev.ignotus.sonyrebuild.protocol.PlaybackControl
import dev.ignotus.sonyrebuild.protocol.PowerInquiredType
import dev.ignotus.sonyrebuild.protocol.SonyTandemV1Table1Protocol
import dev.ignotus.sonyrebuild.protocol.SonyTandemV1Table2Protocol
import dev.ignotus.sonyrebuild.protocol.SonyTandemV2Table1Protocol
import dev.ignotus.sonyrebuild.protocol.SonyTandemV2Table2Protocol

interface TandemCodec {
    val variant: HeadphoneProtocolVariant
    val defaultChannel: TandemChannel
    fun parse(raw: ByteArray): ParsedTandemResponse
}

object TandemCodecRegistry {
    fun codecFor(variant: HeadphoneProtocolVariant): TandemCodec = when (variant) {
        HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1 -> SonyTandemV1Table1Codec
        HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE2 -> SonyTandemV1Table2Codec
        HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1 -> SonyTandemV2Table1Codec
        HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE2 -> SonyTandemV2Table2Codec
        HeadphoneProtocolVariant.UNKNOWN -> UnknownTandemCodec
    }
}

object UnknownTandemCodec : TandemCodec {
    override val variant: HeadphoneProtocolVariant = HeadphoneProtocolVariant.UNKNOWN
    override val defaultChannel: TandemChannel = TandemChannel.GATT_V2_HPC
    override fun parse(raw: ByteArray): ParsedTandemResponse =
        ParsedTandemResponse.Unknown(
            dataType = raw.getOrNull(0)?.toInt()?.and(0xFF),
            command = raw.getOrNull(1)?.toInt()?.and(0xFF),
            payload = raw.drop(2).toByteArray(),
            raw = raw,
        )
}

object SonyTandemV1Table1Codec : TandemCodec {
    override val variant: HeadphoneProtocolVariant = HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1
    override val defaultChannel: TandemChannel = TandemChannel.GATT_V1_MC

    fun buildGetBatteryStatus(type: PowerInquiredType): ByteArray =
        SonyTandemV1Table1Protocol.buildGetBatteryStatus(type)

    fun buildGetNcAsmParam(): ByteArray =
        SonyTandemV1Table1Protocol.buildGetNcAsmParam()

    fun buildSetNoiseControlMode(
        mode: NoiseControlMode,
        ambientLevel: Int,
        ambientMode: AmbientSoundMode,
    ): ByteArray =
        SonyTandemV1Table1Protocol.buildSetNoiseControlMode(mode, ambientLevel, ambientMode)

    override fun parse(raw: ByteArray): ParsedTandemResponse =
        SonyTandemV1Table1Protocol.parse(raw)
}

object SonyTandemV1Table2Codec : TandemCodec {
    override val variant: HeadphoneProtocolVariant = HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE2
    override val defaultChannel: TandemChannel = TandemChannel.GATT_V1_MC
    override fun parse(raw: ByteArray): ParsedTandemResponse =
        SonyTandemV1Table2Protocol.parse(raw)
}

object SonyTandemV2Table1Codec : TandemCodec {
    override val variant: HeadphoneProtocolVariant = HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1
    override val defaultChannel: TandemChannel = TandemChannel.GATT_V2_HPC

    fun buildGetProtocolInfo(): ByteArray =
        SonyTandemV2Table1Protocol.buildGetProtocolInfo()

    fun buildGetDeviceInfo(type: DeviceInfoType): ByteArray =
        SonyTandemV2Table1Protocol.buildGetDeviceInfo(type)

    fun buildGetDisplayFirmwareVersion(): ByteArray =
        SonyTandemV2Table1Protocol.buildGetDisplayFirmwareVersion()

    fun buildGetBatteryStatus(type: PowerInquiredType): ByteArray =
        SonyTandemV2Table1Protocol.buildGetBatteryStatus(type)

    fun buildGetEqEbbStatus(type: EqEbbInquiredType): ByteArray =
        SonyTandemV2Table1Protocol.buildGetEqEbbStatus(type)

    fun buildGetEqEbbParam(type: EqEbbInquiredType): ByteArray =
        SonyTandemV2Table1Protocol.buildGetEqEbbParam(type)

    fun buildSetEqPreset(
        preset: EqPresetId,
        type: EqEbbInquiredType,
        bandSteps: List<Int> = emptyList(),
    ): ByteArray =
        SonyTandemV2Table1Protocol.buildSetEqPreset(preset, type, bandSteps)

    fun buildSetCustomEqBandSteps(rawSteps: List<Int>): ByteArray =
        SonyTandemV2Table1Protocol.buildSetCustomEqBandSteps(rawSteps)

    fun buildSetClearBass(level: Int): ByteArray =
        SonyTandemV2Table1Protocol.buildSetClearBass(level)

    fun buildGetNcAsmStatus(type: NcAsmInquiredType): ByteArray =
        SonyTandemV2Table1Protocol.buildGetNcAsmStatus(type)

    fun buildGetNcAsmParam(type: NcAsmInquiredType): ByteArray =
        SonyTandemV2Table1Protocol.buildGetNcAsmParam(type)

    fun buildSetNoiseControlMode(
        mode: NoiseControlMode,
        ambientLevel: Int,
        ambientMode: AmbientSoundMode,
    ): ByteArray =
        SonyTandemV2Table1Protocol.buildSetNoiseControlMode(mode, ambientLevel, ambientMode)

    fun buildSetNcModeSwitchAndAmbientLevel(
        mode: NoiseControlMode,
        ambientLevel: Int,
        ambientMode: AmbientSoundMode,
    ): ByteArray =
        SonyTandemV2Table1Protocol.buildSetNcModeSwitchAndAmbientLevel(mode, ambientLevel, ambientMode)

    fun buildSetNcOnOff(enabled: Boolean): ByteArray =
        SonyTandemV2Table1Protocol.buildSetNcOnOff(enabled)

    fun buildSetAmbientSound(enabled: Boolean, mode: AmbientSoundMode): ByteArray =
        SonyTandemV2Table1Protocol.buildSetAmbientSound(enabled, mode)

    fun buildSetAmbientLevel(level: Int, enabled: Boolean, mode: AmbientSoundMode): ByteArray =
        SonyTandemV2Table1Protocol.buildSetAmbientLevel(level, enabled, mode)

    fun buildGetPlaybackStatus(): ByteArray =
        SonyTandemV2Table1Protocol.buildGetPlaybackStatus()

    fun buildPlayback(control: PlaybackControl): ByteArray =
        SonyTandemV2Table1Protocol.buildPlayback(control)

    fun buildGetLeaStatus(type: LeaInquiredType): ByteArray =
        SonyTandemV2Table1Protocol.buildGetLeaStatus(type)

    fun buildGetLeaPairedHistory(type: LeaInquiredType): ByteArray =
        SonyTandemV2Table1Protocol.buildGetLeaPairedHistory(type)

    fun buildGetQuickAccess(): ByteArray =
        SonyTandemV2Table1Protocol.buildGetQuickAccess()

    fun buildGetWearingStatus(): ByteArray =
        SonyTandemV2Table1Protocol.buildGetWearingStatus()

    override fun parse(raw: ByteArray): ParsedTandemResponse =
        SonyTandemV2Table1Protocol.parse(raw)
}

object SonyTandemV2Table2Codec : TandemCodec {
    override val variant: HeadphoneProtocolVariant = HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE2
    override val defaultChannel: TandemChannel = TandemChannel.GATT_V2_MC
    override fun parse(raw: ByteArray): ParsedTandemResponse =
        SonyTandemV2Table2Protocol.parse(raw)
}
