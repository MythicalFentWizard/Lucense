package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import com.exo.musicplayer.data.weather.Affinity
import com.exo.musicplayer.data.weather.WeatherAffinity
import com.exo.musicplayer.data.weather.WeatherCondition
import com.exo.musicplayer.desktop.data.DesktopController
import com.exo.musicplayer.desktop.library.DesktopTrack
import kotlin.math.roundToInt

/**
 * Moods: the songs you actually reach for in a given kind of weather.
 *
 * Not "most played while it rained" — that just returns your most-played songs
 * full stop. Each track's rate in one condition is compared against its own
 * baseline, so what surfaces is disproportionate preference. The scorer is the
 * one from :shared, the same one the phone uses.
 */
@Composable
fun MoodsScreen(controller: DesktopController) {
    LaunchedEffect(Unit) {
        if (controller.weather == null) controller.refreshWeather()
    }

    val condition = controller.moodCondition
    val enoughHistory = controller.moodHistorySize >= WeatherAffinity.MIN_HISTORY

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val snapshot = controller.weather
                    if (snapshot != null) {
                        Text(
                            "${snapshot.condition.emoji}  ${snapshot.condition.label}" +
                                if (snapshot.temperatureC.isNaN()) {
                                    ""
                                } else {
                                    " · ${snapshot.temperatureC.roundToInt()}°C"
                                },
                            style = MaterialTheme.typography.headlineMedium,
                            color = Palette.Text
                        )
                        Spacer(Modifier.height(2.dp))
                        Hint(
                            controller.weatherPlace.ifBlank { "Located from your IP address" } +
                                " · Open-Meteo"
                        )
                    } else {
                        Text(
                            "Weather unknown",
                            style = MaterialTheme.typography.titleLarge,
                            color = Palette.Text
                        )
                        Spacer(Modifier.height(2.dp))
                        Hint(
                            controller.weatherNote
                                ?: "Fetching current conditions…"
                        )
                    }
                }
                GhostButton("Refresh", icon = Icons.Default.Refresh) {
                    controller.refreshWeather(force = true)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                WeatherCondition.entries.forEach { entry ->
                    ConditionChip(
                        condition = entry,
                        selected = entry == condition,
                        isNow = entry == controller.weather?.condition
                    ) { controller.loadMood(entry) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        when {
            !enoughHistory -> EmptyState(
                icon = Icons.Default.Cloud,
                title = "Still listening",
                body = "Moods needs at least ${WeatherAffinity.MIN_HISTORY} plays recorded " +
                    "alongside the weather before it can tell a preference from a\n" +
                    "coincidence. ${controller.moodHistorySize} so far — keep playing."
            )

            controller.moodTracks.isEmpty() -> EmptyState(
                icon = Icons.Default.Cloud,
                title = "No pattern yet for ${condition?.label?.lowercase() ?: "this"}",
                body = "Nothing in your library is played disproportionately in this weather.\n" +
                    "A song needs at least ${WeatherAffinity.MIN_PLAYS_IN_CONDITION} plays " +
                    "in the condition before it counts."
            )

            else -> {
                SectionTitle(
                    "${controller.moodTracks.size} tracks lean towards " +
                        (condition?.label?.lowercase() ?: "this weather")
                ) {
                    AccentButton("Play these", icon = Icons.Default.PlayArrow) {
                        val queue = controller.moodTracks.map { it.first }
                        queue.firstOrNull()?.let { controller.play(it, queue) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Hint(
                    "Lift is how much more often you play it in this weather than your " +
                        "own listening would predict. 1.0× would be pure chance."
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(
                        controller.moodTracks,
                        key = { _, item -> item.first.file.absolutePath }
                    ) { index, (track, affinity) ->
                        MoodRow(index + 1, track, affinity) {
                            controller.play(track, controller.moodTracks.map { it.first })
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ConditionChip(
    condition: WeatherCondition,
    selected: Boolean,
    isNow: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    selected -> Palette.Accent
                    hovered -> Palette.Hover
                    else -> Palette.Content
                }
            )
            .border(
                1.dp,
                if (isNow && !selected) Palette.Accent else Palette.Line,
                RoundedCornerShape(20.dp)
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(condition.emoji, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(7.dp))
        Text(
            condition.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Palette.OnAccent else Palette.TextDim
        )
        if (isNow) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (selected) Palette.OnAccent else Palette.Accent)
            )
        }
    }
}

@Composable
private fun MoodRow(
    position: Int,
    track: DesktopTrack,
    affinity: Affinity,
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
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(28.dp)) {
            if (hovered) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(15.dp), tint = Palette.Text)
            } else {
                Text(
                    position.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint
                )
            }
        }
        Artwork(track, 36.dp)
        Spacer(Modifier.width(12.dp))
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
        // A bar makes the ranking legible at a glance; the number keeps it honest.
        LiftBar(affinity.lift)
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
            Text(
                "%.2f×".format(affinity.lift),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Accent
            )
            Text(
                "${affinity.playsInCondition} of ${affinity.totalPlays} plays",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextFaint
            )
        }
    }
}

@Composable
private fun LiftBar(lift: Double) {
    // 3× and above all fill the bar; beyond that the differences stop meaning much.
    val fraction = ((lift - 1.0) / 2.0).coerceIn(0.0, 1.0).toFloat()
    Box(
        Modifier
            .width(90.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.Line)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Palette.Accent)
        )
    }
}
