package com.exo.musicplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BedtimeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.exo.musicplayer.data.db.Lyrics
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.lyrics.LyricLine
import com.exo.musicplayer.playback.AudioFxState
import com.exo.musicplayer.playback.AudioOutput
import com.exo.musicplayer.playback.FxPreset
import com.exo.musicplayer.playback.PlaybackState
import com.exo.musicplayer.playback.ReverbRoom
import com.exo.musicplayer.playback.Sleep
import com.exo.musicplayer.playback.SleepTimer
import com.exo.musicplayer.ui.common.Artwork
import com.exo.musicplayer.ui.common.ArtworkLarge
import com.exo.musicplayer.ui.common.LineSlider
import com.exo.musicplayer.ui.common.LocalTrackActions
import com.exo.musicplayer.ui.theme.LocalStarfieldActive
import com.exo.musicplayer.ui.theme.ScreenBackdrop
import com.exo.musicplayer.util.asDuration
import kotlinx.coroutines.delay

/** What the effects panel can change; one object rather than fourteen parameters. */
class FxControls(
    val onPreset: (FxPreset) -> Unit,
    val onSpeed: (Float) -> Unit,
    val onPitch: (Float) -> Unit,
    val onReverbEnabled: (Boolean) -> Unit,
    val onReverbRoom: (ReverbRoom) -> Unit,
    val onReverbAmount: (Float) -> Unit,
    val onEqEnabled: (Boolean) -> Unit,
    val onEqBand: (Int, Float) -> Unit,
    val onEqFlat: () -> Unit,
    val onReset: () -> Unit,
    val onRemember: () -> Unit,
    val onForget: () -> Unit,
    val onPickOutput: (String) -> Unit,
    val onMirrorOutputs: (Boolean) -> Unit
)

/** What the Up next panel can do to the queue. Positions are in play order. */
class QueueControls(
    val onJump: (Int) -> Unit,
    val onMove: (Int, Int) -> Unit,
    val onRemove: (Int) -> Unit,
    val onClear: () -> Unit
)

private enum class Panel(val label: String, val icon: ImageVector) {
    QUEUE("Up next", Icons.AutoMirrored.Filled.QueueMusic),
    LYRICS("Lyrics", Icons.AutoMirrored.Filled.Article),
    EFFECTS("Effects", Icons.Default.GraphicEq)
}

@Composable
fun PlayerScreen(
    track: Track?,
    state: PlaybackState,
    queue: List<Track>,
    lyrics: Lyrics?,
    lyricLines: List<LyricLine>,
    lyricsBusy: Boolean,
    lyricsMessage: String?,
    fx: AudioFxState,
    fxRemembered: Boolean,
    outputs: List<AudioOutput>,
    selectedOutputs: Set<String>,
    mirrorOutputs: Boolean,
    sleep: Sleep,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRate: (Int) -> Unit,
    onSleep: (Int) -> Unit,
    onSleepEndOfSong: () -> Unit,
    onCancelSleep: () -> Unit,
    onFetchLyrics: () -> Unit,
    onSaveLyrics: (String) -> Unit,
    onDeleteLyrics: () -> Unit,
    fxControls: FxControls,
    queueControls: QueueControls
) {
    var panel by rememberSaveable { mutableStateOf<Panel?>(null) }
    // Back closes the open panel first, and only then the player.
    BackHandler(enabled = panel != null) { panel = null }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(Modifier.fillMaxSize()) {
            if (LocalStarfieldActive.current) ScreenBackdrop()
            Column(
                Modifier
                    .fillMaxSize()
                    // This screen sits above the Scaffold, so it gets no content
                    // padding from it: without this the header slides under the
                    // status bar and the controls under the gesture bar.
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 20.dp)
            ) {
                TopBar(
                    track = track,
                    title = panel?.label ?: "Now playing",
                    sleep = sleep,
                    onCollapse = onCollapse,
                    onSleep = onSleep,
                    onSleepEndOfSong = onSleepEndOfSong,
                    onCancelSleep = onCancelSleep
                )

                // Exactly one vertical scroller lives here at a time; nesting the
                // lyrics list inside the queue list would crash.
                Box(Modifier.weight(1f)) {
                    when (panel) {
                        Panel.EFFECTS -> EffectsSheet(
                            state = fx,
                            remembered = fxRemembered,
                            hasSong = track != null,
                            controls = fxControls,
                            outputs = outputs,
                            selectedOutputs = selectedOutputs,
                            mirrorOutputs = mirrorOutputs,
                            playing = state.isPlaying
                        )
                        Panel.LYRICS -> LyricsPanel(
                            lyrics = lyrics,
                            lines = lyricLines,
                            positionMs = state.positionMs,
                            busy = lyricsBusy,
                            message = lyricsMessage,
                            onFetch = onFetchLyrics,
                            onSave = onSaveLyrics,
                            onDelete = onDeleteLyrics
                        )
                        Panel.QUEUE -> QueuePanel(queue, state.queueIndex, queueControls)
                        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ArtworkLarge(
                                track = track,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .padding(top = 8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track?.title ?: "Nothing playing",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOfNotNull(track?.artist, track?.album)
                                .ifEmpty { listOf("Unknown artist") }
                                .joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onToggleFavorite, enabled = track != null) {
                        Icon(
                            imageVector = if (track?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (track?.isFavorite == true) "Remove favourite" else "Favourite",
                            tint = if (track?.isFavorite == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (track != null) Stars(rating = track.rating, onRate = onRate)

                Spacer(Modifier.height(4.dp))
                Scrubber(state = state, onSeek = onSeek)
                Controls(
                    state = state,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Panel.entries.forEach { entry ->
                        val on = panel == entry
                        val active = on || (entry == Panel.EFFECTS && !fx.isDefault)
                        TextButton(onClick = { panel = if (on) null else entry }) {
                            Icon(
                                entry.icon, null, Modifier.size(18.dp),
                                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                entry.label,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun TopBar(
    track: Track?,
    title: String,
    sleep: Sleep,
    onCollapse: () -> Unit,
    onSleep: (Int) -> Unit,
    onSleepEndOfSong: () -> Unit,
    onCancelSleep: () -> Unit
) {
    val actions = LocalTrackActions.current
    var sleepMenu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close player")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Box {
            TextButton(onClick = { sleepMenu = true }) {
                Icon(
                    if (sleep == Sleep.Off) Icons.Default.BedtimeOff else Icons.Default.Bedtime,
                    contentDescription = "Sleep timer",
                    tint = if (sleep == Sleep.Off) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                SleepCountdown(sleep)
            }
            DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                Text(
                    "Sleep timer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                SleepTimer.CHOICES.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text("$minutes minutes") },
                        onClick = { sleepMenu = false; onSleep(minutes) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("When this song ends") },
                    onClick = { sleepMenu = false; onSleepEndOfSong() }
                )
                if (sleep != Sleep.Off) {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Turn off") }, onClick = { sleepMenu = false; onCancelSleep() })
                }
            }
        }
        Box {
            IconButton(onClick = { more = true }, enabled = track != null) {
                Icon(Icons.Default.MoreVert, contentDescription = "More for this song")
            }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                if (track != null) {
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        onClick = { more = false; actions.addToPlaylist(track) }
                    )
                    DropdownMenuItem(
                        text = { Text("Fix…") },
                        leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) },
                        onClick = { more = false; actions.fix(track) }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit details…") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { more = false; actions.edit(track) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = { more = false; actions.share(track) }
                    )
                }
            }
        }
    }
}

/** Minutes left, or "end", counted down live while the timer runs. */
@Composable
private fun SleepCountdown(sleep: Sleep) {
    when (sleep) {
        is Sleep.At -> {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(sleep) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(1_000)
                }
            }
            val left = ((sleep.endsAtMs - now) / 1000).coerceAtLeast(0)
            Spacer(Modifier.width(4.dp))
            Text(
                "%d:%02d".format(left / 60, left % 60),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Sleep.EndOfSong -> {
            Spacer(Modifier.width(4.dp))
            Text("End", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Sleep.Off -> Unit
    }
}

@Composable
private fun Stars(rating: Int, onRate: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { star ->
            Icon(
                imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "$star of 5",
                tint = if (star <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    // Tapping the star already set clears the rating.
                    .clickable { onRate(if (star == rating) 0 else star) }
                    .padding(2.dp)
            )
        }
    }
}

@Composable
private fun Scrubber(state: PlaybackState, onSeek: (Float) -> Unit) {
    // While the thumb is held, show the finger position rather than the player's.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val shown = if (scrubbing) scrubValue else state.progress

    Column {
        LineSlider(
            value = shown,
            onValueChange = {
                scrubbing = true
                scrubValue = it
            },
            onValueChangeFinished = {
                if (scrubbing) onSeek(scrubValue)
                scrubbing = false
            },
            enabled = state.durationMs > 0,
            description = "Seek"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val tabular = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
            Text(
                text = (shown * state.durationMs).toLong().asDuration(),
                style = tabular,
                color = if (scrubbing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = state.durationMs.asDuration(),
                style = tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Controls(
    state: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = if (state.shuffleEnabled) "Shuffle on" else "Shuffle off",
                tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
        }
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onTogglePlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
            )
        }
        IconButton(onClick = onNext, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else -> "Repeat off"
                },
                tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * What's coming, in the order it will play - the shuffled order with shuffle
 * on - with each song movable up or down, removable, and the lot clearable.
 */
@Composable
private fun QueuePanel(queue: List<Track>, currentIndex: Int, controls: QueueControls) {
    val upcoming = if (currentIndex >= 0) queue.drop(currentIndex + 1) else queue
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            queue.getOrNull(currentIndex)?.let { current ->
                Text(
                    "Playing now",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                QueueRow(track = current, isCurrent = true, onClick = {}, onUp = null, onDown = null, onRemove = null)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    if (upcoming.isEmpty()) "Nothing after this" else "Up next · ${upcoming.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (upcoming.isNotEmpty()) TextButton(onClick = controls.onClear) { Text("Clear") }
            }
        }
        itemsIndexed(upcoming, key = { i, t -> "${currentIndex + 1 + i}-${t.id}" }) { offset, item ->
            val position = currentIndex + 1 + offset
            QueueRow(
                track = item,
                isCurrent = false,
                onClick = { controls.onJump(position) },
                onUp = if (offset > 0) ({ controls.onMove(position, position - 1) }) else null,
                onDown = if (offset < upcoming.lastIndex) ({ controls.onMove(position, position + 1) }) else null,
                onRemove = { controls.onRemove(position) }
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun QueueRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRemove: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = !isCurrent, onClick = onClick)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent
            )
            .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track = track, size = 40.dp, cornerRadius = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(track.artist ?: "Unknown artist", track.durationMs.takeIf { it > 0 }?.asDuration())
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onUp != null || onDown != null) {
            IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "Move up")
            }
            IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "Move down")
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, "Take out of the queue", Modifier.size(18.dp))
            }
        }
    }
}
