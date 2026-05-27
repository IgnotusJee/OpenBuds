package dev.ignotus.openbuds.protocol

/**
 * QCY TLV frame serialization/deserialization and command ID constants.
 *
 * Frame format:
 *   [0xFF] [PayloadLength] [CMD_ID:1] [DATA_LEN:1] [DATA:N]...
 *
 * Multiple TLV entries per frame are supported.
 *
 * Read request:
 *   [0xFF] [0x03] [0xFE] [0x01] [TARGET_CMD_ID]
 *
 * Reference:
 *   com.qcymall.qcylibrary.dataBean.DataBean.java (CMDID constants, lines 7-72)
 *   com.qcymall.qcylibrary.dataBean.DataAnalyse.java (frame serialize/deserialize, lines 7-49)
 */

data class QcyTlvEntry(
    val cmdId: Byte,
    val data: ByteArray,
) {
    val dataLen: Byte get() = data.size.toByte()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QcyTlvEntry) return false
        return cmdId == other.cmdId && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * cmdId.toInt() + data.contentHashCode()

    override fun toString(): String =
        "QcyTlvEntry(cmdId=0x${cmdId.unsigned.toString(16).uppercase().padStart(2, '0')}, data=[${data.hexString()}])"
}

object QcyProtocol {
    const val SOF: Byte = 0xFF.toByte()

    // ── Command IDs ──────────────────────────────────────────────
    const val CMDID_REQUESTDATA: Byte = 0xFE.toByte()  // -2: read request wrapper
    const val CMDID_RESET_DEFAULT: Byte = 1
    const val CMDID_CLEAR_PAIR: Byte = 2
    const val CMDID_FACTORY_RESET: Byte = 3
    const val CMDID_MUSIC_ACTION: Byte = 4
    const val CMDID_LIGHT: Byte = 5
    const val CMDID_RUER: Byte = 6              // in-ear detection
    const val CMDID_NOISE_VALUE: Byte = 7        // ANC noise value
    const val CMDID_VOLUME: Byte = 8
    const val CMDID_DIYANSHI: Byte = 9           // time-lapse
    const val CMDID_JIANTING: Byte = 10          // transparency/monitor mode
    const val CMDID_NOISE_MODE: Byte = 12        // ANC mode (0=off, 1=ANC, 2=outdoor, 3=transparency)
    const val CMDID_TEST_MODE: Byte = 13
    const val CMDID_SLEEP_MODE: Byte = 16
    const val CMDID_COMPACTNESS: Byte = 17       // earbud fit test
    const val CMDID_LED_MODE: Byte = 18
    const val CMDID_POWER_MANAGER: Byte = 20     // auto power-off timer
    const val CMDID_BALANCE: Byte = 22           // channel balance (50=center)
    const val CMDID_ANC_SETTING: Byte = 23       // ANC detailed settings
    const val CMDID_PAIR_NAME: Byte = 24
    const val CMDID_VOICE_LANG: Byte = 25        // voice prompt language
    const val CMDID_TONE_VOLUME: Byte = 29       // prompt tone volume
    const val CMDID_STANDBY: Byte = 31
    const val CMDID_MULTIEQ: Byte = 32           // old EQ (6 bytes/band)
    const val CMDID_MULTIEQ2: Byte = 34          // new EQ (7 bytes/band)
    const val CMDID_LDAC: Byte = 35
    const val CMDID_AEQ: Byte = 39               // adaptive EQ
    const val CMDID_KEY_FUNCTION: Byte = 43      // key function (read response)
    const val CMDID_PEIDAI: Byte = 44            // wearing detection
    const val CMDID_SPACE_AUDIO: Byte = 45
    const val CMDID_MUSIC_MODE: Byte = 46
    const val CMDID_BATTERY: Byte = 47           // battery query (0x2F)
    const val CMDID_VERSION: Byte = 48           // firmware version query (0x30)
    const val CMDID_ENV_ADAPTATION: Byte = 50
    const val CMDID_TWS_ENABLE: Byte = 52
    const val CMDID_LED_SWITCH: Byte = 53
    const val CMDID_LED_EFFECT: Byte = 54
    const val CMDID_PLAY_MODE: Byte = 55
    const val CMDID_FOCUS_MODE: Byte = 57
    const val CMDID_MUSIC_STATUS: Byte = 58      // music playback status
    const val CMDID_MUSIC_INFO: Byte = 59        // music file info
    const val CMDID_TONE_PLAY: Byte = 61
    const val CMDID_SYNC_TIME: Byte = 62         // time sync
    const val CMDID_ALARM: Byte = 63
    const val CMDID_AI: Byte = 67
    const val CMDID_MAXEQ_COUNT: Byte = 68
    const val CMDID_CUSTOM_EQ_TEST: Byte = 69
    const val CMDID_MULTIEQ_LEFT: Byte = 70      // left ear independent EQ
    const val CMDID_MULTIEQ_RIGHT: Byte = 71     // right ear independent EQ
    const val CMDID_INEAR_SENSITIVITY: Byte = 72
    const val CMDID_GAME_CONFIG: Byte = 74
    const val CMDID_PHONE_INFO: Byte = 77
    const val CMDID_PHONE_STATUS: Byte = 79
    const val CMDID_HEAD_ACTION: Byte = 80
    const val CMDID_SENSOR_CONFIG: Byte = 81
    const val CMDID_SENSOR_TEST: Byte = 82
    const val CMDID_AI_ANC: Byte = 84
    const val CMDID_AI_ANC_CFG: Byte = 85
    const val CMDID_AI_ANC_CFG2: Byte = 86
    const val CMDID_HEAR_HEALTH: Byte = 88
    const val CMDID_AI_ANC_PREVIEW: Byte = 90
    const val CMDID_SPACE_AUDIO_MODE: Byte = 91

    // ── ANC mode constants ───────────────────────────────────────
    const val NOISE_MODE_OFF: Byte = 0
    const val NOISE_MODE_ANC: Byte = 1
    const val NOISE_MODE_OUTDOOR: Byte = 2
    const val NOISE_MODE_TRANSPARENCY: Byte = 3

    // ── Music action constants ───────────────────────────────────
    const val MUSIC_ACTION_PLAY: Byte = 1
    const val MUSIC_ACTION_PAUSE: Byte = 2
    const val MUSIC_ACTION_PREV: Byte = 3
    const val MUSIC_ACTION_NEXT: Byte = 4

    // ── Frame builder ────────────────────────────────────────────

    /**
     * Build a multi-TLV frame: [0xFF] [totalLen] [entries...]
     */
    fun buildFrame(entries: List<QcyTlvEntry>): ByteArray {
        val body = entries.flatMap { entry ->
            listOf(entry.cmdId, entry.dataLen) + entry.data.toList()
        }
        val totalLen = body.size.toByte()
        return byteArrayOf(SOF, totalLen) + body.toByteArray()
    }

    /**
     * Build a read-request frame for a target command ID.
     * Format: [0xFF, 0x03, 0xFE, 0x01, targetCmdId]
     */
    fun buildReadRequest(targetCmdId: Byte): ByteArray =
        buildFrame(listOf(QcyTlvEntry(CMDID_REQUESTDATA, byteArrayOf(targetCmdId))))

    /**
     * Build a simple single-command frame: [0xFF, LEN, cmdId, dataLen, data...]
     */
    fun buildSimpleCommand(cmdId: Byte, data: ByteArray): ByteArray =
        buildFrame(listOf(QcyTlvEntry(cmdId, data)))

    /**
     * Build a single-byte-value command frame.
     * Many QCY commands use the pattern [CMD_ID, 1, value].
     */
    fun buildSingleValueCommand(cmdId: Byte, value: Byte): ByteArray =
        buildSimpleCommand(cmdId, byteArrayOf(value))

    // ── Frame parser ─────────────────────────────────────────────

    /**
     * Parse a raw QCY frame into TLV entries.
     * Strictly validates SOF byte and that total length matches `declaredLen + 2`,
     * matching the reference `DataAnalyse.analyseAllCMD` semantics
     * (`bArr.length == (bArr[1] & 255) + 2`). Returns empty list for any
     * malformed/truncated frame instead of partial results.
     */
    fun parseFrame(raw: ByteArray): List<QcyTlvEntry> {
        if (raw.size < 2 || raw[0] != SOF) return emptyList()

        val declaredLen = raw[1].unsigned
        if (raw.size != declaredLen + 2) return emptyList()

        val bodyStart = 2
        val bodyEnd = raw.size

        val entries = mutableListOf<QcyTlvEntry>()
        var pos = bodyStart

        while (pos < bodyEnd) {
            // Each entry needs at least cmdId + dataLen
            if (pos + 2 > bodyEnd) return emptyList()
            val cmdId = raw[pos]
            val dataLen = raw[pos + 1].unsigned
            pos += 2

            if (pos + dataLen > bodyEnd) return emptyList() // truncated entry → whole frame invalid

            val data = raw.copyOfRange(pos, pos + dataLen)
            entries.add(QcyTlvEntry(cmdId, data))
            pos += dataLen
        }

        return entries
    }

    // ── EQ helpers ───────────────────────────────────────────────

    /**
     * Parse new-style EQ response (CMD 34, 7 bytes per band).
     * Expected format: [EQ_TYPE:1] [MASTER_GAIN:2 LE] [BANDS...]
     * Each band: [freq:2 LE, gain:2 LE, q:2 LE, band_type:1]
     */
    fun parseEqResponseNew(data: ByteArray): QcyEqResponse {
        if (data.size < 3) return QcyEqResponse(0, 0f, emptyList())

        val eqType = data[0].unsigned
        val masterGainRaw = (data[1].unsigned or (data[2].unsigned shl 8)).toShort()
        val masterGain = masterGainRaw / 100f

        val bands = mutableListOf<QcyEqBand>()
        var pos = 3
        while (pos + 7 <= data.size) {
            val freq = data[pos].unsigned or (data[pos + 1].unsigned shl 8)
            val gainRaw = ((data[pos + 2].unsigned or (data[pos + 3].unsigned shl 8)).toShort())
            val qRaw = data[pos + 4].unsigned or (data[pos + 5].unsigned shl 8)
            val bandType = data[pos + 6].unsigned
            bands.add(
                QcyEqBand(
                    frequency = freq,
                    gain = gainRaw / 100f,
                    q = qRaw / 100f,
                    bandType = bandType,
                )
            )
            pos += 7
        }

        return QcyEqResponse(eqType, masterGain, bands)
    }

    /**
     * Parse old-style EQ response (CMD 32, 6 bytes per band).
     * Expected format: [EQ_TYPE:1] [MASTER_GAIN:2 LE] [BANDS...]
     * Each band: [freq:2 LE, gain:2 LE, q:2 LE]
     */
    fun parseEqResponseOld(data: ByteArray): QcyEqResponse {
        if (data.size < 3) return QcyEqResponse(0, 0f, emptyList())

        val eqType = data[0].unsigned
        val masterGainRaw = (data[1].unsigned or (data[2].unsigned shl 8)).toShort()
        val masterGain = masterGainRaw / 100f

        val bands = mutableListOf<QcyEqBand>()
        var pos = 3
        while (pos + 6 <= data.size) {
            val freq = data[pos].unsigned or (data[pos + 1].unsigned shl 8)
            val gainRaw = ((data[pos + 2].unsigned or (data[pos + 3].unsigned shl 8)).toShort())
            val qRaw = data[pos + 4].unsigned or (data[pos + 5].unsigned shl 8)
            bands.add(
                QcyEqBand(
                    frequency = freq,
                    gain = gainRaw / 100f,
                    q = qRaw / 100f,
                    bandType = 0,
                )
            )
            pos += 6
        }

        return QcyEqResponse(eqType, masterGain, bands)
    }

    /**
     * Build a new-style EQ set command (CMD 34, 7 bytes per band).
     * Resulting frame: [SOF][OuterLen][CMD_MULTIEQ2][DataLen][EQ_TYPE:1][MASTER_GAIN:2 LE][BANDS...]
     * where DataLen = 3 + bands*7 and OuterLen = DataLen + 2 (CMD + DataLen).
     *
     * For custom EQ the [eqType] should be 0x7E (126) matching the reference
     * `setEQWtihQFG2Type(str, 126, ...)`.
     *
     * Reference: `QCYHeadsetClient.setEQWtihQFG2Type` + `DataAnalyse.getSendData`
     * (outer len is total bytes - 2, i.e. CMD + DataLen + body).
     */
    fun buildEqCommandNew(eqType: Byte, masterGain: Float, bands: List<QcyEqBand>): ByteArray =
        buildSimpleCommand(CMDID_MULTIEQ2, encodeEqBody(eqType, masterGain, bands, bytesPerBand = 7))

    /**
     * Build an old-style EQ set command (CMD 32, 6 bytes per band, no bandType).
     */
    fun buildEqCommandOld(eqType: Byte, masterGain: Float, bands: List<QcyEqBand>): ByteArray =
        buildSimpleCommand(CMDID_MULTIEQ, encodeEqBody(eqType, masterGain, bands, bytesPerBand = 6))

    /**
     * Encode the EQ inner body: [EQ_TYPE:1][MASTER_GAIN:2 LE][BANDS...]
     * Each band is either 6 (old) or 7 (new, with bandType) bytes.
     */
    private fun encodeEqBody(
        eqType: Byte,
        masterGain: Float,
        bands: List<QcyEqBand>,
        bytesPerBand: Int,
    ): ByteArray {
        require(bytesPerBand == 6 || bytesPerBand == 7) { "bytesPerBand must be 6 or 7" }
        val masterGainRaw = (masterGain * 100).toInt().coerceIn(-32768, 32767)
        val out = ByteArray(3 + bands.size * bytesPerBand)
        out[0] = eqType
        out[1] = (masterGainRaw and 0xFF).toByte()
        out[2] = ((masterGainRaw shr 8) and 0xFF).toByte()
        var pos = 3
        for (band in bands) {
            val gainRaw = clampGain(band.gain)
            val qRaw = (band.q * 100).toInt().coerceIn(0, 65535)
            out[pos] = (band.frequency and 0xFF).toByte()
            out[pos + 1] = ((band.frequency shr 8) and 0xFF).toByte()
            out[pos + 2] = (gainRaw and 0xFF).toByte()
            out[pos + 3] = ((gainRaw shr 8) and 0xFF).toByte()
            out[pos + 4] = (qRaw and 0xFF).toByte()
            out[pos + 5] = ((qRaw shr 8) and 0xFF).toByte()
            if (bytesPerBand == 7) {
                out[pos + 6] = band.bandType.toByte()
            }
            pos += bytesPerBand
        }
        return out
    }

    private fun clampGain(gain: Float): Int =
        (gain * 100).toInt().coerceIn(-1270, 1270)
}

/**
 * Parsed EQ response (new or old format).
 * Holds eqType, masterGain (already scaled to dB) and the band list.
 */
data class QcyEqResponse(
    val eqType: Int,
    val masterGain: Float,
    val bands: List<QcyEqBand>,
)
