package dev.ignotus.openbuds.ble

import dev.ignotus.openbuds.ble.sony.DiscoveredSonyDevice
import dev.ignotus.openbuds.ble.sony.UnsupportedEndpointDiagnostics
import dev.ignotus.openbuds.headphones.TandemChannel

/**
 * Transport-layer abstraction for BLE headphone communication.
 *
 * Each brand (Sony Tandem, QCY GATT, …) implements this interface so that
 * [HeadphoneRepository] can route connect/disconnect/send calls through a
 * single active client without brand-specific branching.
 *
 * The listener ([HeadphoneTransportListener]) is shared across all clients;
 * the repository acts as the single listener.
 */
interface HeadphoneTransportClient {

    /** Stable identifier: "sony-tandem", "qcy-gatt", etc. */
    val id: String

    /**
     * Return true if this client should handle [device].
     * Called by [HeadphoneTransportSelector] during device → client routing.
     * The [reportedModelName] is an optional hint from bonded device records.
     */
    fun matches(device: DiscoveredSonyDevice, reportedModelName: String?): Boolean

    /** Start BLE scan for devices this client can handle. */
    fun startScan(strictFilter: Boolean)

    /** Stop any ongoing BLE scan. */
    fun stopScan()

    /** Connect to [device] using this client's transport. */
    fun connect(device: DiscoveredSonyDevice)

    /** Disconnect and release GATT resources. */
    fun disconnect()

    /** Send [bytes] on the given [channel]. */
    fun sendToChannel(channel: TandemChannel, bytes: ByteArray)

    /** Channels currently available (i.e. GATT endpoints discovered). */
    fun availableChannels(): Set<TandemChannel>

    /**
     * Re-run the unsupported-endpoint probe for the current connection.
     * Only meaningful for the Sony Tandem client; others no-op.
     */
    fun refreshUnsupportedEndpointProbe() {}
}

/**
 * Connection metadata reported when a transport is protocol-ready.
 */
data class HeadphoneConnectionInfo(
    val mtu: Int = 23,
    val writableValueLength: Int? = null,
    val optimalMtu: Int? = null,
    val transport: String = "GATT_HPC",
)

/**
 * Callback interface shared by all [HeadphoneTransportClient] implementations.
 * The repository implements this to receive scan results, connection events,
 * diagnostics, and incoming protocol messages.
 */
interface HeadphoneTransportListener {
    fun onBluetoothUnavailable(reason: String)
    fun onUnsupportedEndpoint(diagnostics: UnsupportedEndpointDiagnostics)
    fun onDeviceFound(device: DiscoveredSonyDevice)
    fun onScanStateChanged(scanning: Boolean)
    fun onConnectionStateChanged(connected: Boolean, device: DiscoveredSonyDevice?)
    fun onReady(info: HeadphoneConnectionInfo)
    fun onMessage(channel: TandemChannel, raw: ByteArray)
    fun onLog(message: String)
}
