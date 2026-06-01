package dev.ignotus.openbuds.ble.sony

import dev.ignotus.openbuds.headphones.TandemChannel

/**
 * Callback interface for [SonyBleClient] events.
 *
 * The [HeadphoneRepository] implements this to receive scan results,
 * connection events, and incoming messages.
 */
interface SonyBleClientListener {
    fun onBluetoothUnavailable(reason: String)
    fun onUnsupportedEndpoint(diagnostics: UnsupportedEndpointDiagnostics)
    fun onDeviceFound(device: DiscoveredSonyDevice)
    fun onScanStateChanged(scanning: Boolean)
    fun onConnectionStateChanged(connected: Boolean, device: DiscoveredSonyDevice?)
    fun onReady(info: SonyBleConnectionInfo)
    fun onMessage(channel: TandemChannel, raw: ByteArray)
    fun onLog(message: String)
}
