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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.recognition.MusicMatch
import com.exo.musicplayer.desktop.data.DesktopController
import com.exo.musicplayer.desktop.data.IdentifyMode
import com.exo.musicplayer.desktop.library.DesktopTrack
import java.io.File

/**
 * Identify: fingerprint a file, or search a wide catalogue by name.
 *
 * Seven services answer in parallel and the rows are interleaved, so the list
 * opens with one result per service rather than twenty from whichever replied
 * first. Anything found can be written onto a track in the library, or handed
 * straight to the downloader.
 */
@Composable
fun IdentifyScreen(controller: DesktopController, onChooseMedia: () -> File?) {
    val target = controller.identifyTarget
    val mode = controller.identifyMode

    fun run() {
        when (mode) {
            IdentifyMode.NAME -> controller.searchCatalogues()
            IdentifyMode.LYRICS -> controller.searchByLyrics()
            IdentifyMode.LINK -> controller.identifyLink()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle("Identify a song") {
                SegmentedRow(
                    options = IdentifyMode.entries,
                    selected = mode,
                    label = { it.label },
                    onSelect = { controller.identifyMode = it }
                )
            }
            Spacer(Modifier.height(6.dp))
            Hint(
                when (mode) {
                    IdentifyMode.NAME ->
                        "iTunes, Deezer, MusicBrainz, Audius, the Internet Archive, YouTube " +
                            "and Genius — queried at once, none of them needing a key."
                    IdentifyMode.LYRICS ->
                        "Genius and NetEase index the words themselves, so a half-remembered " +
                            "line is enough. Neither needs an account."
                    IdentifyMode.LINK ->
                        "Pulls the audio down, fingerprints it, and throws the file away. " +
                            "TikTok, Instagram, YouTube and a thousand other sites."
                }
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(
                    value = controller.identifyQuery,
                    onValueChange = { controller.identifyQuery = it },
                    placeholder = mode.placeholder,
                    leading = if (mode == IdentifyMode.LINK) {
                        Icons.Default.Link
                    } else {
                        Icons.Default.Search
                    },
                    modifier = Modifier.weight(1f),
                    onSubmit = { run() }
                )
                Spacer(Modifier.width(10.dp))
                AccentButton(
                    if (mode == IdentifyMode.LINK) "Identify" else "Search",
                    enabled = !controller.identifyBusy && controller.identifyQuery.isNotBlank()
                ) { run() }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(
                    "Identify a file…",
                    enabled = !controller.identifyBusy,
                    icon = Icons.Default.GraphicEq
                ) { onChooseMedia()?.let { controller.identifyMediaFile(it) } }
                Spacer(Modifier.width(8.dp))
                GhostButton(
                    "Identify by sound",
                    enabled = !controller.identifyBusy && target != null,
                    icon = Icons.Default.Fingerprint
                ) { target?.let { controller.identifyFile(it) } }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Audio or video — a clip, a voice note, a screen recording.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint
                )
            }

            if (target != null) {
                Spacer(Modifier.height(12.dp))
                TargetBanner(target) { controller.identifyTarget = null }
            } else {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Right-click a track in the library and choose Identify to fingerprint " +
                        "it — only frequency peaks are sent, never the audio.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint
                )
            }

            controller.identifyStatus?.let { status ->
                Spacer(Modifier.height(12.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
            }
            if (controller.identifyNeedsFfmpeg) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Video and m4a need ffmpeg to decode. It is a one-click install in the " +
                        "Download tab, and everything else here works without it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextDim
                )
            }
            if (controller.identifyBusy) {
                Spacer(Modifier.height(10.dp))
                ThinProgress(null)
            }
        }

        Spacer(Modifier.height(14.dp))

        if (controller.identifyResults.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Fingerprint,
                title = "Nothing found yet",
                body = "Search by name or by a line of lyrics, paste a link, or point it at " +
                    "an audio\nor video file and let the fingerprinter work it out."
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(controller.identifyResults) { match ->
                    MatchRow(
                        match = match,
                        target = target,
                        onApply = { target?.let { controller.applyMatch(it, match) } },
                        onDownload = { controller.downloadMatch(match) },
                        onUseAsQuery = {
                            controller.identifyQuery = match.display
                            controller.identifyMode = IdentifyMode.NAME
                            controller.searchCatalogues(match.display)
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun TargetBanner(track: DesktopTrack, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(Palette.Selected)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track, 30.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Applying results to",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextDim
            )
            Text(
                track.file.name,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClear),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, "Clear", Modifier.size(14.dp), tint = Palette.TextDim)
        }
    }
}

@Composable
private fun MatchRow(
    match: MusicMatch,
    target: DesktopTrack?,
    onApply: () -> Unit,
    onDownload: () -> Unit,
    onUseAsQuery: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) Palette.Hover else Palette.Raised)
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp))
            .hoverable(interaction)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteArtwork(match.artworkUrl, 42.dp)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                match.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    match.artist,
                    match.album,
                    match.releaseYear?.toString()
                ).joinToString(" · ").ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(10.dp))
        ProviderBadge(match)
        Spacer(Modifier.width(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (target != null) {
                AccentButton("Use this") { onApply() }
            } else {
                GhostButton("Search this") { onUseAsQuery() }
            }
            if (match.drmProtected) {
                // Its audio is encrypted and there is no file to fetch. Saying
                // so is better than a button that cannot work.
                Text(
                    "streaming only",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint
                )
            } else {
                GhostButton("Download", icon = Icons.Default.Download) { onDownload() }
            }
        }
    }
}

@Composable
private fun ProviderBadge(match: MusicMatch) {
    val fingerprinted = match.source == MusicMatch.Source.FINGERPRINT
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (fingerprinted) Palette.AccentSoft else Palette.Content)
            .border(
                1.dp,
                if (fingerprinted) Palette.Accent else Palette.Line,
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            if (fingerprinted) "Shazam" else match.provider.ifBlank { "Search" },
            style = MaterialTheme.typography.labelSmall,
            color = if (fingerprinted) Color(0xFFEFE6FF) else Palette.TextDim
        )
    }
}
