package dev.ignotus.openbuds.lsposed.mitws

import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiTwsBridgeCacheTest {
    private var nowMs = 1_000L
    private val cache = MiTwsBridgeCache(ttlMs = 5_000L, now = { nowMs })

    @Test
    fun snapshotFor_requiresEnabledAuthorizedAndFreshSnapshot() {
        val snapshot = snapshot("AA:BB:CC:DD:EE:FF")

        cache.updateSnapshot(snapshot)
        assertNull(cache.snapshotFor(snapshot.mac))

        cache.updateStatus(enabled = true, authorized = listOf(snapshot.mac))
        assertEquals(snapshot, cache.snapshotFor(snapshot.mac))
    }

    @Test
    fun snapshotFor_returnsStaleSnapshot_withinStaleTolerance() {
        val snapshot = snapshot("AA:BB:CC:DD:EE:FF")
        cache.updateStatus(enabled = true, authorized = listOf(snapshot.mac))
        cache.updateSnapshot(snapshot)

        // Advance past TTL but within stale tolerance (300s)
        nowMs += 60_000L

        assertEquals(snapshot, cache.snapshotFor(snapshot.mac))
    }

    @Test
    fun snapshotFor_returnsNull_afterStaleTolerance() {
        val snapshot = snapshot("AA:BB:CC:DD:EE:FF")
        cache.updateStatus(enabled = true, authorized = listOf(snapshot.mac))
        cache.updateSnapshot(snapshot)

        // Advance past stale tolerance
        nowMs += 301_000L

        assertNull(cache.snapshotFor(snapshot.mac))
    }

    @Test
    fun markError_disablesAndClearsStrictBridgeState() {
        val snapshot = snapshot("AA:BB:CC:DD:EE:FF")
        cache.updateStatus(enabled = true, authorized = listOf(snapshot.mac))
        cache.updateSnapshot(snapshot)

        cache.markError("bridge_down")

        assertNull(cache.snapshotFor(snapshot.mac))
        assertEquals(emptySet<String>(), cache.authorizedMacs())
    }

    private fun snapshot(mac: String): MilinkDeviceSnapshot =
        MilinkDeviceSnapshot(
            mac = mac,
            name = "LinkBuds S",
            brand = "Sony",
            model = "LinkBuds S",
            deviceId = MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
            connected = true,
            protocolReady = true,
            leftBattery = 70,
            rightBattery = 80,
            caseBattery = 90,
            singleBattery = null,
            leftWearing = null,
            rightWearing = null,
            leftCharging = null,
            rightCharging = null,
            caseCharging = null,
            revision = 1L,
            updatedAt = nowMs,
        )
}
