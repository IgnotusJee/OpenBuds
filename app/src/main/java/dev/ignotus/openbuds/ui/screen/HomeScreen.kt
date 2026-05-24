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
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.ui.EmptyHint
import dev.ignotus.openbuds.ui.InfoLine
import dev.ignotus.openbuds.ui.PageColumn
import dev.ignotus.openbuds.ui.SectionCard
import dev.ignotus.openbuds.ui.screen.AppIdentityHeader
import dev.ignotus.openbuds.ui.screen.DeviceModelImage
import dev.ignotus.openbuds.ui.screen.FeatureStatusCard

@Composable
internal fun HomeScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: Dp,
    onOpenDevice: () -> Unit,
    onRefresh: () -> Unit,
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        AppIdentityHeader(state = state)
        SectionCard(title = "Work status", icon = Icons.Rounded.Code) {
            val connected = state.connectedDevice != null
            InfoLine("Connection", if (connected) "Connected" else state.scanState)
            InfoLine("Protocol", if (state.deviceInfo.protocolReady) "Ready" else "Waiting")
            InfoLine("Profile", state.connectedProfile?.let { "${it.brand} ${it.modelName}" } ?: "No active profile")
            InfoLine("Features", "${state.supportedFeatures.count { it.implemented }} wired / ${state.supportedFeatures.size} tracked")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenDevice) {
                    Text(if (connected) "Open controls" else "Open devices")
                }
                OutlinedButton(onClick = onRefresh, enabled = connected) {
                    Text("Refresh")
                }
            }
        }
        SectionCard(title = "Current device", icon = Icons.Rounded.Bluetooth) {
            val connected = state.connectedDevice
            if (connected == null) {
                EmptyHint("No headset connected. Device page shows known devices and scan results.")
            } else {
                DeviceModelImage(
                    imageUrl = state.deviceInfo.modelImageUrl,
                    modelName = state.deviceInfo.modelName ?: connected.name,
                )
                InfoLine("Name", connected.name)
                InfoLine("Address", connected.address)
                InfoLine("Transport", state.connectedProfile?.transport?.name ?: "Unknown")
                val battery = state.batteryState.single ?: state.batteryState.left ?: state.batteryState.right
                InfoLine("Battery", battery?.let { "$it%" } ?: "Unknown")
            }
        }
        FeatureStatusCard(state.supportedFeatures)
    }
}
