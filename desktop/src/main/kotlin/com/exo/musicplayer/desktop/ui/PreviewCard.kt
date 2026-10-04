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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.desktop.data.DesktopController

/**
 * The small player for whatever is being previewed: a YouTube result from
 * Identify, a song's clip from Discover, or the song an artist or a genre is
 * heard by.
 *
 * It sits in the corner of every page, because a preview used to be stoppable
 * only from the row that started it, and that row is gone the moment you
 * scroll or change page. It says what is playing, pauses it, and the X in its
 * corner closes it. Nothing is drawn when nothing is being previewed.
 */
@Composable
fun PreviewCard(controller: DesktopController, modifier: Modifier = Modifier) {
    val state by controller.preview.state.collectAsState()
    if (!state.active) return
    val label = state.label
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier
            .width(330.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Palette.Raised)
            .border(1.dp, Palette.AccentSoft, shape)
    ) {
        Row(
            // Room on the right for the X, so a long title never runs under it.
            Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 30.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(7.dp)).background(Palette.Hover),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Headphones, null, Modifier.size(20.dp), tint = Palette.TextFaint)
                label?.artworkUrl?.let { RemoteImage(it, Modifier.fillMaxSize(), corner = 0.dp, placeholder = false) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.Accent
                )
                Text(
                    label?.title ?: "Preview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            when {
                state.loading -> Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Palette.Accent, strokeWidth = 2.dp)
                }
                state.error != null -> Unit
                else -> {
                    val interaction = remember { MutableInteractionSource() }
                    val hovered by interaction.collectIsHoveredAsState()
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (hovered) Palette.Accent else Palette.Button)
                            .hoverable(interaction)
                            .clickable { controller.preview.pauseOrResume() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when {
                                state.ended -> Icons.Default.Replay
                                state.paused -> Icons.Default.PlayArrow
                                else -> Icons.Default.Pause
                            },
                            when {
                                state.ended -> "Play it again"
                                state.paused -> "Carry on"
                                else -> "Pause the preview"
                            },
                            Modifier.size(18.dp),
                            tint = if (hovered) Palette.OnAccent else Palette.OnButton
                        )
                    }
                }
            }
        }

        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (hovered) Palette.Line else Color.Transparent)
                .hoverable(interaction)
                .clickable { controller.stopPreview() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, "Close the preview", Modifier.size(13.dp), tint = if (hovered) Palette.Text else Palette.TextDim)
        }
    }
}
