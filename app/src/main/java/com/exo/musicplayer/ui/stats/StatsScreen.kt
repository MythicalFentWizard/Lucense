package com.exo.musicplayer.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.db.HourBucket
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.db.TrackListenTime
import com.exo.musicplayer.data.db.WeatherBucket
import com.exo.musicplayer.data.weather.WeatherCondition
import com.exo.musicplayer.ui.common.Artwork

@Composable
fun StatsScreen(
    totalListenedMs: Long,
    totalPlays: Int,
    distinctTracks: Int,
    topTracks: List<Pair<Track, TrackListenTime>>,
    topThisWeek: List<Pair<Track, TrackListenTime>>,
    favorites: List<Track>,
    byHour: List<HourBucket>,
    byWeather: List<WeatherBucket>,
    currentTrackId: Long?,
    onPlayTrack: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalPlays == 0 && favorites.isEmpty()) {
        Box(
            modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Play some music and your listening history will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatTile("Listening time", humanDuration(totalListenedMs), Modifier.weight(1f))
                StatTile("Plays", totalPlays.toString(), Modifier.weight(1f))
                StatTile("Songs", distinctTracks.toString(), Modifier.weight(1f))
            }
        }

        if (topThisWeek.isNotEmpty()) {
            item { SectionHeader("This week") }
            itemsIndexed(topThisWeek, key = { _, p -> "week-${p.first.id}" }) { index, (track, row) ->
                RankedRow(
                    rank = index + 1,
                    track = track,
                    detail = "${humanDuration(row.totalMs)} · ${row.plays} plays",
                    isCurrent = track.id == currentTrackId,
                    onClick = { onPlayTrack(topThisWeek.map { it.first }, index) }
                )
            }
        }

        if (byHour.isNotEmpty()) {
            item { SectionHeader("When you listen") }
            item { HourChart(byHour) }
        }

        if (byWeather.isNotEmpty()) {
            item { SectionHeader("Weather while listening") }
            item { WeatherChart(byWeather) }
        }

        if (topTracks.isNotEmpty()) {
            item { SectionHeader("Most played of all time") }
            itemsIndexed(topTracks, key = { _, p -> "all-${p.first.id}" }) { index, (track, row) ->
                RankedRow(
                    rank = index + 1,
                    track = track,
                    detail = "${humanDuration(row.totalMs)} · ${row.plays} plays",
                    isCurrent = track.id == currentTrackId,
                    onClick = { onPlayTrack(topTracks.map { it.first }, index) }
                )
            }
        }

        if (favorites.isNotEmpty()) {
            item { SectionHeader("Favourites") }
            itemsIndexed(favorites, key = { _, t -> "fav-${t.id}" }) { index, track ->
                RankedRow(
                    rank = null,
                    track = track,
                    detail = track.artist ?: "Unknown artist",
                    isCurrent = track.id == currentTrackId,
                    onClick = { onPlayTrack(favorites, index) },
                    trailingFavorite = true
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun RankedRow(
    rank: Int?,
    track: Track,
    detail: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    trailingFavorite: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )
        }
        Artwork(track = track, size = 44.dp, cornerRadius = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailingFavorite) {
            androidx.compose.material3.Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 24 bars, one per hour. Drawn with Boxes — a chart library isn't worth 2 MB. */
@Composable
private fun HourChart(buckets: List<HourBucket>) {
    val byHour = buckets.associate { it.hourOfDay to it.plays }
    val max = (byHour.values.maxOrNull() ?: 1).coerceAtLeast(1)

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(90.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (hour in 0..23) {
                val plays = byHour[hour] ?: 0
                val fraction = plays.toFloat() / max
                Box(
                    Modifier
                        .weight(1f)
                        .height((6 + fraction * 78).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (plays > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "23").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeatherChart(buckets: List<WeatherBucket>) {
    val total = buckets.sumOf { it.plays }.coerceAtLeast(1)
    Column(Modifier.padding(horizontal = 16.dp)) {
        buckets.forEach { bucket ->
            val condition = WeatherCondition.fromName(bucket.weatherGroup)
            val fraction = bucket.plays.toFloat() / total
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = condition?.emoji ?: "•",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(28.dp)
                )
                Text(
                    text = condition?.label ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(72.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = bucket.plays.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp)
                )
            }
        }
    }
}

private fun humanDuration(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    return when {
        hours >= 24 -> "${hours / 24}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}
