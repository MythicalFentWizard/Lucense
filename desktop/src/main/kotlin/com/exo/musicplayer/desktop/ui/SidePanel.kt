package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.desktop.audio.DesktopAudioOutput
import com.exo.musicplayer.desktop.data.DesktopController
import com.exo.musicplayer.desktop.library.DesktopTrack
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Which side panel is showing, if any. */
enum class SidePanelKind { EFFECTS, OUTPUT, LYRICS }

data class DesktopFxState(
    val speed: Float = 1f,
    val pitchSemitones: Float = 0f,
    val reverbEnabled: Boolean = false,
    val reverbMix: Float = 0.35f,
    val reverbDecay: Float = 0.82f
) {
    val isDefault: Boolean
        get() = abs(speed - 1f) < 0.005f && abs(pitchSemitones) < 0.05f && !reverbEnabled
}

/**
 * Docked right-hand panel.
 *
 * A panel rather than a modal sheet: on a 1180px window there is room to leave
 * the controls open while browsing the library, and adjusting speed while a
 * track plays is exactly when you want to see the list too. The phone build had
 * to take over the screen; here that would be a regression.
 */
@Composable
fun SidePanel(
    kind: SidePanelKind,
    controller: DesktopController,
    track: DesktopTrack?,
    positionMs: Long,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .width(if (kind == SidePanelKind.LYRICS) 340.dp else 288.dp)
            .fillMaxHeight()
            .background(Palette.Sidebar)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .then(
                // The lyrics panel scrolls its own list; the others are short
                // enough to scroll as one column.
                if (kind == SidePanelKind.LYRICS) {
                    Modifier
                } else {
                    Modifier.verticalScroll(rememberScrollState())
                }
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (kind) {
                    SidePanelKind.EFFECTS -> "Effects"
                    SidePanelKind.OUTPUT -> "Output"
                    SidePanelKind.LYRICS -> "Lyrics"
                },
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Text,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close panel", Modifier.size(16.dp), tint = Palette.TextDim)
            }
        }
        Spacer(Modifier.height(10.dp))

        when (kind) {
            SidePanelKind.EFFECTS -> EffectsControls(controller.fx) { controller.fx = it }
            SidePanelKind.OUTPUT -> OutputControls(
                controller.outputs,
                controller.selectedOutputs,
                controller::toggleOutput
            )
            SidePanelKind.LYRICS -> LyricsPanel(controller, track, positionMs)
        }
    }
}

@Composable
private fun EffectsControls(fx: DesktopFxState, onFx: (DesktopFxState) -> Unit) {
    Text(
        "Speed, pitch and reverb are independent — stack them however you like.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextDim
    )

    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Preset("Normal", fx.isDefault) { onFx(DesktopFxState()) }
        Preset("Slowed", abs(fx.speed - 0.85f) < 0.01f) {
            onFx(fx.copy(speed = 0.85f, pitchSemitones = -1.5f))
        }
        Preset("Sped up", abs(fx.speed - 1.25f) < 0.01f) {
            onFx(fx.copy(speed = 1.25f, pitchSemitones = 1.5f))
        }
    }

    Spacer(Modifier.height(18.dp))
    Labelled("Speed", "%.2f×".format(fx.speed))
    Slider(
        value = fx.speed,
        onValueChange = { onFx(fx.copy(speed = it)) },
        valueRange = 0.5f..2f,
        steps = 29,
        colors = sliderColours()
    )
    Text(
        "Tempo only — pitch stays where you set it.",
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextFaint
    )

    Spacer(Modifier.height(16.dp))
    Labelled("Pitch", formatSemitones(fx.pitchSemitones))
    PitchBar(fx.pitchSemitones)
    Slider(
        value = fx.pitchSemitones,
        onValueChange = { onFx(fx.copy(pitchSemitones = it)) },
        valueRange = -12f..12f,
        steps = 95,
        colors = sliderColours()
    )
    Text(
        "×%.3f frequency · tempo unaffected".format(2f.pow(fx.pitchSemitones / 12f)),
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextFaint
    )

    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Reverb", style = MaterialTheme.typography.titleMedium,
            color = Palette.Text, modifier = Modifier.weight(1f))
        Switch(
            checked = fx.reverbEnabled,
            onCheckedChange = { onFx(fx.copy(reverbEnabled = it)) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Base,
                checkedTrackColor = Palette.Accent,
                uncheckedTrackColor = Palette.Hover
            )
        )
    }
    if (fx.reverbEnabled) {
        Spacer(Modifier.height(6.dp))
        Labelled("Amount", "${(fx.reverbMix * 100).roundToInt()}%")
        Slider(
            value = fx.reverbMix,
            onValueChange = { onFx(fx.copy(reverbMix = it)) },
            valueRange = 0f..1f,
            colors = sliderColours()
        )
        Labelled("Room size", "${(fx.reverbDecay * 100).roundToInt()}%")
        Slider(
            value = fx.reverbDecay,
            onValueChange = { onFx(fx.copy(reverbDecay = it)) },
            // Above ~0.95 the comb filters stop decaying and ring indefinitely.
            valueRange = 0.4f..0.94f,
            colors = sliderColours()
        )
    }

    Spacer(Modifier.height(20.dp))
}

@Composable
private fun OutputControls(
    outputs: List<DesktopAudioOutput>,
    selected: List<String>,
    onToggle: (DesktopAudioOutput) -> Unit
) {
    Text(
        "Tick more than one to play through several devices at once.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextDim
    )
    Spacer(Modifier.height(12.dp))

    outputs.forEach { output ->
        val index = selected.indexOf(output.name)
        OutputRow(
            output = output,
            position = index,
            onClick = { onToggle(output) }
        )
    }

    Spacer(Modifier.height(14.dp))
    Text(
        // Worth stating: this is the capability Android could not give properly.
        "One decode feeds every device, so they stay sample-identical. Each " +
            "endpoint still has its own hardware latency, so mixing a Bluetooth " +
            "speaker with a wired one can sound offset.",
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextFaint
    )
}

@Composable
private fun OutputRow(output: DesktopAudioOutput, position: Int, onClick: () -> Unit) {
    val selected = position >= 0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) Palette.Selected else Palette.Hover)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (output.isDefault) Icons.Default.Speaker else Icons.Default.Headphones,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (selected) Palette.Accent else Palette.TextDim
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                output.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) Palette.Text else Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (position == 0) {
                Text("primary", style = MaterialTheme.typography.labelSmall, color = Palette.Accent)
            } else if (position > 0) {
                Text("mirror", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            }
        }
        if (selected) {
            Icon(Icons.Default.Check, null, Modifier.size(15.dp), tint = Palette.Accent)
        }
    }
}

/** Fills outward from centre so "no shift" reads as neutral. */
@Composable
private fun PitchBar(semitones: Float) {
    val fraction = ((semitones + 12f) / 24f).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.Line)
    ) {
        val from = minOf(fraction, 0.5f)
        val to = maxOf(fraction, 0.5f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(start = 0.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(to)
                    .height(4.dp)
                    .background(Color.Transparent)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(if (to > 0f) (to - from) / to else 0f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Palette.Accent)
                )
            }
        }
    }
}

@Composable
private fun Labelled(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = Palette.TextDim, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
    }
}

@Composable
private fun Preset(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Palette.Accent else Palette.Hover)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Palette.Base else Palette.TextDim
        )
    }
}

@Composable
private fun sliderColours() = SliderDefaults.colors(
    thumbColor = Palette.Accent,
    activeTrackColor = Palette.Accent,
    inactiveTrackColor = Palette.Line
)

private fun formatSemitones(value: Float): String {
    val rounded = (value * 10).roundToInt() / 10f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    return if (rounded > 0f) "+$text st" else "$text st"
}
