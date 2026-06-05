package dev.ignotus.openbuds.headphones

import dev.ignotus.openbuds.ble.DiscoveredDevice
import dev.ignotus.openbuds.ble.IncomingHeadphoneMessage
import dev.ignotus.openbuds.headphones.qcy.QcyHeadphoneAdapter
import dev.ignotus.openbuds.headphones.sony.EqProtocolEngine
import dev.ignotus.openbuds.headphones.sony.SonyTandemHeadphoneAdapter
import dev.ignotus.openbuds.protocol.AmbientSoundMode
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dev.ignotus.openbuds.protocol.ParsedHeadphoneResponse
import dev.ignotus.openbuds.protocol.PlaybackStatus
import dev.ignotus.openbuds.protocol.sony.EqEbbInquiredType
import dev.ignotus.openbuds.protocol.sony.NcAsmInquiredType
import dev.ignotus.openbuds.protocol.sony.PlaybackControl
import dev.ignotus.openbuds.protocol.sony.PlayInquiredType
import dev.ignotus.openbuds.protocol.sony.PowerInquiredType

enum class HeadphoneProtocolVariant {
    SONY_TANDEM_V1_TABLE1,
    SONY_TANDEM_V1_TABLE2,
    SONY_TANDEM_V2_TABLE1,
    SONY_TANDEM_V2_TABLE2,
    QCY,
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
    VOLUME,
    AUDIO_EFFECT,
    /**
     * Ring/find earbud capability.
     *
     * FRAMEWORK STUB (2026-06-05): Neither Sony Tandem (v13.0.5) nor QCY protocol
     * exposes a dedicated BLE command for ringing/finding earbuds. The Sony Alert
     * family is for app-side dialog notifications; QCY CMDID_TONE_PLAY(61) is
     * defined but never implemented. Both OEM "find earbuds" features are phone-side
     * (GPS location + phone speaker alarm).
     *
     * This enum value + adapter interface stubs exist so the MiLink bridge layer
     * can conditionally expose ring controls when/if protocol support is discovered.
     * Currently no profile declares this feature; supportsRing remains false.
     */
    RING,
}

enum class HeadphoneTransport {
    UNKNOWN,
    SPP,
    GATT,
    GATT_HPC,
    GATT_MC,
    QCY_GATT,
    UNSUPPORTED_LE_ENDPOINT,
}

enum class PlaybackDispatchStrategy {
    TANDEM_FIRST,
    ANDROID_MEDIA_FALLBACK,
    TANDEM_ONLY,
}

data class FeatureProtocolBinding(
    val feature: HeadphoneFeature,
    val variant: HeadphoneProtocolVariant,
    val queryTypes: List<Any> = emptyList(),
    val writableTypes: Set<Any> = emptySet(),
)

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

data class EqWriteContext(
    val rawBandSteps: List<Int> = emptyList(),
    val preset: EqPresetId? = null,
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
        statusQueryTypes = emptyList(),
        paramQueryTypes = emptyList(),
        bandCount = 0,
        hasClearBass = false,
    ),
    val playbackControlType: PlayInquiredType = PlayInquiredType.PLAYBACK_CONTROL_WITH_CALL_VOLUME_ADJUSTMENT,
    val queryProtocolInfo: Boolean = true,
    val queryNoiseControlParams: Boolean = true,
)

enum class InfoLayoutHint { SONY_SERIES, BRAND_MODEL }

data class ConnectedHeadphoneProfile(
    val adapterId: String,
    val brand: String,
    val modelName: String,
    val displayName: String,
    val protocolName: String,
    val series: String? = null,
    val infoLayoutHint: InfoLayoutHint = InfoLayoutHint.SONY_SERIES,
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
}

data class ProfileTemplate(
    val modelName: String,
    val series: String?,
    val capabilities: HeadphoneCapabilities,
    val featureProtocolMap: Map<HeadphoneFeature, HeadphoneProtocolVariant>,
    val knownStaticProfile: Boolean = true,
    val infoLayoutHint: InfoLayoutHint = InfoLayoutHint.SONY_SERIES,
) {
    init {
        if (knownStaticProfile) {
            val missingFeatures = capabilities.features - featureProtocolMap.keys
            require(missingFeatures.isEmpty()) {
                "Static profile $modelName is missing protocol bindings for $missingFeatures"
            }
        }
    }

    val featureBindings: Map<HeadphoneFeature, FeatureProtocolBinding> by lazy {
        featureProtocolMap.mapValues { (feature, variant) ->
            FeatureProtocolBinding(
                feature = feature,
                variant = variant,
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
        HeadphoneFeature.PLAYBACK_CONTROL -> listOf(capabilities.playbackControlType)
        else -> emptyList()
    }

    private fun writableTypesFor(feature: HeadphoneFeature): Set<Any> = when (feature) {
        HeadphoneFeature.NOISE_CONTROL,
        HeadphoneFeature.AMBIENT_LEVEL,
        HeadphoneFeature.AMBIENT_VOICE_MODE -> capabilities.writableNoiseControlTypes
        HeadphoneFeature.PLAYBACK_CONTROL -> setOf(capabilities.playbackControlType)
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
            infoLayoutHint = infoLayoutHint,
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

    fun match(device: DiscoveredDevice, reportedModelName: String? = null): ConnectedHeadphoneProfile?
    fun fallbackProfile(device: DiscoveredDevice): ConnectedHeadphoneProfile
    fun withTransport(profile: ConnectedHeadphoneProfile, transport: HeadphoneTransport): ConnectedHeadphoneProfile =
        profile.copy(transport = transport)

    fun matchTemplate(
        template: ProfileTemplate,
        device: DiscoveredDevice,
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

    fun buildRefreshVolumeCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> = emptyList()

    fun buildSetVolumeCommands(profile: ConnectedHeadphoneProfile, volume: Int): List<HeadphoneCommand> = emptyList()

    // FRAMEWORK STUBS — ring/find earbud protocol commands.
    // No Sony Tandem or QCY protocol command exists for this. When protocol support is
    // discovered, override these in the adapter implementations and wire into the MiLink
    // bridge (MilinkBridgeService, MiTwsControlMapper, MilinkMiTwsFacadeEntry hooks).
    fun buildRingStartCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> = emptyList()
    fun buildRingStopCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> = emptyList()

    fun buildRefreshAudioEffectCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> = emptyList()
    fun buildSetAudioEffectCommands(profile: ConnectedHeadphoneProfile, enabled: Boolean): List<HeadphoneCommand> = emptyList()

    fun parse(profile: ConnectedHeadphoneProfile, message: IncomingHeadphoneMessage): ParsedHeadphoneResponse

    fun parse(profile: ConnectedHeadphoneProfile, raw: ByteArray): ParsedHeadphoneResponse =
        parse(profile, IncomingHeadphoneMessage(id, "default", raw))

    fun canWrite(profile: ConnectedHeadphoneProfile, feature: HeadphoneFeature): Boolean =
        profile.supports(feature)
}

object HeadphoneAdapterRegistry {
    private val adapters: List<HeadphoneAdapter> = listOf(SonyTandemHeadphoneAdapter, QcyHeadphoneAdapter)

    fun resolve(device: DiscoveredDevice, reportedModelName: String? = null): ConnectedHeadphoneProfile {
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

    fun buildRefreshVolumeCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRefreshVolumeCommands(profile)

    fun buildSetVolumeCommands(profile: ConnectedHeadphoneProfile, volume: Int): List<HeadphoneCommand> =
        adapterFor(profile).buildSetVolumeCommands(profile, volume)

    // FRAMEWORK STUBS — ring/find earbud (see HeadphoneFeature.RING docs)
    fun buildRingStartCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRingStartCommands(profile)

    fun buildRingStopCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRingStopCommands(profile)

    fun buildRefreshAudioEffectCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        adapterFor(profile).buildRefreshAudioEffectCommands(profile)

    fun buildSetAudioEffectCommands(profile: ConnectedHeadphoneProfile, enabled: Boolean): List<HeadphoneCommand> =
        adapterFor(profile).buildSetAudioEffectCommands(profile, enabled)

    fun parse(profile: ConnectedHeadphoneProfile, message: IncomingHeadphoneMessage): ParsedHeadphoneResponse =
        adapterFor(profile).parse(profile, message)

    fun parse(profile: ConnectedHeadphoneProfile, raw: ByteArray): ParsedHeadphoneResponse =
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
