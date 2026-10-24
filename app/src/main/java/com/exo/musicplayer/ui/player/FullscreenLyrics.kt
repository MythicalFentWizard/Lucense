package com.exo.musicplayer.ui.player

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.lyrics.LrcParser
import com.exo.musicplayer.data.lyrics.LyricLine

/**
 * The lyrics alone, big, over the song's cover: Riff's fullscreen and karaoke
 * view (github.com/rootscripts/riff, MIT). The status and navigation bars are
 * hidden while it is open; Back or the X closes it.
 */
@Composable
fun FullscreenLyrics(
    track: Track?,
    lines: List<LyricLine>,
    plainText: String?,
    positionMs: Long,
    translation: List<String>?,
    playing: Boolean,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    ImmersiveWhileShown()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        track?.artPath?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(48.dp)
            )
        }
        Box(Modifier.fillMaxSize().background(Color(0xB3000000)))

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track?.title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track?.artist.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close fullscreen lyrics", tint = Color.White) }
            }

            Box(Modifier.weight(1f).fillMaxWidth().fadeEdges(), contentAlignment = Alignment.Center) {
                when {
                    lines.isNotEmpty() -> BigSyncedLines(lines, positionMs, translation)
                    !plainText.isNullOrBlank() -> SelectionContainer {
                        Text(
                            plainText,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        )
                    }
                    else -> Text("No lyrics for this song.", color = Color.White.copy(alpha = 0.7f))
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FilledIconButton(onClick = onTogglePlayPause, modifier = Modifier.size(56.dp)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play")
                }
            }
        }
    }
}

/** The current line large and bright, in the middle; the rest small and dim around it. */
@Composable
private fun BigSyncedLines(lines: List<LyricLine>, positionMs: Long, translation: List<String>?) {
    val active = LrcParser.activeIndex(lines, positionMs)
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) runCatching { listState.animateScrollToItem(maxOf(0, active - 3)) }
    }
    SelectionContainer {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == active
                val colour by animateColorAsState(if (isActive) Color.White else Color.White.copy(alpha = 0.38f), label = "line")
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(
                        line.text.ifBlank { "♪" },
                        style = if (isActive) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = colour,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    translation?.getOrNull(index)?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.titleSmall,
                            fontStyle = FontStyle.Italic,
                            color = colour.copy(alpha = colour.alpha * 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** Hides the status and navigation bars while shown, and brings them back after. */
@Composable
private fun ImmersiveWhileShown() {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(view) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Fades the top and bottom edges, so lines leave the view softly rather than being sliced off. */
private fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.14f to Color.Black,
                0.86f to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }
