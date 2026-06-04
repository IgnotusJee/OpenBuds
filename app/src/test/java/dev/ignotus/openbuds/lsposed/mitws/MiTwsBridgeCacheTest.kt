package dev.ignotus.openbuds.lsposed.mitws

import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        // Advance past stale tolerance — entry is evicted.
        nowMs += 301_000L

        assertNull(cache.snapshotFor(snapshot.mac))
    }

    @Test
    fun snapshotFor_returnsNull_whenAuthorizedButNoSnapshot() {
        cache.updateStatus(enabled = true, authorized = listOf("AA:BB:CC:DD:EE:FF"))

        assertNull(cache.snapshotFor("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun knownAuthorizedMacs_survivesBridgeDisconnection() {
        val snapshot = snapshot("AA:BB:CC:DD:EE:FF")
        cache.updateStatus(enabled = true, authorized = listOf(snapshot.mac))
        cache.updateSnapshot(snapshot)

        // Simulate bridge disconnect. markError keeps the last known state so
        // short binder/session flaps do not immediately drop MiTWS classification.
        cache.markError("bridge_disconnected")

        assertEquals(snapshot, cache.snapshotFor(snapshot.mac))
        assertTrue(cache.isClassificationEligible(snapshot.mac))

        // Simulate bridge reconnect
        cache.updateStatus(enabled = true, authorized = listOf(snapshot.mac))
        val result = cache.snapshotFor(snapshot.mac)
        assertEquals(snapshot.mac, result?.mac)
    }

    @Test
    fun markError_softDegradesAndKeepsAuthorizedState() {
        val snapshot = snapshot("AA:BB:CC:DD:EE:FF")
        cache.updateStatus(enabled = true, authorized = listOf(snapshot.mac))
        cache.updateSnapshot(snapshot)

        cache.markError("bridge_down")

        assertEquals(snapshot, cache.snapshotFor(snapshot.mac))
        assertEquals(setOf(snapshot.mac), cache.authorizedMacs())
    }

    @Test
    fun isClassificationEligible_survivesKnownAuthorizedWithoutLiveSnapshot() {
        val snapshot = snapshot("AA:BB:CC:DD:EE:FF")
        cache.updateStatus(enabled = true, authorized = listOf(snapshot.mac))
        cache.updateSnapshot(snapshot)

        nowMs += 301_000L
        assertNull(cache.snapshotFor(snapshot.mac))
        assertTrue(cache.isClassificationEligible(snapshot.mac))
    }

    @Test
    fun authorizedSnapshots_excludesMissingLiveSnapshot() {
        cache.updateStatus(enabled = true, authorized = listOf("AA:BB:CC:DD:EE:FF"))

        assertEquals(emptyList<MilinkDeviceSnapshot>(), cache.authorizedSnapshots())
    }

    @Test
    fun isClassificationEligible_requiresEnabled() {
        val snapshot = snapshot("AA:BB:CC:DD:EE:FF")
        cache.updateStatus(enabled = true, authorized = listOf(snapshot.mac))
        assertTrue(cache.isClassificationEligible(snapshot.mac))

        cache.updateStatus(enabled = false, authorized = listOf(snapshot.mac))
        assertFalse(cache.isClassificationEligible(snapshot.mac))
    }

    private fun snapshot(mac: String): MilinkDeviceSnapshot =
        MilinkDeviceSnapshot(
            mac = mac,
            name = "LinkBuds S",
            brand = "Sony",
            model = "LinkBuds S",
            deviceId = MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
            formFactor = MiTwsRuntimeProjection.FORM_FACTOR_TRUE_WIRELESS,
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
            ancMode = 1,
            ringing = false,
            currentVolume = null,
            currentAudioEffectState = null,
            supportsBattery = true,
            supportsNoiseControl = true,
            supportsWearing = true,
            supportsRing = false,
            supportsVolumeControl = false,
            supportsAudioEffect = false,
            revision = 1L,
            updatedAt = nowMs,
        )
}
