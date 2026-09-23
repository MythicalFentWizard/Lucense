package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.desktop.data.DesktopController
import com.exo.musicplayer.util.asDuration

/**
 * The small always-on-top window.
 *
 * Deliberately only what is wanted while working in something else: what is
 * playing, how far through it is, and the three buttons. Everything else stays
 * in the main window, which is still open behind it.
 */
@Composable
fun MiniPlayer(controller: DesktopController) {
    val status by controller.engine.status.collectAsState()
    val track = status.track

    Row(
        Modifier.fillMaxSize().background(Palette.Base).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track, 62.dp, corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track?.title ?: "Nothing playing",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track?.displayArtist ?: "Start a song in the main window",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(9.dp))
            ThinProgress(status.progress, Modifier.fillMaxWidth())
            Spacer(Modifier.height(5.dp))
            Text(
                status.positionMs.asDuration() + " / " + status.durationMs.asDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextFaint
            )
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = { controller.previous() }) {
            Icon(
                Icons.Default.SkipPrevious, "Previous",
                tint = Palette.TextDim, modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = { controller.togglePlay() }) {
            Icon(
                if (status.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                "Play or pause",
                tint = Palette.Accent,
                modifier = Modifier.size(26.dp)
            )
        }
        IconButton(onClick = { controller.next() }) {
            Icon(
                Icons.Default.SkipNext, "Next",
                tint = Palette.TextDim, modifier = Modifier.size(20.dp)
            )
        }
    }
}
