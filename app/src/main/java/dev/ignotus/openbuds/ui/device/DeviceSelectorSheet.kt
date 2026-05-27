package dev.ignotus.openbuds.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ignotus.openbuds.R
import dev.ignotus.openbuds.ble.DiscoveredSonyDevice

@Composable
internal fun DeviceSelectorSheet(
    knownDevices: List<DiscoveredSonyDevice>,
    scanResults: List<DiscoveredSonyDevice>,
    onSelect: (DiscoveredSonyDevice) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        if (knownDevices.isNotEmpty()) {
            Text(stringResource(R.string.device_known_devices), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            knownDevices.forEach { device -> DeviceSelectorRow(device, onSelect) }
        }
        Text(stringResource(R.string.device_scan_results), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        scanResults.forEach { device -> DeviceSelectorRow(device, onSelect) }
    }
}

@Composable
private fun DeviceSelectorRow(device: DiscoveredSonyDevice, onSelect: (DiscoveredSonyDevice) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${device.address}  RSSI ${device.rssi}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onSelect(device) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.device_connect))
            }
        }
    }
}
