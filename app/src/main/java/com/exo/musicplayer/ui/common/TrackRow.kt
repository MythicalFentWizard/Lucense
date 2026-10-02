package com.exo.musicplayer.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.util.asDuration

/**
 * What can be done to one song from its menu, wherever the song is listed.
 *
 * Provided once at the top of the app rather than threaded through every
 * screen: a list of songs on the Moods tab offers exactly what one in the
 * library does, and a screen that grows a new list doesn't have to grow nine
 * new parameters to go with it.
 */
class TrackActions(
    val playNext: (Track) -> Unit = {},
    val addToQueue: (Track) -> Unit = {},
    val addToPlaylist: (Track) -> Unit = {},
    val toggleFavorite: (Track) -> Unit = {},
    val rate: (Track) -> Unit = {},
    /** Tags, genres and lyrics for this one song, as Fix does for a selection. */
    val fix: (Track) -> Unit = {},
    /** Listen to it and choose from what comes back. */
    val identify: (Track) -> Unit = {},
    val edit: (Track) -> Unit = {},
    val revert: (Track) -> Unit = {},
    val share: (Track) -> Unit = {},
    val delete: (Track) -> Unit = {}
)

val LocalTrackActions = staticCompositionLocalOf { TrackActions() }

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Ticked, and tinted, while this row is part of a selection. */
    isSelected: Boolean = false,
    /** Long press, for screens that can select several songs. */
    onLongPress: (() -> Unit)? = null,
    /** Taking it out of a list, offered first - in a playlist that's the usual intent. */
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove from playlist",
    /** Leading position number, for album and playlist views. */
    number: Int? = null,
    artSize: Dp = 52.dp
) {
    val actions = LocalTrackActions.current
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongPress == null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
                }
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
            )
            .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(26.dp)
            )
        }
        Box {
            Artwork(track = track, size = artSize)
            if (isSelected) {
                // Over the artwork rather than beside it, so entering
                // selection mode does not shift every row sideways.
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = remember(track.artist, track.durationMs) {
                        buildString {
                            append(track.artist?.takeIf { it.isNotBlank() } ?: "Unknown artist")
                            if (track.durationMs > 0) {
                                append("  ·  ")
                                append(track.durationMs.asDuration())
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.rating > 0) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = track.rating.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isCurrent && isPlaying) {
            Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = "Now playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(18.dp)
            )
        }
        if (track.isFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favourite",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(16.dp)
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More for ${track.title}")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onRemove != null) {
                    MenuItem(removeLabel, Icons.Default.PlaylistRemove) { menuOpen = false; onRemove() }
                    HorizontalDivider()
                }
                MenuItem("Play next", Icons.Default.QueuePlayNext) { menuOpen = false; actions.playNext(track) }
                MenuItem("Add to queue", Icons.AutoMirrored.Filled.QueueMusic) { menuOpen = false; actions.addToQueue(track) }
                MenuItem("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) { menuOpen = false; actions.addToPlaylist(track) }
                MenuItem(
                    if (track.isFavorite) "Remove favourite" else "Favourite",
                    if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder
                ) { menuOpen = false; actions.toggleFavorite(track) }
                MenuItem(
                    if (track.rating > 0) "Rated ${track.rating} of 5" else "Rate",
                    if (track.rating > 0) Icons.Default.Star else Icons.Default.StarBorder
                ) { menuOpen = false; actions.rate(track) }
                HorizontalDivider()
                MenuItem("Fix…", Icons.Default.AutoFixHigh) { menuOpen = false; actions.fix(track) }
                MenuItem("Identify by listening…", Icons.Default.Fingerprint) { menuOpen = false; actions.identify(track) }
                MenuItem("Edit details…", Icons.Default.Edit) { menuOpen = false; actions.edit(track) }
                MenuItem("Revert to the file's own details", Icons.Default.Undo) { menuOpen = false; actions.revert(track) }
                HorizontalDivider()
                MenuItem("Share", Icons.Default.Share) { menuOpen = false; actions.share(track) }
                MenuItem("Delete from library…", Icons.Default.Delete) { menuOpen = false; actions.delete(track) }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        onClick = onClick
    )
}
