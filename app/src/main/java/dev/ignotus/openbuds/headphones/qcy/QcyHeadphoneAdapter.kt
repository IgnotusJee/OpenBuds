package dev.ignotus.openbuds.headphones.qcy

import dev.ignotus.openbuds.ble.IncomingHeadphoneMessage
import dev.ignotus.openbuds.ble.qcy.QcyChannel
import dev.ignotus.openbuds.ble.DiscoveredDevice
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
import dev.ignotus.openbuds.headphones.HeadphoneTransport
import dev.ignotus.openbuds.headphones.InfoLayoutHint
import dev.ignotus.openbuds.headphones.PlaybackDispatchStrategy
import dev.ignotus.openbuds.headphones.ProfileTemplate
import dev.ignotus.openbuds.headphones.qcy.devices.QcyC30SProfile
import dev.ignotus.openbuds.protocol.AmbientSoundMode
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dev.ignotus.openbuds.protocol.ParsedHeadphoneResponse
import dev.ignotus.openbuds.protocol.sony.EqEbbInquiredType
import dev.ignotus.openbuds.protocol.sony.PlaybackControl
import dev.ignotus.openbuds.protocol.QcyEqBand
import dev.ignotus.openbuds.protocol.qcy.QcyProtocol
import dev.ignotus.openbuds.protocol.qcy.QcyTlvEntry
import dev.ignotus.openbuds.protocol.unsigned

/**
 * HeadphoneAdapter implementation for QCY headphones.
 *
 * Protocol layout:
 *   - Writes go to QCY's 0x1001 characteristic.
 *   - Notifications/reads are dispatched by brand-private QCY source keys.
 *
 * Reference:
 *   QCY_C30S_PROTOCOL.md
 *   com.qcymall.qcylibrary.QCYHeadsetClient.java
 */
object QcyHeadphoneAdapter : HeadphoneAdapter {
    override val id: String = "qcy"
    override val brand: String = "QCY"
    override val protocolName: String = "QCY GATT TLV"

    private val templates = listOf(QcyC30SProfile.template)

    // ── Matching ─────────────────────────────────────────────────

    override fun match(
        device: DiscoveredDevice,
        reportedModelName: String?,
    ): ConnectedHeadphoneProfile? {
        // First try template match
        val templateMatch = templates.firstOrNull { template ->
            matchTemplate(template, device, reportedModelName) != null
        }?.let { template ->
            matchTemplate(template, device, reportedModelName)
        }
        if (templateMatch != null) return templateMatch

        // Fall back to name-based match for any QCY device
        val candidates = listOfNotNull(reportedModelName, device.name.removePrefix("LE_"))
        if (candidates.any { it.lowercase().contains("qcy") }) {
            return fallbackProfile(device)
        }
        return null
    }

    override fun fallbackProfile(device: DiscoveredDevice): ConnectedHeadphoneProfile =
        ProfileTemplate(
            modelName = device.name.removePrefix("LE_").takeIf { it.isNotBlank() } ?: "QCY audio device",
            series = null,
            capabilities = HeadphoneCapabilities(
                features = setOf(HeadphoneFeature.DEVICE_INFO, HeadphoneFeature.BATTERY),
                formFactor = HeadphoneFormFactor.UNKNOWN,
                batteryQueries = emptyList(),
                noiseControlQueryTypes = emptyList(),
                writableNoiseControlTypes = emptySet(),
                eqConfig = EqDeviceConfig(
                    availablePresets = listOf(EqPresetId.OFF),
                    writeInquiredType = dev.ignotus.openbuds.protocol.sony.EqEbbInquiredType.PRESET_EQ,
                    statusQueryTypes = emptyList(),
                    paramQueryTypes = emptyList(),
                    bandCount = 0,
                    hasClearBass = false,
                ),
                queryProtocolInfo = false,
                queryNoiseControlParams = false,
            ),
            featureProtocolMap = mapOf(
                HeadphoneFeature.DEVICE_INFO to HeadphoneProtocolVariant.QCY,
                HeadphoneFeature.BATTERY to HeadphoneProtocolVariant.QCY,
            ),
            knownStaticProfile = false,
            infoLayoutHint = InfoLayoutHint.BRAND_MODEL,
        ).toProfile(id, brand, protocolName, device.name)

    // ── Command builders ─────────────────────────────────────────

    private fun writeCommand(label: String, bytes: ByteArray): HeadphoneCommand =
        HeadphoneCommand(label = label, bytes = bytes)

    override fun buildRefreshCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        buildList {
            addAll(buildRefreshBatteryCommands(profile))
            if (profile.supports(HeadphoneFeature.DEVICE_INFO)) {
                add(
                    writeCommand(
                        "QCY GET version",
                        QcyProtocol.buildReadRequest(QcyProtocol.CMDID_VERSION),
                    )
                )
            }
            if (profile.supports(HeadphoneFeature.NOISE_CONTROL)) {
                addAll(buildRefreshNoiseControlCommands(profile))
            }
            if (profile.supports(HeadphoneFeature.EQ)) {
                addAll(buildRefreshEqCommands(profile))
            }
        }

    override fun buildRefreshBatteryCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        if (!profile.supports(HeadphoneFeature.BATTERY)) {
            emptyList()
        } else {
            listOf(
                writeCommand(
                    "QCY GET battery",
                    QcyProtocol.buildReadRequest(QcyProtocol.CMDID_BATTERY),
                )
            )
        }

    override fun buildRefreshNoiseControlCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        listOf(
            writeCommand(
                "QCY GET noise mode",
                QcyProtocol.buildReadRequest(QcyProtocol.CMDID_NOISE_MODE),
            ),
            writeCommand(
                "QCY GET noise value",
                QcyProtocol.buildReadRequest(QcyProtocol.CMDID_NOISE_VALUE),
            ),
        )

    override fun buildRefreshEqCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        listOf(
            writeCommand(
                "QCY GET EQ (old format)",
                QcyProtocol.buildReadRequest(QcyProtocol.CMDID_MULTIEQ),
            ),
            writeCommand(
                "QCY GET EQ (new format)",
                QcyProtocol.buildReadRequest(QcyProtocol.CMDID_MULTIEQ2),
            ),
        )

    override fun buildRefreshPlaybackCommands(profile: ConnectedHeadphoneProfile): List<HeadphoneCommand> =
        emptyList() // QCY doesn't support polling playback status

    // ── Write commands ───────────────────────────────────────────

    override fun buildSetNoiseControlModeCommands(
        profile: ConnectedHeadphoneProfile,
        mode: NoiseControlMode,
        ambientLevel: Int,
        ambientMode: AmbientSoundMode,
    ): List<HeadphoneCommand> {
        val qcyMode = when (mode) {
            NoiseControlMode.NOISE_CANCELLING -> QcyProtocol.NOISE_MODE_ANC
            NoiseControlMode.AMBIENT_SOUND -> QcyProtocol.NOISE_MODE_TRANSPARENCY
            NoiseControlMode.OFF -> QcyProtocol.NOISE_MODE_OFF
        }

        // TODO local approximation, not protocol-verified: 1..20 → 0..240.
        // Protocol doc says CMD 7 accepts 0-255; finer mapping is firmware-dependent.
        val noiseValue = when (mode) {
            NoiseControlMode.NOISE_CANCELLING -> (ambientLevel.coerceIn(1, 20) * 12).coerceIn(0, 255)
            NoiseControlMode.AMBIENT_SOUND -> (ambientLevel.coerceIn(1, 20) * 12).coerceIn(0, 255)
            NoiseControlMode.OFF -> 0
        }

        return listOf(
            writeCommand(
                "QCY SET noise mode $qcyMode",
                QcyProtocol.buildSingleValueCommand(QcyProtocol.CMDID_NOISE_MODE, qcyMode),
            ),
            writeCommand(
                "QCY SET noise value $noiseValue",
                QcyProtocol.buildSingleValueCommand(QcyProtocol.CMDID_NOISE_VALUE, noiseValue.toByte()),
            ),
        )
    }

    override fun buildSetEqPresetCommands(
        profile: ConnectedHeadphoneProfile,
        preset: EqPresetId,
        context: EqWriteContext,
    ): List<HeadphoneCommand> =
        emptyList() // QCY EQ presets are set via EQ characteristic write, TBD

    override fun buildSetEqBandCommands(
        profile: ConnectedHeadphoneProfile,
        rawSteps: List<Int>,
        preset: EqPresetId?,
        context: EqWriteContext,
    ): List<HeadphoneCommand> {
        val bandCount = profile.capabilities.eqConfig.bandCount
        val steps = if (rawSteps.size == bandCount) rawSteps else List(bandCount) { 10 }

        // QCY band frequencies for C30S (typical 10-band EQ)
        val frequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        val bands = steps.mapIndexed { index, step ->
            // Map from display step (0-20, center=10) to gain (-12.0..+12.0 dB)
            val gain = ((step - 10) * 1.2f).coerceIn(-12.7f, 12.7f)
            QcyEqBand(
                frequency = frequencies.getOrElse(index) { 1000 },
                gain = gain,
                q = 1.0f,
                bandType = 0,
            )
        }

        val eqBytes = QcyProtocol.buildEqCommandNew(
            eqType = 0x7E.toByte(), // 126 = custom EQ (reference: setEQWtihQFG2Type(str, 126, ...))
            masterGain = 0f,
            bands = bands,
        )

        return listOf(writeCommand("QCY SET EQ custom bands", eqBytes))
    }

    override fun buildPlaybackCommands(
        profile: ConnectedHeadphoneProfile,
        control: PlaybackControl,
    ): List<HeadphoneCommand> {
        val action = when (control) {
            PlaybackControl.PLAY -> QcyProtocol.MUSIC_ACTION_PLAY
            PlaybackControl.PAUSE -> QcyProtocol.MUSIC_ACTION_PAUSE
            PlaybackControl.TRACK_DOWN -> QcyProtocol.MUSIC_ACTION_PREV
            PlaybackControl.TRACK_UP -> QcyProtocol.MUSIC_ACTION_NEXT
            PlaybackControl.STOP -> QcyProtocol.MUSIC_ACTION_PAUSE // QCY has no stop, map to pause
        }
        return listOf(
            writeCommand(
                "QCY PLAYBACK ${control.name}",
                QcyProtocol.buildSingleValueCommand(QcyProtocol.CMDID_MUSIC_ACTION, action),
            )
        )
    }

    // ── Response parsing ─────────────────────────────────────────

    override fun parse(
        profile: ConnectedHeadphoneProfile,
        message: IncomingHeadphoneMessage,
    ): ParsedHeadphoneResponse {
        val raw = message.raw
        val qc = QcyChannel.fromSourceKey(message.sourceKey) ?: QcyChannel.READSET
        return when (qc) {
            QcyChannel.BATTERY -> parseQcyBattery(raw)
            QcyChannel.VERSION -> parseQcyVersionRaw(raw)
            QcyChannel.FUNCTION -> parseQcyFunctionStatus(raw)
            QcyChannel.EQ_RAW -> parseQcyEqRaw(raw)
            QcyChannel.READSET -> parseQcyReadset(raw)
            QcyChannel.SETTING_WRITE -> parseQcyReadset(raw)
        }
    }

    private fun parseQcyVersionRaw(raw: ByteArray): ParsedHeadphoneResponse {
        // Direct version characteristic read format (VersionDataBean.java:16-22):
        // bytes[0-2]: left firmware (major.minor.patch)
        // bytes[3-5]: right firmware (major.minor.patch)
        val leftFw = if (raw.size >= 3) {
            "${raw[0].unsigned}.${raw[1].unsigned}.${raw[2].unsigned}"
        } else ""
        val rightFw = if (raw.size >= 6) {
            "${raw[3].unsigned}.${raw[4].unsigned}.${raw[5].unsigned}"
        } else leftFw
        return ParsedHeadphoneResponse.Qcy.DeviceInfo(
            leftFirmware = leftFw,
            rightFirmware = rightFw,
            raw = raw,
        )
    }

    private fun parseQcyBattery(raw: ByteArray): ParsedHeadphoneResponse {
        // Battery characteristic (BatteryDataBean.java:21-25):
        // byte[i] bit 7 = charging flag, low 7 bits = level (0..100)
        fun parseBatteryByte(b: Byte): Pair<Int, Boolean> {
            val charging = (b.toInt() and 0x80) != 0
            val level = b.toInt() and 0x7F
            return (level.coerceIn(0, 100)) to charging
        }

        val left = if (raw.isNotEmpty()) parseBatteryByte(raw[0]) else (0 to false)
        val right = if (raw.size > 1) parseBatteryByte(raw[1]) else (0 to false)
        val case = if (raw.size > 2) parseBatteryByte(raw[2]) else (0 to false)

        return ParsedHeadphoneResponse.Qcy.Battery(
            leftLevel = left.first,
            rightLevel = right.first,
            caseLevel = case.first,
            leftCharging = left.second,
            rightCharging = right.second,
            caseCharging = case.second,
            raw = raw,
        )
    }

    private fun parseQcyFunctionStatus(raw: ByteArray): ParsedHeadphoneResponse {
        // UUID_FUNCTION_V1 (0x000F):
        //   byte[0]: CMDID_RUER (6) — in-ear detection
        //   byte[1]: CMDID_JIANTING (10) — transparency/monitor mode
        val ruer = raw.isNotEmpty() && raw[0].unsigned != 0
        val jianting = raw.size > 1 && raw[1].unsigned != 0
        return ParsedHeadphoneResponse.Qcy.FunctionStatus(
            inEarDetectionOn = ruer,
            transparencyOn = jianting,
            raw = raw,
        )
    }

    private fun parseQcyEqRaw(raw: ByteArray): ParsedHeadphoneResponse {
        // Raw EQ characteristic (0x000B). Format mirrors the EQ TLV response body:
        //   [EQ_TYPE:1] [MASTER_GAIN:2 LE] [BANDS...]
        // Heuristic: new (7 B/band) if (raw.size - 3) % 7 == 0 and divides evenly;
        // else fall back to old (6 B/band).
        if (raw.size < 3) {
            return ParsedHeadphoneResponse.SonyTandem.Unknown(null, null, byteArrayOf(), raw)
        }
        val payloadLen = raw.size - 3
        val response = if (payloadLen > 0 && payloadLen % 7 == 0) {
            QcyProtocol.parseEqResponseNew(raw)
        } else {
            QcyProtocol.parseEqResponseOld(raw)
        }
        return ParsedHeadphoneResponse.Qcy.EqData(
            eqType = response.eqType,
            masterGain = response.masterGain,
            bands = response.bands,
            raw = raw,
        )
    }

    private fun parseQcyReadset(raw: ByteArray): ParsedHeadphoneResponse {
        val entries = QcyProtocol.parseFrame(raw)
        if (entries.isEmpty()) {
            return ParsedHeadphoneResponse.SonyTandem.Unknown(null, null, byteArrayOf(), raw)
        }
        val items = entries.mapNotNull { entry -> mapTlvEntry(entry, raw) }
        return when (items.size) {
            0 -> ParsedHeadphoneResponse.SonyTandem.Unknown(null, null, byteArrayOf(), raw)
            1 -> items[0]
            else -> ParsedHeadphoneResponse.Batch(items, raw)
        }
    }

    /**
     * Map a single TLV entry to a ParsedHeadphoneResponse subtype.
     * The `raw` parameter is the full frame for traceability; individual subtypes
     * also keep entry.data for debugging.
     */
    private fun mapTlvEntry(entry: dev.ignotus.openbuds.protocol.qcy.QcyTlvEntry, raw: ByteArray): ParsedHeadphoneResponse? {
        return when (entry.cmdId) {
            QcyProtocol.CMDID_BATTERY -> parseQcyBattery(entry.data)
            QcyProtocol.CMDID_NOISE_MODE -> ParsedHeadphoneResponse.Qcy.NoiseControl(
                mode = if (entry.data.isNotEmpty()) entry.data[0].unsigned else null,
                noiseValue = null,
                raw = entry.data,
            )
            QcyProtocol.CMDID_NOISE_VALUE -> ParsedHeadphoneResponse.Qcy.NoiseControl(
                mode = null,
                noiseValue = if (entry.data.isNotEmpty()) entry.data[0].unsigned else null,
                raw = entry.data,
            )
            QcyProtocol.CMDID_VERSION -> {
                val text = if (entry.data.isNotEmpty()) {
                    entry.data.joinToString(".") { it.unsigned.toString() }
                } else "Unknown"
                ParsedHeadphoneResponse.Qcy.DeviceInfo(
                    leftFirmware = text,
                    rightFirmware = text,
                    raw = entry.data,
                )
            }
            QcyProtocol.CMDID_MULTIEQ2 -> {
                val resp = QcyProtocol.parseEqResponseNew(entry.data)
                ParsedHeadphoneResponse.Qcy.EqData(
                    eqType = resp.eqType,
                    masterGain = resp.masterGain,
                    bands = resp.bands,
                    raw = entry.data,
                )
            }
            QcyProtocol.CMDID_MULTIEQ -> {
                val resp = QcyProtocol.parseEqResponseOld(entry.data)
                ParsedHeadphoneResponse.Qcy.EqData(
                    eqType = resp.eqType,
                    masterGain = resp.masterGain,
                    bands = resp.bands,
                    raw = entry.data,
                )
            }
            QcyProtocol.CMDID_VOLUME -> {
                val left = if (entry.data.isNotEmpty()) entry.data[0].unsigned else 0
                val right = if (entry.data.size > 1) entry.data[1].unsigned else left
                ParsedHeadphoneResponse.Qcy.Volume(
                    leftVolume = left,
                    rightVolume = right,
                    raw = entry.data,
                )
            }
            QcyProtocol.CMDID_MUSIC_ACTION -> null // ack, ignore
            else -> null // unknown CMD; quietly drop
        }
    }
}
