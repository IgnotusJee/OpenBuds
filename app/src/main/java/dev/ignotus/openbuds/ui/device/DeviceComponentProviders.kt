package dev.ignotus.openbuds.ui.device

import dev.ignotus.openbuds.ble.DiscoveredSonyDevice
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.headphones.ConnectedHeadphoneProfile
import dev.ignotus.openbuds.headphones.HeadphoneFeature
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.NoiseControlMode
import androidx.compose.runtime.Composable

data class DeviceActionCallback(
    val onStartScan: () -> Unit,
    val onStopScan: () -> Unit,
    val onConnect: (DiscoveredSonyDevice) -> Unit,
    val onDisconnect: () -> Unit,
    val onRefresh: () -> Unit,
    val onSetNoiseControlMode: (NoiseControlMode) -> Unit,
    val onSetAmbientLevel: (Int) -> Unit,
    val onSetAmbientVoiceMode: (Boolean) -> Unit,
    val onSetEqPreset: (EqPresetId) -> Unit,
    val onSetClearBass: (Int) -> Unit,
    val onSetCustomEqBand: (Int, Int) -> Unit,
    val onPlaybackPrevious: () -> Unit,
    val onPlaybackPlayPause: () -> Unit,
    val onPlaybackNext: () -> Unit,
)

interface IDeviceComponent {
    val priority: Int
    fun visible(state: SonyHeadphoneUiState): Boolean
    @Composable
    fun Render(state: SonyHeadphoneUiState, actions: DeviceActionCallback)
}

object DeviceComponentRegistry {
    private val _providers = mutableListOf<IDeviceComponent>()
    val providers: List<IDeviceComponent> get() = _providers.sortedBy { it.priority }

    fun register(provider: IDeviceComponent) {
        _providers.add(provider)
    }

    fun visibleProviders(state: SonyHeadphoneUiState): List<IDeviceComponent> {
        return providers.filter { it.visible(state) }
    }
}

internal fun ConnectedHeadphoneProfile?.supports(feature: HeadphoneFeature): Boolean =
    this?.supports(feature) == true

fun initDeviceComponents() {
    DeviceComponentRegistry.register(connectionProvider)
    DeviceComponentRegistry.register(endpointDiagProvider)
    DeviceComponentRegistry.register(deviceInfoProvider)
    DeviceComponentRegistry.register(batteryProvider)
    DeviceComponentRegistry.register(leaProvider)
    DeviceComponentRegistry.register(quickAccessProvider)
    DeviceComponentRegistry.register(wearingProvider)
    DeviceComponentRegistry.register(quickControlProvider)
    DeviceComponentRegistry.register(featureMapProvider)
}

private val connectionProvider = object : IDeviceComponent {
    override val priority = 0
    override fun visible(s: SonyHeadphoneUiState) = true
    @Composable override fun Render(s: SonyHeadphoneUiState, a: DeviceActionCallback) { ConnectionCard(s, a) }
}

private val endpointDiagProvider = object : IDeviceComponent {
    override val priority = 10
    override fun visible(s: SonyHeadphoneUiState) = s.endpointDiagnostic != null
    @Composable override fun Render(s: SonyHeadphoneUiState, a: DeviceActionCallback) { EndpointDiagnosticsCard(s) }
}

private val deviceInfoProvider = object : IDeviceComponent {
    override val priority = 20
    override fun visible(s: SonyHeadphoneUiState) = s.connectedDevice != null
    @Composable override fun Render(s: SonyHeadphoneUiState, a: DeviceActionCallback) { DeviceInfoCard(s) }
}

private val batteryProvider = object : IDeviceComponent {
    override val priority = 30
    override fun visible(s: SonyHeadphoneUiState) = s.connectedDevice != null
    @Composable override fun Render(s: SonyHeadphoneUiState, a: DeviceActionCallback) { BatteryCard(s) }
}

private val leaProvider = object : IDeviceComponent {
    override val priority = 40
    override fun visible(s: SonyHeadphoneUiState) = s.connectedDevice != null && (s.leaState.enabled != null || s.leaState.streamingStatusL != null || s.leaState.streamingStatusR != null || s.leaState.pairedHistory != null)
    @Composable override fun Render(s: SonyHeadphoneUiState, a: DeviceActionCallback) { LeaStatusCard(s) }
}

private val quickAccessProvider = object : IDeviceComponent {
    override val priority = 50
    override fun visible(s: SonyHeadphoneUiState) = s.connectedDevice != null && (s.quickAccessState.lrKeyFunction != null || s.quickAccessState.ncAmbKeyFunction != null)
    @Composable override fun Render(s: SonyHeadphoneUiState, a: DeviceActionCallback) { QuickAccessStatusCard(s) }
}

private val wearingProvider = object : IDeviceComponent {
    override val priority = 60
    override fun visible(s: SonyHeadphoneUiState) = s.connectedDevice != null && (s.wearingState.status != null || s.wearingState.result != null)
    @Composable override fun Render(s: SonyHeadphoneUiState, a: DeviceActionCallback) { WearingStatusCard(s) }
}

private val quickControlProvider = object : IDeviceComponent {
    override val priority = 70
    override fun visible(s: SonyHeadphoneUiState) = s.connectedDevice != null
    @Composable override fun Render(s: SonyHeadphoneUiState, a: DeviceActionCallback) { QuickControlCard(s, a) }
}

private val featureMapProvider = object : IDeviceComponent {
    override val priority = 999
    override fun visible(s: SonyHeadphoneUiState) = true
    @Composable override fun Render(s: SonyHeadphoneUiState, a: DeviceActionCallback) { FeatureStatusCard(s.supportedFeatures) }
}
