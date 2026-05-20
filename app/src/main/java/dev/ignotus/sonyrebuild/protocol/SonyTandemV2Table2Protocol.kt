package dev.ignotus.sonyrebuild.protocol

object SonyTandemV2Table2Protocol {
    fun parse(raw: ByteArray): ParsedTandemResponse {
        val normalized = if (raw.firstOrNull() == SonyTandemFrame.DATA_MDR) raw else byteArrayOf(SonyTandemFrame.DATA_MDR) + raw
        val command = normalized.getOrNull(1)
        val payload = if (normalized.size > 2) normalized.copyOfRange(2, normalized.size) else byteArrayOf()
        return ParsedTandemResponse.Unknown(
            dataType = normalized.firstOrNull()?.unsigned,
            command = command?.unsigned,
            payload = payload,
            raw = raw,
        )
    }
}
