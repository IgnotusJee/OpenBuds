package dev.ignotus.openbuds.lsposed.mitws

import dev.ignotus.openbuds.integration.milink.MilinkBridgeContract
import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiTwsControlMapperTest {
    @Test
    fun noiseModeForAncMethod_mapsThreeAncMethods() {
        assertEquals(1, MiTwsControlMapper.noiseModeForAncMethod(MiTwsControlMapper.METHOD_OPEN_ANC))
        assertEquals(2, MiTwsControlMapper.noiseModeForAncMethod(MiTwsControlMapper.METHOD_OPEN_TRANSPARENT))
        assertEquals(0, MiTwsControlMapper.noiseModeForAncMethod(MiTwsControlMapper.METHOD_CLOSE_ANC))
    }

    @Test
    fun noiseModeForAncMethod_returnsNullForUnknownMethod() {
        assertNull(MiTwsControlMapper.noiseModeForAncMethod("changeAncMode"))
    }

    @Test
    fun buildAncCommandValue_mapsOpenAnc() {
        val command = MiTwsControlMapper.buildAncCommandValue(
            methodName = MiTwsControlMapper.METHOD_OPEN_ANC,
            snapshot = snapshot(),
            requestId = "req-open",
        )!!

        assertEquals(MilinkBridgeContract.COMMAND_SET_NOISE_CONTROL, command.commandType)
        assertEquals(1, command.noiseMode)
        assertEquals("req-open", command.requestId)
    }

    @Test
    fun buildAncCommandValue_mapsOpenTransparent() {
        val command = MiTwsControlMapper.buildAncCommandValue(
            methodName = MiTwsControlMapper.METHOD_OPEN_TRANSPARENT,
            snapshot = snapshot(),
            requestId = "req-transparent",
        )!!

        assertEquals(MilinkBridgeContract.COMMAND_SET_NOISE_CONTROL, command.commandType)
        assertEquals(2, command.noiseMode)
        assertEquals("req-transparent", command.requestId)
    }

    @Test
    fun buildAncCommandValue_mapsCloseAnc() {
        val command = MiTwsControlMapper.buildAncCommandValue(
            methodName = MiTwsControlMapper.METHOD_CLOSE_ANC,
            snapshot = snapshot(),
            requestId = "req-close",
        )!!

        assertEquals(MilinkBridgeContract.COMMAND_SET_NOISE_CONTROL, command.commandType)
        assertEquals(0, command.noiseMode)
        assertEquals("req-close", command.requestId)
    }

    @Test
    fun buildAncCommandValue_returnsNullWhenCapabilityUnsupported() {
        val command = MiTwsControlMapper.buildAncCommandValue(
            methodName = MiTwsControlMapper.METHOD_OPEN_ANC,
            snapshot = snapshot(supportsNoiseControl = false),
            requestId = "req",
        )

        assertNull(command)
    }

    private fun snapshot(supportsNoiseControl: Boolean = true): MilinkDeviceSnapshot =
        MilinkDeviceSnapshot(
            mac = "AA:BB:CC:DD:EE:FF",
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
