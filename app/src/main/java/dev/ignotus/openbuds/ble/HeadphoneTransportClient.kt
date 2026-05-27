package dev.ignotus.openbuds.ble

import dev.ignotus.openbuds.headphones.TandemChannel

/**
 * Transport-layer abstraction for BLE headphone communication.
 *
 * Each brand (Sony Tandem, QCY GATT, …) implements this interface so that
 * [SonyHeadphoneRepository] can route connect/disconnect/send calls through a
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
 * Callback interface shared by all [HeadphoneTransportClient] implementations.
 * The repository implements this to receive scan results, connection events,
 * and incoming messages.
 *
 * For backward compatibility this is a typealias to [SonyBleClientListener].
 * Sony-specific callbacks (onUnsupportedEndpoint, onScanStateChanged) are
 * no-op for non-Sony clients (QCY) but remain available to avoid a sweeping
 * rename across the repository code. A follow-up PR-3 can split this if
 * brand-neutral callbacks become necessary.
 */
typealias HeadphoneTransportListener = SonyBleClientListener
