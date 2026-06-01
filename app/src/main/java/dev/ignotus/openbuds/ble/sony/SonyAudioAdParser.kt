package dev.ignotus.openbuds.ble.sony

import dev.ignotus.openbuds.protocol.hexString

/**
 * Sony Audio Advertisement parsed from BLE manufacturer-specific data (0x012D).
 */
data class SonyAudioAdvertisement(
    val version: Int,
    val raw: String,
    val androidLine: String? = null,
    val androidGattCapable: Boolean = false,
    val audioStream: String? = null,
    val leGattControlFlag: Boolean = false,
    val modelId: Int? = null,
    val classicHash: Long? = null,
) {
    val summary: String
        get() = buildList {
            add("Sony Audio AD v$version")
            androidLine?.let { add("Android=$it") }
            audioStream?.let { add("Stream=$it") }
            if (androidGattCapable) add("GATT line")
            if (leGattControlFlag) add("LE control flag")
            classicHash?.let { add("Hash=$it") }
        }.joinToString(", ")
}

/**
 * Pure-function parser for Sony Audio Advertisement data.
 *
 * The advertisement is embedded in BLE manufacturer-specific data
 * (manufacturer ID 0x012D) and uses a chunked V2 format:
 *
 * ```
 * [0x04, 0x00, 0x02, chunkCount, chunks...]
 * chunk = [header:1] [body:N]
 * header = (bodyLength << 4) | chunkType
 * ```
 *
 * Chunk types:
 *   0x00 — Basic information (model ID)
 *   0x03 — Tandem transmitting line (SPP/GATT capability)
 *   0x05 — Classic Bluetooth hash
 */
object SonyAudioAdParser {
    const val AD_TYPE_MANUFACTURER_SPECIFIC = 0xFF
    const val SONY_AUDIO_MANUFACTURER_ID = 0x012D
    val SONY_AUDIO_HEAD = byteArrayOf(0x04, 0x00)
    const val SONY_CHUNK_BASIC_INFORMATION = 0x00
    const val SONY_CHUNK_TANDEM_TRANSMITTING_LINE = 0x03
    const val SONY_CHUNK_CLASSIC_BLUETOOTH_HASH = 0x05

    /**
     * Parse a Sony Audio V2 advertisement from raw manufacturer payload bytes.
     * Returns null if the payload is not a valid V2 advertisement.
     */
    fun parseSonyAudioV2Advertisement(payload: ByteArray): SonyAudioAdvertisement? {
        if (!payload.isSonyAudioV2Start() || payload.size < 4) return null
        val chunkCount = payload[3].toInt() and 0xFF
        if (chunkCount < 1) return null

        var index = 4
        var androidLine: String? = null
        var androidGattCapable = false
        var audioStream: String? = null
        var leGattControlFlag = false
        var modelId: Int? = null
        var classicHash: Long? = null

        repeat(chunkCount) {
            if (index >= payload.size) return@repeat
            val header = payload[index].toInt() and 0xFF
            val bodyLength = (header and 0xF0) ushr 4
            val chunkType = header and 0x0F
            val bodyStart = index + 1
            val bodyEnd = bodyStart + bodyLength
            if (bodyLength <= 0 || bodyEnd > payload.size) {
                index = payload.size
                return@repeat
            }
            when (chunkType) {
                SONY_CHUNK_BASIC_INFORMATION -> {
                    if (bodyLength == 11) {
                        modelId = payload[bodyStart].toInt() and 0xFF
                    }
                }
                SONY_CHUNK_TANDEM_TRANSMITTING_LINE -> {
                    if (bodyLength == 3 || bodyLength == 4) {
                        val android = payload[bodyStart].toInt() and 0xFF
                        androidLine = transmittingLineLabel(android and 0x0F)
                        audioStream = audioStreamLabel(android and 0xF0)
                        androidGattCapable = (android and 0x0F) == 1 || (android and 0x0F) == 3
                        val bluetoothSpec = payload[bodyStart + 2].toInt() and 0xFF
                        leGattControlFlag = (bluetoothSpec and 0x01) == 0x01
                    }
                }
                SONY_CHUNK_CLASSIC_BLUETOOTH_HASH -> {
                    if (bodyLength == 4 || bodyLength == 8) {
                        classicHash = unsignedInt(payload, bodyStart)
                    }
                }
            }
            index = bodyEnd
        }
        return SonyAudioAdvertisement(
            version = 2,
            raw = payload.hexString(),
            androidLine = androidLine,
            androidGattCapable = androidGattCapable,
            audioStream = audioStream,
            leGattControlFlag = leGattControlFlag,
            modelId = modelId,
            classicHash = classicHash,
        )
    }

    /**
     * Extract Sony Audio manufacturer payloads from a raw BLE scan record.
     * Handles V1 and V2 advertisement formats, including multi-chunk V2 payloads.
     */
    fun extractSonyAudioManufacturerPayloads(record: ByteArray): List<ByteArray> {
        val parsed = mutableListOf<ByteArray>()
        var pendingV2: ByteArray? = null
        var index = 0
        while (index < record.size) {
            val length = record[index].toInt() and 0xFF
            if (length == 0) break
            val typeIndex = index + 1
            val nextIndex = index + length + 1
            if (typeIndex >= record.size || nextIndex > record.size) break
            val type = record[typeIndex].toInt() and 0xFF
            if (type == AD_TYPE_MANUFACTURER_SPECIFIC && length >= 4) {
                val manufacturerId =
                    (record[index + 2].toInt() and 0xFF) or
                        ((record[index + 3].toInt() and 0xFF) shl 8)
                if (manufacturerId == SONY_AUDIO_MANUFACTURER_ID) {
                    val payload = record.copyOfRange(index + 4, nextIndex)
                    when {
                        payload.isSonyAudioV2Start() -> {
                            pendingV2 = payload
                            parsed += payload
                        }
                        payload.isSonyAudioV1Start() -> parsed += payload
                        pendingV2 != null -> {
                            val combined = pendingV2 + payload
                            pendingV2 = combined
                            parsed += combined
                        }
                    }
                }
            }
            index = nextIndex
        }
        return parsed
    }

    private fun ByteArray.isSonyAudioV1Start(): Boolean =
        size >= SONY_AUDIO_HEAD.size + 1 &&
            this[0] == SONY_AUDIO_HEAD[0] &&
            this[1] == SONY_AUDIO_HEAD[1] &&
            this[2].toInt() == 1

    private fun ByteArray.isSonyAudioV2Start(): Boolean =
        size >= SONY_AUDIO_HEAD.size + 1 &&
            this[0] == SONY_AUDIO_HEAD[0] &&
            this[1] == SONY_AUDIO_HEAD[1] &&
            this[2].toInt() == 2

    fun transmittingLineLabel(code: Int): String =
        when (code) {
            0 -> "SPP"
            1 -> "GATT"
            3 -> "SPP_OR_GATT"
            else -> "UNKNOWN(0x${code.toString(16)})"
        }

    fun audioStreamLabel(code: Int): String =
        when (code) {
            0x00 -> "A2DP"
            0x10 -> "LE_AUDIO"
            0x20 -> "A2DP_OR_LE_AUDIO"
            else -> "UNKNOWN(0x${code.toString(16)})"
        }

    fun unsignedInt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
}
