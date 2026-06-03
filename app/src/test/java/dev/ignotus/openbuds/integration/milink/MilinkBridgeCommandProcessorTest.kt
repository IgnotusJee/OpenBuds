package dev.ignotus.openbuds.integration.milink

import dev.ignotus.openbuds.lsposed.mitws.MiTwsDeviceIdPolicy
import dev.ignotus.openbuds.protocol.NoiseControlMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MilinkBridgeCommandProcessorTest {
    @Test
    fun evaluate_rejectsWhenAdapterDisabled() {
        val decision = evaluate(adapterEnabled = false)

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_DISABLED, decision.reason)
        assertEquals("req-1", decision.requestId)
        assertNull(decision.action)
    }

    @Test
    fun evaluate_rejectsUnauthorizedDevice() {
        val decision = MilinkBridgeCommandProcessor.evaluate(
            adapterEnabled = true,
            snapshot = null,
            command = setNoiseCommand(1),
        )

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_UNAUTHORIZED_DEVICE, decision.reason)
    }

    @Test
    fun evaluate_rejectsDisconnectedSnapshot() {
        val decision = evaluate(snapshot = snapshot(connected = false))

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_NOT_CONNECTED, decision.reason)
    }

    @Test
    fun evaluate_rejectsProtocolNotReady() {
        val decision = evaluate(snapshot = snapshot(protocolReady = false))

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_PROTOCOL_NOT_READY, decision.reason)
    }

    @Test
    fun evaluate_rejectsUnsupportedNoiseControl() {
        val decision = evaluate(snapshot = snapshot(supportsNoiseControl = false))

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_UNSUPPORTED_CAPABILITY, decision.reason)
    }

    @Test
    fun evaluate_rejectsInvalidNoiseMode() {
        val decision = evaluate(command = setNoiseCommand(9))

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_INVALID_NOISE_MODE, decision.reason)
    }

    @Test
    fun evaluate_rejectsUnsupportedCommand() {
        val decision = evaluate(
            command = MilinkBridgeCommandEnvelope(
                commandType = MilinkBridgeContract.COMMAND_RING_FIND,
                noiseMode = null,
                requestId = "req-1",
            ),
        )

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_UNSUPPORTED_COMMAND, decision.reason)
    }

    @Test
    fun evaluate_acceptsNoiseControlModes() {
        assertAccepted(0, NoiseControlMode.OFF)
        assertAccepted(1, NoiseControlMode.NOISE_CANCELLING)
        assertAccepted(2, NoiseControlMode.AMBIENT_SOUND)
    }

    private fun assertAccepted(noiseMode: Int, expected: NoiseControlMode) {
        val decision = evaluate(command = setNoiseCommand(noiseMode))

        assertTrue(decision.success)
        assertEquals(MilinkBridgeContract.REASON_OK, decision.reason)
        val action = decision.action as MilinkBridgeCommandAction.SetNoiseControl
        assertEquals(expected, action.mode)
    }

    private fun evaluate(
        adapterEnabled: Boolean = true,
        snapshot: MilinkDeviceSnapshot = snapshot(),
        command: MilinkBridgeCommandEnvelope = setNoiseCommand(1),
    ): MilinkBridgeCommandDecision =
        MilinkBridgeCommandProcessor.evaluate(
            adapterEnabled = adapterEnabled,
            snapshot = snapshot,
            command = command,
        )

    private fun setNoiseCommand(noiseMode: Int): MilinkBridgeCommandEnvelope =
        MilinkBridgeCommandEnvelope(
            commandType = MilinkBridgeContract.COMMAND_SET_NOISE_CONTROL,
            noiseMode = noiseMode,
            requestId = "req-1",
        )

    private fun snapshot(
        connected: Boolean = true,
        protocolReady: Boolean = true,
        supportsNoiseControl: Boolean = true,
    ): MilinkDeviceSnapshot =
        MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = "LinkBuds S",
            brand = "Sony",
            model = "LinkBuds S",
            deviceId = MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
            connected = connected,
            protocolReady = protocolReady,
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
            supportsBattery = true,
            supportsNoiseControl = supportsNoiseControl,
            supportsWearing = true,
            supportsRing = false,
            revision = 1L,
            updatedAt = 2L,
        )
}
