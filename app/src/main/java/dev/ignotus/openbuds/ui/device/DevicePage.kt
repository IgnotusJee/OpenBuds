package dev.ignotus.openbuds.ui.device

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import dev.ignotus.openbuds.R
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.ui.PageColumn
import dev.ignotus.openbuds.ui.PageHeader

@Composable
internal fun DevicePage(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: Dp,
    actions: DeviceActionCallback,
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        PageHeader(
            title = stringResource(R.string.device_page_title),
            subtitle = if (state.connectedDevice == null) {
                stringResource(R.string.device_page_subtitle_disconnected)
            } else {
                stringResource(R.string.device_page_subtitle_connected)
            },
        )
        ConnectionCard(state, actions)
        EndpointDiagnosticsCard(state)
        if (state.connectedDevice != null) {
            DeviceInfoCard(state)
            BatteryCard(state)
            LeaStatusCard(state)
            QuickAccessStatusCard(state)
            WearingStatusCard(state)
            QuickControlCard(state, actions)
        }
        FeatureStatusCard(state.supportedFeatures)
    }
}
