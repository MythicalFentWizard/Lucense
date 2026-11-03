package com.exo.musicplayer.desktop.ui

import androidx.compose.material.icons.filled.Search
import com.exo.musicplayer.data.NameSearch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.desktop.data.CollectionKind
import com.exo.musicplayer.desktop.data.DesktopController
import com.exo.musicplayer.desktop.data.GroupSort
import com.exo.musicplayer.desktop.data.MergePlan
import com.exo.musicplayer.desktop.data.TrackGroup
import com.exo.musicplayer.desktop.data.TrackGroups
import com.exo.musicplayer.desktop.library.DesktopTrack
import com.exo.musicplayer.desktop.system.Explorer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Albums or artists as cards.
 *
 * A plain click opens the album or artist on a page of its own, as Spotify
 * does; the play button that shows under the mouse plays it straight away.
 * Ctrl+click picks a card and Shift+click a run of them, as in the library.
 * Two or more picked can be merged into one; "Merge
 * duplicates" finds the ones spelled more than one way; "Clear duplicates"
 * looks for songs that are there twice - in the picked cards, or in all of
 * them when none are picked.
 */
@Composable
fun CollectionScreen(
    controller: DesktopController,
    kind: CollectionKind,
    onMerge: () -> Unit,
    onClearDuplicates: (List<DesktopTrack>) -> Unit,
    /** Goes to an album's or an artist's page, on whichever of the two pages it belongs. */
    onOpenGroup: (CollectionKind, String) -> Unit,
    onAddToPlaylist: (List<DesktopTrack>) -> Unit
) {
    val sort = if (kind == CollectionKind.ALBUMS) controller.albumSort else controller.artistSort
    val orders = controller.albumOrders
    val all = remember(controller.tracks, kind, sort, orders) {
        TrackGroups.gather(kind, controller.tracks, sort).let { groups ->
            // Albums dragged into an order of their own play in it, from the card too.
            if (kind != CollectionKind.ALBUMS || orders.isEmpty()) {
                groups
            } else {
                groups.map { group -> orders[group.key]?.let { TrackGroups.inOrder(group, it) } ?: group }
            }
        }
    }

    // One open on its own page. Gone from the library - merged, retagged, deleted - the cards come back.
    val openKey = controller.openGroupKey(kind)
    val open = openKey?.let { key -> all.firstOrNull { it.key == key } }
    if (openKey != null && open == null) {
        LaunchedEffect(openKey) { controller.openGroup(kind, null) }
    }
    if (open != null) {
        OpenGroup(controller, kind, open, onOpenGroup, onAddToPlaylist)
        return
    }
    val query = controller.collectionQuery(kind)
    // An album by its name or who it's by; an artist by name. What is shown is what picking and merging act on.
    val groups = remember(all, query) { if (query.isBlank()) all else all.filter { NameSearch.matches(query, it.name, it.artist) } }
    val picked = if (controller.pickedKind == kind) controller.pickedGroups else emptySet()
    val grid = remember(kind) { LazyGridState() }

    // A pick belongs to the page it was made on.
    DisposableEffect(kind) { onDispose { controller.clearPicked() } }

    if (groups.isEmpty() && all.isNotEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Default.Search,
                title = "No ${kind.plural} match \"$query\"",
                body = "Every word typed is looked for in the name and in who it's by."
            )
        }
        return
    }

    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = if (kind == CollectionKind.ALBUMS) Icons.Default.Album else Icons.Default.Person,
                title = "No ${kind.plural}",
                body = "Nothing in the library has ${if (kind == CollectionKind.ALBUMS) "an album" else "an artist"} " +
                    "tag yet.\nRun Names & tags from the library toolbar to fill them in."
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // As in the library: the sort chips scroll, the buttons keep their width.
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SegmentedRow(
                    options = GroupSort.choices(kind),
                    selected = sort,
                    label = { it.label },
                    onSelect = {
                        if (kind == CollectionKind.ALBUMS) controller.albumSort = it else controller.artistSort = it
                    }
                )
                Spacer(Modifier.width(12.dp))
                if (query.isNotBlank()) {
                    Text(
                        "${groups.size} of ${all.size} ${kind.plural}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextDim
                    )
                    Spacer(Modifier.width(12.dp))
                }
                if (picked.isNotEmpty()) {
                    Text(
                        "${picked.size} picked",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextDim
                    )
                    Spacer(Modifier.width(8.dp))
                    GhostButton("Unpick") { controller.clearPicked() }
                    Spacer(Modifier.width(10.dp))
                }
            }
            if (picked.size >= 2) {
                AccentButton(
                    "Merge ${picked.size}",
                    enabled = !controller.merging,
                    icon = Icons.AutoMirrored.Filled.CallMerge
                ) {
                    controller.planPickedMerge(kind, groups)
                    onMerge()
                }
                Spacer(Modifier.width(8.dp))
            }
            GhostButton("Merge duplicates", enabled = !controller.merging, icon = Icons.AutoMirrored.Filled.CallMerge) {
                controller.planDuplicateMerges(kind, groups)
                onMerge()
            }
            Spacer(Modifier.width(8.dp))
            GhostButton("Clear duplicates", icon = Icons.Default.ContentCopy) {
                onClearDuplicates(
                    groups.filter { picked.isEmpty() || it.key in picked }
                        .flatMap { it.tracks }
                        .distinctBy { it.file.absolutePath }
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            state = grid,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
        ) {
            items(groups, key = { it.key }) { group ->
                GroupCard(
                    group = group,
                    kind = kind,
                    picked = group.key in picked,
                    onPick = { controller.togglePicked(kind, group) },
                    onPickRun = { controller.extendPicked(kind, group, groups) },
                    onOpen = { controller.openGroup(kind, group.key) },
                    onPlay = { controller.play(group.tracks.first(), group.tracks) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(
    group: TrackGroup,
    kind: CollectionKind,
    picked: Boolean,
    onPick: () -> Unit,
    onPickRun: () -> Unit,
    onOpen: () -> Unit,
    onPlay: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val artist = kind == CollectionKind.ARTISTS

    Column(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                when {
                    picked -> Palette.Accent.copy(alpha = 0.20f)
                    hovered -> Palette.Hover
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            // Read from the click itself, as the library rows do.
            .onClick(keyboardModifiers = { isCtrlPressed }, onClick = onPick)
            .onClick(keyboardModifiers = { isShiftPressed && !isCtrlPressed }, onClick = onPickRun)
            .onClick(keyboardModifiers = { !isCtrlPressed && !isShiftPressed }, onClick = onOpen)
            .padding(8.dp),
        horizontalAlignment = if (artist) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Box {
            Artwork(
                group.tracks.first(),
                144.dp,
                corner = if (artist) 200.dp else 7.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            if (picked) {
                Box(
                    Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Palette.Accent)
                        .align(Alignment.TopStart),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, "Picked", Modifier.size(15.dp), tint = Palette.OnAccent)
                }
            }
            if (hovered) {
                // Plays without opening the page; the card itself opens it.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Palette.Accent)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, "Play", Modifier.size(20.dp), tint = Palette.OnAccent)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            group.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val songs = count(group.tracks.size, "song")
        Text(
            if (artist) {
                group.albumCount.takeIf { it > 0 }?.let { "${count(it, "album")} · $songs" } ?: songs
            } else {
                "${group.artist.ifEmpty { "Unknown artist" }} · $songs"
            },
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** An album's or an artist's page, fed from the controller. */
@Composable
private fun OpenGroup(
    controller: DesktopController,
    kind: CollectionKind,
    group: TrackGroup,
    onOpenGroup: (CollectionKind, String) -> Unit,
    onAddToPlaylist: (List<DesktopTrack>) -> Unit
) {
    // The song and whether it plays, without the position: that changes many times a second.
    val playback by remember(controller) {
        controller.engine.status.map { it.track to it.playing }.distinctUntilChanged()
    }.collectAsState(controller.engine.status.value.let { it.track to it.playing })

    DisposableEffect(kind) {
        controller.groupPageShown = kind
        onDispose { if (controller.groupPageShown == kind) controller.groupPageShown = null }
    }

    // An artist's albums, whole, newest first; an album's artists, each with a page where they have one.
    val albums = remember(controller.tracks, group, kind) {
        if (kind != CollectionKind.ARTISTS) {
            emptyList()
        } else {
            val files = group.tracks.mapTo(HashSet()) { it.file }
            TrackGroups.albums(controller.tracks)
                .filter { album -> album.tracks.any { it.file in files } }
                .sortedByDescending { album -> album.tracks.maxOf { it.year ?: 0 } }
        }
    }
    val credits = remember(controller.tracks, group, kind) {
        if (kind != CollectionKind.ALBUMS) {
            emptyList()
        } else {
            val artists = TrackGroups.artists(controller.tracks).mapTo(HashSet()) { it.key }
            TrackGroups.splitArtists(group.artist).map { name ->
                name to ("artist:" + TrackGroups.caseKey(name)).takeIf { it in artists }
            }
        }
    }

    GroupPage(
        GroupView(
            group = group,
            kind = kind,
            albums = albums,
            credits = credits,
            nowPlaying = playback.first,
            playing = playback.second,
            fromHere = controller.isQueue(group.tracks),
            shuffle = controller.shuffle,
            plays = controller.playCounts,
            favourites = controller.favourites,
            ownOrder = kind == CollectionKind.ALBUMS && group.key in controller.albumOrders
        ),
        GroupActions(
            play = { track, from -> controller.play(track, from) },
            togglePlay = { controller.togglePlay() },
            setShuffle = { controller.shuffle = it },
            playNext = { controller.playNext(it) },
            addToQueue = { controller.addToQueue(it) },
            toggleFavourite = { controller.toggleFavourite(it) },
            addToPlaylist = onAddToPlaylist,
            edit = { controller.editTarget = it },
            reveal = { Explorer.reveal(it) },
            openArtist = { onOpenGroup(CollectionKind.ARTISTS, it) },
            openAlbum = { onOpenGroup(CollectionKind.ALBUMS, it) },
            back = { controller.openGroup(kind, null) },
            reorder = { controller.reorderAlbum(group, it) },
            resetOrder = { controller.resetAlbumOrder(group) }
        )
    )
}

/** Reviews albums or artists about to be merged, and merges them. */
@Composable
fun MergeDialog(controller: DesktopController, onDismiss: () -> Unit) {
    val plans = controller.mergePlans
    if (controller.mergeFromPicked && plans.size == 1) {
        PickedMerge(controller, plans.first(), onDismiss)
    } else {
        DuplicateMerges(controller, controller.mergeKind, plans, onDismiss)
    }
}

@Composable
private fun PickedMerge(controller: DesktopController, plan: MergePlan, onDismiss: () -> Unit) {
    val albums = plan.kind == CollectionKind.ALBUMS
    var name by remember(plan) { mutableStateOf(plan.name) }
    var artist by remember(plan) { mutableStateOf(plan.artist) }
    val songs = plan.groups.sumOf { it.tracks.size }

    ScrimDialog(
        title = "Merge ${plan.groups.size} ${plan.kind.plural}",
        subtitle = "${count(songs, "song")} will be retagged",
        onDismiss = onDismiss
    ) {
        Hint(
            "Pick the name to keep, or type your own. It is written into every song's tags, " +
                "so the ${plan.kind.plural} stay merged in any player."
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 260.dp)) {
            items(plan.groups, key = { it.key }) { group ->
                ChoiceRow(
                    label = group.name,
                    note = (if (albums) "${group.artist.ifEmpty { "Unknown artist" }} · " else "") +
                        count(group.tracks.size, "song"),
                    chosen = TrackGroups.caseKey(group.name) == TrackGroups.caseKey(name) &&
                        (!albums || TrackGroups.caseKey(group.artist) == TrackGroups.caseKey(artist))
                ) {
                    name = group.name
                    if (albums) artist = group.artist
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextInput(
            value = name,
            onValueChange = { name = it },
            placeholder = if (albums) "Album name" else "Artist name",
            modifier = Modifier.fillMaxWidth()
        )
        if (albums) {
            Spacer(Modifier.height(8.dp))
            TextInput(
                value = artist,
                onValueChange = { artist = it },
                placeholder = "Album artist",
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Changes the files' tags",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
                modifier = Modifier.weight(1f)
            )
            GhostButton("Cancel", onClick = onDismiss)
            Spacer(Modifier.width(8.dp))
            AccentButton("Merge", enabled = name.isNotBlank() && !controller.merging) {
                controller.merge(listOf(plan.copy(name = name.trim(), artist = artist.trim())))
                onDismiss()
            }
        }
    }
}

@Composable
private fun DuplicateMerges(
    controller: DesktopController,
    kind: CollectionKind,
    plans: List<MergePlan>,
    onDismiss: () -> Unit
) {
    var excluded by remember(plans) { mutableStateOf(setOf<Int>()) }
    val chosen = plans.filterIndexed { index, _ -> index !in excluded }
    val songs = chosen.sumOf { plan -> plan.groups.sumOf { it.tracks.size } }

    ScrimDialog(
        title = "Merge duplicate ${kind.plural}",
        subtitle = if (plans.isEmpty()) {
            "Nothing to merge"
        } else {
            "${count(plans.size, "set")} · ${count(plans.sumOf { plan -> plan.groups.sumOf { it.tracks.size } }, "song")}"
        },
        onDismiss = onDismiss
    ) {
        if (plans.isEmpty()) {
            Hint(
                if (kind == CollectionKind.ALBUMS) {
                    "No two albums look like the same one: the same name give or take case, accents, " +
                        "punctuation or a \"Deluxe\" or \"- Single\", with an artist in common. " +
                        "To merge others, Ctrl+click them and press Merge."
                } else {
                    "No two artists look like the same one: the same name give or take case, accents " +
                        "and punctuation. To merge ones spelled differently, Ctrl+click them and press Merge."
                }
            )
            Spacer(Modifier.height(16.dp))
            Row {
                Spacer(Modifier.weight(1f))
                AccentButton("Close", onClick = onDismiss)
            }
        } else {
            Hint(
                "Each set is one ${kind.singular} spelled more than one way. The spelling with the most " +
                    "songs is kept and written into the others' tags. Untick a set to leave it alone."
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 360.dp)) {
                itemsIndexed(plans) { index, plan ->
                    MergeSetRow(plan, included = index !in excluded) {
                        excluded = if (index in excluded) excluded - index else excluded + index
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${count(songs, "song")} will be retagged",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    modifier = Modifier.weight(1f)
                )
                GhostButton("Cancel", onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                AccentButton(
                    "Merge ${count(chosen.size, "set")}",
                    enabled = chosen.isNotEmpty() && !controller.merging
                ) {
                    controller.merge(chosen)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun MergeSetRow(plan: MergePlan, included: Boolean, onToggle: () -> Unit) {
    val albums = plan.kind == CollectionKind.ALBUMS
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Well)
            .padding(13.dp)
    ) {
        CheckRow(
            label = "Keep “${plan.name}”",
            checked = included,
            note = if (albums && plan.artist.isNotBlank()) plan.artist else null
        ) { onToggle() }
        Spacer(Modifier.height(6.dp))
        plan.groups.forEach { group ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(
                    group.name + if (albums) " — ${group.artist.ifEmpty { "Unknown artist" }}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    count(group.tracks.size, "song"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, note: String, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (chosen) Palette.Selected else Palette.Well)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (chosen) Palette.Accent else Palette.TextFaint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (chosen) Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Accent))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun count(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"
