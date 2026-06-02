package dev.ignotus.openbuds.lsposed.mitws

import android.os.SystemClock
import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import dev.ignotus.openbuds.integration.milink.normalizeMac
import java.util.concurrent.ConcurrentHashMap

class MiTwsBridgeCache(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val entries = ConcurrentHashMap<String, Entry>()

    @Volatile
    private var authorizedMacs: Set<String> = emptySet()

    /**
     * MACs that were ever authorized by the bridge, persisted across bridge
     * disconnections. This prevents classification flapping when the bridge
     * hasn't connected yet or is temporarily unavailable — the hook can still
     * recognize known devices as MiTWS.
     */
    @Volatile
    private var knownAuthorizedMacs: Set<String> = emptySet()

    @Volatile
    var adapterEnabled: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    fun updateStatus(enabled: Boolean, authorized: Collection<String>) {
        adapterEnabled = enabled
        val normalized = authorized.mapNotNull { it.normalizeMac() }.toSet()
        authorizedMacs = normalized
        // Persist authorized MACs across bridge disconnections
        knownAuthorizedMacs = knownAuthorizedMacs + normalized
        if (!enabled) clearSnapshots()
    }

    fun updateSnapshot(snapshot: MilinkDeviceSnapshot) {
        val mac = snapshot.mac.normalizeMac() ?: return
        if (snapshot.connected) {
            entries[mac] = Entry(snapshot.copy(mac = mac), now())
        } else {
            entries.remove(mac)
        }
    }

    /**
     * Returns a snapshot for the given MAC if available and fresh enough.
     * Falls back to a minimal placeholder snapshot if the MAC is a known
     * authorized device but no live data is available — this ensures
     * classification as MiTWS even before the bridge connects.
     */
    fun snapshotFor(mac: String?): MilinkDeviceSnapshot? {
        if (!adapterEnabled) return null
        val normalized = mac?.normalizeMac() ?: return null
        if (normalized !in authorizedMacs) {
            // Not in current authorized list — check if it's a known device
            // from a previous bridge session
            if (normalized in knownAuthorizedMacs) {
                return placeholderSnapshot(normalized)
            }
            return null
        }
        val entry = entries[normalized]
        if (entry == null) {
            // Authorized but no snapshot yet (bridge hasn't pushed data)
            return placeholderSnapshot(normalized)
        }
        // Return stale snapshot if within stale tolerance to avoid classification flapping.
        if (now() - entry.savedAtMs > ttlMs) {
            if (now() - entry.savedAtMs > STALE_TOLERANCE_MS) {
                entries.remove(normalized)
                // Fall through to placeholder — MAC is still known authorized
            } else {
                return entry.snapshot.takeIf { it.connected }
            }
        } else {
            return entry.snapshot.takeIf { it.connected }
        }
        return placeholderSnapshot(normalized)
    }

    fun authorizedMacs(): Set<String> =
        if (adapterEnabled) authorizedMacs else emptySet()

    fun authorizedSnapshots(): List<MilinkDeviceSnapshot> =
        if (!adapterEnabled) {
            emptyList()
        } else {
            authorizedMacs.mapNotNull(::snapshotFor).sortedBy { it.mac }
        }

    fun markError(message: String?) {
        lastError = message
        if (message != null) {
            adapterEnabled = false
            authorizedMacs = emptySet()
            clearSnapshots()
            // knownAuthorizedMacs intentionally NOT cleared — survives bridge restart
        }
    }

    fun clearSnapshots() {
        entries.clear()
    }

    private fun placeholderSnapshot(mac: String): MilinkDeviceSnapshot =
        MilinkDeviceSnapshot(
            mac = mac,
            name = "",
            brand = "",
            model = "",
            deviceId = "",
            connected = true,
            protocolReady = false,
            leftBattery = null,
            rightBattery = null,
            caseBattery = null,
            singleBattery = null,
            leftWearing = null,
            rightWearing = null,
            leftCharging = null,
            rightCharging = null,
            caseCharging = null,
            ancMode = null,
            ringing = false,
            supportsBattery = false,
            supportsNoiseControl = false,
            supportsWearing = false,
            supportsRing = false,
            revision = 0L,
            updatedAt = now(),
        )

    private data class Entry(
        val snapshot: MilinkDeviceSnapshot,
        val savedAtMs: Long,
    )

    companion object {
        const val DEFAULT_TTL_MS = 10_000L
        const val STALE_TOLERANCE_MS = 300_000L // 5 min — prevent classification flapping
    }
}
