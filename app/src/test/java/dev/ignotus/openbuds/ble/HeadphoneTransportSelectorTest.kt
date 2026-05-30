package dev.ignotus.openbuds.ble

import dev.ignotus.openbuds.ble.sony.DiscoveredSonyDevice
import dev.ignotus.openbuds.headphones.TandemChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for HeadphoneTransportSelector covering brand picking, dedup,
 * and connection routing. Uses a fake [HeadphoneTransportClient] to avoid
 * Android BLE deps.
 */
class HeadphoneTransportSelectorTest {

    private lateinit var sonyClient: FakeTransportClient
    private lateinit var qcyClient: FakeTransportClient
    private lateinit var selector: HeadphoneTransportSelector

    @Before
    fun setup() {
        sonyClient = FakeTransportClient(
            id = "sony-tandem",
            matchPredicate = { device, _ ->
                val name = device.name.lowercase()
                !name.contains("qcy") && (name.contains("sony") || name.startsWith("wf-"))
            },
            channels = setOf(TandemChannel.GATT_V2_HPC, TandemChannel.GATT_V2_MC),
        )
        qcyClient = FakeTransportClient(
            id = "qcy-gatt",
            matchPredicate = { device, _ -> device.name.lowercase().contains("qcy") },
            channels = setOf(TandemChannel.QCY_SETTING_WRITE, TandemChannel.QCY_BATTERY),
        )
        selector = HeadphoneTransportSelector(listOf(sonyClient, qcyClient))
    }

    @Test
    fun pickFor_sonyName_returnsSonyClient() {
        val device = device("WF-1000XM5")
        assertSame(sonyClient, selector.pickFor(device))
    }

    @Test
    fun pickFor_qcyName_returnsQcyClient() {
        val device = device("QCY-C30S")
        assertSame(qcyClient, selector.pickFor(device))
    }

    @Test
    fun pickFor_unknownDevice_returnsNull() {
        assertNull(selector.pickFor(device("UnknownDevice")))
    }

    @Test
    fun pickFor_ambiguousName_firstClientWins() {
        // Sony-like fake that matches everything, registered first; QCY won't match.
        val matchAll = FakeTransportClient("match-all", { _, _ -> true }, emptySet())
        val sel = HeadphoneTransportSelector(listOf(matchAll, qcyClient))
        assertSame(matchAll, sel.pickFor(device("QCY-C30S")))
    }

    @Test
    fun connect_setsActiveClient() {
        val device = device("QCY-C30S")
        selector.connect(device)
        assertSame(qcyClient, selector.activeClient)
        assertEquals(listOf(device), qcyClient.connectCalls)
    }

    @Test
    fun connect_unknownDevice_throws() {
        val device = device("Mystery")
        try {
            selector.connect(device)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun disconnect_disconnectsAllClientsAndClearsActive() {
        selector.connect(device("QCY-C30S"))
        selector.disconnect()
        assertNull(selector.activeClient)
        assertEquals(1, sonyClient.disconnectCount)
        assertEquals(1, qcyClient.disconnectCount)
    }

    @Test
    fun sendToChannel_routesToActiveClient() {
        selector.connect(device("QCY-C30S"))
        val bytes = byteArrayOf(1, 2, 3)
        selector.sendToChannel(TandemChannel.QCY_SETTING_WRITE, bytes)
        assertEquals(1, qcyClient.sentMessages.size)
        assertEquals(TandemChannel.QCY_SETTING_WRITE, qcyClient.sentMessages[0].first)
        assertTrue(qcyClient.sentMessages[0].second.contentEquals(bytes))
        assertEquals(0, sonyClient.sentMessages.size)
    }

    @Test
    fun sendToChannel_noActive_throws() {
        try {
            selector.sendToChannel(TandemChannel.QCY_BATTERY, byteArrayOf())
            throw AssertionError("Expected error()")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun availableChannels_returnsActiveClientChannels() {
        selector.connect(device("QCY-C30S"))
        assertEquals(qcyClient.availableChannels(), selector.availableChannels())
    }

    @Test
    fun availableChannels_noActive_returnsEmpty() {
        assertEquals(emptySet<TandemChannel>(), selector.availableChannels())
    }

    @Test
    fun startScan_broadcastsToAllClients() {
        selector.startScan(strictFilter = true)
        assertEquals(1, sonyClient.startScanCount)
        assertEquals(1, qcyClient.startScanCount)
    }

    @Test
    fun stopScan_broadcastsToAllClients() {
        selector.stopScan()
        assertEquals(1, sonyClient.stopScanCount)
        assertEquals(1, qcyClient.stopScanCount)
    }

    @Test
    fun isDuplicateScanResult_firstTime_returnsFalse() {
        val device = device("WF-1000XM5", "AA:BB:CC:DD:EE:FF")
        assertFalse(selector.isDuplicateScanResult(device))
    }

    @Test
    fun isDuplicateScanResult_secondTime_returnsTrue() {
        val device = device("WF-1000XM5", "AA:BB:CC:DD:EE:FF")
        selector.isDuplicateScanResult(device)
        assertTrue(selector.isDuplicateScanResult(device))
    }

    @Test
    fun isDuplicateScanResult_differentAddress_returnsFalse() {
        selector.isDuplicateScanResult(device("WF-A", "AA:BB:CC:DD:EE:11"))
        assertFalse(selector.isDuplicateScanResult(device("WF-B", "AA:BB:CC:DD:EE:22")))
    }

    // ── helpers ─────────────────────────────────────────────────

    private fun device(name: String, address: String = "00:00:00:00:00:00"): DiscoveredSonyDevice =
        DiscoveredSonyDevice(name = name, address = address, rssi = 0, source = "test")
}

/**
 * Test double for [HeadphoneTransportClient]. Records calls and returns
 * configured channel set / match result.
 */
private class FakeTransportClient(
    override val id: String,
    private val matchPredicate: (DiscoveredSonyDevice, String?) -> Boolean,
    private val channels: Set<TandemChannel>,
) : HeadphoneTransportClient {

    val connectCalls = mutableListOf<DiscoveredSonyDevice>()
    var disconnectCount = 0
    var startScanCount = 0
    var stopScanCount = 0
    val sentMessages = mutableListOf<Pair<TandemChannel, ByteArray>>()

    override fun matches(device: DiscoveredSonyDevice, reportedModelName: String?): Boolean =
        matchPredicate(device, reportedModelName)

    override fun startScan(strictFilter: Boolean) {
        startScanCount++
    }

    override fun stopScan() {
        stopScanCount++
    }

    override fun connect(device: DiscoveredSonyDevice) {
        connectCalls.add(device)
    }

    override fun disconnect() {
        disconnectCount++
    }

    override fun sendToChannel(channel: TandemChannel, bytes: ByteArray) {
        sentMessages.add(channel to bytes)
    }

    override fun availableChannels(): Set<TandemChannel> = channels
}
