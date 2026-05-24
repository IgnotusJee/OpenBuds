package dev.ignotus.openbuds.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ignotus.openbuds.ble.DiscoveredSonyDevice
import dev.ignotus.openbuds.data.FeatureStatus
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.headphones.ConnectedHeadphoneProfile
import dev.ignotus.openbuds.headphones.EqUiCapability
import dev.ignotus.openbuds.headphones.HeadphoneFeature
import dev.ignotus.openbuds.headphones.HeadphoneFormFactor
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dev.ignotus.openbuds.protocol.PlaybackStatus
import dev.ignotus.openbuds.ui.EmptyHint
import dev.ignotus.openbuds.ui.InfoLine
import dev.ignotus.openbuds.ui.ModeButton
import dev.ignotus.openbuds.ui.PageColumn
import dev.ignotus.openbuds.ui.PageHeader
import dev.ignotus.openbuds.ui.SectionCard
import dev.ignotus.openbuds.ui.SettingRow
import dev.ignotus.openbuds.ui.StatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ---------------------------------------------------------------------------
// Helpers (not Composable)
// ---------------------------------------------------------------------------

internal val DeviceImageCache = LruCache<String, Bitmap>(16)

internal fun cachedBitmap(imageUrl: String): Bitmap? = synchronized(DeviceImageCache) {
    DeviceImageCache.get(imageUrl)
}

internal suspend fun loadRemoteBitmap(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
    cachedBitmap(imageUrl)?.let { return@withContext it }
    runCatching {
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            instanceFollowRedirects = true
        }
        try {
            connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()?.also { bitmap ->
        synchronized(DeviceImageCache) {
            DeviceImageCache.put(imageUrl, bitmap)
        }
    }
}

internal fun ConnectedHeadphoneProfile?.supports(feature: HeadphoneFeature): Boolean =
    this?.supports(feature) == true

// ---------------------------------------------------------------------------
// Composables
// ---------------------------------------------------------------------------

@Composable
internal fun AppIdentityHeader(state: SonyHeadphoneUiState) {
    SectionCard(title = "OpenBuds", icon = Icons.Rounded.Headphones) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.connectedProfile?.modelName ?: "Sony headphone control",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Status: ${if (state.connectedDevice != null) "connected" else state.scanState.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun DeviceScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: androidx.compose.ui.unit.Dp,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (DiscoveredSonyDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onSetNoiseControlMode: (NoiseControlMode) -> Unit,
    onSetAmbientLevel: (Int) -> Unit,
    onSetAmbientVoiceMode: (Boolean) -> Unit,
    onSetEqPreset: (EqPresetId) -> Unit,
    onSetClearBass: (Int) -> Unit,
    onSetCustomEqBand: (Int, Int) -> Unit,
    onPlaybackPrevious: () -> Unit,
    onPlaybackPlayPause: () -> Unit,
    onPlaybackNext: () -> Unit,
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        PageHeader(
            title = "Device",
            subtitle = if (state.connectedDevice == null) {
                "Connect a Sony control endpoint or inspect discovered devices"
            } else {
                "Headphone controls are shown from the active capability profile"
            },
        )
        ConnectionCard(
            state = state,
            onStartScan = onStartScan,
            onStopScan = onStopScan,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onRefresh = onRefresh,
        )
        EndpointDiagnosticsCard(state)
        if (state.connectedDevice != null) {
            DeviceInfoCard(state)
            BatteryCard(state)
            LeaStatusCard(state)
            QuickAccessStatusCard(state)
            WearingStatusCard(state)
            QuickControlCard(
                state = state,
                onSetNoiseControlMode = onSetNoiseControlMode,
                onSetAmbientLevel = onSetAmbientLevel,
                onSetAmbientVoiceMode = onSetAmbientVoiceMode,
                onSetEqPreset = onSetEqPreset,
                onSetClearBass = onSetClearBass,
                onSetCustomEqBand = onSetCustomEqBand,
                onPlaybackPrevious = onPlaybackPrevious,
                onPlaybackPlayPause = onPlaybackPlayPause,
                onPlaybackNext = onPlaybackNext,
            )
        }
    }
}

@Composable
internal fun ConnectionCard(
    state: SonyHeadphoneUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (DiscoveredSonyDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
) {
    SectionCard(title = "Connection", icon = Icons.Rounded.Bluetooth) {
        val connected = state.connectedDevice
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatusPill(state.scanState, connected != null || state.isScanning)
            state.connectionInfo?.let {
                StatusPill("MTU ${it.mtu}", true)
            }
            state.permissionIssue?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (connected == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onStartScan, enabled = !state.isScanning) {
                    Text("Scan")
                }
                OutlinedButton(onClick = onStopScan, enabled = state.isScanning) {
                    Text("Stop")
                }
            }
            if (state.knownDevices.isNotEmpty()) {
                Text(
                    text = "Known devices",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                state.knownDevices.forEach { device ->
                    DeviceRow(device = device, onConnect = { onConnect(device) })
                }
            }
            Text(
                text = "Scan results",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.discoveredDevices.isEmpty()) {
                EmptyHint("No Sony Tandem V2 devices found yet.")
            } else {
                state.discoveredDevices.forEach { device ->
                    DeviceRow(device = device, onConnect = { onConnect(device) })
                }
            }
        } else {
            InfoLine("Connected", "${connected.name} (${connected.address})")
            state.connectedProfile?.let { profile ->
                InfoLine("Profile", "${profile.brand} ${profile.modelName}")
                InfoLine("Transport", profile.transport.name)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRefresh) {
                    Text("Refresh")
                }
                OutlinedButton(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
internal fun EndpointDiagnosticsCard(state: SonyHeadphoneUiState) {
    val diagnostic = state.endpointDiagnostic ?: return
    SectionCard(title = "Endpoint diagnostics", icon = Icons.Rounded.Info) {
        Text(
            text = diagnostic.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        InfoLine("Mode", "LE Audio / auxiliary GATT endpoint")
        InfoLine(
            "LEA compatibility",
            diagnostic.leAudioSwitchCompatibility?.toString() ?: "Unknown",
        )
        diagnostic.friendlyName?.let { InfoLine("Friendly name", it) }
        diagnostic.publicAddress?.let { InfoLine("Public address", it) }
        InfoLine("Services", diagnostic.serviceLabels.joinToString())
        diagnostic.rawReads.entries.take(5).forEach { (name, value) ->
            InfoLine(name, value)
        }
    }
}

@Composable
internal fun DeviceInfoCard(state: SonyHeadphoneUiState) {
    SectionCard(title = "Device info") {
        val info = state.deviceInfo
        DeviceModelImage(
            imageUrl = info.modelImageUrl,
            modelName = info.modelName ?: state.connectedDevice?.name,
        )
        InfoLine("Protocol channel", if (info.protocolReady) "Sony Tandem ready" else "Not ready")
        state.connectedProfile?.let { profile ->
            InfoLine("Adapter", "${profile.adapterId} / ${profile.protocolName}")
            InfoLine("Transport", profile.transport.name)
        }
        InfoLine("Model", info.modelName ?: state.connectedDevice?.name ?: "Unknown")
        InfoLine("Firmware", info.firmwareVersion ?: "Unknown")
        InfoLine("Series / color", info.seriesAndColor ?: "Unknown")
        InfoLine("Image match", info.modelImageUrl?.let { info.modelColor ?: "Default" } ?: "Default placeholder")
    }
}

@Composable
internal fun DeviceModelImage(
    imageUrl: String?,
    modelName: String?,
) {
    val bitmap by produceState<Bitmap?>(initialValue = imageUrl?.let(::cachedBitmap), imageUrl) {
        value = imageUrl?.let { cachedBitmap(it) ?: loadRemoteBitmap(it) }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = modelName ?: "Sony device",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .padding(12.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Bluetooth,
                contentDescription = modelName ?: "Sony device",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
internal fun BatteryCard(state: SonyHeadphoneUiState) {
    SectionCard(title = "Battery", icon = Icons.Rounded.BatteryChargingFull) {
        val battery = state.batteryState
        val headsetBatteryOnly = state.connectedProfile?.capabilities?.formFactor == HeadphoneFormFactor.HEADSET
        if (headsetBatteryOnly) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BatteryTile("Headset", battery.single ?: battery.left ?: battery.right)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BatteryTile("Left", battery.left)
                BatteryTile("Right", battery.right)
                BatteryTile("Case", battery.cradle)
            }
        }
        if (battery.raw.isNotEmpty()) {
            Text(
                text = "Raw battery payload: ${battery.raw}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
internal fun LeaStatusCard(state: SonyHeadphoneUiState) {
    val lea = state.leaState
    if (lea.enabled == null && lea.streamingStatusL == null && lea.streamingStatusR == null && lea.pairedHistory == null) return
    SectionCard(title = "LE Audio", icon = Icons.Rounded.Bluetooth) {
        lea.enabled?.let { InfoLine("Enabled", it) }
        lea.streamingStatusL?.let { InfoLine("Streaming L", it) }
        lea.streamingStatusR?.let { InfoLine("Streaming R", it) }
        lea.pairedHistory?.let { InfoLine("Paired history", it) }
        if (lea.raw.isNotEmpty()) {
            InfoLine("Raw", lea.raw.joinToString(" "))
        }
    }
}

@Composable
internal fun QuickAccessStatusCard(state: SonyHeadphoneUiState) {
    val qa = state.quickAccessState
    if (qa.lrKeyFunction == null && qa.ncAmbKeyFunction == null) return
    SectionCard(title = "Quick Access", icon = Icons.Rounded.Settings) {
        qa.lrKeyFunction?.let { InfoLine("L/R Key", it) }
        qa.ncAmbKeyFunction?.let { InfoLine("NC/AMB Key", it) }
        if (qa.raw.isNotEmpty()) {
            InfoLine("Raw", qa.raw.joinToString(" "))
        }
    }
}

@Composable
internal fun WearingStatusCard(state: SonyHeadphoneUiState) {
    val w = state.wearingState
    if (w.status == null && w.result == null) return
    SectionCard(title = "Wearing detection", icon = Icons.Rounded.Headphones) {
        w.status?.let { InfoLine("Status", it) }
        w.result?.let { InfoLine("Fit result", it) }
        if (w.raw.isNotEmpty()) {
            InfoLine("Raw", w.raw.joinToString(" "))
        }
    }
}

@Composable
internal fun QuickControlCard(
    state: SonyHeadphoneUiState,
    onSetNoiseControlMode: (NoiseControlMode) -> Unit,
    onSetAmbientLevel: (Int) -> Unit,
    onSetAmbientVoiceMode: (Boolean) -> Unit,
    onSetEqPreset: (EqPresetId) -> Unit,
    onSetClearBass: (Int) -> Unit,
    onSetCustomEqBand: (Int, Int) -> Unit,
    onPlaybackPrevious: () -> Unit,
    onPlaybackPlayPause: () -> Unit,
    onPlaybackNext: () -> Unit,
) {
    SectionCard(title = "Quick controls", icon = Icons.Rounded.MusicNote) {
        var showEqDetails by remember { mutableStateOf(false) }
        val connected = state.connectedDevice != null && state.deviceInfo.protocolReady
        val noiseEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.NOISE_CONTROL)
        val ambientLevelEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.AMBIENT_LEVEL)
        val ambientVoiceEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.AMBIENT_VOICE_MODE)
        val eqEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.EQ)
        val playbackEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.PLAYBACK_CONTROL)
        val controlMode = state.noiseControlState.controlMode ?: when {
            state.noiseControlState.ambientSoundEnabled == true -> NoiseControlMode.AMBIENT_SOUND
            state.noiseControlState.noiseCancellingEnabled == true -> NoiseControlMode.NOISE_CANCELLING
            state.noiseControlState.noiseCancellingEnabled == false ||
                state.noiseControlState.ambientSoundEnabled == false -> NoiseControlMode.OFF
            else -> null
        }
        val ambientLevel = (state.noiseControlState.ambientLevel ?: 10).coerceIn(1, 20)
        NoiseControlModeCard(
            selectedMode = controlMode,
            ambientLevel = ambientLevel,
            voiceFocus = state.noiseControlState.ambientVoiceMode,
            enabled = noiseEnabled,
            ambientLevelEnabled = ambientLevelEnabled,
            ambientVoiceEnabled = ambientVoiceEnabled,
            onSetMode = onSetNoiseControlMode,
            onSetAmbientLevel = onSetAmbientLevel,
            onSetAmbientVoiceMode = onSetAmbientVoiceMode,
        )
        EqControlCard(
            capability = state.eqUiCapability,
            selectedPreset = state.eqState.preset,
            clearBass = state.eqState.clearBass ?: 0,
            bandSteps = state.eqState.bandSteps,
            bandStepCenter = state.eqState.bandStepCenter,
            usesCustomEqPayload = state.eqState.usesCustomEqPayload,
            expanded = showEqDetails,
            enabled = eqEnabled,
            onToggleExpanded = { showEqDetails = !showEqDetails },
            onSetEqPreset = onSetEqPreset,
            onSetClearBass = onSetClearBass,
            onSetCustomEqBand = onSetCustomEqBand,
        )
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val playPauseIcon = if (state.playbackStatus == PlaybackStatus.PLAYING) {
                Icons.Rounded.Pause
            } else {
                Icons.Rounded.PlayArrow
            }
            PlaybackButton(Icons.Rounded.SkipPrevious, "Previous", playbackEnabled, onPlaybackPrevious)
            PlaybackButton(playPauseIcon, "Play or pause", playbackEnabled, onPlaybackPlayPause)
            PlaybackButton(Icons.Rounded.SkipNext, "Next", playbackEnabled, onPlaybackNext)
        }
        Text(
            text = "Playback: ${state.playbackStatus.name.lowercase()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun EqControlCard(
    capability: EqUiCapability?,
    selectedPreset: EqPresetId?,
    clearBass: Int,
    bandSteps: List<Int>,
    bandStepCenter: Int,
    usesCustomEqPayload: Boolean,
    expanded: Boolean,
    enabled: Boolean,
    onToggleExpanded: () -> Unit,
    onSetEqPreset: (EqPresetId) -> Unit,
    onSetClearBass: (Int) -> Unit,
    onSetCustomEqBand: (Int, Int) -> Unit,
) {
    val bandLabels = capability?.bandLabels ?: emptyList()
    val presets = capability?.availablePresets ?: emptyList()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = "EQ / Clear Bass",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Preset: ${selectedPreset?.displayName ?: "Unknown"}  Bands: ${bandSteps.size}  Center: $bandStepCenter",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingRow(
                title = "Equalizer",
                subtitle = "Clear Bass ${clearBass.coerceIn(-10, 10)}",
                trailing = {
                    IconButton(onClick = onToggleExpanded, enabled = enabled) {
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Rounded.KeyboardArrowUp
                            } else {
                                Icons.Rounded.KeyboardArrowDown
                            },
                            contentDescription = if (expanded) "Collapse equalizer" else "Expand equalizer",
                        )
                    }
                },
            )
            if (!expanded) {
                return@Column
            }
            presets.chunked(3).forEach { rowPresets ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowPresets.forEach { preset ->
                        ModeButton(
                            text = preset.displayName,
                            selected = selectedPreset == preset,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                            onClick = { onSetEqPreset(preset) },
                        )
                    }
                    repeat(3 - rowPresets.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            SettingRow(
                title = "Clear Bass",
                subtitle = "Level ${clearBass.coerceIn(-10, 10)}",
                trailing = {
                    StepperControl(
                        value = clearBass.coerceIn(-10, 10),
                        enabled = enabled,
                        min = -10,
                        max = 10,
                        onDecrease = { onSetClearBass(clearBass - 1) },
                        onIncrease = { onSetClearBass(clearBass + 1) },
                    )
                },
            )
            var sliderBass by remember(clearBass) { mutableFloatStateOf(clearBass.coerceIn(-10, 10).toFloat()) }
            Slider(
                value = sliderBass,
                onValueChange = { sliderBass = it },
                onValueChangeFinished = { onSetClearBass(sliderBass.toInt().coerceIn(-10, 10)) },
                enabled = enabled,
                valueRange = -10f..10f,
                steps = 19,
            )
            if (bandSteps.isEmpty()) {
                Text(
                    text = "No editable EQ band payload reported by this headset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = if (usesCustomEqPayload) "CUSTOM EQ payload active" else "Preset EQ band payload active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                bandSteps.forEachIndexed { index, step ->
                    val bandLabel = bandLabels.getOrNull(index) ?: "Band ${index + 1}"
                    SettingRow(
                        title = bandLabel,
                        subtitle = "Level ${step.coerceIn(-10, 10)}",
                        trailing = {
                            StepperControl(
                                value = step.coerceIn(-10, 10),
                                enabled = enabled,
                                min = -10,
                                max = 10,
                                onDecrease = { onSetCustomEqBand(index, step - 1) },
                                onIncrease = { onSetCustomEqBand(index, step + 1) },
                            )
                        },
                    )
                    var sliderBand by remember(index, step) { mutableFloatStateOf(step.coerceIn(-10, 10).toFloat()) }
                    Slider(
                        value = sliderBand,
                        onValueChange = { sliderBand = it },
                        onValueChangeFinished = {
                            onSetCustomEqBand(index, sliderBand.toInt().coerceIn(-10, 10))
                        },
                        enabled = enabled,
                        valueRange = -10f..10f,
                        steps = 19,
                    )
                }
            }
        }
    }
}

@Composable
internal fun NoiseControlModeCard(
    selectedMode: NoiseControlMode?,
    ambientLevel: Int,
    voiceFocus: Boolean,
    enabled: Boolean,
    ambientLevelEnabled: Boolean,
    ambientVoiceEnabled: Boolean,
    onSetMode: (NoiseControlMode) -> Unit,
    onSetAmbientLevel: (Int) -> Unit,
    onSetAmbientVoiceMode: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = "Noise / Ambient",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ModeButton(
                    text = "降噪",
                    selected = selectedMode == NoiseControlMode.NOISE_CANCELLING,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSetMode(NoiseControlMode.NOISE_CANCELLING) },
                )
                ModeButton(
                    text = "环境声",
                    selected = selectedMode == NoiseControlMode.AMBIENT_SOUND,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSetMode(NoiseControlMode.AMBIENT_SOUND) },
                )
                ModeButton(
                    text = "关闭",
                    selected = selectedMode == NoiseControlMode.OFF,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSetMode(NoiseControlMode.OFF) },
                )
            }
            if (selectedMode == NoiseControlMode.AMBIENT_SOUND) {
                SettingRow(
                    title = "环境声强度",
                    subtitle = "Level $ambientLevel / 20",
                    trailing = {
                        StepperControl(
                            value = ambientLevel,
                            enabled = enabled && ambientLevelEnabled,
                            onDecrease = { onSetAmbientLevel(ambientLevel - 1) },
                            onIncrease = { onSetAmbientLevel(ambientLevel + 1) },
                        )
                    },
                )
                var sliderLevel by remember(ambientLevel) { mutableFloatStateOf(ambientLevel.toFloat()) }
                Slider(
                    value = sliderLevel,
                    onValueChange = { sliderLevel = it },
                    onValueChangeFinished = { onSetAmbientLevel(sliderLevel.toInt().coerceIn(1, 20)) },
                    enabled = enabled && ambientLevelEnabled,
                    valueRange = 1f..20f,
                    steps = 18,
                )
                SettingRow(
                    title = "关注语音",
                    subtitle = if (voiceFocus) "Voice focus enabled" else "Normal ambient sound",
                    trailing = {
                        Switch(
                            checked = voiceFocus,
                            enabled = enabled && ambientVoiceEnabled,
                            onCheckedChange = onSetAmbientVoiceMode,
                        )
                    },
                )
            }
            if (selectedMode == null) {
                Text(
                    text = "Current NC/ASM mode is not reported by this headset; controls are still available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}



@Composable
internal fun FeatureStatusCard(features: List<FeatureStatus>) {
    SectionCard(title = "Feature map", icon = Icons.Rounded.Code) {
        features.forEach { feature ->
            SettingRow(
                title = feature.title,
                subtitle = feature.description,
                trailing = { StatusPill(if (feature.implemented) "wired" else "reserved", feature.implemented) },
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${device.address}  ${device.source}  RSSI ${device.rssi}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (device.isLikelyControlEndpoint) {
                        "BLE control candidate"
                    } else if (device.sonyAd != null) {
                        "Sony AD found; official app uses SPP unless LE control flag is set"
                    } else {
                        "Classic audio endpoint; Connect uses official Sony SPP UUID"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                device.sonyAd?.let { ad ->
                    Text(
                        text = ad.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ad.androidGattCapable || ad.leGattControlFlag) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onConnect) {
                Text("Connect")
            }
        }
    }
}

@Composable
internal fun RowScope.BatteryTile(
    label: String,
    value: Int?,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.weight(1f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
        ) {
            Text(
                text = value?.let { "$it%" } ?: "--",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun StepperControl(
    value: Int,
    enabled: Boolean,
    min: Int = 0,
    max: Int = 20,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDecrease, enabled = enabled && value > min) {
            Text("-")
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = onIncrease, enabled = enabled && value < max) {
            Text("+")
        }
    }
}

@Composable
internal fun PlaybackButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
