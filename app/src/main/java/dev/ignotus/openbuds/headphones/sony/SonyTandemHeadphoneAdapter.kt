package dev.ignotus.openbuds.headphones.sony

import android.content.Context
import dev.ignotus.openbuds.ble.HeadphoneTransportClient
import dev.ignotus.openbuds.ble.HeadphoneTransportListener
import dev.ignotus.openbuds.ble.IncomingHeadphoneMessage
import dev.ignotus.openbuds.ble.DiscoveredDevice
import dev.ignotus.openbuds.ble.sony.SonyChannel
import dev.ignotus.openbuds.ble.sony.SonyTandemTransportClient
import dev.ignotus.openbuds.headphones.ClearBassWriteMode
import dev.ignotus.openbuds.headphones.ConnectedHeadphoneProfile
import dev.ignotus.openbuds.headphones.EqDeviceConfig
import dev.ignotus.openbuds.headphones.sony.EqProtocolEngine
import dev.ignotus.openbuds.headphones.EqWriteContext
import dev.ignotus.openbuds.headphones.FeatureProtocolBinding
import dev.ignotus.openbuds.headphones.HeadphoneAdapter
import dev.ignotus.openbuds.headphones.HeadphoneCapabilities
import dev.ignotus.openbuds.headphones.HeadphoneCommand
import dev.ignotus.openbuds.headphones.HeadphoneFeature
import dev.ignotus.openbuds.headphones.HeadphoneFormFactor
import dev.ignotus.openbuds.headphones.HeadphoneProtocolVariant
import dev.ignotus.openbuds.headphones.ProfileTemplate
import dev.ignotus.openbuds.headphones.sony.devices.LinkBudsSProfile
import dev.ignotus.openbuds.headphones.sony.devices.Wf1000Xm5Profile
import dev.ignotus.openbuds.headphones.sony.devices.Wh1000Xm4Profile
import dev.ignotus.openbuds.protocol.AmbientSoundMode
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dev.ignotus.openbuds.protocol.ParsedHeadphoneResponse
import dev.ignotus.openbuds.protocol.PlaybackStatus
import dev.ignotus.openbuds.protocol.sony.CommonInquiredType
import dev.ignotus.openbuds.protocol.sony.DeviceInfoType
import dev.ignotus.openbuds.protocol.sony.EqEbbInquiredType
import dev.ignotus.openbuds.protocol.sony.LeaInquiredType
import dev.ignotus.openbuds.protocol.sony.NcAsmInquiredType
import dev.ignotus.openbuds.protocol.sony.PlaybackControl
import dev.ignotus.openbuds.protocol.sony.PlayInquiredType
import dev.ignotus.openbuds.protocol.sony.PowerInquiredType
import dev.ignotus.openbuds.protocol.sony.SonyTandemConstants.DATA_MDR
import dev.ignotus.openbuds.protocol.sony.SonyTandemConstants.DATA_MDR_NO2

object SonyTandemHeadphoneAdapter : HeadphoneAdapter {
    private const val COMMON_RET_BATTERY_LEVEL: Byte = 0x11
    private const val COMMON_NTFY_BATTERY_LEVEL: Byte = 0x13
    private const val POWER_RET_STATUS: Byte = 0x23
    private const val POWER_NTFY_STATUS: Byte = 0x25
    private const val EQEBB_GET_STATUS: Byte = 0x52
    private const val EQEBB_RET_STATUS: Byte = 0x53
    private const val EQEBB_NTFY_STATUS: Byte = 0x55
    private const val EQEBB_GET_PARAM: Byte = 0x56
    private const val EQEBB_RET_PARAM: Byte = 0x57
    private const val EQEBB_SET_PARAM: Byte = 0x58
    private const val EQEBB_NTFY_PARAM: Byte = 0x59
    private const val EQEBB_GET_EXTENDED_INFO: Byte = 0x5A
    private const val EQEBB_RET_EXTENDED_INFO: Byte = 0x5B
    private const val NCASM_GET_STATUS: Byte = 0x62
    private const val NCASM_RET_STATUS: Byte = 0x63
    private const val NCASM_NTFY_STATUS: Byte = 0x65
    private const val NCASM_GET_PARAM: Byte = 0x66
    private const val NCASM_RET_PARAM: Byte = 0x67
    private const val NCASM_SET_PARAM: Byte = 0x68
    private const val NCASM_NTFY_PARAM: Byte = 0x69
    private const val PLAY_RET_STATUS: Byte = 0xA3.toByte()
    private const val PLAY_NTFY_STATUS: Byte = 0xA5.toByte()
    private const val PLAY_RET_PARAM: Byte = 0xA7.toByte()
    private const val PLAY_NTFY_PARAM: Byte = 0xA9.toByte()
    private const val AUDIO_RET_PARAM: Byte = 0xE7.toByte()
    private const val AUDIO_NTFY_PARAM: Byte = 0xE9.toByte()
    override val id: String = "sony-tandem"
    override val brand: String = "Sony"
    override val protocolName: String = "Sony Tandem"

    val legacyIds: Set<String> = setOf("sony-tandem-v2")

    private val templates = listOf(Wh1000Xm4Profile.template, LinkBudsSProfile.template, Wf1000Xm5Profile.template)

    fun createTransportClient(context: Context, listener: HeadphoneTransportListener): HeadphoneTransportClient =
        SonyTandemTransportClient(context, listener)

    private fun command(
        profile: ConnectedHeadphoneProfile,
        feature: HeadphoneFeature,
        label: String,
        bytes: ByteArray,
    ): HeadphoneCommand =
        HeadphoneCommand(label = label, bytes = bytes)

    private fun codecFor(profile: ConnectedHeadphoneProfile, feature: HeadphoneFeature): TandemCodec =
        TandemCodecRegistry.codecFor(profile.protocolFor(feature))

    override fun match(
        device: DiscoveredDevice,
        reportedModelName: String?,
    ): ConnectedHeadphoneProfile? {
        return templates.firstOrNull { template ->
            matchTemplate(template, device, reportedModelName) != null
        }?.let { template ->
            matchTemplate(template, device, reportedModelName)
        }
    }

    override fun fallbackProfile(device: DiscoveredDevice): ConnectedHeadphoneProfile =
        ProfileTemplate(
            modelName = device.name.removePrefix("LE_").takeIf { it.isNotBlank() } ?: "Sony audio device",
            series = null,
            capabilities = HeadphoneCapabilities(
                features = setOf(HeadphoneFeature.DEVICE_INFO, HeadphoneFeature.BATTERY),
                formFactor = HeadphoneFormFactor.UNKNOWN,
                batteryQueries = listOf(PowerInquiredType.BATTERY),
                noiseControlQueryTypes = emptyList(),
                writableNoiseControlTypes = emptySet(),
                eqConfig = EqDeviceConfig(
                    availablePresets = listOf(EqPresetId.OFF),
                    writeInquiredType = EqEbbInquiredType.PRESET_EQ,
                    statusQueryTypes = emptyList(),
                    paramQueryTypes = emptyList(),
                    bandCount = 0,
                    hasClearBass = false,
                ),
            ),
            featureProtocolMap = mapOf(
                HeadphoneFeature.DEVICE_INFO to HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
                HeadphoneFeature.BATTERY to HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
            ),
            knownStaticProfile = false,
        ).toProfile(id, brand, protocolName, device.name)

    override fun buildRefreshCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        buildList {
            val deviceInfoCodec = codecFor(profile, HeadphoneFeature.DEVICE_INFO)
            if (profile.capabilities.queryProtocolInfo) {
                deviceInfoCodec.buildGetProtocolInfo()?.let {
                    add(command(profile, HeadphoneFeature.DEVICE_INFO, "GET protocol info", it))
                }
            }
            if (profile.supports(HeadphoneFeature.DEVICE_INFO)) {
                DeviceInfoType.entries.forEach {
                    deviceInfoCodec.buildGetDeviceInfo(it)?.let { bytes ->
                        add(command(profile, HeadphoneFeature.DEVICE_INFO, "GET device info $it", bytes))
                    }
                }
                deviceInfoCodec.buildGetDisplayFirmwareVersion()?.let {
                    add(command(profile, HeadphoneFeature.DEVICE_INFO, "GET display firmware version", it))
                }
            }
            addAll(buildRefreshBatteryCommands(profile))
            if (profile.supports(HeadphoneFeature.NOISE_CONTROL)) {
                addAll(buildRefreshNoiseControlCommands(profile))
            }
            if (profile.supports(HeadphoneFeature.EQ)) {
                addAll(buildRefreshEqCommands(profile))
            }
            if (profile.supports(HeadphoneFeature.PLAYBACK_CONTROL)) {
                addAll(buildRefreshPlaybackCommands(profile))
            }
            if (profile.supports(HeadphoneFeature.VOLUME)) {
                addAll(buildRefreshVolumeCommands(profile))
            }
            if (profile.supports(HeadphoneFeature.AUDIO_EFFECT)) {
                addAll(buildRefreshAudioEffectCommands(profile))
            }
            if (profile.supports(HeadphoneFeature.LEA_STATUS)) {
                addAll(buildRefreshLeaCommands(profile))
            }
            if (profile.supports(HeadphoneFeature.QUICK_ACCESS)) {
                codecFor(profile, HeadphoneFeature.QUICK_ACCESS).buildGetQuickAccess()?.let {
                    add(command(profile, HeadphoneFeature.QUICK_ACCESS, "GET Quick Access", it))
                }
            }
            if (profile.supports(HeadphoneFeature.WEARING_STATUS)) {
                codecFor(profile, HeadphoneFeature.WEARING_STATUS).buildGetWearingStatus()?.let {
                    add(command(profile, HeadphoneFeature.WEARING_STATUS, "GET Wearing status", it))
                }
            }
        }

    override fun canWrite(profile: ConnectedHeadphoneProfile, feature: HeadphoneFeature): Boolean =
        when (feature) {
            HeadphoneFeature.NOISE_CONTROL,
            HeadphoneFeature.AMBIENT_LEVEL,
            HeadphoneFeature.AMBIENT_VOICE_MODE ->
                profile.supports(feature) && profile.capabilities.writableNoiseControlTypes.isNotEmpty()
            HeadphoneFeature.EQ,
            HeadphoneFeature.CLEAR_BASS,
            HeadphoneFeature.PLAYBACK_CONTROL,
            HeadphoneFeature.VOLUME,
            HeadphoneFeature.AUDIO_EFFECT ->
                profile.supports(feature) && profile.protocolEvidence.any { it.startsWith("static-profile:") }
            else -> profile.supports(feature)
        }

    override fun buildSetNoiseControlModeCommands(
        profile: ConnectedHeadphoneProfile,
        mode: NoiseControlMode,
        ambientLevel: Int,
        ambientMode: AmbientSoundMode,
    ): List<HeadphoneCommand> {
        val level = ambientLevel.coerceIn(1, 20)
        val codec = codecFor(profile, HeadphoneFeature.NOISE_CONTROL)

        profile.capabilities.writableNoiseControlTypes.forEach { type ->
            val bytes = codec.buildSetNoiseControlMode(type, mode, level, ambientMode) ?: return@forEach
            return listOf(
                command(
                    profile,
                    HeadphoneFeature.NOISE_CONTROL,
                    noiseControlWriteLabel(type, mode, level, ambientMode),
                    bytes,
                )
            )
        }

        return when (mode) {
            NoiseControlMode.NOISE_CANCELLING ->
                codec.buildSetNcOnOff(true)
                    ?.let { listOf(command(profile, HeadphoneFeature.NOISE_CONTROL, "SET NC on", it)) }
                    .orEmpty()
            NoiseControlMode.AMBIENT_SOUND ->
                codec.buildSetAmbientLevel(level, enabled = true, mode = ambientMode)
                    ?.let {
                        listOf(
                            command(
                                profile,
                                HeadphoneFeature.NOISE_CONTROL,
                                "SET ASM level $level voice=${ambientMode == AmbientSoundMode.VOICE}",
                                it,
                            )
                        )
                    }
                    .orEmpty()
            NoiseControlMode.OFF -> listOfNotNull(
                codec.buildSetNcOnOff(false)?.let {
                    command(profile, HeadphoneFeature.NOISE_CONTROL, "SET NC off", it)
                },
                codec.buildSetAmbientSound(false, ambientMode)?.let {
                    command(profile, HeadphoneFeature.NOISE_CONTROL, "SET ASM off", it)
                },
            )
        }
    }

    private fun noiseControlWriteLabel(
        type: NcAsmInquiredType,
        mode: NoiseControlMode,
        level: Int,
        ambientMode: AmbientSoundMode,
    ): String {
        val voice = ambientMode == AmbientSoundMode.VOICE
        return when (type) {
            NcAsmInquiredType.V1_TABLE_SET1_NC_ASM ->
                "SET NC/ASM V1 table1 mode $mode level=$level voice=$voice"
            NcAsmInquiredType.NC_MODE_SWITCH_AND_ASM_SEAMLESS ->
                "SET NC/ASM 0x14 mode $mode level=$level voice=$voice"
            else -> "SET NC/ASM mode $mode level=$level voice=$voice"
        }
    }

    override fun buildSetEqPresetCommands(
        profile: ConnectedHeadphoneProfile,
        preset: EqPresetId,
        context: EqWriteContext,
    ): List<HeadphoneCommand> {
        val engine = EqProtocolEngine(profile.capabilities.eqConfig, codecFor(profile, HeadphoneFeature.EQ))
        return listOf(
            command(
                profile,
                HeadphoneFeature.EQ,
                "SET EQ preset ${preset.name} type=${profile.capabilities.eqConfig.writeInquiredType}",
                engine.buildSetPreset(preset),
            )
        )
    }

    override fun buildSetEqBandCommands(
        profile: ConnectedHeadphoneProfile,
        rawSteps: List<Int>,
        preset: EqPresetId?,
        context: EqWriteContext,
    ): List<HeadphoneCommand> {
        val engine = EqProtocolEngine(profile.capabilities.eqConfig, codecFor(profile, HeadphoneFeature.EQ))
        val targetPreset = preset ?: EqPresetId.CUSTOM
        val writeType = profile.capabilities.eqConfig.writeInquiredType
        return listOf(
            command(
                profile,
                HeadphoneFeature.EQ,
                "SET EQ bands type=$writeType preset=${targetPreset.name}",
                engine.buildSetBands(rawSteps, targetPreset),
            )
        )
    }

    override fun buildSetClearBassCommands(
        profile: ConnectedHeadphoneProfile,
        level: Int,
        context: EqWriteContext,
    ): List<HeadphoneCommand> {
        val engine = EqProtocolEngine(profile.capabilities.eqConfig, codecFor(profile, HeadphoneFeature.CLEAR_BASS))
        return when (profile.capabilities.eqConfig.clearBassWriteMode) {
            ClearBassWriteMode.EBB_PARAM -> listOf(
                command(
                    profile,
                    HeadphoneFeature.CLEAR_BASS,
                    "SET Clear Bass $level",
                    engine.buildSetClearBass(level),
                )
            )
            ClearBassWriteMode.PRESET_EQ_BANDS -> {
                val targetPreset = context.userEqPresetOrDefault()
                val rawSteps = mergedClearBassRawSteps(profile.capabilities.eqConfig, context.rawBandSteps, level)
                listOf(
                    command(
                        profile,
                        HeadphoneFeature.CLEAR_BASS,
                        "SET Clear Bass $level via EQ bands preset=${targetPreset.name}",
                        engine.buildSetBands(rawSteps, targetPreset),
                    )
                )
            }
        }
    }

    private fun EqWriteContext.userEqPresetOrDefault(): EqPresetId =
        when (preset) {
            EqPresetId.CUSTOM,
            EqPresetId.USER_SETTING1,
            EqPresetId.USER_SETTING2 -> preset
            else -> EqPresetId.CUSTOM
        }

    private fun mergedClearBassRawSteps(
        config: EqDeviceConfig,
        currentRawSteps: List<Int>,
        level: Int,
    ): List<Int> {
        val bandCount = config.bandCount.takeIf { it > 0 } ?: currentRawSteps.size.coerceAtLeast(1)
        val rawSteps = if (currentRawSteps.size == bandCount) {
            currentRawSteps.toMutableList()
        } else {
            MutableList(bandCount) { EqProtocolEngine.BAND_STEP_CENTER }
        }
        rawSteps[0] = clearBassDisplayStepToRaw(level)
        return rawSteps
    }

    private fun clearBassDisplayStepToRaw(level: Int): Int =
        (level.coerceIn(-10, 10) + EqProtocolEngine.BAND_STEP_CENTER).coerceIn(0, 255)

    override fun buildRefreshNoiseControlCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        buildList {
            val codec = codecFor(profile, HeadphoneFeature.NOISE_CONTROL)
            profile.capabilities.noiseControlQueryTypes.forEach { type ->
                val bytes = if (profile.capabilities.queryNoiseControlParams) {
                    codec.buildGetNcAsmParam(type)
                } else {
                    codec.buildGetNcAsmStatus(type)
                }
                bytes?.let {
                    val label = if (type == NcAsmInquiredType.V1_TABLE_SET1_NC_ASM) {
                        "GET NC/ASM param V1"
                    } else if (profile.capabilities.queryNoiseControlParams) {
                        "GET NC/ASM param $type"
                    } else {
                        "GET NC/ASM status $type"
                    }
                    add(command(profile, HeadphoneFeature.NOISE_CONTROL, label, it))
                }
            }
        }

    override fun buildRefreshEqCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> {
        val engine = EqProtocolEngine(profile.capabilities.eqConfig, codecFor(profile, HeadphoneFeature.EQ))
        return engine.buildRefreshCommands { label, bytes ->
            command(profile, HeadphoneFeature.EQ, label, bytes)
        }
    }

    override fun buildRefreshBatteryCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        if (!profile.supports(HeadphoneFeature.BATTERY)) {
            emptyList()
        } else {
            val codec = codecFor(profile, HeadphoneFeature.BATTERY)
            profile.capabilities.batteryQueries.mapNotNull {
                codec.buildGetBatteryStatus(it)?.let { bytes ->
                    command(profile, HeadphoneFeature.BATTERY, "GET battery $it", bytes)
                }
            }
        }

    override fun buildRefreshPlaybackCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        codecFor(profile, HeadphoneFeature.PLAYBACK_CONTROL)
            .buildGetPlaybackStatus(profile.capabilities.playbackControlType)
            ?.let { listOf(command(profile, HeadphoneFeature.PLAYBACK_CONTROL, "GET playback status", it)) }
            .orEmpty()

    private fun buildRefreshLeaCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        LeaInquiredType.entries.flatMap { type ->
            val codec = codecFor(profile, HeadphoneFeature.LEA_STATUS)
            listOf(
                codec.buildGetLeaStatus(type)?.let {
                    command(profile, HeadphoneFeature.LEA_STATUS, "GET LEA status $type", it)
                },
                codec.buildGetLeaPairedHistory(type)?.let {
                    command(profile, HeadphoneFeature.LEA_STATUS, "GET LEA paired history $type", it)
                },
            ).filterNotNull()
        }

    override fun buildPlaybackCommands(profile: ConnectedHeadphoneProfile, control: PlaybackControl): List<HeadphoneCommand> =
        codecFor(profile, HeadphoneFeature.PLAYBACK_CONTROL)
            .buildPlayback(control, profile.capabilities.playbackControlType)
            ?.let { listOf(command(profile, HeadphoneFeature.PLAYBACK_CONTROL, "PLAYBACK ${control.name}", it)) }
            .orEmpty()

    override fun buildRefreshVolumeCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        codecFor(profile, HeadphoneFeature.VOLUME)
            .buildGetMusicVolume()
            ?.let { listOf(command(profile, HeadphoneFeature.VOLUME, "GET music volume", it)) }
            .orEmpty()

    override fun buildSetVolumeCommands(profile: ConnectedHeadphoneProfile, volume: Int): List<HeadphoneCommand> =
        codecFor(profile, HeadphoneFeature.VOLUME)
            .buildSetMusicVolume(volume)
            ?.let { listOf(command(profile, HeadphoneFeature.VOLUME, "SET music volume $volume", it)) }
            .orEmpty()

    override fun buildRefreshAudioEffectCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        codecFor(profile, HeadphoneFeature.AUDIO_EFFECT)
            .buildGetAudioEffect()
            ?.let { listOf(command(profile, HeadphoneFeature.AUDIO_EFFECT, "GET audio effect", it)) }
            .orEmpty()

    override fun buildSetAudioEffectCommands(profile: ConnectedHeadphoneProfile, enabled: Boolean): List<HeadphoneCommand> =
        codecFor(profile, HeadphoneFeature.AUDIO_EFFECT)
            .buildSetAudioEffect(enabled)
            ?.let { listOf(command(profile, HeadphoneFeature.AUDIO_EFFECT, "SET audio effect $enabled", it)) }
            .orEmpty()

    override fun parse(
        profile: ConnectedHeadphoneProfile,
        message: IncomingHeadphoneMessage,
    ): ParsedHeadphoneResponse {
        val raw = message.raw
        val s = SonyChannel.fromSourceKey(message.sourceKey) ?: defaultSonyResponseChannel(profile)
        if (raw.firstOrNull() == DATA_MDR_NO2) {
            val variant = table2VariantForResponse(profile, s)
            return parseDataMdrNo2(variant, raw)
        }
        val normalized = if (raw.firstOrNull() == DATA_MDR) raw else byteArrayOf(DATA_MDR) + raw
        val command = normalized.getOrNull(1) ?: return ParsedHeadphoneResponse.SonyTandem.Unknown(null, null, byteArrayOf(), raw)
        val payload = normalized.drop(2).toByteArray()
        val binding = bindingForResponse(profile, s, command, payload)
        return TandemCodecRegistry.codecFor(binding.variant).parse(raw).let { parsed ->
            if (parsed !is ParsedHeadphoneResponse.SonyTandem.Unknown) {
                parsed
            } else if (
                binding.feature == HeadphoneFeature.DEVICE_INFO &&
                    profile.protocolFor(HeadphoneFeature.DEVICE_INFO) == HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1
            ) {
                codecFor(profile, HeadphoneFeature.DEVICE_INFO).parse(raw)
            } else {
                parsed
            }
        }
    }

    private fun parseDataMdrNo2(
        variant: HeadphoneProtocolVariant,
        raw: ByteArray,
    ): ParsedHeadphoneResponse {
        if (variant != HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE2) {
            return TandemCodecRegistry.codecFor(variant).parse(raw)
        }
        val normalized = byteArrayOf(DATA_MDR) + raw.drop(1).toByteArray()
        return when (val parsed = TandemCodecRegistry.codecFor(variant).parse(normalized)) {
            is ParsedHeadphoneResponse.SonyTandem.Table2Generic -> parsed.copy(raw = raw)
            is ParsedHeadphoneResponse.SonyTandem.Table2Common -> parsed.copy(raw = raw)
            is ParsedHeadphoneResponse.SonyTandem.Unknown -> parsed.copy(raw = raw)
            else -> parsed
        }
    }

    private fun defaultSonyResponseChannel(profile: ConnectedHeadphoneProfile): SonyChannel {
        val variants = profile.featureBindings.values.map { it.variant }.toSet()
        return when {
            variants.any { it == HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1 } -> SonyChannel.GATT_V2_HPC
            variants.any {
                it == HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1 ||
                    it == HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE2
            } -> SonyChannel.GATT_V1_MC
            variants.any { it == HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE2 } -> SonyChannel.GATT_V2_MC
            else -> SonyChannel.SPP_MDR
        }
    }

    private fun table2VariantForResponse(
        profile: ConnectedHeadphoneProfile,
        channel: SonyChannel,
    ): HeadphoneProtocolVariant =
        when (channel) {
            SonyChannel.GATT_V2_MC -> HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE2
            SonyChannel.GATT_V1_MC -> HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE2
            SonyChannel.GATT_V2_HPC,
            SonyChannel.SPP_MDR -> {
                val profileVariants = profile.featureBindings.values.map { it.variant }.toSet()
                when {
                    profileVariants.any { it == HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1 || it == HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE2 } ->
                        HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE2
                    profileVariants.any { it == HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1 || it == HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE2 } ->
                        HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE2
                    else -> HeadphoneProtocolVariant.UNKNOWN
                }
            }
        }

    private fun bindingForResponse(
        profile: ConnectedHeadphoneProfile,
        channel: SonyChannel,
        command: Byte,
        payload: ByteArray,
    ): FeatureProtocolBinding {
        if (channel == SonyChannel.GATT_V2_MC) {
            return FeatureProtocolBinding(
                feature = HeadphoneFeature.DEVICE_INFO,
                variant = HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE2,
            )
        }
        if (channel == SonyChannel.GATT_V1_MC && isV1Table2Command(command)) {
            return FeatureProtocolBinding(
                feature = HeadphoneFeature.DEVICE_INFO,
                variant = HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE2,
            )
        }
        val feature = classifyCommand(command, payload)
        return profile.bindingFor(feature) ?: FeatureProtocolBinding(
            feature = feature,
            variant = variantForChannel(channel, command),
        )
    }

    private fun isV1Table2Command(command: Byte): Boolean {
        val code = command.toInt() and 0xFF
        return code in 0x30..0x49
    }

    private fun variantForChannel(channel: SonyChannel, command: Byte): HeadphoneProtocolVariant =
        when (channel) {
            SonyChannel.GATT_V2_HPC -> HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1
            SonyChannel.GATT_V2_MC -> HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE2
            SonyChannel.GATT_V1_MC -> if (isV1Table2Command(command)) {
                HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE2
            } else {
                HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1
            }
            SonyChannel.SPP_MDR -> HeadphoneProtocolVariant.UNKNOWN
        }

    private fun classifyCommand(command: Byte, payload: ByteArray = byteArrayOf()): HeadphoneFeature = when (command) {
        COMMON_RET_BATTERY_LEVEL -> HeadphoneFeature.BATTERY
        COMMON_NTFY_BATTERY_LEVEL -> classify0x13(payload)
        POWER_RET_STATUS, POWER_NTFY_STATUS -> HeadphoneFeature.BATTERY
        EQEBB_GET_STATUS, EQEBB_RET_STATUS, EQEBB_NTFY_STATUS,
        EQEBB_GET_PARAM, EQEBB_RET_PARAM, EQEBB_SET_PARAM,
        EQEBB_NTFY_PARAM, EQEBB_GET_EXTENDED_INFO,
        EQEBB_RET_EXTENDED_INFO -> HeadphoneFeature.EQ
        NCASM_GET_STATUS, NCASM_RET_STATUS, NCASM_NTFY_STATUS,
        NCASM_GET_PARAM, NCASM_RET_PARAM, NCASM_SET_PARAM,
        NCASM_NTFY_PARAM -> HeadphoneFeature.NOISE_CONTROL
        PLAY_RET_STATUS, PLAY_NTFY_STATUS -> HeadphoneFeature.PLAYBACK_CONTROL
        PLAY_RET_PARAM, PLAY_NTFY_PARAM -> classifyPlayParam(payload)
        AUDIO_RET_PARAM, AUDIO_NTFY_PARAM -> HeadphoneFeature.AUDIO_EFFECT
        else -> HeadphoneFeature.DEVICE_INFO
    }

    private fun classifyPlayParam(payload: ByteArray): HeadphoneFeature {
        val inquiredType = payload.firstOrNull()?.let { code ->
            PlayInquiredType.entries.firstOrNull { it.code == code }
        }
        return when (inquiredType) {
            PlayInquiredType.MUSIC_VOLUME,
            PlayInquiredType.CALL_VOLUME,
            PlayInquiredType.MUSIC_VOLUME_WITH_MUTE,
            PlayInquiredType.CALL_VOLUME_WITH_MUTE -> HeadphoneFeature.VOLUME
            else -> HeadphoneFeature.PLAYBACK_CONTROL
        }
    }

    /**
     * 0x13 is overloaded: V2 COMMON_RET_STATUS and V1 COMMON_NTFY_BATTERY_LEVEL.
     * CommonInquiredType codes 0x00-0x06 overlap with PowerInquiredType codes.
     * Non-overlapping codes are routed directly; for overlapping codes we inspect
     * payload shape to decide whether it looks like a V1 battery response.
     */
    private fun classify0x13(payload: ByteArray): HeadphoneFeature {
        val firstPayloadByte = payload.firstOrNull() ?: return HeadphoneFeature.DEVICE_INFO
        val isCommonType = CommonInquiredType.entries.any { it.code == firstPayloadByte }
        val isPowerType = PowerInquiredType.entries.any { it.code == firstPayloadByte }

        return when {
            isCommonType && !isPowerType -> HeadphoneFeature.DEVICE_INFO
            isPowerType && !isCommonType -> HeadphoneFeature.BATTERY
            isPowerType && isCommonType -> {
                if (looksLikeV1BatteryPayload(payload)) HeadphoneFeature.BATTERY
                else HeadphoneFeature.DEVICE_INFO
            }
            else -> HeadphoneFeature.DEVICE_INFO
        }
    }

    private fun looksLikeV1BatteryPayload(payload: ByteArray): Boolean {
        val type = payload.firstOrNull()?.toInt()?.and(0xFF) ?: return false
        return when (type) {
            0x00, 0x02 -> {
                payload.size == 2 && payload[1].isBatteryPercentage()
            }
            0x01 -> {
                payload.size == 4 &&
                    payload[1].isBatteryPercentage() &&
                    payload[2].toInt().and(0xFF) == 0x00 &&
                    payload[3].isBatteryPercentage()
            }
            0x04, 0x06 -> false
            else -> false
        }
    }

    private fun Byte.isBatteryPercentage(): Boolean {
        val v = this.toInt() and 0xFF
        return v in 0..100 || v == 0xFF
    }

}
