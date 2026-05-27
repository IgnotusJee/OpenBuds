package dev.ignotus.openbuds.ui.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ignotus.openbuds.R
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.headphones.HeadphoneFeature
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dev.ignotus.openbuds.protocol.PlaybackStatus
import dev.ignotus.openbuds.ui.ModeButton
import dev.ignotus.openbuds.ui.SectionCard
import dev.ignotus.openbuds.ui.SettingRow

@Composable
internal fun QuickControlCard(state: SonyHeadphoneUiState, actions: DeviceActionCallback) {
    val connected = state.connectedDevice != null && state.deviceInfo.protocolReady
    val noiseEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.NOISE_CONTROL)
    val ambientLevelEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.AMBIENT_LEVEL)
    val ambientVoiceEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.AMBIENT_VOICE_MODE)
    val eqEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.EQ)
    val playbackEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.PLAYBACK_CONTROL)

    SectionCard(title = stringResource(R.string.device_quick_controls), icon = Icons.Rounded.MusicNote) {
        var showEqDetails by remember { mutableStateOf(false) }
        val controlMode = state.noiseControlState.controlMode ?: when {
            state.noiseControlState.ambientSoundEnabled == true -> NoiseControlMode.AMBIENT_SOUND
            state.noiseControlState.noiseCancellingEnabled == true -> NoiseControlMode.NOISE_CANCELLING
            state.noiseControlState.noiseCancellingEnabled == false || state.noiseControlState.ambientSoundEnabled == false -> NoiseControlMode.OFF
            else -> null
        }
        val ambientLevel = (state.noiseControlState.ambientLevel ?: 10).coerceIn(1, 20)
        NoiseControlModeCard(
            selectedMode = controlMode, ambientLevel = ambientLevel, voiceFocus = state.noiseControlState.ambientVoiceMode,
            enabled = noiseEnabled, ambientLevelEnabled = ambientLevelEnabled, ambientVoiceEnabled = ambientVoiceEnabled,
            onSetMode = actions.onSetNoiseControlMode, onSetAmbientLevel = actions.onSetAmbientLevel, onSetAmbientVoiceMode = actions.onSetAmbientVoiceMode,
        )
        EqControlCard(
            capability = state.eqUiCapability, selectedPreset = state.eqState.preset, clearBass = state.eqState.clearBass ?: 0,
            bandSteps = state.eqState.bandSteps, bandStepCenter = state.eqState.bandStepCenter, usesCustomEqPayload = state.eqState.usesCustomEqPayload,
            expanded = showEqDetails, enabled = eqEnabled,
            onToggleExpanded = { showEqDetails = !showEqDetails }, onSetEqPreset = actions.onSetEqPreset,
            onSetClearBass = actions.onSetClearBass, onSetCustomEqBand = actions.onSetCustomEqBand,
        )
        Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val playPauseIcon = if (state.playbackStatus == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
            PlaybackButton(Icons.Rounded.SkipPrevious, stringResource(R.string.device_playback_previous), playbackEnabled, actions.onPlaybackPrevious)
            PlaybackButton(playPauseIcon, stringResource(R.string.device_playback_play_pause), playbackEnabled, actions.onPlaybackPlayPause)
            PlaybackButton(Icons.Rounded.SkipNext, stringResource(R.string.device_playback_next), playbackEnabled, actions.onPlaybackNext)
        }
        Text(stringResource(R.string.device_playback_status, state.playbackStatus.name.lowercase()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NoiseControlModeCard(selectedMode: NoiseControlMode?, ambientLevel: Int, voiceFocus: Boolean, enabled: Boolean, ambientLevelEnabled: Boolean, ambientVoiceEnabled: Boolean,
                                 onSetMode: (NoiseControlMode) -> Unit, onSetAmbientLevel: (Int) -> Unit, onSetAmbientVoiceMode: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.device_noise_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ModeButton(text = stringResource(R.string.device_nc_noise_cancelling), selected = selectedMode == NoiseControlMode.NOISE_CANCELLING, enabled = enabled, modifier = Modifier.weight(1f), onClick = { onSetMode(NoiseControlMode.NOISE_CANCELLING) })
                ModeButton(text = stringResource(R.string.device_nc_ambient_sound), selected = selectedMode == NoiseControlMode.AMBIENT_SOUND, enabled = enabled, modifier = Modifier.weight(1f), onClick = { onSetMode(NoiseControlMode.AMBIENT_SOUND) })
                ModeButton(text = stringResource(R.string.device_nc_off), selected = selectedMode == NoiseControlMode.OFF, enabled = enabled, modifier = Modifier.weight(1f), onClick = { onSetMode(NoiseControlMode.OFF) })
            }
            if (selectedMode == NoiseControlMode.AMBIENT_SOUND) {
                SettingRow(title = stringResource(R.string.device_ambient_level), subtitle = stringResource(R.string.device_ambient_level_label, ambientLevel), trailing = {
                    StepperControl(value = ambientLevel, enabled = enabled && ambientLevelEnabled, min = 1, max = 20, onDecrease = { onSetAmbientLevel(ambientLevel - 1) }, onIncrease = { onSetAmbientLevel(ambientLevel + 1) })
                })
                var sliderLevel by remember(ambientLevel) { mutableFloatStateOf(ambientLevel.toFloat()) }
                Slider(value = sliderLevel, onValueChange = { sliderLevel = it }, onValueChangeFinished = { onSetAmbientLevel(sliderLevel.toInt().coerceIn(1, 20)) }, enabled = enabled && ambientLevelEnabled, valueRange = 1f..20f, steps = 18, modifier = Modifier.fillMaxWidth())
                SettingRow(title = stringResource(R.string.device_voice_focus), subtitle = if (voiceFocus) stringResource(R.string.device_voice_focus_on) else stringResource(R.string.device_voice_focus_off), trailing = { Switch(checked = voiceFocus, enabled = enabled && ambientVoiceEnabled, onCheckedChange = onSetAmbientVoiceMode) })
            }
            if (selectedMode == null) { Text(stringResource(R.string.device_nc_not_reported), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun EqControlCard(capability: dev.ignotus.openbuds.headphones.EqUiCapability?, selectedPreset: EqPresetId?, clearBass: Int, bandSteps: List<Int>, bandStepCenter: Int,
                          usesCustomEqPayload: Boolean, expanded: Boolean, enabled: Boolean, onToggleExpanded: () -> Unit,
                          onSetEqPreset: (EqPresetId) -> Unit, onSetClearBass: (Int) -> Unit, onSetCustomEqBand: (Int, Int) -> Unit) {
    val bandLabels = capability?.bandLabels ?: emptyList()
    val presets = capability?.availablePresets ?: emptyList()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.device_eq_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.device_eq_preset_label, selectedPreset?.displayName ?: stringResource(R.string.home_unknown), bandSteps.size, bandStepCenter), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingRow(title = stringResource(R.string.device_eq_equalizer), subtitle = stringResource(R.string.device_eq_clear_bass, clearBass.coerceIn(-10, 10)), trailing = {
                IconButton(onClick = onToggleExpanded, enabled = enabled) { Icon(imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = if (expanded) stringResource(R.string.device_eq_collapse) else stringResource(R.string.device_eq_expand)) }
            })
            if (!expanded) return@Column
            presets.chunked(3).forEach { rowPresets ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowPresets.forEach { preset -> ModeButton(text = preset.displayName, selected = selectedPreset == preset, enabled = enabled, modifier = Modifier.weight(1f), onClick = { onSetEqPreset(preset) }) }
                    repeat(3 - rowPresets.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            SettingRow(title = stringResource(R.string.device_eq_clear_bass_title), subtitle = stringResource(R.string.device_eq_clear_bass_level, clearBass.coerceIn(-10, 10)), trailing = {
                StepperControl(value = clearBass.coerceIn(-10, 10), enabled = enabled, min = -10, max = 10, onDecrease = { onSetClearBass(clearBass - 1) }, onIncrease = { onSetClearBass(clearBass + 1) })
            })
            var sliderBass by remember(clearBass) { mutableFloatStateOf(clearBass.coerceIn(-10, 10).toFloat()) }
            Slider(value = sliderBass, onValueChange = { sliderBass = it }, onValueChangeFinished = { onSetClearBass(sliderBass.toInt().coerceIn(-10, 10)) }, enabled = enabled, valueRange = -10f..10f, steps = 19)
            if (bandSteps.isEmpty()) {
                Text(stringResource(R.string.device_eq_no_bands), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(if (usesCustomEqPayload) stringResource(R.string.device_eq_custom_active) else stringResource(R.string.device_eq_preset_active), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                bandSteps.forEachIndexed { index, step ->
                    val bandLabel = bandLabels.getOrNull(index) ?: stringResource(R.string.device_eq_band_label, index + 1)
                    SettingRow(title = bandLabel, subtitle = stringResource(R.string.device_eq_band_level, step.coerceIn(-10, 10)), trailing = {
                        StepperControl(value = step.coerceIn(-10, 10), enabled = enabled, min = -10, max = 10, onDecrease = { onSetCustomEqBand(index, step - 1) }, onIncrease = { onSetCustomEqBand(index, step + 1) })
                    })
                    var sliderBand by remember(index, step) { mutableFloatStateOf(step.coerceIn(-10, 10).toFloat()) }
                    Slider(value = sliderBand, onValueChange = { sliderBand = it }, onValueChangeFinished = { onSetCustomEqBand(index, sliderBand.toInt().coerceIn(-10, 10)) }, enabled = enabled, valueRange = -10f..10f, steps = 19)
                }
            }
        }
    }
}

@Composable
private fun PlaybackButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun StepperControl(value: Int, enabled: Boolean, min: Int = 0, max: Int = 20, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDecrease, enabled = enabled && value > min) { Text("-") }
        Text(value.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onIncrease, enabled = enabled && value < max) { Text("+") }
    }
}
