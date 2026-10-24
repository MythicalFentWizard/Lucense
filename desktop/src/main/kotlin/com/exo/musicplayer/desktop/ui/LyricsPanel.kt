package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Translate
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.lyrics.LrcParser
import com.exo.musicplayer.desktop.data.DesktopController
import com.exo.musicplayer.desktop.library.DesktopTrack

/**
 * Lyrics for the playing track.
 *
 * Four services are tried in turn — LRCLIB, NetEase, lyrics.ovh, then Genius —
 * and if all four come up empty the words can be typed in by hand. A pasted LRC
 * is detected and kept timed, so hand-entered lyrics still scroll in sync rather
 * than being demoted to a plain block.
 */
@Composable
fun LyricsPanel(controller: DesktopController, track: DesktopTrack?, positionMs: Long) {
    var editing by remember(track?.file?.absolutePath) { mutableStateOf(false) }

    if (track == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Hint("Play something to see its lyrics.")
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            track.title,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Text,
            maxLines = 2
        )
        Text(
            track.displayArtist,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextDim
        )

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(
                if (controller.lyricsPlain == null) "Find lyrics" else "Look again",
                enabled = !controller.lyricsLoading
            ) { controller.fetchLyrics(track) }
            Spacer(Modifier.width(7.dp))
            GhostButton(
                if (editing) "Cancel" else "Edit",
                icon = if (editing) null else Icons.Default.Edit
            ) { editing = !editing }
        }

        if (controller.lyricsLoading) {
            Spacer(Modifier.height(10.dp))
            ThinProgress(null)
        }

        controller.lyricsSource?.let {
            Spacer(Modifier.height(8.dp))
            Text("from $it", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        }
        controller.lyricsNote?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
        }

        // Translate, move in time, go fullscreen: Riff's lyrics tools.
        if (!editing && (controller.lyricsSynced != null || controller.lyricsPlain != null)) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(
                    when {
                        controller.lyricsTranslating -> "Translating…"
                        controller.lyricsTranslation != null -> "Hide translation"
                        else -> "Translate"
                    },
                    enabled = !controller.lyricsTranslating,
                    icon = Icons.Default.Translate
                ) { controller.translateLyrics() }
                Spacer(Modifier.width(7.dp))
                GhostButton("Fullscreen", icon = Icons.Default.Fullscreen) { controller.lyricsFullscreen = true }
            }
            controller.lyricsTranslationNote?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            }
            if (controller.lyricsSynced != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Timing", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
                    Spacer(Modifier.width(8.dp))
                    GhostButton("Later") { controller.nudgeLyrics(-NUDGE_MS) }
                    Spacer(Modifier.width(6.dp))
                    GhostButton("Sooner") { controller.nudgeLyrics(NUDGE_MS) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        offsetLabel(controller.lyricsOffsetMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint,
                        modifier = Modifier.clickable { controller.resetLyricsOffset() }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Read once each: these are state, and a second read of the same one
        // can come back null between a fetch finishing and this being drawn.
        val synced = controller.lyricsSynced
        val plain = controller.lyricsPlain
        when {
            editing -> LyricsEditor(
                initial = synced ?: plain.orEmpty(),
                onSave = { text ->
                    controller.saveManualLyrics(track, text)
                    editing = false
                }
            )

            synced != null -> SyncedLyrics(synced, positionMs + controller.lyricsOffsetMs, controller.lyricsTranslation)

            // Selectable, so a line can be copied out.
            plain != null -> SelectionContainer {
                val translation = controller.lyricsTranslation
                if (translation == null) {
                    Text(
                        plain,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextDim,
                        lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.6,
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    )
                } else {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        plain.lines().forEachIndexed { i, line ->
                            Text(line, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                            translation.getOrNull(i)?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = Palette.TextFaint)
                            }
                            Spacer(Modifier.height(5.dp))
                        }
                    }
                }
            }

            else -> Column {
                Hint(
                    "No lyrics stored for this track. Try the four services, or paste " +
                        "them in yourself — plain text or an LRC file both work."
                )
            }
        }
    }
}

/** Highlights the current line and keeps it centred. */
@Composable
private fun SyncedLyrics(lrc: String, positionMs: Long, translation: List<String>?) {
    val lines = remember(lrc) { LrcParser.parse(lrc) }
    if (lines.isEmpty()) {
        Hint("These lyrics have timestamps that couldn't be read.")
        return
    }

    val active = remember(lines, positionMs) { LrcParser.activeIndex(lines, positionMs) }
    val listState = rememberLazyListState()

    LaunchedEffect(active) {
        if (active >= 0) {
            // Two lines of lead-in so the highlighted line sits in the upper
            // middle rather than pinned to the top edge.
            listState.animateScrollToItem(maxOf(0, active - 2))
        }
    }

    // Selectable, so a line can be copied out.
    SelectionContainer {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == active
                val alpha by animateFloatAsState(if (isActive) 1f else 0.45f)
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(
                        line.text.ifBlank { "·" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) {
                            Palette.LyricsActive
                        } else {
                            Palette.LyricsInactive.copy(alpha = Palette.LyricsInactive.alpha * alpha)
                        }
                    )
                    translation?.getOrNull(index)?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = Palette.TextFaint
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

@Composable
private fun LyricsEditor(initial: String, onSave: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }

    Column(Modifier.fillMaxSize()) {
        Hint(
            "Paste plain lyrics, or an LRC with [mm:ss.xx] timestamps to keep them synced. " +
                "Clearing the box removes what's stored."
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(7.dp))
                .background(Palette.Well)
                .border(1.dp, Palette.Line, RoundedCornerShape(7.dp))
                .padding(10.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            )
        }
        Spacer(Modifier.height(10.dp))
        AccentButton("Save lyrics", icon = Icons.Default.Lyrics) { onSave(text) }
        Spacer(Modifier.height(12.dp))
    }
}

/** How much one press moves the lyrics. */
private const val NUDGE_MS = 250L

/** "0.5 s sooner", "1.25 s later" or "on time". */
private fun offsetLabel(offsetMs: Long): String = when (offsetMs) {
    0L -> "on time"
    else -> {
        val seconds = kotlin.math.abs(offsetMs) / 1000.0
        val text = if (seconds % 1.0 == 0.0) "${seconds.toInt()}" else "%.2f".format(seconds).trimEnd('0')
        "$text s ${if (offsetMs > 0) "sooner" else "later"} · reset"
    }
}
