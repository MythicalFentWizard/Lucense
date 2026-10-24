package com.exo.musicplayer.ui.player

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.db.Lyrics
import com.exo.musicplayer.data.lyrics.LrcParser
import com.exo.musicplayer.data.lyrics.LyricLine
import com.exo.musicplayer.ui.theme.LocalLyricsColors
import kotlin.math.abs

/**
 * What the lyrics panel can do beyond showing them: move them in time, and
 * translate them. Both are Riff's (github.com/rootscripts/riff, MIT).
 */
class LyricsExtras(
    /** How far the lyrics are moved for this song; positive shows each line sooner. */
    val offsetMs: Long,
    /** A translation, one line per line of the lyrics, or null when none is showing. */
    val translation: List<String>?,
    val translating: Boolean,
    /** "Translated from Portuguese", or why it couldn't be. */
    val note: String?,
    val onNudge: (Long) -> Unit,
    val onResetOffset: () -> Unit,
    /** Translates, or hides the translation when one is showing. */
    val onTranslate: () -> Unit
)

/** How much one tap moves the lyrics. */
private const val NUDGE_MS = 250L

/** "0.5 s sooner", "1.25 s later" or "on time". */
internal fun offsetLabel(offsetMs: Long): String = when {
    offsetMs == 0L -> "on time"
    else -> {
        val seconds = abs(offsetMs) / 1000.0
        val text = if (seconds % 1.0 == 0.0) "${seconds.toInt()}" else "%.2f".format(seconds).trimEnd('0')
        "$text s ${if (offsetMs > 0) "sooner" else "later"}"
    }
}

@Composable
fun LyricsPanel(
    lyrics: Lyrics?,
    lines: List<LyricLine>,
    positionMs: Long,
    busy: Boolean,
    message: String?,
    extras: LyricsExtras,
    onFetch: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember(lyrics?.trackId) { mutableStateOf(false) }

    if (editing) {
        LyricsEditor(
            initial = lyrics?.syncedText?.takeIf { it.isNotBlank() } ?: lyrics?.plainText.orEmpty(),
            onSave = { onSave(it); editing = false },
            onDismiss = { editing = false }
        )
    }

    val hasWords = lines.isNotEmpty() || !lyrics?.plainText.isNullOrBlank()

    Column(modifier.fillMaxSize()) {
        if (hasWords && !busy) {
            LyricsTools(extras = extras, synced = lines.isNotEmpty(), onFullscreen = onFullscreen)
        }

        when {
            busy -> Centered {
                CircularProgressIndicator()
                Spacer(Modifier.height(14.dp))
                Text(
                    "Looking for lyrics…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            lines.isNotEmpty() -> SyncedLyrics(
                lines = lines,
                positionMs = positionMs + extras.offsetMs,
                translation = extras.translation,
                modifier = Modifier.weight(1f)
            )

            !lyrics?.plainText.isNullOrBlank() -> SelectionContainer(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    val plain = lyrics?.plainText.orEmpty().lines()
                    val translation = extras.translation
                    if (translation == null) {
                        Text(
                            text = plain.joinToString("\n"),
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.6f
                        )
                    } else {
                        plain.forEachIndexed { i, line ->
                            Text(line, style = MaterialTheme.typography.bodyLarge)
                            translation.getOrNull(i)?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            else -> Centered {
                Text(
                    text = message ?: "No lyrics yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onFetch) {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Search for lyrics")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { editing = true }) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Type them in")
                }
            }
        }

        if (lyrics != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lyrics.isManual) "Added by you" else "From LRCLIB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { editing = true }) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
            }
        }
    }
}

/** Translate, move the lyrics in time when they're early or late, and go fullscreen. */
@Composable
private fun LyricsTools(extras: LyricsExtras, synced: Boolean, onFullscreen: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = extras.onTranslate, enabled = !extras.translating) {
                if (extras.translating) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Translate, null, Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (extras.translation != null) "Hide translation" else "Translate")
            }
            Spacer(Modifier.weight(1f))
            if (synced) {
                TextButton(onClick = { extras.onNudge(-NUDGE_MS) }) { Text("Later") }
                Text(
                    offsetLabel(extras.offsetMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { extras.onNudge(NUDGE_MS) }) { Text("Sooner") }
            }
            IconButton(onClick = onFullscreen) { Icon(Icons.Default.Fullscreen, "Fullscreen lyrics") }
        }
        extras.note?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
            )
        }
    }
}

/** Highlights the line matching the playhead and keeps it in view. */
@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    translation: List<String>?,
    modifier: Modifier = Modifier
) {
    val active = LrcParser.activeIndex(lines, positionMs)
    val listState = rememberLazyListState()

    LaunchedEffect(active) {
        if (active >= 0) {
            // Keep the current line a third of the way down rather than at the
            // very top, so the next few lines are readable ahead of time.
            runCatching { listState.animateScrollToItem(maxOf(0, active - 2)) }
        }
    }

    // Selectable, so a line can be copied out.
    SelectionContainer(modifier.fillMaxWidth()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == active
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        text = line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) {
                            LocalLyricsColors.current.active ?: MaterialTheme.colorScheme.primary
                        } else {
                            LocalLyricsColors.current.inactive ?: MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    translation?.getOrNull(index)?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

@Composable
private fun LyricsEditor(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics") },
        text = {
            Column {
                Text(
                    "Paste or type the lyrics. If you paste an LRC file with " +
                        "[00:12.34] timestamps, they'll be kept and the lines will " +
                        "highlight in time with the music.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Lyrics…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 340.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) { content() }
    }
}
