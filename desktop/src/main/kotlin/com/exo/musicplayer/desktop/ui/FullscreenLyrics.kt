package com.exo.musicplayer.desktop.ui

import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exo.musicplayer.data.lyrics.LrcParser
import com.exo.musicplayer.desktop.data.Covers
import com.exo.musicplayer.desktop.data.DesktopController

/**
 * The lyrics alone, big, over the song's cover: Riff's fullscreen and karaoke
 * view (github.com/rootscripts/riff, MIT), in a window of its own that covers
 * the screen. Escape or the X closes it.
 */
@Composable
fun FullscreenLyrics(controller: DesktopController) {
    val status by controller.engine.status.collectAsState()
    val track = status.track
    var cover by remember(track?.file?.absolutePath) { mutableStateOf<ImageBitmap?>(track?.let { Covers.cached(it) }) }
    LaunchedEffect(track?.file?.absolutePath) {
        if (track != null && cover == null && !Covers.knownMissing(track)) cover = Covers.load(track)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (controller.lyricsOwnBackground) {
            // Your own background: the wallpaper, or the theme's glow, with the
            // background effect over it, as behind the library.
            GlassBackground(controller.wallpaper, controller.wallpaperDim, Modifier.matchParentSize())
            Backdrop(
                style = controller.backdrop,
                color = Palette.Stars,
                spectrum = controller.engine.spectrum,
                graph = controller.songGraph,
                beat = controller.engine.beat,
                reactiveMode = controller.reactiveMode,
                modifier = Modifier.matchParentSize()
            )
            // White words need a dark veil, and a light theme's background needs more of one.
            val veil = if (Palette.Base.luminance() > 0.5f) 0.74f else 0.5f
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = veil)))
        } else {
            cover?.let {
                Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(60.dp))
            }
            Box(Modifier.fillMaxSize().background(Color(0xB8000000)))
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track?.title.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track?.displayArtist.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                // Behind the words: the song's cover, or your own background.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackgroundChoice("Cover", !controller.lyricsOwnBackground) { controller.lyricsOwnBackground = false }
                    BackgroundChoice("My background", controller.lyricsOwnBackground) { controller.lyricsOwnBackground = true }
                }
                Spacer(Modifier.width(16.dp))
                Text("Esc to close", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
                Spacer(Modifier.width(12.dp))
                RoundButton(Icons.Default.Close, "Close fullscreen lyrics") { controller.lyricsFullscreen = false }
            }

            Box(Modifier.weight(1f).fillMaxWidth().fadeEdges(), contentAlignment = Alignment.Center) {
                val synced = controller.lyricsSynced
                val plain = controller.lyricsPlain
                val lines = remember(synced) { synced?.let(LrcParser::parse).orEmpty() }
                when {
                    lines.isNotEmpty() -> BigLines(
                        lines.map { it.text },
                        LrcParser.activeIndex(lines, status.positionMs + controller.lyricsOffsetMs),
                        controller.lyricsTranslation
                    )
                    !plain.isNullOrBlank() -> SelectionContainer {
                        Text(
                            plain,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        )
                    }
                    else -> Text("No lyrics for this song.", color = Color.White.copy(alpha = 0.7f))
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RoundButton(
                    if (status.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (status.playing) "Pause" else "Play",
                    big = true
                ) { controller.togglePlay() }
            }
        }
    }
}

@Composable
private fun BigLines(lines: List<String>, active: Int, translation: List<String>?) {
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem(maxOf(0, active - 3))
    }
    SelectionContainer {
        LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 120.dp), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == active
                val colour by animateColorAsState(if (isActive) Color.White else Color.White.copy(alpha = 0.35f))
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(
                        line.ifBlank { "♪" },
                        fontSize = if (isActive) 42.sp else 30.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = colour,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    translation?.getOrNull(index)?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            fontSize = 20.sp,
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

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (big) 64.dp else 40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (big) 0.9f else 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(if (big) 32.dp else 20.dp), tint = if (big) Color.Black else Color.White)
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

@Composable
private fun BackgroundChoice(label: String, chosen: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (chosen) Color.Black else Color.White.copy(alpha = 0.8f),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (chosen) Color.White.copy(alpha = 0.9f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}
