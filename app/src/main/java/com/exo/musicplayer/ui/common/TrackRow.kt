package com.exo.musicplayer.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.util.asDuration

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFixTags: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** Playlist views remove the entry rather than the file, so the wording differs. */
    deleteLabel: String = "Delete from library"
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track = track, size = 52.dp)
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
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
                text = remember(track.artist, track.durationMs) {
                    buildString {
                        append(track.artist ?: "Unknown artist")
                        if (track.durationMs > 0) {
                            append("  ·  ")
                            append(track.durationMs.asDuration())
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isCurrent && isPlaying) {
            Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = "Now playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (track.isFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favourite",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp)
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    leadingIcon = { Icon(Icons.Default.QueueMusic, null) },
                    onClick = { menuOpen = false; onPlayNext() }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.Default.QueueMusic, null) },
                    onClick = { menuOpen = false; onAddToQueue() }
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) },
                    onClick = { menuOpen = false; onAddToPlaylist() }
                )
                DropdownMenuItem(
                    text = { Text(if (track.isFavorite) "Remove favourite" else "Favourite") },
                    leadingIcon = {
                        Icon(
                            if (track.isFavorite) Icons.Default.Favorite
                            else Icons.Default.FavoriteBorder,
                            null
                        )
                    },
                    onClick = { menuOpen = false; onToggleFavorite() }
                )
                DropdownMenuItem(
                    text = { Text("Identify & fix tags") },
                    leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) },
                    onClick = { menuOpen = false; onFixTags() }
                )
                DropdownMenuItem(
                    text = { Text(deleteLabel) },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}
