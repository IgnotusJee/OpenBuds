package dev.ignotus.openbuds.ui.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ignotus.openbuds.R
import dev.ignotus.openbuds.ble.DiscoveredSonyDevice
import dev.ignotus.openbuds.data.FeatureStatus
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.headphones.HeadphoneFormFactor
import dev.ignotus.openbuds.headphones.InfoLayoutHint
import dev.ignotus.openbuds.ui.EmptyHint
import dev.ignotus.openbuds.ui.InfoLine
import dev.ignotus.openbuds.ui.SectionCard
import dev.ignotus.openbuds.ui.SettingRow
import dev.ignotus.openbuds.ui.StatusPill

@Composable
internal fun ConnectionCard(state: SonyHeadphoneUiState, actions: DeviceActionCallback) {
    SectionCard(title = stringResource(R.string.device_connection), icon = Icons.Rounded.Bluetooth) {
        val connected = state.connectedDevice
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            StatusPill(state.scanState, connected != null || state.isScanning)
            state.connectionInfo?.let { StatusPill(stringResource(R.string.device_info_gatt_ready) + " ${it.mtu}", true) }
            state.permissionIssue?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (connected == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = actions.onStartScan, enabled = !state.isScanning) { Text(stringResource(R.string.device_scan)) }
                OutlinedButton(onClick = actions.onStopScan, enabled = state.isScanning) { Text(stringResource(R.string.device_stop)) }
            }
            if (state.knownDevices.isNotEmpty()) {
                Text(stringResource(R.string.device_known_devices), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                state.knownDevices.forEach { device -> DeviceRow(device = device, onConnect = { actions.onConnect(device) }) }
            }
            Text(stringResource(R.string.device_scan_results), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (state.discoveredDevices.isEmpty()) {
                EmptyHint(stringResource(R.string.device_no_devices_found))
            } else {
                state.discoveredDevices.forEach { device -> DeviceRow(device = device, onConnect = { actions.onConnect(device) }) }
            }
        } else {
            InfoLine(stringResource(R.string.device_info_connected), "${connected.name} (${connected.address})")
            state.connectedProfile?.let { profile ->
                InfoLine(stringResource(R.string.device_info_profile), "${profile.brand} ${profile.modelName}")
                InfoLine(stringResource(R.string.device_info_transport), profile.transport.name)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = actions.onRefresh) { Text(stringResource(R.string.device_refresh)) }
                OutlinedButton(onClick = actions.onDisconnect) { Text(stringResource(R.string.device_disconnect)) }
            }
        }
    }
}

@Composable
internal fun EndpointDiagnosticsCard(state: SonyHeadphoneUiState) {
    val diagnostic = state.endpointDiagnostic ?: return
    SectionCard(title = stringResource(R.string.device_endpoint_diag), icon = Icons.Rounded.Info) {
        Text(diagnostic.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        InfoLine(stringResource(R.string.device_diag_lea_mode), "LE Audio / auxiliary GATT endpoint")
        InfoLine(stringResource(R.string.device_diag_lea_compat), diagnostic.leAudioSwitchCompatibility?.toString() ?: stringResource(R.string.home_unknown))
        diagnostic.friendlyName?.let { InfoLine(stringResource(R.string.device_diag_friendly_name), it) }
        diagnostic.publicAddress?.let { InfoLine(stringResource(R.string.device_diag_public_address), it) }
        InfoLine(stringResource(R.string.device_diag_services), diagnostic.serviceLabels.joinToString())
        diagnostic.rawReads.entries.take(5).forEach { (name, value) -> InfoLine(name, value) }
    }
}

@Composable
internal fun DeviceInfoCard(state: SonyHeadphoneUiState) {
    SectionCard(title = stringResource(R.string.device_device_info)) {
        val info = state.deviceInfo
        DeviceModelImage(imageUrl = info.modelImageUrl, modelName = info.modelName ?: state.connectedDevice?.name)
        InfoLine(stringResource(R.string.device_info_protocol_channel), if (info.protocolReady) {
            state.connectedProfile?.protocolName?.let { "$it ready" } ?: stringResource(R.string.device_info_gatt_ready)
        } else { stringResource(R.string.device_info_not_ready) })
        state.connectedProfile?.let { profile ->
            InfoLine(stringResource(R.string.device_info_adapter), "${profile.adapterId} / ${profile.protocolName}")
            InfoLine(stringResource(R.string.device_info_transport), profile.transport.name)
        }
        InfoLine(stringResource(R.string.device_info_model), info.modelName ?: state.connectedDevice?.name ?: stringResource(R.string.home_unknown))
        InfoLine(stringResource(R.string.device_info_firmware), info.firmwareVersion ?: stringResource(R.string.home_unknown))
        if (state.connectedProfile?.infoLayoutHint == InfoLayoutHint.SONY_SERIES) {
            InfoLine(stringResource(R.string.device_info_series), info.seriesAndColor ?: stringResource(R.string.home_unknown))
        } else {
            InfoLine(stringResource(R.string.device_info_brand_model), "${state.connectedProfile?.brand ?: stringResource(R.string.home_unknown)} ${state.connectedProfile?.displayName ?: ""}".trim())
        }
        InfoLine(stringResource(R.string.device_info_image_match), info.modelImageUrl?.let { info.modelColor ?: "Default" } ?: "Default placeholder")
    }
}

@Composable
internal fun BatteryCard(state: SonyHeadphoneUiState) {
    SectionCard(title = stringResource(R.string.device_battery), icon = Icons.Rounded.BatteryChargingFull) {
        val battery = state.batteryState
        val headsetOnly = state.connectedProfile?.capabilities?.formFactor == HeadphoneFormFactor.HEADSET
        if (headsetOnly) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BatteryTile(stringResource(R.string.device_battery_headset), battery.single ?: battery.left ?: battery.right)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BatteryTile(stringResource(R.string.device_battery_left), battery.left)
                BatteryTile(stringResource(R.string.device_battery_right), battery.right)
                BatteryTile(stringResource(R.string.device_battery_case), battery.cradle)
            }
        }
        if (battery.raw.isNotEmpty()) {
            Text(stringResource(R.string.device_battery_raw, battery.raw), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
internal fun LeaStatusCard(state: SonyHeadphoneUiState) {
    val lea = state.leaState
    if (lea.enabled == null && lea.streamingStatusL == null && lea.streamingStatusR == null && lea.pairedHistory == null) return
    SectionCard(title = stringResource(R.string.device_le_audio), icon = Icons.Rounded.Bluetooth) {
        lea.enabled?.let { InfoLine(stringResource(R.string.device_lea_enabled), it) }
        lea.streamingStatusL?.let { InfoLine(stringResource(R.string.device_lea_streaming_l), it) }
        lea.streamingStatusR?.let { InfoLine(stringResource(R.string.device_lea_streaming_r), it) }
        lea.pairedHistory?.let { InfoLine(stringResource(R.string.device_lea_paired), it) }
        if (lea.raw.isNotEmpty()) { InfoLine("Raw", lea.raw.joinToString(" ")) }
    }
}

@Composable
internal fun QuickAccessStatusCard(state: SonyHeadphoneUiState) {
    val qa = state.quickAccessState
    if (qa.lrKeyFunction == null && qa.ncAmbKeyFunction == null) return
    SectionCard(title = stringResource(R.string.device_quick_access), icon = Icons.Rounded.Settings) {
        qa.lrKeyFunction?.let { InfoLine(stringResource(R.string.device_qa_lr_key), it) }
        qa.ncAmbKeyFunction?.let { InfoLine(stringResource(R.string.device_qa_nc_amb_key), it) }
        if (qa.raw.isNotEmpty()) { InfoLine("Raw", qa.raw.joinToString(" ")) }
    }
}

@Composable
internal fun WearingStatusCard(state: SonyHeadphoneUiState) {
    val w = state.wearingState
    if (w.status == null && w.result == null) return
    SectionCard(title = stringResource(R.string.device_wearing), icon = Icons.Rounded.Headphones) {
        w.status?.let { InfoLine(stringResource(R.string.device_wearing_status), it) }
        w.result?.let { InfoLine(stringResource(R.string.device_wearing_result), it) }
        if (w.raw.isNotEmpty()) { InfoLine("Raw", w.raw.joinToString(" ")) }
    }
}

@Composable
internal fun FeatureStatusCard(features: List<FeatureStatus>) {
    SectionCard(title = stringResource(R.string.device_feature_map), icon = Icons.Rounded.Code) {
        features.forEach { feature ->
            SettingRow(
                title = feature.title,
                subtitle = feature.description,
                trailing = { StatusPill(if (feature.implemented) stringResource(R.string.settings_status_wired) else stringResource(R.string.settings_status_reserved), feature.implemented) },
            )
        }
    }
}

@Composable
internal fun DeviceRow(device: DiscoveredSonyDevice, onConnect: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                Text("${device.address}  ${device.source}  RSSI ${device.rssi}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (device.isLikelyControlEndpoint) stringResource(R.string.device_ble_candidate)
                    else if (device.sonyAd != null) stringResource(R.string.device_sony_ad_found)
                    else stringResource(R.string.device_classic_audio_endpoint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                device.sonyAd?.let { ad ->
                    Text(ad.summary, style = MaterialTheme.typography.bodySmall,
                        color = if (ad.androidGattCapable || ad.leGattControlFlag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(onClick = onConnect) { Text(stringResource(R.string.device_connect)) }
        }
    }
}

@Composable
internal fun RowScope.BatteryTile(label: String, value: Int?, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.weight(1f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Text(text = value?.let { "$it%" } ?: "--", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun AppIdentityHeader(state: SonyHeadphoneUiState) {
    SectionCard(title = stringResource(R.string.app_name), icon = Icons.Rounded.Headphones) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))) {
                Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = state.connectedProfile?.modelName ?: stringResource(R.string.home_sony_control), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = if (state.connectedDevice != null) stringResource(R.string.device_status_connected) else "Status: ${state.scanState.lowercase()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
