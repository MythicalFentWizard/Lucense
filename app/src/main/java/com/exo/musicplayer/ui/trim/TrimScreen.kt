package com.exo.musicplayer.ui.trim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.exo.musicplayer.data.audio.Trim
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.youtube.PreviewLabel
import com.exo.musicplayer.data.youtube.YouTubePreviewPlayer
import com.exo.musicplayer.ui.theme.SongShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Bars across the waveform. */
private const val BARS = 120

/**
 * Cut a song down and keep the piece as a new song: Riff's trimmer
 * (github.com/rootscripts/riff, MIT). The song is drawn as its loudness; the
 * two handles mark what is kept, and the preview plays just that stretch in
 * the small player, leaving the queue alone.
 */
@Composable
fun TrimScreen(
    track: Track,
    preview: YouTubePreviewPlayer,
    /** Pauses the library's music, so the preview isn't played over it. */
    onPausePlayer: () -> Unit,
    onSave: suspend (startMs: Long, endMs: Long) -> Result<Track>,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Trimmer(track, preview, onPausePlayer, onSave, onClose)
        }
    }
}

private class Wave(val bars: FloatArray, val lengthMs: Long, val reading: Boolean)

@Composable
private fun Trimmer(
    track: Track,
    preview: YouTubePreviewPlayer,
    onPausePlayer: () -> Unit,
    onSave: suspend (Long, Long) -> Result<Track>,
    onClose: () -> Unit
) {
    // The song's loudness, read once: a value every 10 ms, shown as it fills in.
    // Its length comes from what was read, which beats a tag that has it wrong.
    var wave by remember(track.id) { mutableStateOf(Wave(FloatArray(BARS), track.durationMs, reading = true)) }
    LaunchedEffect(track.id) {
        val shape = SongShape(track.id)
        val poll = launch {
            while (isActive) {
                delay(150)
                wave = Wave(Trim.bars(shape.slices, shape.filled, BARS), track.durationMs, reading = true)
            }
        }
        shape.load(track.filePath)
        poll.cancel()
        val filled = shape.filled
        wave = if (filled > 0) {
            Wave(Trim.bars(shape.slices.copyOf(filled), filled, BARS), filled * 10L, reading = false)
        } else {
            Wave(FloatArray(BARS), track.durationMs, reading = false)
        }
    }
    val lengthMs = wave.lengthMs

    var startMs by remember(track.id) { mutableStateOf(0L) }
    // Null is "to the end", so it follows the length once the whole song is read.
    var endSet by remember(track.id) { mutableStateOf<Long?>(null) }
    val endMs = (endSet ?: lengthMs).coerceAtMost(lengthMs)
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Track?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun setStart(ms: Long) {
        startMs = ms.coerceIn(0L, (endMs - Trim.MIN_LENGTH_MS).coerceAtLeast(0L))
        saved = null
    }

    fun setEnd(ms: Long) {
        val end = ms.coerceIn((startMs + Trim.MIN_LENGTH_MS).coerceAtMost(lengthMs), lengthMs)
        endSet = if (end >= lengthMs) null else end
        saved = null
    }

    val key = "trim:${track.id}"
    val state by preview.state.collectAsState()
    val mine = state.videoId?.startsWith("$key#") == true
    val previewFrom = if (mine) state.videoId?.substringAfterLast('#')?.substringBefore('-')?.toLongOrNull() else null
    var playhead by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(mine, state.playing, previewFrom) {
        if (!mine || previewFrom == null || state.ended) {
            playhead = null
            return@LaunchedEffect
        }
        while (isActive && state.playing) {
            withFrameMillis { }
            playhead = previewFrom + preview.positionMs()
        }
    }
    DisposableEffect(key) {
        onDispose { if (preview.state.value.videoId?.startsWith("$key#") == true) preview.stop() }
    }

    fun hear(from: Long, to: Long) {
        onPausePlayer()
        // A new key for every stretch, so asking for another starts it rather than stopping this one.
        preview.toggle(
            "$key#$from-$to",
            PreviewLabel("Trim: ${track.title}", track.artist, track.artPath),
            clip = from..to
        ) { track.filePath }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Trim", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
        }
        Spacer(Modifier.height(18.dp))

        Waveform(
            bars = wave.bars,
            lengthMs = lengthMs,
            startMs = startMs,
            endMs = endMs,
            playheadMs = playhead,
            onStart = ::setStart,
            onEnd = ::setEnd,
            modifier = Modifier.fillMaxWidth().height(140.dp)
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0:00.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (wave.reading) {
                Text("Reading the song…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Trim.clock(lengthMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "Drag a handle, or tap near one to move it there.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(16.dp))
        CutRow("Start", startMs, { setStart(startMs - Trim.NUDGE_MS) }, { setStart(startMs + Trim.NUDGE_MS) })
        CutRow("End", endMs, { setEnd(endMs - Trim.NUDGE_MS) }, { setEnd(endMs + Trim.NUDGE_MS) })
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kept", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(64.dp))
            Text(Trim.clock(endMs - startMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { hear(startMs, endMs) }, enabled = lengthMs > 0) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Play the cut")
            }
            OutlinedButton(
                onClick = { hear((endMs - Trim.END_PREVIEW_MS).coerceAtLeast(startMs), endMs) },
                enabled = lengthMs > 0
            ) {
                Icon(Icons.Default.SkipNext, null)
                Spacer(Modifier.width(6.dp))
                Text("Hear the end")
            }
        }
        if (mine && !state.ended) {
            OutlinedButton(onClick = { preview.stop() }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.Stop, null)
                Spacer(Modifier.width(6.dp))
                Text("Stop")
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Lossless: the audio is cut, not encoded again, so it sounds exactly as before. A cut lands within " +
                "a few hundredths of a second. The piece is added to your library as a new song; the original stays.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                saving = true
                problem = null
                scope.launch {
                    onSave(startMs, endMs)
                        .onSuccess { saved = it }
                        .onFailure { problem = "Couldn't save it: ${it.message}" }
                    saving = false
                }
            },
            enabled = !saving && lengthMs > 0 && endMs - startMs >= Trim.MIN_LENGTH_MS && (startMs > 0 || endMs < lengthMs),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ContentCut, null)
            Spacer(Modifier.width(8.dp))
            Text(if (saving) "Saving…" else "Save as a new song")
        }
        problem?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        saved?.let {
            Text(
                "Saved as “${it.title}”, and added to your library.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CutRow(label: String, ms: Long, onEarlier: () -> Unit, onLater: () -> Unit) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(64.dp))
        FilledTonalIconButton(onClick = onEarlier) { Text("−", style = MaterialTheme.typography.titleMedium) }
        Text(
            Trim.clock(ms),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(88.dp)
        )
        FilledTonalIconButton(onClick = onLater) { Text("+", style = MaterialTheme.typography.titleMedium) }
    }
}

/**
 * The song's loudness as bars, the kept stretch bright and the rest faint,
 * with a handle at each end. A touch moves whichever handle is nearer, and a
 * drag carries on from there.
 */
@Composable
private fun Waveform(
    bars: FloatArray,
    lengthMs: Long,
    startMs: Long,
    endMs: Long,
    playheadMs: Long?,
    onStart: (Long) -> Unit,
    onEnd: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val shade = MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
    val handle = MaterialTheme.colorScheme.onSurface
    val latest = remember { object { var start = 0L; var end = 0L; var length = 0L } }
    latest.start = startMs
    latest.end = endMs
    latest.length = lengthMs

    Canvas(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (latest.length <= 0) return@awaitEachGesture
                    // Inset, so a handle at either end is drawn whole.
                    val pad = 12.dp.toPx()
                    fun at(x: Float): Long =
                        ((x - pad) / (size.width - 2 * pad) * latest.length).toLong().coerceIn(0L, latest.length)
                    val pressed = at(down.position.x)
                    val moveStart = abs(pressed - latest.start) <= abs(pressed - latest.end)
                    if (moveStart) onStart(pressed) else onEnd(pressed)
                    drag(down.id) { change ->
                        change.consume()
                        val ms = at(change.position.x)
                        if (moveStart) onStart(ms) else onEnd(ms)
                    }
                }
            }
    ) {
        if (lengthMs <= 0) return@Canvas
        val pad = 12.dp.toPx()
        val span = size.width - 2 * pad
        val step = span / BARS
        val middle = size.height / 2
        val startX = pad + startMs.toFloat() / lengthMs * span
        val endX = pad + endMs.toFloat() / lengthMs * span
        bars.forEachIndexed { i, value ->
            val x = pad + i * step + step / 2
            val height = (value * (size.height - 24f)).coerceAtLeast(3f)
            drawRoundRect(
                color = if (x in startX..endX) accent else faint,
                topLeft = Offset(x - step * 0.32f, middle - height / 2),
                size = Size(step * 0.64f, height),
                cornerRadius = CornerRadius(step * 0.3f)
            )
        }
        drawRect(shade, Offset.Zero, Size(startX, size.height))
        drawRect(shade, Offset(endX, 0f), Size(size.width - endX, size.height))
        val knob = 8.dp.toPx()
        for (x in listOf(startX, endX)) {
            drawRect(handle, Offset(x - 2f, 0f), Size(4f, size.height))
            drawRoundRect(handle, Offset(x - knob, middle - knob * 2.2f), Size(knob * 2, knob * 4.4f), CornerRadius(knob))
        }
        playheadMs?.let {
            val x = pad + it.toFloat() / lengthMs * span
            drawRect(accent, Offset(x - 1.5f, 0f), Size(3f, size.height))
        }
    }
}
