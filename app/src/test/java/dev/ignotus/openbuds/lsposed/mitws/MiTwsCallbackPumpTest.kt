package dev.ignotus.openbuds.lsposed.mitws

import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiTwsCallbackPumpTest {
    @Test
    fun registerDispatchesInitialSnapshot() {
        val callback = FakeMmaCallback()
        val pump = pump()

        pump.register(callback, listOf(snapshot(revision = 1L)))

        assertEquals(listOf(true), callback.connections)
        assertEquals(listOf(listOf(70, 80, 90)), callback.batteries)
        assertEquals(listOf(1), callback.ancStates)
        assertEquals(listOf(1), callback.reportAncStates)
        assertEquals(listOf(MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID), callback.deviceIds)
        assertEquals(listOf(false), callback.ringStates)
    }

    @Test
    fun unchangedRevisionIsNotRedispatched() {
        val callback = FakeMmaCallback()
        val pump = pump()
        val snapshot = snapshot(revision = 1L)

        pump.register(callback)
        pump.dispatchSnapshot(snapshot)
        pump.dispatchSnapshot(snapshot)

        assertEquals(1, callback.connections.size)
        assertEquals(1, callback.batteries.size)
    }

    @Test
    fun updatedRevisionDispatchesSnapshot() {
        val callback = FakeMmaCallback()
        val pump = pump()

        pump.register(callback)
        pump.dispatchSnapshot(snapshot(revision = 1L, leftBattery = 70, ancMode = 1, ringing = false))
        pump.dispatchSnapshot(snapshot(revision = 2L, leftBattery = 60, ancMode = 2, ringing = true))

        assertEquals(listOf(listOf(70, 80, 90), listOf(60, 80, 90)), callback.batteries)
        assertEquals(listOf(1, 2), callback.ancStates)
        assertEquals(listOf(false, true), callback.ringStates)
    }

    @Test
    fun unregisterStopsDispatch() {
        val callback = FakeMmaCallback()
        val pump = pump()

        pump.register(callback)
        assertTrue(pump.unregister(callback))
        pump.dispatchSnapshot(snapshot(revision = 1L))

        assertEquals(0, callback.connections.size)
        assertEquals(0, callback.batteries.size)
    }

    @Test
    fun dispatchConnectionSendsOnlyConnectionState() {
        val callback = FakeMmaCallback()
        val pump = pump()

        pump.register(callback)
        pump.dispatchConnection(snapshot(revision = 1L), connected = false)

        assertEquals(listOf(false), callback.connections)
        assertEquals(0, callback.batteries.size)
    }

    private fun pump(): MiTwsCallbackPump =
        MiTwsCallbackPump(
            deviceLookup = { null },
            deviceIdForMac = { MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID },
            asyncDispatcher = DirectDispatcher,
            allowNullDevice = true,
        )

    private fun snapshot(
        revision: Long,
        leftBattery: Int = 70,
        ancMode: Int? = 1,
        ringing: Boolean = false,
    ): MilinkDeviceSnapshot =
        MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = "LinkBuds S",
            brand = "Sony",
            model = "LinkBuds S",
            deviceId = MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
            connected = true,
            protocolReady = true,
            leftBattery = leftBattery,
            rightBattery = 80,
            caseBattery = 90,
            singleBattery = null,
            leftWearing = true,
            rightWearing = false,
            leftCharging = false,
            rightCharging = false,
            caseCharging = false,
            ancMode = ancMode,
            ringing = ringing,
            supportsBattery = true,
            supportsNoiseControl = true,
            supportsWearing = true,
            supportsRing = true,
            revision = revision,
            updatedAt = revision,
        )

    class FakeMmaCallback {
        val connections = mutableListOf<Boolean>()
        val batteries = mutableListOf<List<Int>>()
        val ancStates = mutableListOf<Int>()
        val reportAncStates = mutableListOf<Int>()
        val deviceIds = mutableListOf<String>()
        val ringStates = mutableListOf<Boolean>()

        fun onConnectMmaStateChanged(device: Any?, connected: Boolean) {
            connections.add(connected)
        }

        fun onBatteryLevel(device: Any?, levels: IntArray) {
            batteries.add(levels.toList())
        }

        fun onAncStateChanged(device: Any?, state: Int) {
            ancStates.add(state)
        }

        fun onReportAncState(device: Any?, state: Int) {
            reportAncStates.add(state)
        }

        fun onDeviceIdUpdate(device: Any?, deviceId: String) {
            deviceIds.add(deviceId)
        }

        fun onRingStateChanged(device: Any?, ringing: Boolean) {
            ringStates.add(ringing)
        }
    }
}
