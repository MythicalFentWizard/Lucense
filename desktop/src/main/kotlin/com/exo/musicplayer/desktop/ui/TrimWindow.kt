package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.exo.musicplayer.data.audio.Trim
import com.exo.musicplayer.desktop.audio.Decoder
import com.exo.musicplayer.desktop.data.DesktopController
import com.exo.musicplayer.desktop.library.DesktopTrack
import com.exo.musicplayer.desktop.system.Explorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/** Bars across the waveform. */
private const val BARS = 220

/**
 * Cut a song down and keep the piece as a new song: Riff's trimmer
 * (github.com/rootscripts/riff, MIT). The song is drawn as its loudness; the
 * two handles mark what is kept, and the preview plays just that stretch in
 * the small player, leaving the queue alone.
 */
@Composable
fun TrimWindow(controller: DesktopController, track: DesktopTrack, onClose: () -> Unit) {
    DialogWindow(
        onCloseRequest = onClose,
        title = "Trim · ${track.title}",
        state = rememberDialogState(size = DpSize(780.dp, 500.dp))
    ) {
        LaunchedEffect(Unit) { window.minimumSize = Dimension(600, 460) }
        LucenseDesktopTheme { Trimmer(controller, track, onClose) }
    }
}

@Composable
internal fun Trimmer(controller: DesktopController, track: DesktopTrack, onClose: () -> Unit) {
    val wave by rememberWave(track)
    val lengthMs = wave.lengthMs
    var startMs by remember(track.file) { mutableStateOf(0L) }
    // Null is "to the end", so it follows the length once the whole song has been read.
    var endSet by remember(track.file) { mutableStateOf<Long?>(null) }
    val endMs = (endSet ?: lengthMs).coerceAtMost(lengthMs)

    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<File?>(null) }
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

    // The preview, and where in the song it has got to.
    val preview by controller.preview.state.collectAsState()
    val key = controller.trimKey(track)
    val mine = preview.videoId?.startsWith("$key#") == true
    val previewFrom = if (mine) preview.videoId?.substringAfterLast('#')?.substringBefore('-')?.toLongOrNull() else null
    var playhead by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(mine, preview.playing, previewFrom) {
        if (!mine || previewFrom == null || preview.ended) {
            playhead = null
            return@LaunchedEffect
        }
        while (isActive && preview.playing) {
            withFrameMillis { }
            playhead = previewFrom + controller.preview.playedMs()
        }
    }
    DisposableEffect(key) {
        onDispose { if (controller.preview.state.value.videoId?.startsWith("$key#") == true) controller.stopPreview() }
    }

    Column(Modifier.fillMaxSize().background(Palette.Content).padding(22.dp)) {
        Text(
            track.title,
            style = MaterialTheme.typography.titleLarge,
            color = Palette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(track.displayArtist, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, maxLines = 1)
        Spacer(Modifier.height(16.dp))

        Waveform(
            bars = wave.bars,
            lengthMs = lengthMs,
            startMs = startMs,
            endMs = endMs,
            playheadMs = playhead,
            onStart = ::setStart,
            onEnd = ::setEnd,
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0:00.0", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            Text(
                if (wave.reading) "Reading the song…" else "Drag the handles, or click near one to move it there",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextFaint
            )
            Text(Trim.clock(lengthMs), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CutField("Start", startMs, { setStart(startMs - Trim.NUDGE_MS) }, { setStart(startMs + Trim.NUDGE_MS) })
            Spacer(Modifier.width(22.dp))
            CutField("End", endMs, { setEnd(endMs - Trim.NUDGE_MS) }, { setEnd(endMs + Trim.NUDGE_MS) })
            Spacer(Modifier.width(22.dp))
            Column {
                Text("Kept", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
                Text(Trim.clock(endMs - startMs), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton("Play the cut", icon = Icons.Default.PlayArrow, enabled = lengthMs > 0) {
                controller.previewTrim(track, startMs, endMs)
            }
            Spacer(Modifier.width(8.dp))
            GhostButton("Hear the end", icon = Icons.Default.SkipNext, enabled = lengthMs > 0) {
                controller.previewTrim(track, (endMs - Trim.END_PREVIEW_MS).coerceAtLeast(startMs), endMs)
            }
            if (mine && !preview.ended) {
                Spacer(Modifier.width(8.dp))
                GhostButton("Stop", icon = Icons.Default.Stop) { controller.stopPreview() }
            }
        }

        Spacer(Modifier.weight(1f))
        Hint(
            "Lossless: the audio is cut, not encoded again, so it sounds exactly as before. A cut lands " +
                "within a few hundredths of a second. The piece is saved beside the original, which stays as it is."
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                when {
                    problem != null -> Text(problem!!, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                    saved != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Saved as “${saved!!.name}”, and added to your library.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        GhostButton("Show", icon = Icons.Default.FolderOpen) { Explorer.reveal(saved!!) }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            GhostButton("Close") { onClose() }
            Spacer(Modifier.width(8.dp))
            AccentButton(
                if (saving) "Saving…" else "Save as a new song",
                enabled = !saving && lengthMs > 0 && endMs - startMs >= Trim.MIN_LENGTH_MS &&
                    (startMs > 0 || endMs < lengthMs),
                icon = Icons.Default.ContentCut
            ) {
                saving = true
                problem = null
                scope.launch {
                    controller.saveTrim(track, startMs, endMs)
                        .onSuccess { saved = it }
                        .onFailure { problem = "Couldn't save it: ${it.message}" }
                    saving = false
                }
            }
        }
    }
}

@Composable
private fun CutField(label: String, ms: Long, onEarlier: () -> Unit, onLater: () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton("−") { onEarlier() }
            Spacer(Modifier.width(6.dp))
            Text(
                Trim.clock(ms),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp)
            )
            GhostButton("+") { onLater() }
        }
    }
}

/**
 * The song's loudness as bars, the kept stretch bright and the rest faint,
 * with a handle at each end. A press moves whichever handle is nearer to it,
 * and a drag carries on from there.
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
    val accent = Palette.Accent
    val faint = Palette.TextFaint.copy(alpha = 0.35f)
    val shade = Palette.Base.copy(alpha = 0.45f)
    val handle = Palette.Text
    // The latest values, for the gesture, which outlives any one of them.
    val latest = remember { object { var start = 0L; var end = 0L; var length = 0L } }
    latest.start = startMs
    latest.end = endMs
    latest.length = lengthMs

    Canvas(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Raised)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (latest.length <= 0) return@awaitEachGesture
                    // Inset, so a handle at either end is drawn whole.
                    val pad = 10.dp.toPx()
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
        val pad = 10.dp.toPx()
        val span = size.width - 2 * pad
        val step = span / BARS
        val middle = size.height / 2
        val startX = pad + startMs.toFloat() / lengthMs * span
        val endX = pad + endMs.toFloat() / lengthMs * span
        bars.forEachIndexed { i, value ->
            val x = pad + i * step + step / 2
            val height = (value * (size.height - 16f)).coerceAtLeast(2f)
            drawRoundRect(
                color = if (x in startX..endX) accent else faint,
                topLeft = Offset(x - step * 0.32f, middle - height / 2),
                size = Size(step * 0.64f, height),
                cornerRadius = CornerRadius(step * 0.3f)
            )
        }
        // What is cut away is shaded as well as faint, so it reads at a glance.
        drawRect(shade, Offset.Zero, Size(startX, size.height))
        drawRect(shade, Offset(endX, 0f), Size(size.width - endX, size.height))
        for (x in listOf(startX, endX)) {
            drawRect(handle, Offset(x - 1.5f, 0f), Size(3f, size.height))
            drawRoundRect(handle, Offset(x - 6f, middle - 14f), Size(12f, 28f), CornerRadius(6f))
        }
        playheadMs?.let {
            val x = pad + it.toFloat() / lengthMs * span
            drawRect(accent, Offset(x - 1f, 0f), Size(2f, size.height))
        }
    }
}

private class Wave(val bars: FloatArray, val lengthMs: Long, val reading: Boolean)

/**
 * The song's loudness, read once in the background: a value per 10 ms, shown
 * as it fills in. The length comes from what was actually read once it is
 * done, which beats a tag that has it wrong.
 */
@Composable
private fun rememberWave(track: DesktopTrack): State<Wave> {
    val state = remember(track.file) { mutableStateOf(Wave(FloatArray(BARS), track.durationMs, reading = true)) }
    LaunchedEffect(track.file) {
        withContext(Dispatchers.IO) {
            val slices = FloatArray(((track.durationMs.coerceAtLeast(1_000L)) / 10 + 200).toInt())
            var filled = 0
            var published = 0L
            runCatching {
                Decoder.open(track.file).use { decoder ->
                    var sum = 0.0
                    var count = 0
                    while (isActive && filled < slices.size) {
                        val chunk = decoder.read(4_410) ?: break
                        var i = 0
                        while (i + 1 < chunk.size) {
                            val mono = (chunk[i] + chunk[i + 1]) * 0.5f
                            sum += mono * mono
                            if (++count == 441) {
                                if (filled < slices.size) slices[filled++] = sqrt(sum / count).toFloat()
                                sum = 0.0
                                count = 0
                            }
                            i += 2
                        }
                        val now = System.currentTimeMillis()
                        if (now - published > 120) {
                            published = now
                            state.value = Wave(Trim.bars(slices, filled, BARS), track.durationMs, reading = true)
                        }
                    }
                }
            }
            state.value = if (filled > 0) {
                Wave(Trim.bars(slices.copyOf(filled), filled, BARS), filled * 10L, reading = false)
            } else {
                Wave(FloatArray(BARS), track.durationMs, reading = false)
            }
        }
    }
    return state
}
