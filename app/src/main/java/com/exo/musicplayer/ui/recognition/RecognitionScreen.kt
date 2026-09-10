package com.exo.musicplayer.ui.recognition

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.exo.musicplayer.data.youtube.PreviewState
import com.exo.musicplayer.data.youtube.YouTubeVideo
import com.exo.musicplayer.data.recognition.MusicMatch
import com.exo.musicplayer.data.recognition.RecognitionResult

@Composable
fun RecognitionScreen(
    stage: RecognitionStage,
    result: RecognitionResult?,
    query: String,
    sourceLabel: String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    mode: SearchMode,
    onModeChange: (SearchMode) -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onFindInLibrary: (MusicMatch) -> Unit,
    onCopy: (MusicMatch) -> Unit,
    onDownload: (MusicMatch) -> Unit,
    youtubeResults: List<YouTubeVideo>,
    youtubeStatus: String?,
    preview: PreviewState,
    onPreviewYouTube: (YouTubeVideo) -> Unit,
    onDownloadYouTube: (YouTubeVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        Text(
            text = "Identify",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp)
        )

        Text(
            text = "Drop in a video or audio file and Resonate works out what song " +
                "is playing. Or search by name across iTunes, Deezer, MusicBrainz, " +
                "Audius, the Internet Archive, YouTube and Genius at once, by a line " +
                "of the lyrics if the name is what you've forgotten, or paste a " +
                "TikTok or Instagram link and it will listen to that. The YouTube " +
                "tab searches all of YouTube, previews any result, and saves it " +
                "as a tagged mp3.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onPickVideo, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Videocam, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Video")
            }
            OutlinedButton(onClick = onPickAudio, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Audio")
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(mode.hint) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSearch, enabled = query.isNotBlank()) { Text("Go") }
        }

        // Searching by name and searching by lyrics hit different indexes, so
        // which one is meant has to be said rather than guessed.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchMode.entries.forEach { option ->
                FilterChip(
                    selected = option == mode,
                    onClick = { onModeChange(option) },
                    label = { Text(option.label) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            // Checked before the busy state so the list stays on screen while a
            // second search runs - a phone search takes seconds, and blanking
            // the results people are reading is worse than a stale list.
            mode == SearchMode.YOUTUBE && youtubeResults.isNotEmpty() -> Column {
                youtubeStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                YouTubeResultList(
                    videos = youtubeResults,
                    preview = preview,
                    onPreview = onPreviewYouTube,
                    onDownload = onDownloadYouTube
                )
            }

            stage != RecognitionStage.IDLE -> Centered {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    stage.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                sourceLabel?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }


            result is RecognitionResult.NoMatch -> Centered {
                Text("No match", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Shazam heard the audio but didn't recognise it. Clips with " +
                        "talking over the music, very obscure tracks, and live or " +
                        "pitch-shifted versions often fail.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            result is RecognitionResult.Error -> Centered {
                Text("Couldn't identify", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    result.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            result is RecognitionResult.Found -> LazyColumn(Modifier.fillMaxSize()) {
                items(result.matches, key = { it.display + it.album }) { match ->
                    MatchCard(
                        match = match,
                        onFindInLibrary = { onFindInLibrary(match) },
                        onCopy = { onCopy(match) },
                        onDownload = { onDownload(match) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            else -> Centered {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (mode == SearchMode.YOUTUBE) {
                        "Type anything and press Go. Every result can be previewed " +
                            "before you take it, and downloads land as tagged mp3 files."
                    } else {
                        "Pick a video or audio file and Resonate will listen to it, " +
                            "or search by name."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

}

@Composable
private fun MatchCard(
    match: MusicMatch,
    onFindInLibrary: () -> Unit,
    onCopy: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface),
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
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = match.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = match.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val detail = listOfNotNull(
                        match.provider.takeIf { it.isNotBlank() },
                        match.album,
                        match.releaseYear?.toString(),
                        match.confidence?.let { "$it% match" }
                    ).joinToString(" · ")
                    if (detail.isNotBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                TextButton(onClick = onFindInLibrary) {
                    Icon(Icons.Default.LibraryMusic, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Library")
                }
                if (match.drmProtected) {
                    // Encrypted audio, no fetchable file - a download button here
                    // could only fail, or silently substitute another recording.
                    Text(
                        "Streaming only",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    TextButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        // Services that host audio download directly; the rest
                        // are matched by name, so the label sets expectations.
                        Text(if (match.downloadUrl != null) "Download" else "Find & download")
                    }
                }
            }
        }
    }
}


@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) { content() }
    }
}
