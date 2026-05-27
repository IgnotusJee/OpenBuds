package dev.ignotus.openbuds.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    LaunchedEffect(Unit) { initDeviceComponents() }
    val providers = remember(state.connectedDevice, state.endpointDiagnostic, state.leaState, state.quickAccessState, state.wearingState) {
        DeviceComponentRegistry.providers.filter { it.visible(state) }
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = bottomInnerPadding + 12.dp),
    ) {
        item {
            StaggeredReveal(index = 0) {
                PageHeader(
                    title = stringResource(R.string.device_page_title),
                    subtitle = if (state.connectedDevice == null) stringResource(R.string.device_page_subtitle_disconnected) else stringResource(R.string.device_page_subtitle_connected),
                )
            }
        }
        providers.forEachIndexed { index, provider ->
            item {
                StaggeredReveal(index = index + 1) {
                    provider.Render(state, actions)
                }
            }
        }
    }
}
