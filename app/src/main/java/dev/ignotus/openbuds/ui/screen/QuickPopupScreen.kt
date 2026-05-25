package dev.ignotus.openbuds.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dev.ignotus.openbuds.protocol.PlaybackStatus
import dev.ignotus.openbuds.service.ControlCommand
import dev.ignotus.openbuds.service.DeviceStateSnapshot
import dev.ignotus.openbuds.ui.ModeButton
import dev.ignotus.openbuds.ui.StatusPill

@Composable
fun QuickPopupScreen(
    state: DeviceStateSnapshot,
    onExecuteCommand: (ControlCommand) -> Boolean,
    onOpenFullApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    var ambientSliderValue by remember(state.ambientLevel) { mutableFloatStateOf((state.ambientLevel ?: 10).toFloat()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header: device icon + name
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.deviceName ?: "Not connected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (state.isConnected) {
                    Spacer(Modifier.height(8.dp))

                    // Connection status
                    StatusPill(
                        if (state.isProtocolReady) "Connected" else "Connecting...",
                        active = state.isProtocolReady,
                    )

                    Spacer(Modifier.height(16.dp))

                    // Battery row
                    BatteryRow(state)
                    Spacer(Modifier.height(16.dp))

                    // Noise control row
                    NoiseControlRow(state, onExecuteCommand)
                    Spacer(Modifier.height(8.dp))

                    // Ambient level slider
                    if (state.ambientSoundEnabled == true) {
                        AmbientLevelSlider(
                            value = ambientSliderValue,
                            onValueChange = { ambientSliderValue = it },
                            onValueChangeFinished = {
                                onExecuteCommand(ControlCommand.SetAmbientLevel(ambientSliderValue.toInt()))
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // Playback control row
                    PlaybackControlRow(state, onExecuteCommand)

                } else {
                    Spacer(Modifier.height(12.dp))
                    Icon(
                        imageVector = Icons.Rounded.BluetoothDisabled,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Waiting for connection...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // More button
                TextButton(onClick = onOpenFullApp) {
                    Text("More settings")
                }
            }
        }
    }
}

@Composable
private fun BatteryRow(state: DeviceStateSnapshot) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.BatteryChargingFull,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        state.batteryLeft?.let {
            Text("L: $it%", style = MaterialTheme.typography.bodyMedium)
        }
        state.batteryRight?.let {
            Text("R: $it%", style = MaterialTheme.typography.bodyMedium)
        }
        state.batteryCradle?.let {
            Text("Case: $it%", style = MaterialTheme.typography.bodyMedium)
        }
        state.batterySingle?.let {
            Text("$it%", style = MaterialTheme.typography.bodyMedium)
        }
        if (state.batteryLeft == null && state.batteryRight == null && state.batteryCradle == null && state.batterySingle == null) {
            Text("---", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoiseControlRow(state: DeviceStateSnapshot, onExecute: (ControlCommand) -> Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ModeButton(
            text = "ANC",
            selected = state.noiseControlMode == NoiseControlMode.NOISE_CANCELLING,
            enabled = state.isProtocolReady,
            modifier = Modifier.weight(1f),
            onClick = { onExecute(ControlCommand.SetNoiseControl(NoiseControlMode.NOISE_CANCELLING)) },
        )
        ModeButton(
            text = "Ambient",
            selected = state.noiseControlMode == NoiseControlMode.AMBIENT_SOUND,
            enabled = state.isProtocolReady,
            modifier = Modifier.weight(1f),
            onClick = { onExecute(ControlCommand.SetNoiseControl(NoiseControlMode.AMBIENT_SOUND)) },
        )
        ModeButton(
            text = "Off",
            selected = state.noiseControlMode == NoiseControlMode.OFF,
            enabled = state.isProtocolReady,
            modifier = Modifier.weight(1f),
            onClick = { onExecute(ControlCommand.SetNoiseControl(NoiseControlMode.OFF)) },
        )
    }
}

@Composable
private fun AmbientLevelSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Ambient Level: ${value.toInt()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 1f..20f,
            steps = 18,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlaybackControlRow(state: DeviceStateSnapshot, onExecute: (ControlCommand) -> Boolean) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onExecute(ControlCommand.Playback(ControlCommand.PlaybackAction.PREVIOUS)) }) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = { onExecute(ControlCommand.Playback(ControlCommand.PlaybackAction.PLAY_PAUSE)) }) {
            Icon(
                imageVector = if (state.playbackStatus == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play/Pause",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = { onExecute(ControlCommand.Playback(ControlCommand.PlaybackAction.NEXT)) }) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
