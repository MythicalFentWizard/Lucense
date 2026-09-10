package com.exo.musicplayer.ui.recognition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.exo.musicplayer.data.youtube.PreviewState
import com.exo.musicplayer.data.youtube.YouTubeFormat
import com.exo.musicplayer.data.youtube.YouTubeVideo

/**
 * YouTube search results, laid out the way YouTube lays them out.
 *
 * Not the compact card the catalogue results use. A YouTube hit is chosen by
 * looking at it: the thumbnail says whether this is the official upload or a
 * slowed edit, the view count says whether it is the one everybody means, the
 * duration says whether it is the song or a two-hour mix. Reducing that to a
 * line of text throws away the information the choice actually rests on.
 *
 * Tapping the thumbnail previews the audio; the buttons underneath spell the
 * same thing out, because a tappable image with no label is a guess.
 */
@Composable
fun YouTubeResultList(
    videos: List<YouTubeVideo>,
    preview: PreviewState,
    onPreview: (YouTubeVideo) -> Unit,
    onDownload: (YouTubeVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize()) {
        items(videos, key = { it.id }) { video ->
            YouTubeRow(
                video = video,
                preview = preview,
                onPreview = { onPreview(video) },
                onDownload = { onDownload(video) }
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun YouTubeRow(
    video: YouTubeVideo,
    preview: PreviewState,
    onPreview: () -> Unit,
    onDownload: () -> Unit
) {
    val isPreviewing = preview.videoId == video.id
    val isLoading = isPreviewing && preview.loading

    Surface(
        color = if (isPreviewing) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row {
                Box(
                    Modifier
                        .width(150.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = onPreview)
                ) {
                    AsyncImage(
                        // The small rendition on purpose: at 150dp the large
                        // one is four times the bytes for no visible gain, and
                        // a result list loads twenty of them at once.
                        model = video.thumbnail(YouTubeVideo.ThumbSize.SMALL),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isLoading) {
                        Box(
                            Modifier.fillMaxSize().background(Color(0xAA000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        }
                    } else if (isPreviewing) {
                        Box(
                            Modifier.fillMaxSize().background(Color(0x55000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                null,
                                Modifier.size(30.dp),
                                tint = Color.White
                            )
                        }
                    }

                    // Duration, or how far the preview has got: while it plays
                    // that is the more useful number, and it is the same corner
                    // people already look at for length.
                    video.durationSeconds?.let { seconds ->
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xCC000000))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                if (isPreviewing && preview.playing) {
                                    YouTubeFormat.duration(preview.secondsPlayed) + " / " +
                                        YouTubeFormat.duration(seconds)
                                } else {
                                    YouTubeFormat.duration(seconds)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        video.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        video.subtitle.ifBlank { "view count not reported" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        video.channelAvatarUrl?.let { avatar ->
                            AsyncImage(
                                model = avatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(16.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            video.channel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (video.channelVerified) {
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                Icons.Default.CheckCircle,
                                "Verified",
                                Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            video.description?.let { text ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            preview.error?.takeIf { isPreviewing }?.let { message ->
                Spacer(Modifier.height(6.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPreview) {
                    Icon(
                        if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null,
                        Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(if (isPreviewing) "Stop" else "Preview")
                }
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download mp3")
                }
            }
        }
    }
}
