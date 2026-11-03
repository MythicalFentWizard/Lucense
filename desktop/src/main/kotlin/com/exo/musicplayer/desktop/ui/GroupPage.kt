package com.exo.musicplayer.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exo.musicplayer.data.art.CoverColour
import com.exo.musicplayer.desktop.data.CollectionKind
import com.exo.musicplayer.desktop.data.Covers
import com.exo.musicplayer.desktop.data.Thumbnails
import com.exo.musicplayer.desktop.data.TrackGroup
import com.exo.musicplayer.desktop.library.DesktopTrack
import com.exo.musicplayer.desktop.system.Platform
import com.exo.musicplayer.util.asDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** What an album's or an artist's page can ask for. The controller does each; a picture of the page does none. */
class GroupActions(
    val play: (track: DesktopTrack, from: List<DesktopTrack>) -> Unit,
    val togglePlay: () -> Unit,
    val setShuffle: (Boolean) -> Unit,
    val playNext: (List<DesktopTrack>) -> Unit,
    val addToQueue: (List<DesktopTrack>) -> Unit,
    val toggleFavourite: (DesktopTrack) -> Unit,
    val addToPlaylist: (List<DesktopTrack>) -> Unit,
    val edit: (DesktopTrack) -> Unit,
    val reveal: (File) -> Unit,
    val openArtist: (key: String) -> Unit,
    val openAlbum: (key: String) -> Unit,
    val back: () -> Unit,
    /** The album's songs dragged into a new order, to keep. */
    val reorder: (List<DesktopTrack>) -> Unit = {},
    /** Back to the album's own order, by track number. */
    val resetOrder: () -> Unit = {}
)

/** What the page shows: read from the controller, or put together for a picture of it. */
class GroupView(
    val group: TrackGroup,
    val kind: CollectionKind,
    /** On an artist's page, their albums, newest first; on an album's, nothing. */
    val albums: List<TrackGroup>,
    /** On an album's page, who it's by, each with the key of their own page where they have one. */
    val credits: List<Pair<String, String?>>,
    val nowPlaying: DesktopTrack?,
    val playing: Boolean,
    /** Whether the queue is this page's songs, so the big button pauses rather than starts over. */
    val fromHere: Boolean,
    val shuffle: Boolean,
    val plays: Map<String, Int>,
    val favourites: Set<String>,
    /** Whether the album's songs were dragged into an order of their own. */
    val ownOrder: Boolean = false
)

/**
 * One album, or one artist, on a page of its own, as Spotify shows one: a
 * banner in the cover's colour with the cover, the name and what it holds;
 * a big play button; and every song in order, the one playing picked out.
 *
 * Back is the arrow at the top, Escape, or the mouse's back button. Once the
 * banner has scrolled away, the bar along the top keeps the name and a play
 * button in reach.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GroupPage(view: GroupView, actions: GroupActions) {
    val group = view.group
    val artistPage = view.kind == CollectionKind.ARTISTS
    // The order on screen: the album's, or the one being dragged into being.
    var songs by remember(group.key, group.tracks) { mutableStateOf(group.tracks) }
    val banner by animateColorAsState(bannerColour(group), tween(450))
    val list = rememberLazyListState()
    val wide = LocalPaneWidth.current >= 860.dp
    val roomy = LocalPaneWidth.current >= 640.dp

    // Something of this page is playing, from this page: the big button is its pause.
    val playingHere = view.fromHere && songs.any { it.file == view.nowPlaying?.file }
    val onBigButton = {
        if (playingHere) {
            actions.togglePlay()
        } else {
            actions.play(if (view.shuffle) songs.random() else songs.first(), songs)
        }
    }

    var heroHeight by remember { mutableStateOf(0) }
    val barPx = with(LocalDensity.current) { BAR_HEIGHT.roundToPx() }
    val barSolid by remember(barPx) {
        derivedStateOf {
            list.firstVisibleItemIndex > 0 || (heroHeight > 0 && list.firstVisibleItemScrollOffset > heroHeight - barPx)
        }
    }

    // Dragging a song up or down an album by its handle. Artists' pages list
    // songs from many albums, so only an album's own page can be reordered.
    val reorder = remember { Reorder() }
    val reorderable = !artistPage
    val density = LocalDensity.current
    val edgePx = with(density) { 56.dp.toPx() }

    /** Moves the dragged song past whichever song its middle is now over. */
    fun follow(delta: Float) {
        val key = reorder.key ?: return
        reorder.offset += delta
        val items = list.layoutInfo.visibleItemsInfo
        val me = items.firstOrNull { it.key == key } ?: return
        val at = songs.indexOfFirst { it.file.absolutePath == key }
        // The list hasn't been laid out since the last move: wait for it.
        if (at < 0 || me.index != SONGS_FROM + at) return
        val middle = me.offset + reorder.offset + me.size / 2f
        val over = items.firstOrNull { item ->
            item.key != key && item.index in SONGS_FROM until SONGS_FROM + songs.size &&
                middle >= item.offset && middle < item.offset + item.size
        } ?: return
        val to = over.index - SONGS_FROM
        // The list keeps its place by its first item; moving that one would scroll it along.
        val first = list.firstVisibleItemIndex
        if (first == me.index || first == over.index) list.requestScrollToItem(first, list.firstVisibleItemScrollOffset)
        songs = songs.toMutableList().apply { add(to, removeAt(at)) }
        reorder.offset += me.offset - over.offset
    }

    fun finish() {
        if (reorder.key == null) return
        reorder.key = null
        reorder.offset = 0f
        if (songs.map { it.file } != group.tracks.map { it.file }) actions.reorder(songs)
    }

    // Held near the top or the bottom, the list scrolls to take the song further.
    LaunchedEffect(reorder.key) {
        if (reorder.key == null) return@LaunchedEffect
        while (reorder.key != null) {
            withFrameNanos { }
            val key = reorder.key ?: break
            val info = list.layoutInfo
            val me = info.visibleItemsInfo.firstOrNull { it.key == key } ?: continue
            val top = me.offset + reorder.offset
            val speed = when {
                top < info.viewportStartOffset + barPx + edgePx * 0.5f -> -14f
                top + me.size > info.viewportEndOffset - edgePx * 0.5f -> 14f
                else -> 0f
            }
            if (speed != 0f) {
                val moved = list.scrollBy(speed)
                reorder.offset += moved
                follow(0f)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Press) { if (it.button == PointerButton.Back) actions.back() }
    ) {
        ScrollingList(list, Modifier.fillMaxSize()) { fill ->
            LazyColumn(state = list, modifier = fill) {
                item(key = "hero") {
                    Hero(view, banner, wide, actions, Modifier.onSizeChanged { heroHeight = it.height })
                }
                item(key = "actions") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(shade(banner).copy(alpha = 0.55f), Color.Transparent)))
                            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BigPlayButton(pause = playingHere && view.playing, size = 56.dp, onClick = onBigButton)
                        Spacer(Modifier.width(22.dp))
                        RoundIcon("Shuffle", Icons.Default.Shuffle, lit = view.shuffle) { actions.setShuffle(!view.shuffle) }
                        RoundIcon("Play next", Icons.AutoMirrored.Filled.PlaylistPlay) { actions.playNext(songs) }
                        RoundIcon("Add to queue", Icons.AutoMirrored.Filled.QueueMusic) { actions.addToQueue(songs) }
                        RoundIcon("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) { actions.addToPlaylist(songs) }
                        if (!artistPage) {
                            RoundIcon(SHOW_IN_FOLDER, Icons.Default.FolderOpen) { actions.reveal(songs.first().file) }
                        }
                        if (view.ownOrder) {
                            RoundIcon("Back to the album's own order", Icons.Default.FormatListNumbered) { actions.resetOrder() }
                        }
                    }
                }
                if (artistPage && view.albums.isNotEmpty()) {
                    item(key = "albums-title") { Heading("Albums") }
                    item(key = "albums") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(view.albums, key = { it.key }) { album ->
                                AlbumTile(album) { actions.openAlbum(album.key) }
                            }
                        }
                    }
                    item(key = "songs-title") { Heading("Songs") }
                }
                item(key = "columns") { ColumnTitles(artistPage, roomy, reorderable) }
                itemsIndexed(songs, key = { _, t -> t.file.absolutePath }) { index, track ->
                    val current = track.file == view.nowPlaying?.file
                    val key = track.file.absolutePath
                    val dragging = reorder.key == key
                    Box(
                        Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) reorder.offset else 0f }
                    ) {
                        SongRow(
                            position = index + 1,
                            track = track,
                            view = view,
                            roomy = roomy,
                            current = current,
                            dragging = dragging,
                            handle = if (!reorderable) {
                                null
                            } else {
                                Modifier.pointerInput(key) {
                                    detectDragGestures(
                                        onDragStart = {
                                            reorder.key = key
                                            reorder.offset = 0f
                                        },
                                        onDragEnd = { finish() },
                                        onDragCancel = { finish() },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            follow(amount.y)
                                        }
                                    )
                                }
                            },
                            actions = actions,
                            onPlay = { if (current) actions.togglePlay() else actions.play(track, songs) }
                        )
                    }
                }
                item(key = "end") { Spacer(Modifier.height(40.dp)) }
            }
        }
        TopBar(
            title = group.name,
            banner = banner,
            solid = barSolid,
            pause = playingHere && view.playing,
            onPlay = onBigButton,
            onBack = actions.back
        )
    }
}

/** On an album's page the songs come after the banner, the buttons and the column titles. */
private const val SONGS_FROM = 3

/** The song being dragged up or down by its handle, and how far it has gone from where it's laid out. */
private class Reorder {
    var key by mutableStateOf<String?>(null)
    var offset by mutableStateOf(0f)
}

/** How far down the bar along the top reaches: where the banner starts to slide under it. */
private val BAR_HEIGHT = 64.dp

/** For a cover that is all greys: a neutral the white writing still reads on. */
private val NEUTRAL_BANNER = Color(0xFF44444E)

private val SHOW_IN_FOLDER = if (Platform.isWindows) "Show in Explorer" else "Show in folder"

/** The banner's colour a step darker: its lower edge, and the bar along the top. */
private fun shade(colour: Color): Color = lerp(colour, Color.Black, 0.35f)

@Composable
private fun Hero(view: GroupView, banner: Color, wide: Boolean, actions: GroupActions, modifier: Modifier) {
    val group = view.group
    val artistPage = view.kind == CollectionKind.ARTISTS
    val cover = if (wide) 216.dp else 152.dp
    val coverShape = if (artistPage) CircleShape else RoundedCornerShape(6.dp)

    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(banner, shade(banner))))
            .padding(start = 24.dp, end = 24.dp, top = 68.dp, bottom = 24.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        LargeArtwork(
            group.tracks.first(),
            cover,
            Modifier.shadow(22.dp, coverShape, clip = false).clip(coverShape)
        )
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (artistPage) "Artist" else releaseKind(group.name),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            val size = titleSize(group.name, wide)
            Text(
                group.name,
                color = Color.White,
                fontSize = size,
                lineHeight = size * 1.08f,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dim = Color.White.copy(alpha = 0.72f)
                if (!artistPage) {
                    val credits = view.credits.ifEmpty { listOf("Unknown artist" to null) }
                    credits.forEachIndexed { index, (name, key) ->
                        if (index > 0) Text(", ", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        CreditLink(name, key?.let { { actions.openArtist(it) } })
                    }
                    yearOf(group)?.let { year ->
                        Text(" • $year", style = MaterialTheme.typography.bodyMedium, color = dim, maxLines = 1)
                    }
                } else if (view.albums.isNotEmpty()) {
                    Text(
                        count(view.albums.size, "album") + " • ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = dim,
                        maxLines = 1
                    )
                }
                Text(
                    (if (artistPage) "" else " • ") + count(group.tracks.size, "song") + ", " +
                        lengthInWords(group.tracks.sumOf { it.durationMs }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A name in the banner that goes to that artist's page, underlined under the mouse. */
@Composable
private fun CreditLink(name: String, onClick: (() -> Unit)?) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        name,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textDecoration = if (hovered && onClick != null) TextDecoration.Underline else null,
        maxLines = 1,
        modifier = if (onClick == null) {
            Modifier
        } else {
            Modifier.hoverable(interaction).clickable(interactionSource = interaction, indication = null, onClick = onClick)
        }
    )
}

/** The bar along the top: back, and once the banner is gone, the name and a play button. */
@Composable
private fun TopBar(title: String, banner: Color, solid: Boolean, pause: Boolean, onPlay: () -> Unit, onBack: () -> Unit) {
    val fill by animateColorAsState(if (solid) shade(banner) else Color.Transparent, tween(180))
    Row(
        Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .background(fill)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = if (hovered) 0.6f else 0.4f))
                .hoverable(interaction)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(18.dp), tint = Color.White)
        }
        AnimatedVisibility(solid, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(14.dp))
                BigPlayButton(pause = pause, size = 38.dp, onClick = onPlay)
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BigPlayButton(pause: Boolean, size: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val grow = if (hovered) 1.05f else 1f
    Box(
        Modifier
            .size(size * grow)
            .shadow(if (hovered) 10.dp else 6.dp, CircleShape)
            .clip(CircleShape)
            .background(Palette.Accent)
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (pause) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (pause) "Pause" else "Play",
            Modifier.size(size * 0.5f),
            tint = Palette.OnAccent
        )
    }
}

@Composable
private fun Tip(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = Palette.Text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.Raised)
            .border(1.dp, Palette.Line, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** Where the handle to drag a song up or down sits, at the start of each row on an album's page. */
private val HANDLE_WIDTH = 22.dp

/** One of the quieter buttons beside the big one; [lit] in the accent when it is switched on. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundIcon(label: String, icon: ImageVector, lit: Boolean = false, onClick: () -> Unit) {
    TooltipArea(tooltip = { Tip(label) }) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Box(
            Modifier
                .padding(end = 6.dp)
                .size(42.dp)
                .clip(CircleShape)
                .hoverable(interaction)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                label,
                Modifier.size(26.dp),
                tint = when {
                    lit -> Palette.Accent
                    hovered -> Palette.Text
                    else -> Palette.TextDim
                }
            )
            if (lit) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Palette.Accent)
                )
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Palette.Text,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 10.dp)
    )
}

/** One of an artist's albums, going to its own page. */
@Composable
private fun AlbumTile(album: TrackGroup, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier
            .width(166.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) Palette.Hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onOpen)
            .padding(8.dp)
    ) {
        Artwork(album.tracks.first(), 150.dp, corner = 6.dp)
        Spacer(Modifier.height(9.dp))
        Text(
            album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Palette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOfNotNull(yearOf(album)?.toString(), count(album.tracks.size, "song")).joinToString(" • "),
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextDim,
            maxLines = 1
        )
    }
}

@Composable
private fun ColumnTitles(artistPage: Boolean, roomy: Boolean, reorderable: Boolean) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (reorderable) Spacer(Modifier.width(HANDLE_WIDTH))
            ColumnTitle("#", Modifier.width(40.dp))
            if (artistPage) Spacer(Modifier.width(52.dp))
            ColumnTitle("Title", Modifier.weight(1f))
            if (artistPage && roomy) ColumnTitle("Album", Modifier.weight(0.8f))
            if (roomy) ColumnTitle("Plays", Modifier.width(80.dp))
            Spacer(Modifier.width(34.dp))
            Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Default.Schedule, "Length", Modifier.size(15.dp), tint = Palette.TextFaint)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ColumnTitle(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextFaint,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    position: Int,
    track: DesktopTrack,
    view: GroupView,
    roomy: Boolean,
    current: Boolean,
    /** Being dragged up or down right now. */
    dragging: Boolean,
    /** The handle's drag, on an album's page; null where songs can't be reordered. */
    handle: Modifier?,
    actions: GroupActions,
    onPlay: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val artistPage = view.kind == CollectionKind.ARTISTS
    val favourite = track.file.absolutePath in view.favourites
    val album = if (artistPage) view.albums.firstOrNull { a -> a.tracks.any { it.file == track.file } } else null
    val playingThis = current && view.playing

    ContextMenuArea(
        items = {
            listOfNotNull(
                ContextMenuItem(if (playingThis) "Pause" else "Play") { onPlay() },
                ContextMenuItem("Play next") { actions.playNext(listOf(track)) },
                ContextMenuItem("Add to queue") { actions.addToQueue(listOf(track)) },
                ContextMenuItem(if (favourite) "Remove from favourites" else "Add to favourites") {
                    actions.toggleFavourite(track)
                },
                ContextMenuItem("Add to playlist...") { actions.addToPlaylist(listOf(track)) },
                ContextMenuItem("Edit details...") { actions.edit(track) },
                album?.let { ContextMenuItem("Go to album") { actions.openAlbum(it.key) } },
                ContextMenuItem(SHOW_IN_FOLDER) { actions.reveal(track.file) }
            )
        }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .then(if (dragging) Modifier.shadow(10.dp, RoundedCornerShape(6.dp)) else Modifier)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        dragging -> Palette.Raised
                        hovered -> Palette.Hover
                        current -> Palette.Selected
                        else -> Color.Transparent
                    }
                )
                .hoverable(interaction)
                .testTag("song:" + track.file.absolutePath)
                // Dragged by the row itself, the song goes out of Lucense as its file.
                .dragOutFiles { listOf(track.file) }
                .clickable(onClick = onPlay)
                .padding(horizontal = 8.dp, vertical = if (artistPage) 6.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (handle != null) {
                TooltipArea(tooltip = { Tip("Drag to move it up or down") }) {
                    Box(
                        Modifier.width(HANDLE_WIDTH).height(30.dp).testTag("handle:" + track.file.absolutePath).then(handle),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (hovered || dragging) {
                            Icon(Icons.Default.DragIndicator, null, Modifier.size(18.dp), tint = Palette.TextDim)
                        }
                    }
                }
            }
            Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) {
                when {
                    hovered -> Icon(
                        if (playingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null,
                        Modifier.size(17.dp),
                        tint = Palette.Text
                    )
                    playingThis -> EqualizerBars(Palette.Accent)
                    else -> Text(
                        position.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (current) Palette.Accent else Palette.TextDim
                    )
                }
            }
            if (artistPage) {
                Artwork(track, 40.dp, corner = 4.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (current) Palette.Accent else Palette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (artistPage && roomy) {
                Text(
                    track.displayAlbum,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.8f).padding(end = 12.dp)
                )
            }
            if (roomy) {
                val plays = view.plays[track.file.absolutePath] ?: 0
                Text(
                    if (plays > 0) String.format(Locale.getDefault(), "%,d", plays) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    modifier = Modifier.width(80.dp)
                )
            }
            Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                if (hovered || favourite) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { actions.toggleFavourite(track) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            if (favourite) "Remove from favourites" else "Add to favourites",
                            Modifier.size(16.dp),
                            tint = if (favourite) Palette.Accent else Palette.TextDim
                        )
                    }
                }
            }
            Text(
                track.durationMs.asDuration(),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
                textAlign = TextAlign.End,
                modifier = Modifier.width(52.dp)
            )
        }
    }
}

/** Three bars rising and falling in place of the number, while that song plays. */
@Composable
private fun EqualizerBars(colour: Color) {
    val motion = rememberInfiniteTransition()
    val heights = listOf(430, 610, 350).mapIndexed { index, millis ->
        motion.animateFloat(
            initialValue = if (index == 1) 0.9f else 0.3f,
            targetValue = if (index == 1) 0.25f else 1f,
            animationSpec = infiniteRepeatable(tween(millis), RepeatMode.Reverse)
        )
    }
    Row(
        Modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { height ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(height.value)
                    .clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
                    .background(colour)
            )
        }
    }
}

/**
 * The cover at the size the banner shows it. The cache keeps covers small,
 * enough for a card, so the banner decodes its one cover again at a size
 * that stays sharp; the cached one stands in until it has.
 */
@Composable
private fun LargeArtwork(track: DesktopTrack, size: Dp, modifier: Modifier) {
    var large by remember(track.file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(track.file.absolutePath, Covers.revision) {
        large = withContext(Dispatchers.IO) {
            runCatching { Covers.bytes(track)?.let { Thumbnails.decodeScaled(it, LARGE_EDGE) } }.getOrNull()
        }
    }
    val shown = large
    if (shown == null) {
        Artwork(track, size, corner = 0.dp, modifier = modifier)
    } else {
        Image(
            shown,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size)
        )
    }
}

/** Twice the banner's cover at 1x, so it is sharp on a screen scaled up to 200%. */
private const val LARGE_EDGE = 480

/** Banner colours worked out already, so a page opened again is its colour from the first frame. */
private val bannerColours = object : LinkedHashMap<String, Color>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?) = size > 300
}

/** The banner's colour: a deep shade of the cover's own, worked out from the cover once it is in. */
@Composable
private fun bannerColour(group: TrackGroup): Color {
    val track = group.tracks.first()
    var colour by remember(group.key) { mutableStateOf(bannerColours[group.key]) }
    LaunchedEffect(group.key, Covers.revision) {
        val art = Covers.cached(track) ?: if (Covers.knownMissing(track)) null else Covers.load(track)
        val found = art?.takeIf { it.width > 0 && it.height > 0 }?.let { bitmap ->
            withContext(Dispatchers.Default) {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.readPixels(pixels)
                CoverColour.bannerFrom(pixels)
            }
        }?.let(::Color) ?: NEUTRAL_BANNER
        bannerColours[group.key] = found
        colour = found
    }
    return colour ?: NEUTRAL_BANNER
}

/** The year most of the songs give, if any do. */
private fun yearOf(group: TrackGroup): Int? =
    group.tracks.mapNotNull { it.year?.takeIf { year -> year > 0 } }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

/**
 * "Single" or "EP" where the name says so - "Name - EP", "Name (Single)" -
 * and otherwise "Album". Not guessed from how many songs there are: an album
 * only partly in the library would be called a single.
 */
internal fun releaseKind(name: String): String {
    val plain = name.trim().lowercase(Locale.ROOT)
    return when {
        plain.endsWith("- single") || plain.endsWith("(single)") || plain.endsWith("[single]") -> "Single"
        plain.endsWith("- ep") || plain.endsWith("(ep)") || plain.endsWith("[ep]") || plain.endsWith(" ep") -> "EP"
        else -> "Album"
    }
}

/** A length as Spotify writes it under a title: "24 min 10 sec", "1 hr 12 min". */
internal fun lengthInWords(ms: Long): String {
    val seconds = ms / 1000
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    return when {
        hours > 0 -> "$hours hr $minutes min"
        minutes > 0 -> "$minutes min ${seconds % 60} sec"
        else -> "${seconds % 60} sec"
    }
}

/** Big for a short name, smaller as it gets longer, so most fit on a line or two. */
private fun titleSize(name: String, wide: Boolean): TextUnit = when {
    name.length <= 14 -> if (wide) 72.sp else 46.sp
    name.length <= 26 -> if (wide) 54.sp else 36.sp
    name.length <= 44 -> if (wide) 40.sp else 30.sp
    else -> if (wide) 32.sp else 26.sp
}

private fun count(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"
