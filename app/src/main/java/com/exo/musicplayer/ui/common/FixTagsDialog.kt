package com.exo.musicplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.recognition.MusicMatch
import com.exo.musicplayer.data.recognition.RecognitionResult

/**
 * Identifies a track already in the library by listening to its own file, and
 * offers to overwrite its tags with what came back.
 */
@Composable
fun FixTagsDialog(
    track: Track,
    busy: Boolean,
    result: RecognitionResult?,
    onApply: (MusicMatch) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (busy) "Identifying…" else "Identify & fix tags") },
        text = {
            Column {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))

                when {
                    busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Listening to the file…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    result is RecognitionResult.Found -> Column {
                        Text(
                            "Tap a result to apply its artist, album, year and cover art.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        result.matches.take(3).forEach { match ->
                            MatchRow(match) { onApply(match) }
                        }
                    }

                    result is RecognitionResult.NoMatch -> Text(
                        "Shazam didn't recognise this track. You can still rename it " +
                            "by searching in the Identify tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    result is RecognitionResult.Error -> Text(
                        result.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (busy) "Cancel" else "Close") }
        }
    )
}

@Composable
private fun MatchRow(match: MusicMatch, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (match.artworkUrl != null) {
                AsyncImage(
                    model = match.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = match.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(match.artist, match.album, match.releaseYear?.toString())
                    .joinToString(" · ")
                    .ifBlank { "Unknown artist" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
