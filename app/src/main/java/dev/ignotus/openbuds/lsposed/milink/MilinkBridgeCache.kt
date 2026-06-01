package dev.ignotus.openbuds.lsposed.milink

import android.os.SystemClock
import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import java.util.concurrent.ConcurrentHashMap

class MilinkBridgeCache(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val entries = ConcurrentHashMap<String, Entry>()

    @Volatile
    private var authorizedMacs: Set<String> = emptySet()

    @Volatile
    var adapterEnabled: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    fun updateStatus(enabled: Boolean, authorized: Collection<String>) {
        adapterEnabled = enabled
        authorizedMacs = authorized.mapNotNull(MilinkAirpodsTargetMatcher::normalizeMac).toSet()
        if (!enabled) clearSnapshots()
    }

    fun updateSnapshot(snapshot: MilinkDeviceSnapshot) {
        val mac = MilinkAirpodsTargetMatcher.normalizeMac(snapshot.mac) ?: return
        if (snapshot.connected) {
            entries[mac] = Entry(snapshot.copy(mac = mac), now())
        } else {
            entries.remove(mac)
        }
    }

    fun snapshotFor(mac: String?): MilinkDeviceSnapshot? {
        if (!adapterEnabled) return null
        val normalized = MilinkAirpodsTargetMatcher.normalizeMac(mac) ?: return null
        if (normalized !in authorizedMacs) return null
        val entry = entries[normalized] ?: return null
        if (now() - entry.savedAtMs > ttlMs) {
            entries.remove(normalized)
            return null
        }
        return entry.snapshot.takeIf { it.connected }
    }

    fun authorizedMacs(): Set<String> =
        if (adapterEnabled) authorizedMacs else emptySet()

    fun markError(message: String?) {
        lastError = message
        if (message != null) {
            adapterEnabled = false
            authorizedMacs = emptySet()
            clearSnapshots()
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
    }
}
