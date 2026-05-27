package dev.ignotus.openbuds.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ignotus.openbuds.R
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.ui.PageHeader
import dev.ignotus.openbuds.ui.animation.StaggeredReveal

@Composable
internal fun DevicePage(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: Dp,
    actions: DeviceActionCallback,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = bottomInnerPadding + 12.dp),
    ) {
        item { StaggeredReveal(index = 0) {
            PageHeader(
                title = stringResource(R.string.device_page_title),
                subtitle = if (state.connectedDevice == null) stringResource(R.string.device_page_subtitle_disconnected) else stringResource(R.string.device_page_subtitle_connected),
            )
        } }
        item { StaggeredReveal(index = 1) { ConnectionCard(state, actions) } }
        item { StaggeredReveal(index = 2) { EndpointDiagnosticsCard(state) } }
        if (state.connectedDevice != null) {
            item { StaggeredReveal(index = 3) { DeviceInfoCard(state) } }
            item { StaggeredReveal(index = 4) { BatteryCard(state) } }
            item { StaggeredReveal(index = 5) { LeaStatusCard(state) } }
            item { StaggeredReveal(index = 6) { QuickAccessStatusCard(state) } }
            item { StaggeredReveal(index = 7) { WearingStatusCard(state) } }
            item { StaggeredReveal(index = 8) { QuickControlCard(state, actions) } }
        }
        item { StaggeredReveal(index = 9) { FeatureStatusCard(state.supportedFeatures) } }
    }
}
