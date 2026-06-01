package dev.ignotus.openbuds.ble.sony

internal interface SonyTandemSession {
    val connectedDevice: DiscoveredSonyDevice?

    fun connect()
    fun disconnect(notify: Boolean)
    fun send(bytes: ByteArray)
    fun sendToChannel(channel: SonyChannel, bytes: ByteArray)
    fun availableChannels(): Set<SonyChannel>
    fun refreshUnsupportedEndpointProbe() {}
}
