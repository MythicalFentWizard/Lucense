package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.exo.musicplayer.desktop.audio.PlaybackStatus
import com.exo.musicplayer.desktop.data.DesktopController
import com.exo.musicplayer.util.asDuration

/**
 * The small window that stays on top of everything: what is playing, how far
 * through, and the buttons - for while working in something else, or with the
 * main window away in the tray.
 *
 * It has no frame of its own: drag it anywhere to move it. Under the mouse it
 * shows a button back to the main window and one to close it. The song can
 * be dragged out of it, by its cover, as its file.
 */
@Composable
fun WindowScope.MiniPlayer(controller: DesktopController, onExpand: () -> Unit, onClose: () -> Unit) {
    val status by controller.engine.status.collectAsState()
    WindowDraggableArea(Modifier.fillMaxSize()) {
        MiniPlayerCard(
            status = status,
            onSeek = { controller.seekFraction(it) },
            onPrevious = { controller.previous() },
            onPlayPause = { controller.togglePlay() },
            onNext = { controller.next() },
            onExpand = onExpand,
            onClose = onClose
        )
    }
}

/** What the mini player shows, apart from the window it is in. */
@Composable
fun MiniPlayerCard(
    status: PlaybackStatus,
    onSeek: (Float) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val track = status.track
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(16.dp)

    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Palette.Base)
            .border(1.dp, Palette.Line, shape)
            .hoverable(interaction)
    ) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                track,
                78.dp,
                corner = 10.dp,
                modifier = Modifier.dragOutFiles { listOfNotNull(track?.file) }
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            track?.title ?: "Nothing playing",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            track?.displayArtist ?: "Start a song in Lucense",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (hovered) {
                        Small(Icons.Default.OpenInFull, "Open Lucense", 14.dp, onExpand)
                        Small(Icons.Default.Close, "Close the mini player", 15.dp, onClose)
                    }
                }
                Spacer(Modifier.height(4.dp))
                LineSlider(
                    value = status.progress,
                    onSet = onSeek,
                    enabled = track != null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        status.positionMs.asDuration() + " / " + status.durationMs.asDuration(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint,
                        modifier = Modifier.weight(1f)
                    )
                    Round(Icons.Default.SkipPrevious, "Previous", 30.dp, Palette.TextDim, onPrevious)
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Palette.Accent)
                            .testTag("mini:play")
                            .clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (status.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (status.playing) "Pause" else "Play",
                            Modifier.size(19.dp),
                            tint = Palette.OnAccent
                        )
                    }
                    Round(Icons.Default.SkipNext, "Next", 30.dp, Palette.TextDim, onNext, Modifier.testTag("mini:next"))
                }
            }
        }
    }
}

@Composable
private fun Round(icon: ImageVector, label: String, size: Dp, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, Modifier.size(size * 0.62f), tint = tint)
    }
}

@Composable
private fun Small(icon: ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, Modifier.size(size), tint = Palette.TextDim)
    }
}
