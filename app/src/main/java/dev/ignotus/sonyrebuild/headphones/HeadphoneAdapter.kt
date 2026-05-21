package dev.ignotus.sonyrebuild.headphones

import dev.ignotus.sonyrebuild.ble.DiscoveredSonyDevice
import dev.ignotus.sonyrebuild.protocol.EqEbbInquiredType
import dev.ignotus.sonyrebuild.protocol.EqPresetId
import dev.ignotus.sonyrebuild.protocol.NcAsmInquiredType
import dev.ignotus.sonyrebuild.protocol.NoiseControlMode
import dev.ignotus.sonyrebuild.protocol.ParsedTandemResponse
import dev.ignotus.sonyrebuild.protocol.PlaybackControl
import dev.ignotus.sonyrebuild.protocol.PowerInquiredType

enum class HeadphoneProtocolVariant {
    SONY_TANDEM_V1_TABLE1,
    SONY_TANDEM_V1_TABLE2,
    SONY_TANDEM_V2_TABLE1,
    SONY_TANDEM_V2_TABLE2,
    UNKNOWN,
}

sealed interface HeadphoneOperation {
    data object RefreshBasics : HeadphoneOperation
    data class RefreshFeature(val feature: HeadphoneFeature) : HeadphoneOperation
    data class SetNoiseControl(
        val mode: NoiseControlMode,
        val ambientLevel: Int,
        val ambientMode: dev.ignotus.sonyrebuild.protocol.AmbientSoundMode,
    ) : HeadphoneOperation
    data class SetEqPreset(
        val preset: EqPresetId,
        val type: EqEbbInquiredType,
        val bandSteps: List<Int>,
    ) : HeadphoneOperation
    data class SetEqBands(
        val rawSteps: List<Int>,
        val preset: EqPresetId?,
        val useCustomPayload: Boolean,
        val type: EqEbbInquiredType,
    ) : HeadphoneOperation
    data class SetClearBass(val level: Int) : HeadphoneOperation
    data class Playback(val control: PlaybackControl) : HeadphoneOperation
}

interface HeadphoneFeatureCodec {
    val feature: HeadphoneFeature
    val protocolVariant: HeadphoneProtocolVariant
}

enum class HeadphoneFormFactor {
    HEADSET,
    TRUE_WIRELESS,
    UNKNOWN,
}

enum class HeadphoneFeature {
    DEVICE_INFO,
    BATTERY,
    NOISE_CONTROL,
    AMBIENT_LEVEL,
    AMBIENT_VOICE_MODE,
    PLAYBACK_CONTROL,
    EQ,
    CLEAR_BASS,
    LEA_STATUS,
    QUICK_ACCESS,
    WEARING_STATUS,
}

enum class HeadphoneTransport {
    UNKNOWN,
    SPP,
    GATT_HPC,
    UNSUPPORTED_LE_ENDPOINT,
}

data class HeadphoneCommand(
    val label: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeadphoneCommand) return false
        return label == other.label && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * label.hashCode() + bytes.contentHashCode()
}

data class HeadphoneCapabilities(
    val features: Set<HeadphoneFeature>,
    val formFactor: HeadphoneFormFactor,
    val batteryQueries: List<PowerInquiredType>,
    val noiseControlQueryTypes: List<NcAsmInquiredType>,
    val writableNoiseControlTypes: Set<NcAsmInquiredType>,
    val eqStatusTypes: List<EqEbbInquiredType>,
    val eqParamTypes: List<EqEbbInquiredType>,
    val queryProtocolInfo: Boolean = true,
    val queryNoiseControlParams: Boolean = true,
)

data class ConnectedHeadphoneProfile(
    val adapterId: String,
    val brand: String,
    val modelName: String,
    val displayName: String,
    val protocolName: String,
    val series: String? = null,
    val transport: HeadphoneTransport = HeadphoneTransport.UNKNOWN,
    val capabilities: HeadphoneCapabilities,
    val featureProtocolMap: Map<HeadphoneFeature, HeadphoneProtocolVariant> = emptyMap(),
    val protocolEvidence: List<String> = emptyList(),
) {
    fun supports(feature: HeadphoneFeature): Boolean = feature in capabilities.features
    fun protocolFor(feature: HeadphoneFeature): HeadphoneProtocolVariant =
        featureProtocolMap[feature] ?: HeadphoneProtocolVariant.UNKNOWN
}

interface HeadphoneAdapter {
    val id: String
    val brand: String
    val protocolName: String

    fun match(device: DiscoveredSonyDevice, reportedModelName: String? = null): ConnectedHeadphoneProfile?
    fun fallbackProfile(device: DiscoveredSonyDevice): ConnectedHeadphoneProfile
    fun withTransport(profile: ConnectedHeadphoneProfile, transport: HeadphoneTransport): ConnectedHeadphoneProfile =
        profile.copy(transport = transport)

    fun buildRefreshCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand>
    fun buildSetNoiseControlModeCommands(
        profile: ConnectedHeadphoneProfile,
        mode: NoiseControlMode,
        ambientLevel: Int,
        ambientMode: dev.ignotus.sonyrebuild.protocol.AmbientSoundMode,
    ): List<HeadphoneCommand> = emptyList()

    fun buildSetEqPresetCommands(
        profile: ConnectedHeadphoneProfile,
        preset: EqPresetId,
        type: EqEbbInquiredType,
        bandSteps: List<Int>,
    ): List<HeadphoneCommand> = emptyList()

    fun buildSetEqBandCommands(
        profile: ConnectedHeadphoneProfile,
        rawSteps: List<Int>,
        preset: EqPresetId?,
        useCustomPayload: Boolean,
        type: EqEbbInquiredType,
    ): List<HeadphoneCommand> = emptyList()

    fun buildSetClearBassCommands(profile: ConnectedHeadphoneProfile, level: Int): List<HeadphoneCommand> =
        emptyList()

    fun buildRefreshNoiseControlCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> = emptyList()

    fun buildRefreshEqCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> = emptyList()

    fun buildRefreshBatteryCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> = emptyList()

    fun buildRefreshPlaybackCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> = emptyList()

    fun buildPlaybackCommands(profile: ConnectedHeadphoneProfile, control: PlaybackControl): List<HeadphoneCommand> =
        emptyList()

    fun parse(profile: ConnectedHeadphoneProfile, raw: ByteArray): ParsedTandemResponse

    fun canWrite(profile: ConnectedHeadphoneProfile, feature: HeadphoneFeature): Boolean =
        profile.supports(feature)
}

object HeadphoneAdapterRegistry {
    private val adapters: List<HeadphoneAdapter> = listOf(SonyTandemV2HeadphoneAdapter)

    fun resolve(device: DiscoveredSonyDevice, reportedModelName: String? = null): ConnectedHeadphoneProfile {
        adapters.forEach { adapter ->
            adapter.match(device, reportedModelName)?.let { return it }
        }
        return SonyTandemV2HeadphoneAdapter.fallbackProfile(device)
    }

    fun buildRefreshCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRefreshCommands(profile)

    fun canWrite(profile: ConnectedHeadphoneProfile, feature: HeadphoneFeature): Boolean =
        adapterFor(profile).canWrite(profile, feature)

    fun buildSetNoiseControlModeCommands(
        profile: ConnectedHeadphoneProfile,
        mode: NoiseControlMode,
        ambientLevel: Int,
        ambientMode: dev.ignotus.sonyrebuild.protocol.AmbientSoundMode,
    ): List<HeadphoneCommand> =
        adapterFor(profile).buildSetNoiseControlModeCommands(profile, mode, ambientLevel, ambientMode)

    fun buildSetEqPresetCommands(
        profile: ConnectedHeadphoneProfile,
        preset: EqPresetId,
        type: EqEbbInquiredType,
        bandSteps: List<Int>,
    ): List<HeadphoneCommand> =
        adapterFor(profile).buildSetEqPresetCommands(profile, preset, type, bandSteps)

    fun buildSetEqBandCommands(
        profile: ConnectedHeadphoneProfile,
        rawSteps: List<Int>,
        preset: EqPresetId?,
        useCustomPayload: Boolean,
        type: EqEbbInquiredType,
    ): List<HeadphoneCommand> =
        adapterFor(profile).buildSetEqBandCommands(profile, rawSteps, preset, useCustomPayload, type)

    fun buildSetClearBassCommands(profile: ConnectedHeadphoneProfile, level: Int): List<HeadphoneCommand> =
        adapterFor(profile).buildSetClearBassCommands(profile, level)

    fun buildRefreshNoiseControlCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRefreshNoiseControlCommands(profile)

    fun buildRefreshEqCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRefreshEqCommands(profile)

    fun buildRefreshBatteryCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRefreshBatteryCommands(profile)

    fun buildRefreshPlaybackCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRefreshPlaybackCommands(profile)

    fun parse(profile: ConnectedHeadphoneProfile, raw: ByteArray): ParsedTandemResponse =
        adapterFor(profile).parse(profile, raw)

    private fun adapterFor(profile: ConnectedHeadphoneProfile): HeadphoneAdapter =
        adapters.firstOrNull { it.id == profile.adapterId } ?: SonyTandemV2HeadphoneAdapter
}
