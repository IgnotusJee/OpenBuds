package dev.ignotus.openbuds.integration.milink

import dev.ignotus.openbuds.lsposed.mitws.MiTwsDeviceIdPolicy
import dev.ignotus.openbuds.lsposed.mitws.MiTwsRuntimeProjection
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
                commandType = "unknown_command",
                requestId = "req-1",
            ),
        )

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_UNSUPPORTED_COMMAND, decision.reason)
    }

    @Test
    fun evaluate_rejectsVolumeWhenCapabilityDisabledByDefault() {
        val decision = evaluate(
            command = MilinkBridgeCommandEnvelope(
                commandType = MilinkBridgeContract.COMMAND_SET_VOLUME,
                volume = 50,
                requestId = "req-volume",
            ),
        )

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_UNSUPPORTED_CAPABILITY, decision.reason)
    }

    @Test
    fun evaluate_rejectsInvalidVolumeWhenCapabilityEnabled() {
        val decision = evaluate(
            snapshot = snapshot(supportsVolumeControl = true),
            command = MilinkBridgeCommandEnvelope(
                commandType = MilinkBridgeContract.COMMAND_SET_VOLUME,
                volume = 101,
                requestId = "req-volume",
            ),
        )

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_INVALID_VOLUME, decision.reason)
    }

    @Test
    fun evaluate_acceptsVolumeWhenCapabilityEnabled() {
        val decision = evaluate(
            snapshot = snapshot(supportsVolumeControl = true),
            command = MilinkBridgeCommandEnvelope(
                commandType = MilinkBridgeContract.COMMAND_SET_VOLUME,
                volume = 60,
                requestId = "req-volume",
            ),
        )

        assertTrue(decision.success)
        val action = decision.action as MilinkBridgeCommandAction.SetVolume
        assertEquals(60, action.volume)
    }

    @Test
    fun evaluate_rejectsAudioEffectWhenCapabilityDisabledByDefault() {
        val decision = evaluate(
            command = MilinkBridgeCommandEnvelope(
                commandType = MilinkBridgeContract.COMMAND_SET_AUDIO_EFFECT,
                audioEffectState = 1,
                requestId = "req-audio",
            ),
        )

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_UNSUPPORTED_CAPABILITY, decision.reason)
    }

    @Test
    fun evaluate_rejectsInvalidAudioEffectWhenCapabilityEnabled() {
        val decision = evaluate(
            snapshot = snapshot(supportsAudioEffect = true),
            command = MilinkBridgeCommandEnvelope(
                commandType = MilinkBridgeContract.COMMAND_SET_AUDIO_EFFECT,
                audioEffectState = -2,
                requestId = "req-audio",
            ),
        )

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_INVALID_AUDIO_EFFECT, decision.reason)
    }

    @Test
    fun evaluate_acceptsAudioEffectWhenCapabilityEnabled() {
        val decision = evaluate(
            snapshot = snapshot(supportsAudioEffect = true),
            command = MilinkBridgeCommandEnvelope(
                commandType = MilinkBridgeContract.COMMAND_SET_AUDIO_EFFECT,
                audioEffectState = 2,
                requestId = "req-audio",
            ),
        )

        assertTrue(decision.success)
        val action = decision.action as MilinkBridgeCommandAction.SetAudioEffect
        assertEquals(2, action.state)
    }

    @Test
    fun evaluate_rejectsRingWhenCapabilityDisabledByDefault() {
        val decision = evaluate(
            command = MilinkBridgeCommandEnvelope(
                commandType = MilinkBridgeContract.COMMAND_START_RING,
                requestId = "req-ring",
            ),
        )

        assertFalse(decision.success)
        assertEquals(MilinkBridgeContract.REASON_UNSUPPORTED_CAPABILITY, decision.reason)
    }

    @Test
    fun evaluate_acceptsStartRingWhenCapabilityEnabled() {
        val decision = evaluate(
            snapshot = snapshot(supportsRing = true),
            command = MilinkBridgeCommandEnvelope(
                commandType = MilinkBridgeContract.COMMAND_START_RING,
                requestId = "req-ring",
            ),
        )

        assertTrue(decision.success)
        assertEquals(MilinkBridgeCommandAction.StartRing, decision.action)
    }

    @Test
    fun evaluate_acceptsStopRingWhenCapabilityEnabled() {
        val decision = evaluate(
            snapshot = snapshot(supportsRing = true),
            command = MilinkBridgeCommandEnvelope(
                commandType = MilinkBridgeContract.COMMAND_STOP_RING,
                requestId = "req-ring",
            ),
        )

        assertTrue(decision.success)
        assertEquals(MilinkBridgeCommandAction.StopRing, decision.action)
    }

    @Test
    fun evaluate_mapsLegacyRingFindByRequestedState() {
        val start = evaluate(
            snapshot = snapshot(supportsRing = true),
            command = MilinkBridgeCommandEnvelope(
                commandType = MilinkBridgeContract.COMMAND_RING_FIND,
                ringEnabled = true,
                requestId = "req-ring",
            ),
        )
        val stop = evaluate(
            snapshot = snapshot(supportsRing = true),
            command = MilinkBridgeCommandEnvelope(
                commandType = MilinkBridgeContract.COMMAND_RING_FIND,
                ringEnabled = false,
                requestId = "req-ring",
            ),
        )

        assertTrue(start.success)
        assertEquals(MilinkBridgeCommandAction.StartRing, start.action)
        assertTrue(stop.success)
        assertEquals(MilinkBridgeCommandAction.StopRing, stop.action)
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
        supportsRing: Boolean = false,
        supportsVolumeControl: Boolean = false,
        supportsAudioEffect: Boolean = false,
    ): MilinkDeviceSnapshot =
        MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
            name = "LinkBuds S",
            brand = "Sony",
            model = "LinkBuds S",
            deviceId = MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
            formFactor = MiTwsRuntimeProjection.FORM_FACTOR_TRUE_WIRELESS,
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
            currentVolume = null,
            currentAudioEffectState = null,
            supportsBattery = true,
            supportsNoiseControl = supportsNoiseControl,
            supportsWearing = true,
            supportsRing = supportsRing,
            supportsVolumeControl = supportsVolumeControl,
            supportsAudioEffect = supportsAudioEffect,
            supportsEq = false,
            supportsLeaStatus = false,
            supportsQuickAccess = false,
            supportsAmbientLevel = false,
            revision = 1L,
            updatedAt = 2L,
        )
}
