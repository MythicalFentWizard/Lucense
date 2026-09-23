package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.desktop.data.DesktopController

/**
 * Lyrics in a window of their own, to move beside anything and size freely.
 *
 * The background effect keeps running while this window isn't focused, since
 * it mostly sits next to whatever has focus. Dock puts the lyrics back in the
 * side panel.
 */
@Composable
fun LyricsWindow(controller: DesktopController) {
    val status by controller.engine.status.collectAsState()
    Box(Modifier.fillMaxSize().background(Palette.Sidebar)) {
        Wallpaper(controller.wallpaper, controller.wallpaperDim, Modifier.matchParentSize())
        Backdrop(
            style = controller.backdrop,
            color = Palette.Stars,
            spectrum = controller.engine.spectrum,
            graph = controller.songGraph,
            beat = controller.engine.beat,
            reactiveMode = controller.reactiveMode,
            modifier = Modifier.matchParentSize(),
            count = 60,
            pauseWhenUnfocused = false
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status.track?.title ?: "Lyrics",
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { controller.toggleBigCover() }) {
                    Icon(
                        if (controller.bigCover) {
                            Icons.Default.CloseFullscreen
                        } else {
                            Icons.Default.OpenInFull
                        },
                        if (controller.bigCover) "Hide the cover" else "Show the cover",
                        Modifier.size(15.dp),
                        tint = Palette.TextDim
                    )
                }
                Spacer(Modifier.width(4.dp))
                GhostButton("Dock") { controller.togglePanel(SidePanelKind.LYRICS) }
            }
            if (controller.bigCover && status.track != null) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Artwork(status.track, 190.dp, corner = 12.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            // On a weight so the words take whatever is left once the cover has
            // had its share, rather than running off the bottom of the window.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LyricsPanel(controller, status.track, status.positionMs)
            }
        }
    }
}
