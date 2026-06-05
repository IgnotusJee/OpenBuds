package dev.ignotus.openbuds.integration.milink

import dev.ignotus.openbuds.lsposed.mitws.MiTwsDeviceIdPolicy
import dev.ignotus.openbuds.lsposed.mitws.MiTwsRuntimeProjection
import dev.ignotus.openbuds.protocol.NoiseControlMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MilinkTransportProxyCoordinatorTest {
    @Test
    fun snapshotFor_prefersProxyOnlyWhenActiveForSameMac() {
        val app = snapshot(mac = "AA:BB:CC:DD:EE:FF", leftBattery = 10, ancMode = 0)
        val proxy = snapshot(mac = "AA:BB:CC:DD:EE:FF", leftBattery = 70, ancMode = 2)

        assertSame(
            proxy,
            MilinkTransportProxyCoordinator.snapshotFor(
                mac = "aa:bb:cc:dd:ee:ff",
                appSnapshots = mapOf(app.mac to app),
                proxySnapshots = mapOf(proxy.mac to proxy),
                activeProxyMacs = setOf(proxy.mac),
            ),
        )
        assertSame(
            app,
            MilinkTransportProxyCoordinator.snapshotFor(
                mac = "AA:BB:CC:DD:EE:FF",
                appSnapshots = mapOf(app.mac to app),
                proxySnapshots = mapOf(proxy.mac to proxy),
                activeProxyMacs = emptySet(),
            ),
        )
    }

    @Test
    fun mergedSnapshots_replacesOnlyActiveProxyMacs() {
        val appTarget = snapshot(mac = "AA:BB:CC:DD:EE:FF", leftBattery = 10)
        val appOther = snapshot(mac = "11:22:33:44:55:66", leftBattery = 20)
        val proxyTarget = snapshot(mac = "AA:BB:CC:DD:EE:FF", leftBattery = 70)
        val proxyInactive = snapshot(mac = "22:33:44:55:66:77", leftBattery = 80)

        val merged = MilinkTransportProxyCoordinator.mergedSnapshots(
            appSnapshots = listOf(appTarget, appOther),
            proxySnapshots = listOf(proxyTarget, proxyInactive),
            activeProxyMacs = setOf(proxyTarget.mac),
        )

        assertEquals(listOf("11:22:33:44:55:66", "AA:BB:CC:DD:EE:FF"), merged.map { it.mac })
        assertEquals(70, merged.first { it.mac == proxyTarget.mac }.leftBattery)
        assertFalse(merged.any { it.mac == proxyInactive.mac })
    }

    @Test
    fun shouldRouteNoiseCommandToProxy_requiresActiveProxyCommandFlagAndNoiseAction() {
        val action = MilinkBridgeCommandAction.SetNoiseControl(NoiseControlMode.AMBIENT_SOUND)

        assertTrue(
            MilinkTransportProxyCoordinator.shouldRouteNoiseCommandToProxy(
                mac = "aa:bb:cc:dd:ee:ff",
                activeProxyMacs = setOf("AA:BB:CC:DD:EE:FF"),
                commandEnabled = true,
                action = action,
            )
        )
        assertFalse(
            MilinkTransportProxyCoordinator.shouldRouteNoiseCommandToProxy(
                mac = "AA:BB:CC:DD:EE:FF",
                activeProxyMacs = setOf("AA:BB:CC:DD:EE:FF"),
                commandEnabled = false,
                action = action,
            )
        )
        assertFalse(
            MilinkTransportProxyCoordinator.shouldRouteNoiseCommandToProxy(
                mac = "AA:BB:CC:DD:EE:FF",
                activeProxyMacs = setOf("AA:BB:CC:DD:EE:FF"),
                commandEnabled = true,
                action = MilinkBridgeCommandAction.SetVolume(50),
            )
        )
    }

    @Test
    fun commandEvaluationSnapshot_usesProxyOnlyForEnabledNoiseCommand() {
        val app = snapshot(mac = "AA:BB:CC:DD:EE:FF", leftBattery = 10, ancMode = 0)
        val proxy = snapshot(mac = "AA:BB:CC:DD:EE:FF", leftBattery = 70, ancMode = 2)
        val noiseCommand = MilinkBridgeCommandEnvelope(
            commandType = MilinkBridgeContract.COMMAND_SET_NOISE_CONTROL,
            noiseMode = 2,
        )

        assertSame(
            proxy,
            MilinkTransportProxyCoordinator.commandEvaluationSnapshotFor(
                mac = "aa:bb:cc:dd:ee:ff",
                command = noiseCommand,
                appSnapshots = mapOf(app.mac to app),
                proxySnapshots = mapOf(proxy.mac to proxy),
                activeProxyMacs = setOf(proxy.mac),
                proxyCommandEnabled = true,
            ),
        )
        assertSame(
            app,
            MilinkTransportProxyCoordinator.commandEvaluationSnapshotFor(
                mac = "AA:BB:CC:DD:EE:FF",
                command = noiseCommand,
                appSnapshots = mapOf(app.mac to app),
                proxySnapshots = mapOf(proxy.mac to proxy),
                activeProxyMacs = setOf(proxy.mac),
                proxyCommandEnabled = false,
            ),
        )
        assertSame(
            app,
            MilinkTransportProxyCoordinator.commandEvaluationSnapshotFor(
                mac = "AA:BB:CC:DD:EE:FF",
                command = MilinkBridgeCommandEnvelope(
                    commandType = MilinkBridgeContract.COMMAND_SET_VOLUME,
                    volume = 60,
                ),
                appSnapshots = mapOf(app.mac to app),
                proxySnapshots = mapOf(proxy.mac to proxy),
                activeProxyMacs = setOf(proxy.mac),
                proxyCommandEnabled = true,
            ),
        )
    }

    @Test
    fun commandEvaluationSnapshot_doesNotAuthorizeProxyOnlyStateWhenCommandFlagDisabled() {
        val proxy = snapshot(mac = "AA:BB:CC:DD:EE:FF", leftBattery = 70, ancMode = 2)

        assertEquals(
            null,
            MilinkTransportProxyCoordinator.commandEvaluationSnapshotFor(
                mac = "AA:BB:CC:DD:EE:FF",
                command = MilinkBridgeCommandEnvelope(
                    commandType = MilinkBridgeContract.COMMAND_SET_NOISE_CONTROL,
                    noiseMode = 2,
                ),
                appSnapshots = emptyMap(),
                proxySnapshots = mapOf(proxy.mac to proxy),
                activeProxyMacs = setOf(proxy.mac),
                proxyCommandEnabled = false,
            ),
        )
    }

    @Test
    fun proxyCommandProperty_defaultReaderCanBeInjectedForTests() {
        assertTrue(
            MilinkTransportProxyCoordinator.isProxyCommandPropertyEnabled { _, _ -> "TRUE" }
        )
        assertFalse(
            MilinkTransportProxyCoordinator.isProxyCommandPropertyEnabled { _, _ -> "false" }
        )
    }

    private fun snapshot(
        mac: String,
        leftBattery: Int? = null,
        ancMode: Int? = 1,
    ): MilinkDeviceSnapshot =
        MilinkDeviceSnapshot(
            mac = mac,
            name = "LinkBuds S",
            brand = "Sony",
            model = "LinkBuds S",
            deviceId = MiTwsDeviceIdPolicy.GENERIC_EARBUD_DEVICE_ID,
            formFactor = MiTwsRuntimeProjection.FORM_FACTOR_TRUE_WIRELESS,
            connected = true,
            protocolReady = true,
            leftBattery = leftBattery,
            rightBattery = null,
            caseBattery = null,
            singleBattery = null,
            leftWearing = null,
            rightWearing = null,
            leftCharging = null,
            rightCharging = null,
            caseCharging = null,
            ancMode = ancMode,
            ringing = false,
            currentVolume = null,
            currentAudioEffectState = null,
            supportsBattery = true,
            supportsNoiseControl = true,
            supportsWearing = false,
            supportsRing = false,
            supportsVolumeControl = false,
            supportsAudioEffect = false,
            supportsEq = false,
            supportsLeaStatus = false,
            supportsQuickAccess = false,
            supportsAmbientLevel = true,
            revision = leftBattery?.toLong() ?: 0L,
            updatedAt = 1L,
        )
}
