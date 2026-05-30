package dev.ignotus.openbuds.protocol.sony

data class TandemMessage(
    val dataType: Byte,
    val command: Byte,
    val payload: ByteArray = byteArrayOf(),
) {
    fun toByteArray(): ByteArray = byteArrayOf(dataType, command) + payload

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TandemMessage) return false
        return dataType == other.dataType &&
            command == other.command &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = dataType.toInt()
        result = 31 * result + command
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

object SonyTandemFrame {
    fun message(command: Byte, payload: ByteArray = byteArrayOf()): ByteArray =
        TandemMessage(SonyTandemConstants.DATA_MDR, command, payload).toByteArray()
}
