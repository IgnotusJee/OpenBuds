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
import androidx.compose.ui.res.stringResource
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
import dev.ignotus.openbuds.headphones.InfoLayoutHint
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
import dev.ignotus.openbuds.R

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
    SectionCard(title = stringResource(R.string.app_name), icon = Icons.Rounded.Headphones) {
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
                    text = state.connectedProfile?.modelName ?: stringResource(R.string.home_sony_control),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (state.connectedDevice != null) {
                        stringResource(R.string.device_status_connected)
                    } else {
                        "Status: ${state.scanState.lowercase()}"
                    },
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
            title = stringResource(R.string.device_page_title),
            subtitle = if (state.connectedDevice == null) {
                stringResource(R.string.device_page_subtitle_disconnected)
            } else {
                stringResource(R.string.device_page_subtitle_connected)
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
    SectionCard(title = stringResource(R.string.device_connection), icon = Icons.Rounded.Bluetooth) {
        val connected = state.connectedDevice
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatusPill(state.scanState, connected != null || state.isScanning)
            state.connectionInfo?.let {
                StatusPill(stringResource(R.string.device_info_gatt_ready) + " ${it.mtu}", true)
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
                    Text(stringResource(R.string.device_scan))
                }
                OutlinedButton(onClick = onStopScan, enabled = state.isScanning) {
                    Text(stringResource(R.string.device_stop))
                }
            }
            if (state.knownDevices.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.device_known_devices),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                state.knownDevices.forEach { device ->
                    DeviceRow(device = device, onConnect = { onConnect(device) })
                }
            }
            Text(
                text = stringResource(R.string.device_scan_results),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.discoveredDevices.isEmpty()) {
                EmptyHint(stringResource(R.string.device_no_devices_found))
            } else {
                state.discoveredDevices.forEach { device ->
                    DeviceRow(device = device, onConnect = { onConnect(device) })
                }
            }
        } else {
            InfoLine(stringResource(R.string.device_info_connected), "${connected.name} (${connected.address})")
            state.connectedProfile?.let { profile ->
                InfoLine(stringResource(R.string.device_info_profile), "${profile.brand} ${profile.modelName}")
                InfoLine(stringResource(R.string.device_info_transport), profile.transport.name)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRefresh) {
                    Text(stringResource(R.string.device_refresh))
                }
                OutlinedButton(onClick = onDisconnect) {
                    Text(stringResource(R.string.device_disconnect))
                }
            }
        }
    }
}

@Composable
internal fun EndpointDiagnosticsCard(state: SonyHeadphoneUiState) {
    val diagnostic = state.endpointDiagnostic ?: return
    SectionCard(title = stringResource(R.string.device_endpoint_diag), icon = Icons.Rounded.Info) {
        Text(
            text = diagnostic.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        InfoLine(stringResource(R.string.device_diag_lea_mode), "LE Audio / auxiliary GATT endpoint")
        InfoLine(
            stringResource(R.string.device_diag_lea_compat),
            diagnostic.leAudioSwitchCompatibility?.toString() ?: stringResource(R.string.home_unknown),
        )
        diagnostic.friendlyName?.let { InfoLine(stringResource(R.string.device_diag_friendly_name), it) }
        diagnostic.publicAddress?.let { InfoLine(stringResource(R.string.device_diag_public_address), it) }
        InfoLine(stringResource(R.string.device_diag_services), diagnostic.serviceLabels.joinToString())
        diagnostic.rawReads.entries.take(5).forEach { (name, value) ->
            InfoLine(name, value)
        }
    }
}

@Composable
internal fun DeviceInfoCard(state: SonyHeadphoneUiState) {
    SectionCard(title = stringResource(R.string.device_device_info)) {
        val info = state.deviceInfo
        DeviceModelImage(
            imageUrl = info.modelImageUrl,
            modelName = info.modelName ?: state.connectedDevice?.name,
        )
        InfoLine(stringResource(R.string.device_info_protocol_channel), if (info.protocolReady) {
            state.connectedProfile?.protocolName?.let { "$it ready" } ?: stringResource(R.string.device_info_gatt_ready)
        } else {
            stringResource(R.string.device_info_not_ready)
        })
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
                contentDescription = modelName ?: stringResource(R.string.device_image_desc),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .padding(12.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Bluetooth,
                contentDescription = modelName ?: stringResource(R.string.device_image_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
internal fun BatteryCard(state: SonyHeadphoneUiState) {
    SectionCard(title = stringResource(R.string.device_battery), icon = Icons.Rounded.BatteryChargingFull) {
        val battery = state.batteryState
        val headsetBatteryOnly = state.connectedProfile?.capabilities?.formFactor == HeadphoneFormFactor.HEADSET
        if (headsetBatteryOnly) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BatteryTile(stringResource(R.string.device_battery_headset), battery.single ?: battery.left ?: battery.right)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BatteryTile(stringResource(R.string.device_battery_left), battery.left)
                BatteryTile(stringResource(R.string.device_battery_right), battery.right)
                BatteryTile(stringResource(R.string.device_battery_case), battery.cradle)
            }
        }
        if (battery.raw.isNotEmpty()) {
            Text(
                text = stringResource(R.string.device_battery_raw, battery.raw),
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
    SectionCard(title = stringResource(R.string.device_le_audio), icon = Icons.Rounded.Bluetooth) {
        lea.enabled?.let { InfoLine(stringResource(R.string.device_lea_enabled), it) }
        lea.streamingStatusL?.let { InfoLine(stringResource(R.string.device_lea_streaming_l), it) }
        lea.streamingStatusR?.let { InfoLine(stringResource(R.string.device_lea_streaming_r), it) }
        lea.pairedHistory?.let { InfoLine(stringResource(R.string.device_lea_paired), it) }
        if (lea.raw.isNotEmpty()) {
            InfoLine("Raw", lea.raw.joinToString(" "))
        }
    }
}

@Composable
internal fun QuickAccessStatusCard(state: SonyHeadphoneUiState) {
    val qa = state.quickAccessState
    if (qa.lrKeyFunction == null && qa.ncAmbKeyFunction == null) return
    SectionCard(title = stringResource(R.string.device_quick_access), icon = Icons.Rounded.Settings) {
        qa.lrKeyFunction?.let { InfoLine(stringResource(R.string.device_qa_lr_key), it) }
        qa.ncAmbKeyFunction?.let { InfoLine(stringResource(R.string.device_qa_nc_amb_key), it) }
        if (qa.raw.isNotEmpty()) {
            InfoLine("Raw", qa.raw.joinToString(" "))
        }
    }
}

@Composable
internal fun WearingStatusCard(state: SonyHeadphoneUiState) {
    val w = state.wearingState
    if (w.status == null && w.result == null) return
    SectionCard(title = stringResource(R.string.device_wearing), icon = Icons.Rounded.Headphones) {
        w.status?.let { InfoLine(stringResource(R.string.device_wearing_status), it) }
        w.result?.let { InfoLine(stringResource(R.string.device_wearing_result), it) }
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
    SectionCard(title = stringResource(R.string.device_quick_controls), icon = Icons.Rounded.MusicNote) {
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
            PlaybackButton(Icons.Rounded.SkipPrevious, stringResource(R.string.device_playback_previous), playbackEnabled, onPlaybackPrevious)
            PlaybackButton(playPauseIcon, stringResource(R.string.device_playback_play_pause), playbackEnabled, onPlaybackPlayPause)
            PlaybackButton(Icons.Rounded.SkipNext, stringResource(R.string.device_playback_next), playbackEnabled, onPlaybackNext)
        }
        Text(
            text = stringResource(R.string.device_playback_status, state.playbackStatus.name.lowercase()),
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
                text = stringResource(R.string.device_eq_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.device_eq_preset_label, selectedPreset?.displayName ?: stringResource(R.string.home_unknown), bandSteps.size, bandStepCenter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingRow(
                title = stringResource(R.string.device_eq_equalizer),
                subtitle = stringResource(R.string.device_eq_clear_bass, clearBass.coerceIn(-10, 10)),
                trailing = {
                    IconButton(onClick = onToggleExpanded, enabled = enabled) {
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Rounded.KeyboardArrowUp
                            } else {
                                Icons.Rounded.KeyboardArrowDown
                            },
                            contentDescription = if (expanded) stringResource(R.string.device_eq_collapse) else stringResource(R.string.device_eq_expand),
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
                title = stringResource(R.string.device_eq_clear_bass_title),
                subtitle = stringResource(R.string.device_eq_clear_bass_level, clearBass.coerceIn(-10, 10)),
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
                    text = stringResource(R.string.device_eq_no_bands),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = if (usesCustomEqPayload) stringResource(R.string.device_eq_custom_active) else stringResource(R.string.device_eq_preset_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                bandSteps.forEachIndexed { index, step ->
                    val bandLabel = bandLabels.getOrNull(index) ?: stringResource(R.string.device_eq_band_label, index + 1)
                    SettingRow(
                        title = bandLabel,
                        subtitle = stringResource(R.string.device_eq_band_level, step.coerceIn(-10, 10)),
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
                text = stringResource(R.string.device_noise_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ModeButton(
                    text = stringResource(R.string.device_nc_noise_cancelling),
                    selected = selectedMode == NoiseControlMode.NOISE_CANCELLING,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSetMode(NoiseControlMode.NOISE_CANCELLING) },
                )
                ModeButton(
                    text = stringResource(R.string.device_nc_ambient_sound),
                    selected = selectedMode == NoiseControlMode.AMBIENT_SOUND,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSetMode(NoiseControlMode.AMBIENT_SOUND) },
                )
                ModeButton(
                    text = stringResource(R.string.device_nc_off),
                    selected = selectedMode == NoiseControlMode.OFF,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSetMode(NoiseControlMode.OFF) },
                )
            }
            if (selectedMode == NoiseControlMode.AMBIENT_SOUND) {
                SettingRow(
                    title = stringResource(R.string.device_ambient_level),
                    subtitle = stringResource(R.string.device_ambient_level_label, ambientLevel),
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
                    title = stringResource(R.string.device_voice_focus),
                    subtitle = if (voiceFocus) stringResource(R.string.device_voice_focus_on) else stringResource(R.string.device_voice_focus_off),
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
                    text = stringResource(R.string.device_nc_not_reported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
                        stringResource(R.string.device_ble_candidate)
                    } else if (device.sonyAd != null) {
                        stringResource(R.string.device_sony_ad_found)
                    } else {
                        stringResource(R.string.device_classic_audio_endpoint)
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
                Text(stringResource(R.string.device_connect))
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
