package dev.ignotus.sonyrebuild.headphones

import dev.ignotus.sonyrebuild.ble.DiscoveredSonyDevice
import dev.ignotus.sonyrebuild.protocol.AmbientSoundMode
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

enum class TandemChannel {
    SPP_MDR,
    GATT_V2_HPC,
    GATT_V2_MC,
    GATT_V1_MC,
    ;

    companion object {
        fun fromServiceUuid(uuid: java.util.UUID): TandemChannel? {
            // Lazy-init to avoid circular dependency with SonyGatt
            val v2Hpc = java.util.UUID.fromString("5b833e20-6bc7-4802-8e9a-723ceca4bd8f")
            val v2Mc = java.util.UUID.fromString("5b833e21-6bc7-4802-8e9a-723ceca4bd8f")
            val v1Mc = java.util.UUID.fromString("5b833e23-6bc7-4802-8e9a-723ceca4bd8f")
            return when (uuid) {
                v2Hpc -> GATT_V2_HPC
                v2Mc -> GATT_V2_MC
                v1Mc -> GATT_V1_MC
                else -> null
            }
        }
    }
}

enum class PlaybackDispatchStrategy {
    TANDEM_FIRST,
    ANDROID_MEDIA_FALLBACK,
    TANDEM_ONLY,
}

data class FeatureProtocolBinding(
    val feature: HeadphoneFeature,
    val variant: HeadphoneProtocolVariant,
    val channel: TandemChannel,
    val queryTypes: List<Any> = emptyList(),
    val writableTypes: Set<Any> = emptySet(),
)

data class HeadphoneCommand(
    val label: String,
    val bytes: ByteArray,
    val channel: TandemChannel = TandemChannel.GATT_V2_HPC,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeadphoneCommand) return false
        return label == other.label && channel == other.channel && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * (31 * label.hashCode() + channel.hashCode()) + bytes.contentHashCode()
}

data class EqWriteContext(
    val rawBandSteps: List<Int> = emptyList(),
)

data class HeadphoneCapabilities(
    val features: Set<HeadphoneFeature>,
    val formFactor: HeadphoneFormFactor,
    val batteryQueries: List<PowerInquiredType>,
    val noiseControlQueryTypes: List<NcAsmInquiredType>,
    val writableNoiseControlTypes: Set<NcAsmInquiredType>,
    val eqConfig: EqDeviceConfig = EqDeviceConfig(
        availablePresets = listOf(EqPresetId.OFF),
        writeInquiredType = EqEbbInquiredType.PRESET_EQ,
        includeBandsOnPresetWrite = false,
        statusQueryTypes = emptyList(),
        paramQueryTypes = emptyList(),
        bandCount = 0,
        hasClearBass = false,
    ),
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
    val featureBindings: Map<HeadphoneFeature, FeatureProtocolBinding> = emptyMap(),
    val protocolEvidence: List<String> = emptyList(),
    val playbackDispatchStrategy: PlaybackDispatchStrategy = PlaybackDispatchStrategy.TANDEM_FIRST,
) {
    fun supports(feature: HeadphoneFeature): Boolean = feature in capabilities.features
    fun protocolFor(feature: HeadphoneFeature): HeadphoneProtocolVariant =
        featureBindings[feature]?.variant ?: featureProtocolMap[feature] ?: HeadphoneProtocolVariant.UNKNOWN
    fun bindingFor(feature: HeadphoneFeature): FeatureProtocolBinding? = featureBindings[feature]
    fun channelFor(feature: HeadphoneFeature): TandemChannel =
        featureBindings[feature]?.channel ?: defaultChannelFor(protocolFor(feature))
}

data class ProfileTemplate(
    val modelName: String,
    val series: String?,
    val capabilities: HeadphoneCapabilities,
    val knownStaticProfile: Boolean = true,
) {
    val featureProtocolMap: Map<HeadphoneFeature, HeadphoneProtocolVariant> by lazy {
        buildFeatureProtocolMap()
    }

    private fun buildFeatureProtocolMap(): Map<HeadphoneFeature, HeadphoneProtocolVariant> =
        when (modelName) {
            "WH-1000XM4" -> capabilities.features.associateWith { feature ->
                when (feature) {
                    HeadphoneFeature.BATTERY,
                    HeadphoneFeature.NOISE_CONTROL,
                    HeadphoneFeature.AMBIENT_LEVEL,
                    HeadphoneFeature.AMBIENT_VOICE_MODE -> HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1
                    HeadphoneFeature.EQ,
                    HeadphoneFeature.CLEAR_BASS,
                    HeadphoneFeature.PLAYBACK_CONTROL -> HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1
                    else -> HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1
                }
            }
            else -> capabilities.features.associateWith { HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1 }
        }

    val featureBindings: Map<HeadphoneFeature, FeatureProtocolBinding> by lazy {
        featureProtocolMap.mapValues { (feature, variant) ->
            FeatureProtocolBinding(
                feature = feature,
                variant = variant,
                channel = defaultChannelFor(variant),
                queryTypes = queryTypesFor(feature),
                writableTypes = writableTypesFor(feature),
            )
        }
    }

    private fun queryTypesFor(feature: HeadphoneFeature): List<Any> = when (feature) {
        HeadphoneFeature.BATTERY -> capabilities.batteryQueries
        HeadphoneFeature.NOISE_CONTROL,
        HeadphoneFeature.AMBIENT_LEVEL,
        HeadphoneFeature.AMBIENT_VOICE_MODE -> capabilities.noiseControlQueryTypes
        HeadphoneFeature.EQ,
        HeadphoneFeature.CLEAR_BASS -> capabilities.eqConfig.statusQueryTypes + capabilities.eqConfig.paramQueryTypes
        else -> emptyList()
    }

    private fun writableTypesFor(feature: HeadphoneFeature): Set<Any> = when (feature) {
        HeadphoneFeature.NOISE_CONTROL,
        HeadphoneFeature.AMBIENT_LEVEL,
        HeadphoneFeature.AMBIENT_VOICE_MODE -> capabilities.writableNoiseControlTypes
        else -> emptySet()
    }

    fun toProfile(adapterId: String, brand: String, protocolName: String, displayName: String): ConnectedHeadphoneProfile =
        ConnectedHeadphoneProfile(
            adapterId = adapterId,
            brand = brand,
            modelName = modelName,
            displayName = displayName.removePrefix("LE_").takeIf { it.isNotBlank() } ?: modelName,
            protocolName = protocolName,
            series = series,
            capabilities = capabilities,
            featureProtocolMap = featureProtocolMap,
            featureBindings = featureBindings,
            protocolEvidence = if (knownStaticProfile) {
                listOf(
                    "static-profile:$modelName",
                    "reverse:C11518x DeviceCapabilityTableset1/2 dispatch",
                    "reverse:MdlSeries table-set mapping",
                )
            } else {
                listOf(
                    "probe-only:unknown-sony-device",
                    "reverse:C11518x DeviceCapabilityTableset1/2 dispatch",
                )
            },
            playbackDispatchStrategy = if (knownStaticProfile) {
                PlaybackDispatchStrategy.TANDEM_FIRST
            } else {
                PlaybackDispatchStrategy.ANDROID_MEDIA_FALLBACK
            },
        )
}

val ConnectedHeadphoneProfile.eqUiCapability: EqUiCapability
    get() = EqProtocolEngine.uiCapability(capabilities.eqConfig)

interface HeadphoneAdapter {
    val id: String
    val brand: String
    val protocolName: String

    fun match(device: DiscoveredSonyDevice, reportedModelName: String? = null): ConnectedHeadphoneProfile?
    fun fallbackProfile(device: DiscoveredSonyDevice): ConnectedHeadphoneProfile
    fun withTransport(profile: ConnectedHeadphoneProfile, transport: HeadphoneTransport): ConnectedHeadphoneProfile =
        profile.copy(transport = transport)

    fun matchTemplate(
        template: ProfileTemplate,
        device: DiscoveredSonyDevice,
        reportedModelName: String? = null,
    ): ConnectedHeadphoneProfile? {
        val candidates = listOfNotNull(reportedModelName, device.name.removePrefix("LE_"))
        val matched = candidates.any { candidate ->
            candidate.normalizedModelName().contains(template.modelName.normalizedModelName())
        }
        return if (matched) template.toProfile(id, brand, protocolName, device.name) else null
    }

    fun buildRefreshCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand>
    fun buildSetNoiseControlModeCommands(
        profile: ConnectedHeadphoneProfile,
        mode: NoiseControlMode,
        ambientLevel: Int,
        ambientMode: AmbientSoundMode,
    ): List<HeadphoneCommand> = emptyList()

    fun buildSetEqPresetCommands(
        profile: ConnectedHeadphoneProfile,
        preset: EqPresetId,
        context: EqWriteContext,
    ): List<HeadphoneCommand> = emptyList()

    fun buildSetEqBandCommands(
        profile: ConnectedHeadphoneProfile,
        rawSteps: List<Int>,
        preset: EqPresetId?,
        context: EqWriteContext,
    ): List<HeadphoneCommand> = emptyList()

    fun buildSetClearBassCommands(
        profile: ConnectedHeadphoneProfile,
        level: Int,
        context: EqWriteContext,
    ): List<HeadphoneCommand> =
        emptyList()

    fun buildRefreshNoiseControlCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> = emptyList()

    fun buildRefreshEqCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> = emptyList()

    fun buildRefreshBatteryCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> = emptyList()

    fun buildRefreshPlaybackCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> = emptyList()

    fun buildPlaybackCommands(profile: ConnectedHeadphoneProfile, control: PlaybackControl): List<HeadphoneCommand> =
        emptyList()

    fun parse(profile: ConnectedHeadphoneProfile, channel: TandemChannel, raw: ByteArray): ParsedTandemResponse

    fun parse(profile: ConnectedHeadphoneProfile, raw: ByteArray): ParsedTandemResponse =
        parse(profile, TandemChannel.GATT_V2_HPC, raw)

    fun canWrite(profile: ConnectedHeadphoneProfile, feature: HeadphoneFeature): Boolean =
        profile.supports(feature)
}

object HeadphoneAdapterRegistry {
    private val adapters: List<HeadphoneAdapter> = listOf(SonyTandemHeadphoneAdapter)

    fun resolve(device: DiscoveredSonyDevice, reportedModelName: String? = null): ConnectedHeadphoneProfile {
        adapters.forEach { adapter ->
            adapter.match(device, reportedModelName)?.let { return it }
        }
        return SonyTandemHeadphoneAdapter.fallbackProfile(device)
    }

    fun buildRefreshCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRefreshCommands(profile)

    fun canWrite(profile: ConnectedHeadphoneProfile, feature: HeadphoneFeature): Boolean =
        adapterFor(profile).canWrite(profile, feature)

    fun buildSetNoiseControlModeCommands(
        profile: ConnectedHeadphoneProfile,
        mode: NoiseControlMode,
        ambientLevel: Int,
        ambientMode: AmbientSoundMode,
    ): List<HeadphoneCommand> =
        adapterFor(profile).buildSetNoiseControlModeCommands(profile, mode, ambientLevel, ambientMode)

    fun buildSetEqPresetCommands(
        profile: ConnectedHeadphoneProfile,
        preset: EqPresetId,
        context: EqWriteContext,
    ): List<HeadphoneCommand> =
        adapterFor(profile).buildSetEqPresetCommands(profile, preset, context)

    fun buildSetEqBandCommands(
        profile: ConnectedHeadphoneProfile,
        rawSteps: List<Int>,
        preset: EqPresetId?,
        context: EqWriteContext,
    ): List<HeadphoneCommand> =
        adapterFor(profile).buildSetEqBandCommands(profile, rawSteps, preset, context)

    fun buildSetClearBassCommands(
        profile: ConnectedHeadphoneProfile,
        level: Int,
        context: EqWriteContext,
    ): List<HeadphoneCommand> =
        adapterFor(profile).buildSetClearBassCommands(profile, level, context)

    fun buildRefreshNoiseControlCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRefreshNoiseControlCommands(profile)

    fun buildRefreshEqCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRefreshEqCommands(profile)

    fun buildRefreshBatteryCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRefreshBatteryCommands(profile)

    fun buildRefreshPlaybackCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRefreshPlaybackCommands(profile)

    fun buildPlaybackCommands(profile: ConnectedHeadphoneProfile, control: PlaybackControl): List<HeadphoneCommand> =
        adapterFor(profile).buildPlaybackCommands(profile, control)

    fun parse(profile: ConnectedHeadphoneProfile, channel: TandemChannel, raw: ByteArray): ParsedTandemResponse =
        adapterFor(profile).parse(profile, channel, raw)

    fun parse(profile: ConnectedHeadphoneProfile, raw: ByteArray): ParsedTandemResponse =
        adapterFor(profile).parse(profile, raw)

    private fun adapterFor(profile: ConnectedHeadphoneProfile): HeadphoneAdapter =
        adapters.firstOrNull { it.id == profile.adapterId }
            ?: adapters.firstOrNull { adapter ->
                adapter is SonyTandemHeadphoneAdapter && profile.adapterId in adapter.legacyIds
            }
            ?: SonyTandemHeadphoneAdapter
}

fun String.normalizedModelName(): String =
    uppercase()
        .removePrefix("LE_")
        .replace(Regex("[\\s\\-_.]+"), "")

fun defaultChannelFor(variant: HeadphoneProtocolVariant): TandemChannel =
    when (variant) {
        HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1,
        HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE2 -> TandemChannel.GATT_V1_MC
        HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE2 -> TandemChannel.GATT_V2_MC
        HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
        HeadphoneProtocolVariant.UNKNOWN -> TandemChannel.GATT_V2_HPC
    }
