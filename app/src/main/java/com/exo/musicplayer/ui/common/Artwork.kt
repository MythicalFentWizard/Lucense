package com.exo.musicplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.exo.musicplayer.data.db.Track

/**
 * Cover art, with a tinted note behind it.
 *
 * The placeholder is drawn unconditionally and the image composited over it,
 * rather than checking `File.exists()` first. That check was a synchronous
 * filesystem stat running on the main thread for every row that scrolled into
 * view, which is exactly the kind of per-frame I/O that makes a list feel
 * sticky. Coil already resolves the file on a background dispatcher and simply
 * draws nothing when it is missing, so the note shows through by itself.
 */
@Composable
fun Artwork(
    track: Track?,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size / 2.4f)
        )
        // AsyncImage sizes its decode to these constraints, so a 1000px cover is
        // never decoded at full resolution for a 52dp thumbnail.
        track?.artPath?.let { path ->
            AsyncImage(
                model = path,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Large square variant used by the full player. */
@Composable
fun ArtworkLarge(track: Track?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(96.dp)
        )
        track?.artPath?.let { path ->
            AsyncImage(
                model = path,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
