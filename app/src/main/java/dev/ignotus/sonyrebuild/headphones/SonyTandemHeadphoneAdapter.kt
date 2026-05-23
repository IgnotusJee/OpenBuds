package dev.ignotus.sonyrebuild.headphones

import dev.ignotus.sonyrebuild.ble.DiscoveredSonyDevice
import dev.ignotus.sonyrebuild.protocol.AmbientSoundMode
import dev.ignotus.sonyrebuild.protocol.CommonInquiredType
import dev.ignotus.sonyrebuild.protocol.DeviceInfoType
import dev.ignotus.sonyrebuild.protocol.EqEbbInquiredType
import dev.ignotus.sonyrebuild.protocol.EqPresetId
import dev.ignotus.sonyrebuild.protocol.LeaInquiredType
import dev.ignotus.sonyrebuild.protocol.NcAsmInquiredType
import dev.ignotus.sonyrebuild.protocol.NoiseControlMode
import dev.ignotus.sonyrebuild.protocol.ParsedTandemResponse
import dev.ignotus.sonyrebuild.protocol.PlaybackControl
import dev.ignotus.sonyrebuild.protocol.PowerInquiredType
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.DATA_MDR
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.DATA_MDR_NO2

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
    private const val NCASM_GET_STATUS: Byte = 0x62
    private const val NCASM_RET_STATUS: Byte = 0x63
    private const val NCASM_NTFY_STATUS: Byte = 0x65
    private const val NCASM_GET_PARAM: Byte = 0x66
    private const val NCASM_RET_PARAM: Byte = 0x67
    private const val NCASM_SET_PARAM: Byte = 0x68
    private const val NCASM_NTFY_PARAM: Byte = 0x69
    override val id: String = "sony-tandem"
    override val brand: String = "Sony"
    override val protocolName: String = "Sony Tandem"

    val legacyIds: Set<String> = setOf("sony-tandem-v2")

    private val commonFeatures = setOf(
        HeadphoneFeature.DEVICE_INFO,
        HeadphoneFeature.BATTERY,
        HeadphoneFeature.NOISE_CONTROL,
        HeadphoneFeature.AMBIENT_LEVEL,
        HeadphoneFeature.AMBIENT_VOICE_MODE,
        HeadphoneFeature.PLAYBACK_CONTROL,
        HeadphoneFeature.EQ,
        HeadphoneFeature.CLEAR_BASS,
        HeadphoneFeature.LEA_STATUS,
        HeadphoneFeature.QUICK_ACCESS,
        HeadphoneFeature.WEARING_STATUS,
    )

    private val linkBudsS = ProfileTemplate(
        modelName = "LinkBuds S",
        series = "LINK_BUDS",
        capabilities = HeadphoneCapabilities(
            features = commonFeatures,
            formFactor = HeadphoneFormFactor.TRUE_WIRELESS,
            batteryQueries = listOf(
                PowerInquiredType.LEFT_RIGHT_BATTERY,
                PowerInquiredType.CRADLE_BATTERY,
            ),
            noiseControlQueryTypes = listOf(
                NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS,
            ),
            writableNoiseControlTypes = setOf(
                NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS,
            ),
            eqConfig = EqDeviceConfig(
                availablePresets = listOf(
                    EqPresetId.OFF, EqPresetId.BRIGHT, EqPresetId.EXCITED,
                    EqPresetId.MELLOW, EqPresetId.RELAXED, EqPresetId.VOCAL,
                    EqPresetId.TREBLE, EqPresetId.BASS, EqPresetId.SPEECH,
                    EqPresetId.CUSTOM, EqPresetId.USER_SETTING1, EqPresetId.USER_SETTING2,
                ),
                writeInquiredType = EqEbbInquiredType.PRESET_EQ,
                includeBandsOnPresetWrite = false,
                statusQueryTypes = listOf(EqEbbInquiredType.PRESET_EQ, EqEbbInquiredType.CUSTOM_EQ, EqEbbInquiredType.EBB),
                paramQueryTypes = listOf(EqEbbInquiredType.PRESET_EQ, EqEbbInquiredType.EBB),
                bandCount = 6,
                hasClearBass = true,
            ),
        ),
    )

    private val wh1000xm4 = ProfileTemplate(
        modelName = "WH-1000XM4",
        series = "PREMIUM",
        capabilities = HeadphoneCapabilities(
            features = commonFeatures,
            formFactor = HeadphoneFormFactor.HEADSET,
            batteryQueries = listOf(PowerInquiredType.BATTERY),
            noiseControlQueryTypes = listOf(
                NcAsmInquiredType.V1_TABLE_SET1_NC_ASM,
            ),
            writableNoiseControlTypes = setOf(
                NcAsmInquiredType.V1_TABLE_SET1_NC_ASM,
            ),
            eqConfig = EqDeviceConfig(
                availablePresets = listOf(
                    EqPresetId.OFF, EqPresetId.BRIGHT, EqPresetId.EXCITED,
                    EqPresetId.MELLOW, EqPresetId.RELAXED, EqPresetId.VOCAL,
                    EqPresetId.TREBLE, EqPresetId.BASS, EqPresetId.SPEECH,
                    EqPresetId.CUSTOM, EqPresetId.USER_SETTING1, EqPresetId.USER_SETTING2,
                ),
                writeInquiredType = EqEbbInquiredType.EBB,
                includeBandsOnPresetWrite = true,
                statusQueryTypes = listOf(EqEbbInquiredType.PRESET_EQ, EqEbbInquiredType.CUSTOM_EQ, EqEbbInquiredType.EBB),
                paramQueryTypes = listOf(EqEbbInquiredType.EBB),
                bandCount = 6,
                hasClearBass = true,
            ),
            queryProtocolInfo = false,
            queryNoiseControlParams = true,
        ),
    )

    private val templates = listOf(wh1000xm4, linkBudsS)

    private fun command(
        profile: ConnectedHeadphoneProfile,
        feature: HeadphoneFeature,
        label: String,
        bytes: ByteArray,
    ): HeadphoneCommand =
        HeadphoneCommand(label = label, bytes = bytes, channel = profile.channelFor(feature))

    override fun match(
        device: DiscoveredSonyDevice,
        reportedModelName: String?,
    ): ConnectedHeadphoneProfile? {
        return templates.firstOrNull { template ->
            matchTemplate(template, device, reportedModelName) != null
        }?.let { template ->
            matchTemplate(template, device, reportedModelName)
        }
    }

    override fun fallbackProfile(device: DiscoveredSonyDevice): ConnectedHeadphoneProfile =
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
                    includeBandsOnPresetWrite = false,
                    statusQueryTypes = emptyList(),
                    paramQueryTypes = emptyList(),
                    bandCount = 0,
                    hasClearBass = false,
                ),
            ),
            knownStaticProfile = false,
        ).toProfile(id, brand, protocolName, device.name)

    override fun buildRefreshCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        buildList {
            if (profile.capabilities.queryProtocolInfo) {
                add(command(profile, HeadphoneFeature.DEVICE_INFO, "GET protocol info", SonyTandemV2Table1Codec.buildGetProtocolInfo()))
            }
            if (profile.supports(HeadphoneFeature.DEVICE_INFO)) {
                DeviceInfoType.entries.forEach {
                    add(command(profile, HeadphoneFeature.DEVICE_INFO, "GET device info $it", SonyTandemV2Table1Codec.buildGetDeviceInfo(it)))
                }
                add(command(profile, HeadphoneFeature.DEVICE_INFO, "GET display firmware version", SonyTandemV2Table1Codec.buildGetDisplayFirmwareVersion()))
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
            if (profile.supports(HeadphoneFeature.LEA_STATUS)) {
                addAll(buildRefreshLeaCommands(profile))
            }
            if (profile.supports(HeadphoneFeature.QUICK_ACCESS)) {
                add(command(profile, HeadphoneFeature.QUICK_ACCESS, "GET Quick Access", SonyTandemV2Table1Codec.buildGetQuickAccess()))
            }
            if (profile.supports(HeadphoneFeature.WEARING_STATUS)) {
                add(command(profile, HeadphoneFeature.WEARING_STATUS, "GET Wearing status", SonyTandemV2Table1Codec.buildGetWearingStatus()))
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
            HeadphoneFeature.PLAYBACK_CONTROL ->
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

        // V1 profile: use V1 TableSet1 builder — protocolFor is the single source of truth
        if (profile.protocolFor(HeadphoneFeature.NOISE_CONTROL) == HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1) {
            return listOf(
                command(
                    profile,
                    HeadphoneFeature.NOISE_CONTROL,
                    "SET NC/ASM V1 table1 mode $mode level=$level voice=${ambientMode == AmbientSoundMode.VOICE}",
                    SonyTandemV1Table1Codec.buildSetNoiseControlMode(mode, level, ambientMode),
                )
            )
        }

        // V2 profile: select builder based on capability subtype
        return if (NcAsmInquiredType.NC_MODE_SWITCH_AND_ASM_SEAMLESS in profile.capabilities.writableNoiseControlTypes) {
            listOf(
                command(
                    profile,
                    HeadphoneFeature.NOISE_CONTROL,
                    "SET NC/ASM 0x14 mode $mode level=$level voice=${ambientMode == AmbientSoundMode.VOICE}",
                    SonyTandemV2Table1Codec.buildSetNcModeSwitchAndAmbientLevel(mode, level, ambientMode),
                )
            )
        } else if (NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS in
            profile.capabilities.writableNoiseControlTypes
        ) {
            listOf(
                command(
                    profile,
                    HeadphoneFeature.NOISE_CONTROL,
                    "SET NC/ASM mode $mode level=$level voice=${ambientMode == AmbientSoundMode.VOICE}",
                    SonyTandemV2Table1Codec.buildSetNoiseControlMode(mode, level, ambientMode),
                )
            )
        } else {
            when (mode) {
                NoiseControlMode.NOISE_CANCELLING -> listOf(
                    command(profile, HeadphoneFeature.NOISE_CONTROL, "SET NC on", SonyTandemV2Table1Codec.buildSetNcOnOff(true)),
                )
                NoiseControlMode.AMBIENT_SOUND -> listOf(
                    command(
                        profile,
                        HeadphoneFeature.NOISE_CONTROL,
                        "SET ASM level $level voice=${ambientMode == AmbientSoundMode.VOICE}",
                        SonyTandemV2Table1Codec.buildSetAmbientLevel(level, enabled = true, mode = ambientMode),
                    ),
                )
                NoiseControlMode.OFF -> listOf(
                    command(profile, HeadphoneFeature.NOISE_CONTROL, "SET NC off", SonyTandemV2Table1Codec.buildSetNcOnOff(false)),
                    command(profile, HeadphoneFeature.NOISE_CONTROL, "SET ASM off", SonyTandemV2Table1Codec.buildSetAmbientSound(false, ambientMode)),
                )
            }
        }
    }

    override fun buildSetEqPresetCommands(
        profile: ConnectedHeadphoneProfile,
        preset: EqPresetId,
        context: EqWriteContext,
    ): List<HeadphoneCommand> {
        val engine = EqProtocolEngine(profile.capabilities.eqConfig)
        return listOf(
            command(
                profile,
                HeadphoneFeature.EQ,
                "SET EQ preset ${preset.name} type=${profile.capabilities.eqConfig.writeInquiredType}",
                engine.buildSetPreset(preset, context.rawBandSteps),
            )
        )
    }

    override fun buildSetEqBandCommands(
        profile: ConnectedHeadphoneProfile,
        rawSteps: List<Int>,
        preset: EqPresetId?,
        context: EqWriteContext,
    ): List<HeadphoneCommand> {
        val engine = EqProtocolEngine(profile.capabilities.eqConfig)
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
        val engine = EqProtocolEngine(profile.capabilities.eqConfig)
        return listOf(
            command(
                profile,
                HeadphoneFeature.CLEAR_BASS,
                "SET Clear Bass $level",
                engine.buildSetClearBass(level),
            )
        )
    }

    override fun buildRefreshNoiseControlCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        when (profile.protocolFor(HeadphoneFeature.NOISE_CONTROL)) {
            HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1 -> {
                listOf(
                    command(
                        profile,
                        HeadphoneFeature.NOISE_CONTROL,
                        "GET NC/ASM param V1",
                        SonyTandemV1Table1Codec.buildGetNcAsmParam(),
                    )
                )
            }
            else -> {
                profile.capabilities.noiseControlQueryTypes.map {
                    if (profile.capabilities.queryNoiseControlParams) {
                        command(profile, HeadphoneFeature.NOISE_CONTROL, "GET NC/ASM param $it", SonyTandemV2Table1Codec.buildGetNcAsmParam(it))
                    } else {
                        command(profile, HeadphoneFeature.NOISE_CONTROL, "GET NC/ASM status $it", SonyTandemV2Table1Codec.buildGetNcAsmStatus(it))
                    }
                }
            }
        }

    override fun buildRefreshEqCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> {
        val engine = EqProtocolEngine(profile.capabilities.eqConfig)
        return engine.buildRefreshCommands { label, bytes ->
            command(profile, HeadphoneFeature.EQ, label, bytes)
        }
    }

    override fun buildRefreshBatteryCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        if (!profile.supports(HeadphoneFeature.BATTERY)) {
            emptyList()
        } else {
            profile.capabilities.batteryQueries.map {
                val bytes = when (profile.protocolFor(HeadphoneFeature.BATTERY)) {
                    HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1 -> SonyTandemV1Table1Codec.buildGetBatteryStatus(it)
                    else -> SonyTandemV2Table1Codec.buildGetBatteryStatus(it)
                }
                command(profile, HeadphoneFeature.BATTERY, "GET battery $it", bytes)
            }
        }

    override fun buildRefreshPlaybackCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        if (profile.supports(HeadphoneFeature.PLAYBACK_CONTROL)) {
            listOf(command(profile, HeadphoneFeature.PLAYBACK_CONTROL, "GET playback status", SonyTandemV2Table1Codec.buildGetPlaybackStatus()))
        } else {
            emptyList()
        }

    private fun buildRefreshLeaCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        LeaInquiredType.entries.flatMap { type ->
            listOf(
                command(profile, HeadphoneFeature.LEA_STATUS, "GET LEA status $type", SonyTandemV2Table1Codec.buildGetLeaStatus(type)),
                command(profile, HeadphoneFeature.LEA_STATUS, "GET LEA paired history $type", SonyTandemV2Table1Codec.buildGetLeaPairedHistory(type)),
            )
        }

    override fun buildPlaybackCommands(profile: ConnectedHeadphoneProfile, control: PlaybackControl): List<HeadphoneCommand> =
        if (profile.supports(HeadphoneFeature.PLAYBACK_CONTROL)) {
            listOf(command(profile, HeadphoneFeature.PLAYBACK_CONTROL, "PLAYBACK ${control.name}", SonyTandemV2Table1Codec.buildPlayback(control)))
        } else {
            emptyList()
        }

    override fun parse(
        profile: ConnectedHeadphoneProfile,
        channel: TandemChannel,
        raw: ByteArray,
    ): ParsedTandemResponse {
        if (raw.firstOrNull() == DATA_MDR_NO2) {
            return SonyTandemV2Table2Codec.parse(raw)
        }
        val normalized = if (raw.firstOrNull() == DATA_MDR) raw else byteArrayOf(DATA_MDR) + raw
        val command = normalized.getOrNull(1) ?: return ParsedTandemResponse.Unknown(null, null, byteArrayOf(), raw)
        val payload = normalized.drop(2).toByteArray()
        val binding = bindingForResponse(profile, channel, command, payload)
        return TandemCodecRegistry.codecFor(binding.variant).parse(raw).let { parsed ->
            if (parsed !is ParsedTandemResponse.Unknown) {
                parsed
            } else if (binding.feature == HeadphoneFeature.DEVICE_INFO &&
                binding.variant != HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1
            ) {
                SonyTandemV2Table1Codec.parse(raw)
            } else {
                parsed
            }
        }
    }

    private fun bindingForResponse(
        profile: ConnectedHeadphoneProfile,
        channel: TandemChannel,
        command: Byte,
        payload: ByteArray,
    ): FeatureProtocolBinding {
        if (channel == TandemChannel.GATT_V2_MC) {
            return FeatureProtocolBinding(
                feature = HeadphoneFeature.DEVICE_INFO,
                variant = HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE2,
                channel = channel,
            )
        }
        if (channel == TandemChannel.GATT_V1_MC && isV1Table2Command(command)) {
            return FeatureProtocolBinding(
                feature = HeadphoneFeature.DEVICE_INFO,
                variant = HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE2,
                channel = channel,
            )
        }
        val feature = classifyCommand(command, payload)
        return profile.bindingFor(feature) ?: FeatureProtocolBinding(
            feature = feature,
            variant = HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1,
            channel = TandemChannel.GATT_V2_HPC,
        )
    }

    private fun isV1Table2Command(command: Byte): Boolean {
        val code = command.toInt() and 0xFF
        return code in 0x30..0x49
    }

    private fun classifyCommand(command: Byte, payload: ByteArray = byteArrayOf()): HeadphoneFeature = when (command) {
        COMMON_RET_BATTERY_LEVEL -> HeadphoneFeature.BATTERY
        COMMON_NTFY_BATTERY_LEVEL -> classify0x13(payload)
        POWER_RET_STATUS, POWER_NTFY_STATUS -> HeadphoneFeature.BATTERY
        EQEBB_GET_STATUS, EQEBB_RET_STATUS, EQEBB_NTFY_STATUS,
        EQEBB_GET_PARAM, EQEBB_RET_PARAM, EQEBB_SET_PARAM,
        EQEBB_NTFY_PARAM -> HeadphoneFeature.EQ
        NCASM_GET_STATUS, NCASM_RET_STATUS, NCASM_NTFY_STATUS,
        NCASM_GET_PARAM, NCASM_RET_PARAM, NCASM_SET_PARAM,
        NCASM_NTFY_PARAM -> HeadphoneFeature.NOISE_CONTROL
        else -> HeadphoneFeature.DEVICE_INFO
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
