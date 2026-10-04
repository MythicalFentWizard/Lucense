package com.exo.musicplayer.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.exo.musicplayer.data.discover.DiscoverArtist
import com.exo.musicplayer.data.discover.DiscoverBrowser
import com.exo.musicplayer.data.discover.DiscoverRelease
import com.exo.musicplayer.data.discover.DiscoverTrack
import com.exo.musicplayer.data.discover.DiscoverWords
import com.exo.musicplayer.data.youtube.YouTubeFormat
import com.exo.musicplayer.ui.download.QueuedDownload
import java.util.Locale

// ---- Page furniture -----------------------------------------------------------------

/** The top of every page but the first: Back, what the page is, and a way straight home. */
@Composable
internal fun PageHeader(title: String, subtitle: String?, shared: DiscoverShared) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = shared.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        shared.home?.let { home -> IconButton(onClick = home) { Icon(Icons.Default.Home, "Back to the start") } }
    }
}

@Composable
internal fun SectionTitle(title: String, note: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
internal fun Busy(text: String) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A long look-up with its count; [fraction] is null before the count is known. */
@Composable
internal fun Progress(text: String, fraction: Float?) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        if (fraction == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun Problem(text: String, onRetry: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Try again") }
    }
}

/**
 * Chips that wrap onto as many lines as they need. Without the usual 48dp
 * touch height each, which left more gap than chip between the lines.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipFlow(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { content() }
    }
}

@Composable
internal fun GenreChip(name: String, onClick: () -> Unit) {
    SuggestionChip(onClick = onClick, label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) })
}

/**
 * A cover or an artist's picture, with the first letter of the name behind it
 * for when there is no picture or it hasn't arrived.
 */
@Composable
internal fun Picture(url: String?, name: String, size: Dp, round: Boolean, modifier: Modifier = Modifier) {
    val shape = if (round) CircleShape else RoundedCornerShape(8.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(Locale.ROOT),
            style = if (size >= 72.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ---- Rows ---------------------------------------------------------------------------

/**
 * A genre in the list of them all: tap the name to open it, tap play to hear
 * it - a song from one of the artists it is most of.
 */
@Composable
internal fun GenreRow(genre: String, shared: DiscoverShared) {
    val key = DiscoverBrowser.genreSampleKey(genre)
    val sampling = shared.preview.videoId == key
    val sample = shared.samples[key]

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { shared.openGenre(genre) }
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                genre,
                style = MaterialTheme.typography.bodyLarge,
                color = if (sampling) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val failed = sampling && shared.preview.error != null
            val note = when {
                failed -> "Nothing from this genre could be played"
                sampling && shared.preview.loading -> "Finding a song…"
                sampling && sample != null -> "♪ ${sample.artist} · ${sample.title}"
                else -> null
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = { shared.actions.onSampleGenre(genre) }) {
            when {
                sampling && shared.preview.loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                sampling && shared.preview.playing -> Icon(Icons.Default.Stop, "Stop")
                else -> Icon(Icons.Default.PlayArrow, "Hear $genre")
            }
        }
    }
}

/**
 * An artist: tap the row for their page, tap play to hear them. Hearing one
 * looks up their most played song, so it takes a moment the first time.
 */
@Composable
internal fun ArtistRow(artist: DiscoverArtist, shared: DiscoverShared) {
    val key = DiscoverBrowser.sampleKey(artist)
    val sampling = shared.preview.videoId == key
    val sample = shared.samples[key]

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { shared.openArtist(artist) }
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Picture(artist.pictureUrl ?: sample?.coverUrl, artist.name, 44.dp, round = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                artist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (sampling) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val failed = sampling && shared.preview.error != null
            val note = when {
                failed -> "Nothing of theirs could be played"
                sampling && shared.preview.loading -> "Finding a song…"
                sample != null -> "♪ ${sample.title}"
                artist.fans != null && artist.fans!! > 0 -> DiscoverWords.fans(artist.fans!!)
                else -> null
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = { shared.actions.onSample(artist) }) {
            when {
                sampling && shared.preview.loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                sampling && shared.preview.playing -> Icon(Icons.Default.Stop, "Stop")
                else -> Icon(Icons.Default.PlayArrow, "Hear ${artist.name}")
            }
        }
    }
}

/** Artists side by side, for "similar" and "you might like". */
@Composable
internal fun ArtistStrip(artists: List<DiscoverArtist>, shared: DiscoverShared) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(artists, key = { "${it.deezerId}-${it.name}" }) { artist ->
            Column(
                Modifier
                    .width(92.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { shared.openArtist(artist) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Picture(artist.pictureUrl, artist.name, 84.dp, round = true)
                Spacer(Modifier.height(6.dp))
                Text(
                    artist.name,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ReleaseRow(release: DiscoverRelease, shared: DiscoverShared, showArtist: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { shared.openRelease(release) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Picture(release.coverUrl, release.title, 52.dp, round = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(release.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                DiscoverWords.release(release, showArtist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A song: tap it to hear half a minute, press download to keep it. The menu
 * has the YouTube search, for picking the upload by hand.
 */
@Composable
internal fun SongRow(track: DiscoverTrack, shared: DiscoverShared, showArtist: Boolean, number: Int? = null) {
    val key = DiscoverBrowser.previewKey(track)
    val previewing = shared.preview.videoId == key
    val owned = remember(track.id, shared.library) { shared.library.owns(track) }
    val queued = shared.queued[key]
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = track.previewUrl != null) { shared.actions.onPreview(track) }
            .padding(start = 16.dp, end = 0.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(26.dp)
            )
        }
        Box(Modifier.size(48.dp)) {
            Picture(track.coverUrl, track.title, 48.dp, round = false)
            if (previewing && (shared.preview.loading || shared.preview.playing)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x88000000)),
                    contentAlignment = Alignment.Center
                ) {
                    if (shared.preview.loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Stop, "Stop", Modifier.size(24.dp), tint = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (previewing) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val failed = previewing && shared.preview.error != null
            Text(
                when {
                    failed -> "The preview couldn't be played"
                    previewing && shared.preview.loading -> "Loading the preview…"
                    previewing && shared.preview.playing ->
                        "Preview · ${YouTubeFormat.duration(shared.preview.secondsPlayed)} of 0:30"
                    previewing && shared.preview.ended -> "Preview finished · tap to hear it again"
                    previewing && shared.preview.paused -> "Preview paused · tap to carry on"
                    else -> listOfNotNull(
                        track.artist.takeIf { showArtist && it.isNotBlank() },
                        (track.durationMs / 1000).toInt().takeIf { it > 0 }?.let(YouTubeFormat::duration),
                        when {
                            owned -> "in your library"
                            queued == QueuedDownload.WAITING -> "waiting to download"
                            queued == QueuedDownload.DOWNLOADING -> "downloading"
                            queued == QueuedDownload.DONE -> "downloaded"
                            queued == QueuedDownload.ALREADY_SAVED -> "already in your library"
                            queued == QueuedDownload.FAILED -> "couldn't be downloaded"
                            else -> null
                        }
                    ).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (failed || queued == QueuedDownload.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        when {
            owned || queued == QueuedDownload.DONE || queued == QueuedDownload.ALREADY_SAVED ->
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CheckCircle, "In your library", tint = MaterialTheme.colorScheme.primary)
                }
            queued == QueuedDownload.WAITING ->
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Schedule, "Waiting to download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            queued == QueuedDownload.DOWNLOADING ->
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    val percent = shared.downloadPercent
                    if (percent > 0f) {
                        CircularProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    }
                }
            queued == QueuedDownload.FAILED ->
                IconButton(onClick = { shared.actions.onDownload(track) }) {
                    Icon(Icons.Default.ErrorOutline, "Try the download again", tint = MaterialTheme.colorScheme.error)
                }
            else ->
                IconButton(onClick = { shared.actions.onDownload(track) }) {
                    Icon(Icons.Default.Download, "Download")
                }
        }

        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Search on YouTube") },
                    onClick = {
                        menu = false
                        shared.actions.onYouTube("${track.artist} ${track.title}".trim())
                    }
                )
                if (track.artist.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Go to ${track.artist}") },
                        onClick = {
                            menu = false
                            shared.openArtist(DiscoverArtist(track.artist, deezerId = track.artistId))
                        }
                    )
                }
                if (owned || queued == QueuedDownload.ALREADY_SAVED) {
                    // "Owned" is judged by name, and a live take or a remix can share one.
                    DropdownMenuItem(
                        text = { Text("Download anyway") },
                        onClick = {
                            menu = false
                            shared.actions.onDownload(track)
                        }
                    )
                }
            }
        }
    }
}

// ---- Words --------------------------------------------------------------------------

internal fun grouped(number: Int): String = DiscoverWords.grouped(number)

internal fun kindLabel(kind: String): String = DiscoverWords.kind(kind)

internal fun releaseDate(date: String): String? = DiscoverWords.date(date)
