package dev.ignotus.openbuds.ble.sony

import dev.ignotus.openbuds.headphones.TandemChannel

internal interface SonyTandemSession {
    val connectedDevice: DiscoveredSonyDevice?

    fun connect()
    fun disconnect(notify: Boolean)
    fun sendToChannel(channel: TandemChannel, bytes: ByteArray)
    fun availableChannels(): Set<TandemChannel>
    fun refreshUnsupportedEndpointProbe() {}
}
