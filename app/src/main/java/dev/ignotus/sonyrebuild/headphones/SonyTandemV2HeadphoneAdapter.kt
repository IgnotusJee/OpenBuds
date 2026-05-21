package dev.ignotus.sonyrebuild.headphones

import dev.ignotus.sonyrebuild.ble.DiscoveredSonyDevice
import dev.ignotus.sonyrebuild.protocol.DeviceInfoType
import dev.ignotus.sonyrebuild.protocol.AmbientSoundMode
import dev.ignotus.sonyrebuild.protocol.EqEbbInquiredType
import dev.ignotus.sonyrebuild.protocol.EqPresetId
import dev.ignotus.sonyrebuild.protocol.LeaInquiredType
import dev.ignotus.sonyrebuild.protocol.NcAsmInquiredType
import dev.ignotus.sonyrebuild.protocol.NoiseControlMode
import dev.ignotus.sonyrebuild.protocol.ParsedTandemResponse
import dev.ignotus.sonyrebuild.protocol.PlaybackControl
import dev.ignotus.sonyrebuild.protocol.PowerInquiredType
import dev.ignotus.sonyrebuild.protocol.SonyTandemV1Table1Protocol
import dev.ignotus.sonyrebuild.protocol.SonyTandemV2Table1Protocol

object SonyTandemV2HeadphoneAdapter : HeadphoneAdapter {
    override val id: String = "sony-tandem-v2"
    override val brand: String = "Sony"
    override val protocolName: String = "Sony Tandem"

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
            eqStatusTypes = listOf(
                EqEbbInquiredType.PRESET_EQ,
                EqEbbInquiredType.CUSTOM_EQ,
                EqEbbInquiredType.EBB,
            ),
            eqParamTypes = listOf(
                EqEbbInquiredType.PRESET_EQ,
                EqEbbInquiredType.CUSTOM_EQ,
                EqEbbInquiredType.EBB,
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
            eqStatusTypes = listOf(
                EqEbbInquiredType.PRESET_EQ,
                EqEbbInquiredType.CUSTOM_EQ,
                EqEbbInquiredType.EBB,
            ),
            eqParamTypes = listOf(
                EqEbbInquiredType.EBB,
            ),
            queryProtocolInfo = false,
            queryNoiseControlParams = true,
        ),
    )

    private val templates = listOf(wh1000xm4, linkBudsS)

    override fun match(
        device: DiscoveredSonyDevice,
        reportedModelName: String?,
    ): ConnectedHeadphoneProfile? {
        val candidates = listOfNotNull(reportedModelName, device.name.removePrefix("LE_"))
        val template = templates.firstOrNull { template ->
            candidates.any { candidate ->
                candidate.normalizedModelName().contains(template.modelName.normalizedModelName())
            }
        } ?: return null
        return template.toProfile(device.name)
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
                eqStatusTypes = emptyList(),
                eqParamTypes = emptyList(),
            ),
            knownStaticProfile = false,
        ).toProfile(device.name)

    override fun buildRefreshCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        buildList {
            if (profile.capabilities.queryProtocolInfo) {
                add(HeadphoneCommand("GET protocol info", SonyTandemV2Table1Protocol.buildGetProtocolInfo()))
            }
            if (profile.supports(HeadphoneFeature.DEVICE_INFO)) {
                DeviceInfoType.entries.forEach {
                    add(HeadphoneCommand("GET device info $it", SonyTandemV2Table1Protocol.buildGetDeviceInfo(it)))
                }
                add(HeadphoneCommand("GET display firmware version", SonyTandemV2Table1Protocol.buildGetDisplayFirmwareVersion()))
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
                add(HeadphoneCommand("GET Quick Access", SonyTandemV2Table1Protocol.buildGetQuickAccess()))
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
        return if (NcAsmInquiredType.V1_TABLE_SET1_NC_ASM in profile.capabilities.writableNoiseControlTypes) {
            listOf(
                HeadphoneCommand(
                    "SET NC/ASM V1 table1 mode $mode level=$level voice=${ambientMode == AmbientSoundMode.VOICE}",
                    SonyTandemV1Table1Protocol.buildSetNoiseControlMode(mode, level, ambientMode),
                )
            )
        } else if (NcAsmInquiredType.NC_MODE_SWITCH_AND_ASM_SEAMLESS in profile.capabilities.writableNoiseControlTypes) {
            listOf(
                HeadphoneCommand(
                    "SET NC/ASM 0x14 mode $mode level=$level voice=${ambientMode == AmbientSoundMode.VOICE}",
                    SonyTandemV2Table1Protocol.buildSetNcModeSwitchAndAmbientLevel(mode, level, ambientMode),
                )
            )
        } else if (NcAsmInquiredType.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS in
            profile.capabilities.writableNoiseControlTypes
        ) {
            listOf(
                HeadphoneCommand(
                    "SET NC/ASM mode $mode level=$level voice=${ambientMode == AmbientSoundMode.VOICE}",
                    SonyTandemV2Table1Protocol.buildSetNoiseControlMode(mode, level, ambientMode),
                )
            )
        } else {
            when (mode) {
                NoiseControlMode.NOISE_CANCELLING -> listOf(
                    HeadphoneCommand("SET NC on", SonyTandemV2Table1Protocol.buildSetNcOnOff(true)),
                )
                NoiseControlMode.AMBIENT_SOUND -> listOf(
                    HeadphoneCommand(
                        "SET ASM level $level voice=${ambientMode == AmbientSoundMode.VOICE}",
                        SonyTandemV2Table1Protocol.buildSetAmbientLevel(level, enabled = true, mode = ambientMode),
                    ),
                )
                NoiseControlMode.OFF -> listOf(
                    HeadphoneCommand("SET NC off", SonyTandemV2Table1Protocol.buildSetNcOnOff(false)),
                    HeadphoneCommand("SET ASM off", SonyTandemV2Table1Protocol.buildSetAmbientSound(false, ambientMode)),
                )
            }
        }
    }

    override fun buildSetEqPresetCommands(
        profile: ConnectedHeadphoneProfile,
        preset: EqPresetId,
        type: EqEbbInquiredType,
        bandSteps: List<Int>,
    ): List<HeadphoneCommand> = listOf(
        HeadphoneCommand(
            "SET EQ preset ${preset.name} type=$type",
            SonyTandemV2Table1Protocol.buildSetEqPreset(preset, type, bandSteps),
        )
    )

    override fun buildSetEqBandCommands(
        profile: ConnectedHeadphoneProfile,
        rawSteps: List<Int>,
        preset: EqPresetId?,
        useCustomPayload: Boolean,
        type: EqEbbInquiredType,
    ): List<HeadphoneCommand> {
        val command = if (useCustomPayload && preset == null) {
            SonyTandemV2Table1Protocol.buildSetCustomEqBandSteps(rawSteps)
        } else {
            SonyTandemV2Table1Protocol.buildSetEqPreset(
                preset = preset ?: EqPresetId.CUSTOM,
                type = type,
                bandSteps = rawSteps,
            )
        }
        return listOf(HeadphoneCommand("SET EQ bands type=$type preset=${preset?.name ?: "CUSTOM"}", command))
    }

    override fun buildSetClearBassCommands(profile: ConnectedHeadphoneProfile, level: Int): List<HeadphoneCommand> =
        listOf(HeadphoneCommand("SET Clear Bass $level", SonyTandemV2Table1Protocol.buildSetClearBass(level)))

    override fun buildRefreshNoiseControlCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        profile.capabilities.noiseControlQueryTypes.map {
            if (profile.capabilities.queryNoiseControlParams) {
                HeadphoneCommand("GET NC/ASM param $it", SonyTandemV2Table1Protocol.buildGetNcAsmParam(it))
            } else {
                HeadphoneCommand("GET NC/ASM status $it", SonyTandemV2Table1Protocol.buildGetNcAsmStatus(it))
            }
        }

    override fun buildRefreshEqCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        buildList {
            profile.capabilities.eqStatusTypes.forEach {
                add(HeadphoneCommand("GET EQ status $it", SonyTandemV2Table1Protocol.buildGetEqEbbStatus(it)))
            }
            profile.capabilities.eqParamTypes.forEach {
                add(HeadphoneCommand("GET EQ param $it", SonyTandemV2Table1Protocol.buildGetEqEbbParam(it)))
            }
        }

    override fun buildRefreshBatteryCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        if (!profile.supports(HeadphoneFeature.BATTERY)) {
            emptyList()
        } else {
            profile.capabilities.batteryQueries.map {
                val bytes = when (profile.protocolFor(HeadphoneFeature.BATTERY)) {
                    HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1 -> SonyTandemV1Table1Protocol.buildGetBatteryStatus(it)
                    else -> SonyTandemV2Table1Protocol.buildGetBatteryStatus(it)
                }
                HeadphoneCommand("GET battery $it", bytes)
            }
        }

    override fun buildRefreshPlaybackCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        if (profile.supports(HeadphoneFeature.PLAYBACK_CONTROL)) {
            listOf(HeadphoneCommand("GET playback status", SonyTandemV2Table1Protocol.buildGetPlaybackStatus()))
        } else {
            emptyList()
        }

    private fun buildRefreshLeaCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        LeaInquiredType.entries.map {
            HeadphoneCommand("GET LEA status $it", SonyTandemV2Table1Protocol.buildGetLeaStatus(it))
        }

    override fun buildPlaybackCommands(profile: ConnectedHeadphoneProfile, control: PlaybackControl): List<HeadphoneCommand> =
        listOf(HeadphoneCommand("PLAYBACK ${control.name}", SonyTandemV2Table1Protocol.buildPlayback(control)))

    override fun parse(profile: ConnectedHeadphoneProfile, raw: ByteArray): ParsedTandemResponse =
        when {
            profile.protocolFor(HeadphoneFeature.BATTERY) == HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1 &&
                raw.getOrNull(if (raw.firstOrNull() == dev.ignotus.sonyrebuild.protocol.SonyTandemFrame.DATA_MDR) 1 else 0) in
                setOf(
                    SonyTandemV1Table1Protocol.COMMON_RET_BATTERY_LEVEL,
                    SonyTandemV1Table1Protocol.COMMON_NTFY_BATTERY_LEVEL,
                ) -> SonyTandemV1Table1Protocol.parse(raw)
            raw.getOrNull(if (raw.firstOrNull() == dev.ignotus.sonyrebuild.protocol.SonyTandemFrame.DATA_MDR) 2 else 1) ==
                NcAsmInquiredType.V1_TABLE_SET1_NC_ASM.code -> SonyTandemV1Table1Protocol.parse(raw)
            else -> SonyTandemV2Table1Protocol.parse(raw)
        }

    private data class ProfileTemplate(
        val modelName: String,
        val series: String?,
        val capabilities: HeadphoneCapabilities,
        val knownStaticProfile: Boolean = true,
    ) {
        private val featureProtocolMap: Map<HeadphoneFeature, HeadphoneProtocolVariant> =
            when (modelName) {
                "WH-1000XM4" -> commonFeatures.associateWith { feature ->
                    when (feature) {
                        HeadphoneFeature.BATTERY,
                        HeadphoneFeature.NOISE_CONTROL,
                        HeadphoneFeature.AMBIENT_LEVEL,
                        HeadphoneFeature.AMBIENT_VOICE_MODE -> HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1
                        else -> HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1
                    }
                }
                else -> commonFeatures.associateWith { HeadphoneProtocolVariant.SONY_TANDEM_V2_TABLE1 }
            }

        fun toProfile(displayName: String): ConnectedHeadphoneProfile =
            ConnectedHeadphoneProfile(
                adapterId = SonyTandemV2HeadphoneAdapter.id,
                brand = SonyTandemV2HeadphoneAdapter.brand,
                modelName = modelName,
                displayName = displayName.removePrefix("LE_").takeIf { it.isNotBlank() } ?: modelName,
                protocolName = SonyTandemV2HeadphoneAdapter.protocolName,
                series = series,
                capabilities = capabilities,
                featureProtocolMap = featureProtocolMap,
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
            )
    }
}

private fun String.normalizedModelName(): String =
    uppercase()
        .removePrefix("LE_")
        .replace(" ", "")
        .replace("_", "-")
