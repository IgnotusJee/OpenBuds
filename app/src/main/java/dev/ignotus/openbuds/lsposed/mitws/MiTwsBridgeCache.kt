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
     * Returns only a live runtime snapshot for the given MAC if available and
     * fresh enough. Classification stickiness is handled separately via
     * [isClassificationEligible].
     */
    fun snapshotFor(mac: String?): MilinkDeviceSnapshot? {
        if (!adapterEnabled) return null
        val normalized = mac?.normalizeMac() ?: return null
        if (normalized !in authorizedMacs) return null
        val entry = entries[normalized]
        if (entry == null) return null
        // Return stale snapshot if within stale tolerance to avoid classification flapping.
        if (now() - entry.savedAtMs > ttlMs) {
            if (now() - entry.savedAtMs > STALE_TOLERANCE_MS) {
                entries.remove(normalized)
                return null
            } else {
                return entry.snapshot.takeIf { it.connected }
            }
        } else {
            return entry.snapshot.takeIf { it.connected }
        }
        return null
    }

    fun isClassificationEligible(mac: String?): Boolean {
        if (!adapterEnabled) return false
        val normalized = mac?.normalizeMac() ?: return false
        return normalized in authorizedMacs || normalized in knownAuthorizedMacs
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
            // Soft-degrade on bridge errors. Keep adapterEnabled and the last known
            // authorized/snapshot state so short binder/session flaps do not make
            // this process instantly lose MiTWS classification and fall back to a
            // third-party card.
            //
            // The next successful status/session refresh will replace these values.
            // We still clear live snapshots only when the app explicitly disables
            // the adapter via updateStatus(enabled=false).
        }
    }

    fun clearSnapshots() {
        entries.clear()
    }

    private data class Entry(
        val snapshot: MilinkDeviceSnapshot,
        val savedAtMs: Long,
    )

    companion object {
        const val DEFAULT_TTL_MS = 10_000L
        const val STALE_TOLERANCE_MS = 300_000L // 5 min — prevent classification flapping
    }
}
