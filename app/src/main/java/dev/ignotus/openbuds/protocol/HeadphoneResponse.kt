package dev.ignotus.openbuds.protocol

import dev.ignotus.openbuds.protocol.sony.CommonInquiredType
import dev.ignotus.openbuds.protocol.sony.DeviceInfoType
import dev.ignotus.openbuds.protocol.sony.EqBandInformationType
import dev.ignotus.openbuds.protocol.sony.EqEbbInquiredType
import dev.ignotus.openbuds.protocol.sony.LeaEnableDisable
import dev.ignotus.openbuds.protocol.sony.LeaInquiredType
import dev.ignotus.openbuds.protocol.sony.LeaPairedHistory
import dev.ignotus.openbuds.protocol.sony.LeaStreamingStatus
import dev.ignotus.openbuds.protocol.sony.NcAsmInquiredType
import dev.ignotus.openbuds.protocol.sony.PlayInquiredType
import dev.ignotus.openbuds.protocol.sony.PowerInquiredType
import dev.ignotus.openbuds.protocol.sony.QuickAccessFunction
import dev.ignotus.openbuds.protocol.sony.QuickAccessKey
import dev.ignotus.openbuds.protocol.sony.WearingDetectionResult
import dev.ignotus.openbuds.protocol.sony.WearingDetectionStatus
import dev.ignotus.openbuds.protocol.sony.AudioInquiredType

/**
 * Top-level sealed response type for all headphone brands.
 * Contains nested sealed interfaces for Sony Tandem and QCY protocols,
 * as well as cross-brand wrappers like [Batch].
 */
sealed interface ParsedHeadphoneResponse {
    val raw: ByteArray

    // ── Sony Tandem response types ────────────────────────────────

    sealed interface SonyTandem : ParsedHeadphoneResponse {
        override val raw: ByteArray

        data class DeviceInfo(
            val type: DeviceInfoType?,
            val text: String?,
            override val raw: ByteArray,
        ) : SonyTandem

        data class CommonStatus(
            val type: CommonInquiredType?,
            val text: String?,
            val values: List<Int>,
            override val raw: ByteArray,
        ) : SonyTandem

        data class Battery(
            val kind: PowerInquiredType?,
            val values: List<Int>,
            override val raw: ByteArray,
        ) : SonyTandem

        data class EqEbb(
            val type: EqEbbInquiredType?,
            val enabled: Boolean? = null,
            val preset: EqPresetId? = null,
            val clearBass: Int? = null,
            val bandSteps: List<Int> = emptyList(),
            val values: List<Int>,
            override val raw: ByteArray,
        ) : SonyTandem

        data class EqBandInfo(
            val type: EqBandInformationType?,
            val value: Int,
        )

        data class EqEbbExtendedInfo(
            val type: EqEbbInquiredType?,
            val bands: List<EqBandInfo>,
            val values: List<Int>,
            override val raw: ByteArray,
        ) : SonyTandem

        data class NoiseControl(
            val type: NcAsmInquiredType?,
            val values: List<Int>,
            val enabled: Boolean? = null,
            val ambientSoundEnabled: Boolean? = null,
            val ambientLevel: Int? = null,
            val ambientMode: AmbientSoundMode? = null,
            val controlMode: NoiseControlMode? = null,
            override val raw: ByteArray,
        ) : SonyTandem

        data class PlaybackAck(
            val values: List<Int>,
            val status: PlaybackStatus = PlaybackStatus.UNKNOWN,
            val isUnsolicited: Boolean = false,
            override val raw: ByteArray,
        ) : SonyTandem

        data class LeaStatus(
            val type: LeaInquiredType?,
            val values: List<Int>,
            val enabled: LeaEnableDisable? = null,
            val streamingStatusL: LeaStreamingStatus? = null,
            val streamingStatusR: LeaStreamingStatus? = null,
            override val raw: ByteArray,
        ) : SonyTandem

        data class LeaPairedHistoryStatus(
            val type: LeaInquiredType?,
            val values: List<Int>,
            val pairedHistory: LeaPairedHistory? = null,
            override val raw: ByteArray,
        ) : SonyTandem

        data class QuickAccess(
            val key: QuickAccessKey? = null,
            val function: QuickAccessFunction? = null,
            val values: List<Int>,
            override val raw: ByteArray,
        ) : SonyTandem

        data class WearingStatus(
            val status: WearingDetectionStatus? = null,
            val result: WearingDetectionResult? = null,
            val values: List<Int>,
            override val raw: ByteArray,
        ) : SonyTandem

        data class Volume(
            val type: PlayInquiredType?,
            val value: Int,
            val muted: Boolean = false,
            val values: List<Int>,
            override val raw: ByteArray,
        ) : SonyTandem

        data class AudioEffect(
            val type: AudioInquiredType?,
            val enabled: Boolean,
            val values: List<Int>,
            override val raw: ByteArray,
        ) : SonyTandem

        data class Unknown(
            val dataType: Int?,
            val command: Int?,
            val payload: ByteArray,
            override val raw: ByteArray,
        ) : SonyTandem

        data class Table2Common(
            val family: String,
            val command: Int,
            val values: List<Int>,
            override val raw: ByteArray,
        ) : SonyTandem {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Table2Common) return false
                return family == other.family &&
                    command == other.command &&
                    values == other.values &&
                    raw.contentEquals(other.raw)
            }

            override fun hashCode(): Int {
                var result = family.hashCode()
                result = 31 * result + command
                result = 31 * result + values.hashCode()
                result = 31 * result + raw.contentHashCode()
                return result
            }
        }

        data class Table2Generic(
            val family: String,
            val inquiredType: Int?,
            val values: List<Int>,
            override val raw: ByteArray,
        ) : SonyTandem {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Table2Generic) return false
                return family == other.family &&
                    inquiredType == other.inquiredType &&
                    values == other.values &&
                    raw.contentEquals(other.raw)
            }

            override fun hashCode(): Int {
                var result = family.hashCode()
                result = 31 * result + (inquiredType ?: 0)
                result = 31 * result + values.hashCode()
                result = 31 * result + raw.contentHashCode()
                return result
            }
        }
    }

    // ── QCY response types ────────────────────────────────────────

    sealed interface Qcy : ParsedHeadphoneResponse {
        override val raw: ByteArray

        data class Battery(
            val leftLevel: Int,
            val rightLevel: Int,
            val caseLevel: Int,
            val leftCharging: Boolean,
            val rightCharging: Boolean,
            val caseCharging: Boolean,
            override val raw: ByteArray,
        ) : Qcy

        /**
         * QCY ANC state. Either field may be null when the response only carried
         * one of mode / value (e.g. separate GET responses for CMD 12 and CMD 7).
         * The repository merges nullable fields with current state.
         */
        data class NoiseControl(
            val mode: Int?,
            val noiseValue: Int?,
            override val raw: ByteArray,
        ) : Qcy

        data class EqData(
            val eqType: Int,
            val masterGain: Float,
            val bands: List<QcyEqBand>,
            override val raw: ByteArray,
        ) : Qcy

        data class DeviceInfo(
            val leftFirmware: String,
            val rightFirmware: String,
            override val raw: ByteArray,
        ) : Qcy

        data class Volume(
            val leftVolume: Int,
            val rightVolume: Int,
            override val raw: ByteArray,
        ) : Qcy

        /**
         * QCY function status (UUID_FUNCTION_V1, 0x000F).
         * byte[0]: in-ear detection status
         * byte[1]: transparency/monitor status
         */
        data class FunctionStatus(
            val inEarDetectionOn: Boolean,
            val transparencyOn: Boolean,
            override val raw: ByteArray,
        ) : Qcy

        data class Generic(
            val cmdId: Byte,
            val values: List<Int>,
            override val raw: ByteArray,
        ) : Qcy
    }

    // ── Cross-brand wrappers ──────────────────────────────────────

    /**
     * Batch wrapper for adapters that decode a single transport frame into
     * multiple logical responses (e.g. QCY multi-TLV frames). The repository
     * dispatches each item individually.
     */
    data class Batch(
        val items: List<ParsedHeadphoneResponse>,
        override val raw: ByteArray,
    ) : ParsedHeadphoneResponse {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Batch) return false
            return items == other.items && raw.contentEquals(other.raw)
        }

        override fun hashCode(): Int = 31 * items.hashCode() + raw.contentHashCode()
    }
}

/**
 * QCY EQ band data used in EqData responses.
 * frequency: uint16 LE, value / 100.0 = Hz
 * gain: int16 LE / 100.0 = dB, range [-12.70, +12.70]
 * q: uint16 LE / 100.0 = Q value
 */
data class QcyEqBand(
    val frequency: Int,
    val gain: Float,
    val q: Float,
    val bandType: Int = 0,
)

val Byte.unsigned: Int
    get() = toInt() and 0xFF

fun ByteArray.hexString(): String = joinToString(" ") { "%02X".format(it.unsigned) }

fun ByteArray.unsignedList(): List<Int> = map { it.unsigned }

fun Byte.percentageOrNull(): Int? = unsigned.takeIf { it in 0..100 }
