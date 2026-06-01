package dev.ignotus.openbuds.ble.sony

import dev.ignotus.openbuds.ble.transport.SppFrameType
import dev.ignotus.openbuds.ble.transport.SppPayloadMapper
import dev.ignotus.openbuds.ble.transport.SppPayloadMapping
import dev.ignotus.openbuds.protocol.sony.SonyTandemConstants.DATA_MDR as TANDEM_DATA_MDR
import dev.ignotus.openbuds.protocol.sony.SonyTandemConstants.DATA_MDR_NO2 as TANDEM_DATA_MDR_NO2

object SonySppPayloadMapper : SppPayloadMapper {
    override fun outbound(bytes: ByteArray): SppPayloadMapping =
        outboundFromTandemBytes(bytes)

    fun outboundFromTandemBytes(bytes: ByteArray): SppPayloadMapping {
        if (bytes.isEmpty()) return SppPayloadMapping(SppFrameType.DATA_MDR, bytes)
        return when (bytes[0]) {
            TANDEM_DATA_MDR ->
                SppPayloadMapping(SppFrameType.DATA_MDR, bytes.copyOfRange(1, bytes.size))
            TANDEM_DATA_MDR_NO2 ->
                SppPayloadMapping(SppFrameType.DATA_MDR_NO2, bytes.copyOfRange(1, bytes.size))
            else -> SppPayloadMapping(SppFrameType.DATA_MDR, bytes)
        }
    }

    override fun inbound(type: SppFrameType, payload: ByteArray): ByteArray? =
        inboundToTandemBytes(type, payload)

    fun inboundToTandemBytes(type: SppFrameType, payload: ByteArray): ByteArray? =
        when (type) {
            SppFrameType.DATA_MDR,
            SppFrameType.SHOT_MDR,
            SppFrameType.LARGE_DATA_MDR ->
                byteArrayOf(TANDEM_DATA_MDR) + payload
            SppFrameType.DATA_MDR_NO2,
            SppFrameType.SHOT_MDR_NO2 ->
                byteArrayOf(TANDEM_DATA_MDR_NO2) + payload
            SppFrameType.ACK,
            SppFrameType.UNKNOWN -> null
        }
}
