package com.exo.musicplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.playback.PlaybackState
import com.exo.musicplayer.ui.common.Artwork

@Composable
fun MiniPlayer(
    track: Track?,
    state: PlaybackState,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = track != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExpand)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(track = track, size = 44.dp, cornerRadius = 6.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track?.title.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track?.artist ?: "Unknown artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            imageVector = if (state.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = onNext, enabled = state.hasNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next")
                    }
                }
            }
        }
    }
}
