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
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.COMMON_NTFY_BATTERY_LEVEL
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.COMMON_RET_BATTERY_LEVEL
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.DATA_MDR
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.EQEBB_GET_PARAM
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.EQEBB_GET_STATUS
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.EQEBB_NTFY_PARAM
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.EQEBB_NTFY_STATUS
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.EQEBB_RET_PARAM
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.EQEBB_RET_STATUS
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.EQEBB_SET_PARAM
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_GET_PARAM
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_GET_STATUS
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_NTFY_PARAM
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_NTFY_STATUS
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_RET_PARAM
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_RET_STATUS
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.NCASM_SET_PARAM
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.POWER_NTFY_STATUS
import dev.ignotus.sonyrebuild.protocol.SonyTandemConstants.POWER_RET_STATUS
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
        eqWriteStrategy = EqWriteStrategy.XM4_COMBINED_EBB,
    )

    private val templates = listOf(wh1000xm4, linkBudsS)

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
                eqStatusTypes = emptyList(),
                eqParamTypes = emptyList(),
            ),
            knownStaticProfile = false,
        ).toProfile(id, brand, protocolName, device.name)

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
            if (profile.supports(HeadphoneFeature.WEARING_STATUS)) {
                add(HeadphoneCommand("GET Wearing status", SonyTandemV2Table1Protocol.buildGetWearingStatus()))
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
        LeaInquiredType.entries.flatMap { type ->
            listOf(
                HeadphoneCommand("GET LEA status $type", SonyTandemV2Table1Protocol.buildGetLeaStatus(type)),
                HeadphoneCommand("GET LEA paired history $type", SonyTandemV2Table1Protocol.buildGetLeaPairedHistory(type)),
            )
        }

    override fun buildPlaybackCommands(profile: ConnectedHeadphoneProfile, control: PlaybackControl): List<HeadphoneCommand> =
        listOf(HeadphoneCommand("PLAYBACK ${control.name}", SonyTandemV2Table1Protocol.buildPlayback(control)))

    override fun parse(profile: ConnectedHeadphoneProfile, raw: ByteArray): ParsedTandemResponse {
        val normalized = if (raw.firstOrNull() == DATA_MDR) raw else byteArrayOf(DATA_MDR) + raw
        val command = normalized.getOrNull(1) ?: return ParsedTandemResponse.Unknown(null, null, byteArrayOf(), raw)
        val payload = normalized.drop(2).toByteArray()
        val feature = classifyCommand(command, payload)
        return when (profile.protocolFor(feature)) {
            HeadphoneProtocolVariant.SONY_TANDEM_V1_TABLE1 -> {
                val result = SonyTandemV1Table1Protocol.parse(raw)
                if (result is ParsedTandemResponse.Unknown) {
                    SonyTandemV2Table1Protocol.parse(raw)
                } else {
                    result
                }
            }
            else -> SonyTandemV2Table1Protocol.parse(raw)
        }
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
            // Non-overlapping CommonInquiredType (0x03, 0x05, 0x07, 0x08, 0x09) → V2 common status
            isCommonType && !isPowerType -> HeadphoneFeature.DEVICE_INFO
            // Non-overlapping PowerInquiredType (0x0E = STAMINA) → V1 battery
            isPowerType && !isCommonType -> HeadphoneFeature.BATTERY
            // Overlapping codes (0x00, 0x01, 0x02, 0x04, 0x06): inspect payload shape
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
                // BATTERY or CRADLE: [type, level] — level is 0-100 or 0xFF
                payload.size == 2 && payload[1].isBatteryPercentage()
            }
            0x01 -> {
                // LEFT_RIGHT: [type, level_L, 0x00, level_R]
                payload.size == 4 &&
                    payload[1].isBatteryPercentage() &&
                    payload[2].toInt().and(0xFF) == 0x00 &&
                    payload[3].isBatteryPercentage()
            }
            0x04, 0x06 -> false // AUTO_POWER_OFF, POWER_SAVE_MODE — route to DEVICE_INFO
            else -> false
        }
    }

    private fun Byte.isBatteryPercentage(): Boolean {
        val v = this.toInt() and 0xFF
        return v in 0..100 || v == 0xFF
    }
}
