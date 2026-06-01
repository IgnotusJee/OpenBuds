package dev.ignotus.openbuds.ble.sony
import dev.ignotus.openbuds.ble.DiscoveredDevice

internal interface SonyTandemSession {
    val connectedDevice: DiscoveredDevice?

    fun connect()
    fun disconnect(notify: Boolean)
    fun send(bytes: ByteArray)
    fun sendToChannel(channel: SonyChannel, bytes: ByteArray)
    fun availableChannels(): Set<SonyChannel>
    fun refreshUnsupportedEndpointProbe() {}
}
