package dev.ignotus.openbuds.ble

import dev.ignotus.openbuds.headphones.TandemChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * Routes connect/disconnect/send calls to the correct [HeadphoneTransportClient]
 * based on the device being connected. Also aggregates scan results from all
 * registered clients, deduplicating by MAC address.
 *
 * Usage:
 * ```
 * val selector = HeadphoneTransportSelector(
 *     clients = listOf(sonyClient, qcyClient),
 *     listener = repository,  // implements SonyBleClientListener
 * )
 * selector.startScan(strictFilter = false)
 * // selector forwards onDeviceFound to listener with dedup
 *
 * // When user taps "connect":
 * val client = selector.pickFor(device)
 * client.connect(device)
 * ```
 */
class HeadphoneTransportSelector(
    private val clients: List<HeadphoneTransportClient>,
) {
    /** The client that was most recently used for a connect() call. */
    @Volatile
    var activeClient: HeadphoneTransportClient? = null
        private set

    /** Deduplication cache: MAC → timestamp of last onDeviceFound. */
    private val dedupCache = ConcurrentHashMap<String, Long>()
    private val dedupWindowMs = 5_000L

    /**
     * Pick the transport client that should handle [device].
     * Returns the first client whose [HeadphoneTransportClient.matches] returns true.
     * Order of [clients] determines priority (first wins).
     */
    fun pickFor(device: DiscoveredSonyDevice, reportedModelName: String? = null): HeadphoneTransportClient? =
        clients.firstOrNull { it.matches(device, reportedModelName) }

    // ── Scan aggregation ─────────────────────────────────────────

    /**
     * Start scan on all clients. QCY's startScan is a no-op (shares Sony's
     * scan results); SonyBleClient drives the actual BLE scan.
     */
    fun startScan(strictFilter: Boolean) {
        clients.forEach { it.startScan(strictFilter) }
    }

    fun stopScan() {
        clients.forEach { it.stopScan() }
    }

    /**
     * Check whether [device] has already been reported recently (by MAC).
     * Callers (i.e. the Sony scan callback) should invoke this before
     * forwarding to [SonyBleClientListener.onDeviceFound] to avoid duplicates.
     *
     * Returns true if the device should be suppressed (already seen within
     * the dedup window).
     */
    fun isDuplicateScanResult(device: DiscoveredSonyDevice): Boolean {
        val now = System.currentTimeMillis()
        val lastSeen = dedupCache[device.address]
        if (lastSeen != null && now - lastSeen < dedupWindowMs) {
            return true
        }
        dedupCache[device.address] = now
        return false
    }

    // ── Connection routing ───────────────────────────────────────

    /**
     * Connect to [device] using the appropriate client.
     * Sets [activeClient] for subsequent sendToChannel/disconnect calls.
     */
    fun connect(device: DiscoveredSonyDevice) {
        val client = pickFor(device)
            ?: throw IllegalArgumentException("No transport client matches device: ${device.name} (${device.address})")
        activeClient = client
        client.connect(device)
    }

    /**
     * Connect using a raw address/name. Picks the client based on the name.
     */
    fun connect(address: String, name: String) {
        val device = DiscoveredSonyDevice(
            name = name,
            address = address,
            rssi = 0,
            source = "manual",
        )
        connect(device)
    }

    fun disconnect() {
        clients.forEach { it.disconnect() }
        activeClient = null
    }

    fun sendToChannel(channel: TandemChannel, bytes: ByteArray) {
        activeClient?.sendToChannel(channel, bytes)
            ?: error("No active transport client; cannot send on $channel")
    }

    fun availableChannels(): Set<TandemChannel> =
        activeClient?.availableChannels() ?: emptySet()

    fun refreshUnsupportedEndpointProbe() {
        activeClient?.refreshUnsupportedEndpointProbe()
    }
}
