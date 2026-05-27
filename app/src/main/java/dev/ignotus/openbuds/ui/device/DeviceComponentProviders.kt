package dev.ignotus.openbuds.ui.device

import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.headphones.ConnectedHeadphoneProfile
import dev.ignotus.openbuds.headphones.HeadphoneFeature
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.NoiseControlMode

data class DeviceActionCallback(
    val onStartScan: () -> Unit,
    val onStopScan: () -> Unit,
    val onConnect: (dev.ignotus.openbuds.ble.DiscoveredSonyDevice) -> Unit,
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
    @androidx.compose.runtime.Composable
    fun Render(state: SonyHeadphoneUiState, actions: DeviceActionCallback)
}

object DeviceComponentRegistry {
    private val _providers = mutableListOf<IDeviceComponent>()
    val providers: List<IDeviceComponent> get() = _providers.sortedBy { it.priority }

    fun register(provider: IDeviceComponent) {
        _providers.add(provider)
    }

    @androidx.compose.runtime.Composable
    fun visibleProviders(state: SonyHeadphoneUiState): List<IDeviceComponent> {
        return providers.filter { it.visible(state) }
    }
}

internal fun ConnectedHeadphoneProfile?.supports(feature: HeadphoneFeature): Boolean =
    this?.supports(feature) == true
