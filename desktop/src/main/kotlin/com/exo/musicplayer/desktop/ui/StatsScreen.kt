package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.weather.WeatherCondition
import com.exo.musicplayer.desktop.data.DesktopController
import com.exo.musicplayer.desktop.data.ListenRow
import com.exo.musicplayer.desktop.library.DesktopTrack
import java.util.Locale

/**
 * Listening statistics.
 *
 * Ranked by time listened rather than play count, which is the honest measure:
 * thirty seconds of a track you skipped is not a play in any sense that matters,
 * and the store only counts one past that threshold anyway.
 */
@Composable
fun StatsScreen(controller: DesktopController) {
    LaunchedEffect(controller.tracks) { controller.refreshStats() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Time listened", humanDuration(controller.statsTotalMs), Modifier.weight(1f))
            StatCard("Plays", controller.statsPlays.toString(), Modifier.weight(1f))
            StatCard("Tracks played", controller.statsTracks.toString(), Modifier.weight(1f))
            StatCard(
                "In library",
                controller.tracks.size.toString(),
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        if (controller.statsPlays == 0) {
            EmptyState(
                icon = Icons.Default.BarChart,
                title = "Nothing recorded yet",
                body = "Play something for half a minute and it starts counting.\n" +
                    "Anything shorter is a skip, not a listen."
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f)) {
                SectionTitle("When you listen")
                Spacer(Modifier.height(14.dp))
                HourChart(controller.statsByHour)
            }
            Panel(Modifier.width(300.dp)) {
                SectionTitle("Weather while playing")
                Spacer(Modifier.height(12.dp))
                WeatherBreakdown(controller.statsByWeather)
            }
        }

        Spacer(Modifier.height(16.dp))

        Panel(Modifier.fillMaxWidth()) {
            SectionTitle("Most listened")
            Spacer(Modifier.height(10.dp))
            val top = controller.statsTop
            if (top.isEmpty()) {
                Hint("Nothing yet.")
            } else {
                val longest = top.first().second.totalMs.coerceAtLeast(1L)
                top.forEachIndexed { index, (track, row) ->
                    TopRow(index + 1, track, row, longest) {
                        controller.play(track, top.map { it.first })
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
    }
}

/** Twenty-four columns; the shape matters more than the exact values. */
@Composable
private fun HourChart(byHour: Map<Int, Int>) {
    val peak = (byHour.values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(110.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (hour in 0..23) {
            val plays = byHour[hour] ?: 0
            val fraction = plays.toFloat() / peak
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((6 + fraction * 78).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (plays > 0) Palette.Accent else Palette.Line)
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (hour % 6 == 0) hour.toString().padStart(2, '0') else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint
                )
            }
        }
    }
}

@Composable
private fun WeatherBreakdown(byWeather: Map<String, Int>) {
    if (byWeather.isEmpty()) {
        Hint("No weather recorded yet. Moods needs this too.")
        return
    }
    val total = byWeather.values.sum().coerceAtLeast(1)
    byWeather.entries
        .sortedByDescending { it.value }
        .forEach { (name, plays) ->
            val condition = WeatherCondition.fromName(name)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(condition?.emoji ?: "•", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(9.dp))
                Text(
                    condition?.label ?: name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    modifier = Modifier.width(70.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Palette.Line)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(plays.toFloat() / total)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Palette.Accent)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${plays * 100 / total}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint,
                    modifier = Modifier.width(30.dp)
                )
            }
        }
}

@Composable
private fun TopRow(
    position: Int,
    track: DesktopTrack,
    row: ListenRow,
    longest: Long,
    onPlay: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered) Palette.Hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(26.dp)) {
            if (hovered) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp), tint = Palette.Text)
            } else {
                Text(
                    position.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (position <= 3) Palette.Accent else Palette.TextFaint,
                    fontWeight = if (position <= 3) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
        Artwork(track, 34.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(120.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Palette.Line)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(row.totalMs.toFloat() / longest)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Palette.Accent)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(92.dp)) {
            Text(
                humanDuration(row.totalMs),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Accent
            )
            Text(
                "${row.plays} play${if (row.plays == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextFaint
            )
        }
    }
}

/** "3h 12m" rather than "3:12:45" — a total, not a position. */
private fun humanDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val minutes = ms / 60_000
    val hours = minutes / 60
    return when {
        hours >= 24 -> String.format(Locale.US, "%dd %dh", hours / 24, hours % 24)
        hours > 0 -> String.format(Locale.US, "%dh %dm", hours, minutes % 60)
        else -> "${minutes}m"
    }
}
