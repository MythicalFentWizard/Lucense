package com.exo.musicplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.playback.AudioFxState
import com.exo.musicplayer.ui.common.LineSlider
import com.exo.musicplayer.playback.AudioOutput
import com.exo.musicplayer.playback.ToneProcessor
import com.exo.musicplayer.playback.FxPreset
import com.exo.musicplayer.playback.ReverbRoom
import kotlin.math.roundToInt

@Composable
fun EffectsSheet(
    state: AudioFxState,
    /** True while the song playing has effects of its own. */
    remembered: Boolean,
    hasSong: Boolean,
    controls: FxControls,
    outputs: List<AudioOutput>,
    selectedOutputs: Set<String>,
    mirrorOutputs: Boolean,
    /** Drives the "live"/"idle" note on the meter. */
    playing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val onPreset = controls.onPreset
    val onSpeed = controls.onSpeed
    val onPitch = controls.onPitch
    val onReverbEnabled = controls.onReverbEnabled
    val onReverbRoom = controls.onReverbRoom
    val onReverbAmount = controls.onReverbAmount
    val onReset = controls.onReset
    val onPickOutput = controls.onPickOutput
    val onMirrorOutputs = controls.onMirrorOutputs
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // Above the controls, as on the Windows build: the effects are the
        // reason to look at the meter, so it belongs where the effects are and
        // it only runs while this sheet is open.
        SpectrumMeter(playing = playing, modifier = Modifier.padding(bottom = 16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Effects",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(visible = !state.isDefault) {
                TextButton(onClick = onReset) {
                    Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset")
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Speed, pitch, reverb and the equalizer are independent — stack them however you like.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Effects kept for one song, as on Windows: they come back when that
        // song plays and give way to the usual ones when it ends.
        if (hasSong) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (remembered) "Kept for this song" else "Only for this song?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (remembered) "These come back whenever it plays." else
                            "Remember these effects and they come back whenever it plays.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (remembered) {
                    TextButton(onClick = controls.onRemember) { Text("Update") }
                    TextButton(onClick = controls.onForget) { Text("Forget") }
                } else {
                    TextButton(onClick = controls.onRemember) { Text("Remember") }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Output",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (outputs.isEmpty()) {
                "No outputs detected."
            } else if (selectedOutputs.isEmpty()) {
                "Following the system. Tap a device to pin playback to it."
            } else {
                "Pinned. Tap again to go back to the system default."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        outputs.forEach { output ->
            OutputRow(
                output = output,
                selected = output.key in selectedOutputs,
                onClick = { onPickOutput(output.key) }
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Play on several at once", style = MaterialTheme.typography.bodyMedium)
                Text(
                    // Stated up front because the limitation is Android's, not a bug.
                    "Experimental. Android has no real multi-output API, so extra " +
                        "devices run as separate synced players — expect an echo on " +
                        "Bluetooth, and some phones will just double up on one device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = mirrorOutputs, onCheckedChange = onMirrorOutputs)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Effects",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FxPreset.entries.forEach { preset ->
                PresetChip(
                    label = preset.label,
                    selected = matches(state, preset),
                    onClick = { onPreset(preset) }
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            // Says what the selected preset actually does. "Nightcore" and
            // "Deep" are not self-explanatory, and the chips have no room to be.
            text = FxPreset.entries.firstOrNull { matches(state, it) }?.note ?: "Custom",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        PitchMeter(semitones = state.pitchSemitones)
        LineSlider(
            value = state.pitchSemitones,
            onValueChange = onPitch,
            valueRange = AudioFxState.MIN_SEMITONES..AudioFxState.MAX_SEMITONES,
            // 96 stops gives quarter-semitone resolution without free-running drift.
            steps = 95,
            from = 0f,
            description = "Pitch"
        )
        TickRow(listOf("-12", "-6", "0", "+6", "+12"))
        LabelRow("Pitch", state.pitchLabel)

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Speed", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = "%.2f×".format(state.speed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        LineSlider(
            value = state.speed,
            onValueChange = onSpeed,
            valueRange = AudioFxState.MIN_SPEED..AudioFxState.MAX_SPEED,
            steps = 29,
            from = 1f,
            description = "Speed"
        )
        Text(
            text = "Tempo only — pitch stays where you set it above.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Reverb",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = state.reverbEnabled, onCheckedChange = onReverbEnabled)
        }

        AnimatedVisibility(visible = state.reverbEnabled) {
            Column {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReverbRoom.entries.forEach { room ->
                        PresetChip(
                            label = room.label,
                            selected = state.reverbRoom == room,
                            onClick = { onReverbRoom(room) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LineSlider(
                    value = state.reverbAmount,
                    onValueChange = onReverbAmount,
                    description = "Reverb amount"
                )
                LabelRow("Amount", "${(state.reverbAmount * 100).roundToInt()}%")
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Equalizer", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Ten bands, 31 Hz to 16 kHz, the same as on Windows.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = state.eqEnabled, onCheckedChange = controls.onEqEnabled)
        }
        AnimatedVisibility(visible = state.eqEnabled) {
            Column {
                Spacer(Modifier.height(6.dp))
                ToneProcessor.LABELS.forEachIndexed { band, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(36.dp)
                        )
                        LineSlider(
                            value = state.eqGains.getOrElse(band) { 0f },
                            onValueChange = { controls.onEqBand(band, (it * 2).roundToInt() / 2f) },
                            valueRange = -12f..12f,
                            from = 0f,
                            description = "$label band",
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "%+.1f".format(state.eqGains.getOrElse(band) { 0f }),
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            modifier = Modifier.width(44.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = controls.onEqFlat) { Text("Flat") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OutputRow(output: AudioOutput, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (output.isSpeaker) {
                Icons.Default.Speaker
            } else {
                Icons.Default.Headphones
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = output.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun LabelRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** A preset reads as selected only when every axis still matches it. */
private fun matches(state: AudioFxState, preset: FxPreset): Boolean {
    val p = preset.state
    return kotlin.math.abs(state.speed - p.speed) < 0.005f &&
        kotlin.math.abs(state.pitchSemitones - p.pitchSemitones) < 0.05f &&
        state.reverbEnabled == p.reverbEnabled &&
        (!p.reverbEnabled || state.reverbRoom == p.reverbRoom)
}

/**
 * Scale markings under a slider.
 *
 * A slider with no scale only tells you where the handle is, not what that
 * means, which matters most for pitch — where the useful information is how far
 * from centre you are.
 */
@Composable
private fun TickRow(labels: List<String>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (index != labels.lastIndex) Spacer(Modifier.weight(1f))
        }
    }
}
