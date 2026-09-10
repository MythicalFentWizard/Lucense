package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.download.DownloadQuality
import com.exo.musicplayer.desktop.data.DesktopController
import com.exo.musicplayer.desktop.data.DownloadEntry
import com.exo.musicplayer.desktop.download.YtDlp
import java.io.File

/**
 * The download window.
 *
 * Paste a YouTube, SoundCloud, Bandcamp or Spotify link and it lands in the
 * music folder. Spotify is routed through spotdl and called out explicitly
 * rather than quietly redirected: its audio is DRM-protected and cannot be
 * downloaded, so what arrives is the same recording fetched from YouTube, which
 * is usually — but not always — the version you expected.
 */
@Composable
fun DownloadScreen(controller: DesktopController, onChooseFolder: () -> File?) {
    LaunchedEffect(Unit) { controller.refreshTools() }
    var showLog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        ToolsPanel(controller)

        Spacer(Modifier.height(14.dp))

        Panel(Modifier.fillMaxWidth()) {
            SectionTitle("Add a link")
            Spacer(Modifier.height(4.dp))
            Hint(
                "YouTube, SoundCloud and Bandcamp download directly. Spotify links go " +
                    "through spotdl, which reads the track, album and artwork from " +
                    "Spotify and then fetches the same recording from YouTube — " +
                    "Spotify's own audio is DRM protected and no tool can download it."
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(
                    value = controller.downloadUrl,
                    onValueChange = { controller.downloadUrl = it },
                    placeholder = "https://…",
                    leading = Icons.Default.Link,
                    modifier = Modifier.weight(1f),
                    onSubmit = { controller.startDownload() }
                )
                Spacer(Modifier.width(10.dp))
                AccentButton(
                    "Download",
                    enabled = controller.tools.ready && controller.downloadUrl.isNotBlank(),
                    icon = Icons.Default.Download
                ) { controller.startDownload() }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f)) {
                    CheckRow(
                        label = "Convert to MP3",
                        checked = controller.downloadToMp3,
                        note = "Off keeps whatever the site served — usually Opus, " +
                            "which the player can't decode"
                    ) { controller.downloadToMp3 = it }
                    CheckRow(
                        label = "Embed cover art",
                        checked = controller.downloadEmbedArt
                    ) { controller.downloadEmbedArt = it }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Quality",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Text
                    )
                    Spacer(Modifier.height(6.dp))
                    SegmentedRow(
                        options = DownloadQuality.entries,
                        selected = controller.downloadQuality,
                        label = { it.label },
                        onSelect = { controller.downloadQuality = it }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        controller.downloadQuality.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint
                    )
                    Spacer(Modifier.height(12.dp))
                    CheckRow(
                        label = "Download the whole playlist",
                        checked = controller.downloadPlaylist,
                        note = "Off means just the one track a playlist link points at"
                    ) { controller.downloadPlaylist = it }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FolderOpen, null, Modifier.size(14.dp), tint = Palette.TextFaint
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    controller.downloadDir,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                GhostButton("Change") {
                    onChooseFolder()?.let { controller.chooseDownloadDir(it) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (controller.downloads.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Download,
                title = "Nothing downloaded yet",
                body = "Paste a link above. Files land in the folder shown, and if that\n" +
                    "folder is part of your library they appear there automatically."
            )
        } else {
            SectionTitle("Queue") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GhostButton(if (showLog) "Hide log" else "Show log") { showLog = !showLog }
                    Spacer(Modifier.width(8.dp))
                    GhostButton("Clear finished") { controller.clearFinishedDownloads() }
                }
            }
            Spacer(Modifier.height(10.dp))
            controller.downloads.reversed().forEach { entry ->
                DownloadRow(entry)
                Spacer(Modifier.height(8.dp))
            }
        }

        if (showLog && controller.downloadLog.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LogPanel(controller.downloadLog)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToolsPanel(controller: DesktopController) {
    val tools = controller.tools
    Panel(Modifier.fillMaxWidth()) {
        SectionTitle("Downloaders") {
            GhostButton("Update yt-dlp") { controller.updateYtDlp() }
        }
        Spacer(Modifier.height(4.dp))
        Hint(
            "All three ship with Resonate — nothing to install. yt-dlp is the only " +
                "one worth updating by hand: extractors break whenever a site changes " +
                "its player, usually faster than Resonate ships."
        )
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolChip("yt-dlp", tools.ytDlp, tools.ytDlpVersion ?: "YouTube, SoundCloud, 1000+")
            ToolChip("ffmpeg", tools.ffmpeg, "MP3 conversion, video audio")
            ToolChip("spotdl", tools.spotdl, "Spotify links")
        }

        if (!tools.ytDlp || !tools.ffmpeg || !tools.spotdl) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Something is missing from this installation — reinstalling will " +
                    "restore it.",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextDim
            )
        }

        if (controller.toolProgress != null || controller.toolNote != null) {
            Spacer(Modifier.height(12.dp))
            controller.toolNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
                Spacer(Modifier.height(6.dp))
            }
            if (controller.toolProgress != null) ThinProgress(controller.toolProgress)
        }
    }
}

@Composable
private fun ToolChip(name: String, installed: Boolean, note: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Palette.Content)
            .border(1.dp, if (installed) Palette.AccentSoft else Palette.Line,
                RoundedCornerShape(7.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (installed) Palette.Accent else Palette.TextFaint)
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(name, style = MaterialTheme.typography.bodySmall, color = Palette.Text)
            Text(note, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        }
    }
}

@Composable
private fun DownloadRow(entry: DownloadEntry) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Raised)
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = when {
                entry.failed -> Icons.Default.ErrorOutline
                entry.done -> Icons.Default.CheckCircle
                else -> Icons.Default.Download
            }
            Icon(
                icon,
                null,
                Modifier.size(15.dp),
                tint = when {
                    entry.failed -> Palette.TextDim
                    entry.done -> Palette.Accent
                    else -> Palette.TextDim
                }
            )
            Spacer(Modifier.width(10.dp))
            Text(
                entry.display,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (!entry.done && entry.percent > 0f) {
                    "${(entry.percent * 100).toInt()}%"
                } else {
                    entry.status
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.failed) Palette.TextDim else Palette.Accent
            )
        }

        if (!entry.done) {
            Spacer(Modifier.height(9.dp))
            ThinProgress(entry.percent.takeIf { it > 0f })
        }

        entry.note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        }

        if (entry.done && entry.files.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            entry.files.forEach { file ->
                Text(
                    file.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (entry.failed) {
            Spacer(Modifier.height(6.dp))
            Text(entry.status, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
        }
    }
}

/** Raw yt-dlp output, for when a download fails and the reason matters. */
@Composable
private fun LogPanel(lines: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Base)
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(lines) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Palette.TextDim,
                    maxLines = 1,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}
