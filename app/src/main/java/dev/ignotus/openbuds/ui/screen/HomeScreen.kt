package dev.ignotus.openbuds.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.ui.EmptyHint
import dev.ignotus.openbuds.ui.InfoLine
import dev.ignotus.openbuds.ui.PageColumn
import dev.ignotus.openbuds.ui.SectionCard
import dev.ignotus.openbuds.ui.screen.AppIdentityHeader
import dev.ignotus.openbuds.ui.screen.DeviceModelImage
import dev.ignotus.openbuds.ui.screen.FeatureStatusCard
import dev.ignotus.openbuds.R

@Composable
internal fun HomeScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: Dp,
    onOpenDevice: () -> Unit,
    onRefresh: () -> Unit,
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        AppIdentityHeader(state = state)
        SectionCard(title = stringResource(R.string.home_work_status), icon = Icons.Rounded.Code) {
            val connected = state.connectedDevice != null
            InfoLine(stringResource(R.string.home_info_connection), if (connected) stringResource(R.string.home_status_connected) else state.scanState)
            InfoLine(stringResource(R.string.home_info_protocol), if (state.deviceInfo.protocolReady) stringResource(R.string.home_status_ready) else stringResource(R.string.home_status_waiting))
            InfoLine(stringResource(R.string.home_info_profile), state.connectedProfile?.let { "${it.brand} ${it.modelName}" } ?: stringResource(R.string.home_no_profile))
            InfoLine(stringResource(R.string.home_info_features), "${state.supportedFeatures.count { it.implemented }} wired / ${state.supportedFeatures.size} tracked")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenDevice) {
                    Text(if (connected) stringResource(R.string.home_open_controls) else stringResource(R.string.home_open_devices))
                }
                OutlinedButton(onClick = onRefresh, enabled = connected) {
                    Text(stringResource(R.string.home_refresh))
                }
            }
        }
        SectionCard(title = stringResource(R.string.home_current_device), icon = Icons.Rounded.Bluetooth) {
            val connected = state.connectedDevice
            if (connected == null) {
                EmptyHint(stringResource(R.string.device_no_headset))
            } else {
                DeviceModelImage(
                    imageUrl = state.deviceInfo.modelImageUrl,
                    modelName = state.deviceInfo.modelName ?: connected.name,
                )
                InfoLine(stringResource(R.string.home_info_name), connected.name)
                InfoLine(stringResource(R.string.home_info_address), connected.address)
                InfoLine(stringResource(R.string.home_info_transport), state.connectedProfile?.transport?.name ?: stringResource(R.string.home_unknown))
                val battery = state.batteryState.single ?: state.batteryState.left ?: state.batteryState.right
                InfoLine(stringResource(R.string.home_info_battery), battery?.let { "$it%" } ?: stringResource(R.string.home_unknown))
            }
        }
        FeatureStatusCard(state.supportedFeatures)
    }
}
