package com.exo.musicplayer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.discover.DiscoverBrowser
import com.exo.musicplayer.data.library.AutoPlaylist
import com.exo.musicplayer.data.prefs.LibraryView
import com.exo.musicplayer.share.ShareTracks
import com.exo.musicplayer.ui.MainViewModel
import com.exo.musicplayer.ui.MoodState
import com.exo.musicplayer.ui.common.AddToPlaylistDialog
import com.exo.musicplayer.ui.common.BulkImportDialog
import com.exo.musicplayer.ui.common.ConfirmDeleteDialog
import com.exo.musicplayer.ui.common.DuplicatesDialog
import com.exo.musicplayer.ui.common.EditManyDialog
import com.exo.musicplayer.ui.common.EditTrackDialog
import com.exo.musicplayer.ui.common.FixDialog
import com.exo.musicplayer.ui.common.FixTagsDialog
import com.exo.musicplayer.ui.common.LibraryToolsSheet
import com.exo.musicplayer.ui.common.LocalTrackActions
import com.exo.musicplayer.ui.common.RateDialog
import com.exo.musicplayer.ui.common.TrackActions
import com.exo.musicplayer.ui.common.launchOrExplain
import com.exo.musicplayer.ui.discover.DiscoverActions
import com.exo.musicplayer.ui.discover.DiscoverScreen
import com.exo.musicplayer.ui.discover.DiscoverViewModel
import com.exo.musicplayer.ui.download.DownloadRequest
import com.exo.musicplayer.ui.download.DownloadScreen
import com.exo.musicplayer.ui.download.DownloadViewModel
import com.exo.musicplayer.ui.library.LibraryScreen
import com.exo.musicplayer.ui.library.SelectionActions
import com.exo.musicplayer.ui.moods.MoodsScreen
import com.exo.musicplayer.ui.player.FxControls
import com.exo.musicplayer.ui.player.MiniPlayer
import com.exo.musicplayer.ui.player.PlayerScreen
import com.exo.musicplayer.ui.player.QueueControls
import com.exo.musicplayer.ui.playlists.PlaylistActions
import com.exo.musicplayer.ui.playlists.PlaylistDetailScreen
import com.exo.musicplayer.ui.playlists.PlaylistsScreen
import com.exo.musicplayer.ui.recognition.RecognitionScreen
import com.exo.musicplayer.ui.recognition.RecognitionViewModel
import com.exo.musicplayer.ui.recognition.SearchMode
import com.exo.musicplayer.ui.settings.AppearanceScreen
import com.exo.musicplayer.ui.settings.SettingsActions
import com.exo.musicplayer.ui.settings.SettingsScreen
import com.exo.musicplayer.ui.settings.SettingsValues
import com.exo.musicplayer.ui.settings.SoundScreen
import com.exo.musicplayer.ui.stats.StatsScreen
import com.exo.musicplayer.ui.theme.LocalStarfieldActive
import com.exo.musicplayer.ui.theme.MusicPlayerTheme
import com.exo.musicplayer.ui.theme.ScreenBackdrop
import com.exo.musicplayer.ui.theme.ThemeSettings
import com.exo.musicplayer.ui.theme.ThemeState
import com.exo.musicplayer.util.counted

private enum class SettingsRoute { HOME, APPEARANCE, SOUND }

/** Opens the Telegram handle, falling back to the browser if Telegram isn't installed. */
private fun openTelegram(context: Context) = openUrl(context, "https://t.me/Eth4wn")

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Couldn't open $url", Toast.LENGTH_SHORT).show() }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    LIBRARY("Library", Icons.Default.LibraryMusic),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    DISCOVER("Discover", Icons.Default.Explore),
    IDENTIFY("Identify", Icons.Default.Sensors),
    MOODS("Moods", Icons.Default.Cloud),
    STATS("Stats", Icons.Default.BarChart)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val recognitionViewModel: RecognitionViewModel by viewModels()
    private val downloadViewModel: DownloadViewModel by viewModels()
    private val discoverViewModel: DiscoverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        musicApp.playback.connect()
        val openPlayerOnLaunch = intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)

        val themeSettings = musicApp.themeSettings
        setContent {
            val theme by themeSettings.state.collectAsStateWithLifecycle()
            // The Waves background draws the song playing; read only while it's chosen.
            val waves = theme.backdrop == com.exo.musicplayer.ui.theme.BackdropStyle.WAVES
            val current by viewModel.currentTrack.collectAsStateWithLifecycle()
            val song = remember(current?.id, waves) {
                if (waves) current?.let { com.exo.musicplayer.ui.theme.SongShape(it.id) } else null
            }
            LaunchedEffect(song) {
                val path = current?.filePath ?: return@LaunchedEffect
                song?.positionMs = { musicApp.playback.state.value.positionNow().toDouble() }
                song?.load(path)
            }
            MusicPlayerTheme(theme = theme, song = song) {
                NotificationPermissionGate()
                AppScaffold(
                    viewModel = viewModel,
                    recognition = recognitionViewModel,
                    download = downloadViewModel,
                    discover = discoverViewModel,
                    theme = theme,
                    themeSettings = themeSettings,
                    openPlayerOnLaunch = openPlayerOnLaunch
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}

/** Android 13+ hides the media notification without this, so ask once on first run. */
@Composable
private fun NotificationPermissionGate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

@Composable
private fun AppScaffold(
    viewModel: MainViewModel,
    recognition: RecognitionViewModel,
    download: DownloadViewModel,
    discover: DiscoverViewModel,
    theme: ThemeState,
    themeSettings: ThemeSettings,
    openPlayerOnLaunch: Boolean
) {
    val context = LocalContext.current
    var settingsRoute by rememberSaveable { mutableStateOf<SettingsRoute?>(null) }
    var showDownload by rememberSaveable { mutableStateOf(false) }
    // With stars painted behind everything, opaque containers would hide them.
    val starry = LocalStarfieldActive.current
    val containerColor = if (starry) Color.Transparent else MaterialTheme.colorScheme.background
    // Material derives content colour from the container, and there is no "on"
    // colour for Transparent - it resolves to unspecified and falls back to
    // black. With the starfield on, that turned every header dark-on-dark, so
    // the content colour is stated explicitly.
    val onContainerColor = MaterialTheme.colorScheme.onBackground

    // The tab comes back as it was left, like the PC's page does.
    val savedTab by viewModel.savedTab.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.entries.firstOrNull { it.name == savedTab } ?: Tab.LIBRARY) }
    LaunchedEffect(tab) { viewModel.rememberTab(tab.name) }

    // Set when Discover's "Search on YouTube" opened Identify, so Back goes
    // back to the page it was pressed on rather than to the library.
    var returnToDiscover by rememberSaveable { mutableStateOf(false) }

    var playerOpen by rememberSaveable { mutableStateOf(openPlayerOnLaunch) }
    var addingToPlaylist by remember { mutableStateOf<List<Track>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<List<Track>>(emptyList()) }
    var deleteSelection by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showDuplicates by remember { mutableStateOf(false) }
    var openAuto by rememberSaveable { mutableStateOf<AutoPlaylist?>(null) }

    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val albumSort by viewModel.albumSort.collectAsStateWithLifecycle()
    val artistSort by viewModel.artistSort.collectAsStateWithLifecycle()
    val openGroup by viewModel.openGroup.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val autoLists by viewModel.autoLists.collectAsStateWithLifecycle()
    val smartLists by viewModel.smartPlaylists.collectAsStateWithLifecycle()
    val smartCounts by viewModel.smartCounts.collectAsStateWithLifecycle()
    val genrePrompt by viewModel.genrePrompt.collectAsStateWithLifecycle()
    val promptCount by viewModel.promptCount.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val archiveState by viewModel.archive.collectAsStateWithLifecycle()
    val playlistImport by viewModel.importResult.collectAsStateWithLifecycle()
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentTrackId by viewModel.currentTrackId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val openPlaylist by viewModel.openPlaylist.collectAsStateWithLifecycle()
    val openPlaylistTracks by viewModel.openPlaylistTracks.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val mood by viewModel.mood.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val editTarget by viewModel.editTarget.collectAsStateWithLifecycle()
    val editMany by viewModel.editMany.collectAsStateWithLifecycle()
    val rateTarget by viewModel.rateTarget.collectAsStateWithLifecycle()
    val fixTargets by viewModel.fixTargets.collectAsStateWithLifecycle()
    val tagTarget by viewModel.tagTarget.collectAsStateWithLifecycle()
    val tagBusy by viewModel.tagBusy.collectAsStateWithLifecycle()
    val tagResult by viewModel.tagResult.collectAsStateWithLifecycle()
    val bulk by viewModel.bulk.collectAsStateWithLifecycle()
    val job by viewModel.job.collectAsStateWithLifecycle()
    val toolCounts by viewModel.toolCounts.collectAsStateWithLifecycle()
    val duplicates by viewModel.duplicates.collectAsStateWithLifecycle()
    val duplicateScanning by viewModel.duplicateScanning.collectAsStateWithLifecycle()
    val duplicateListening by viewModel.duplicateListening.collectAsStateWithLifecycle()
    val audioFx by viewModel.audioFx.collectAsStateWithLifecycle()
    val fxRemembered by viewModel.fxRemembered.collectAsStateWithLifecycle()
    val audioOutputs by viewModel.audioOutputs.collectAsStateWithLifecycle()
    val selectedOutputs by viewModel.selectedOutputs.collectAsStateWithLifecycle()
    val mirrorOutputs by viewModel.mirrorOutputs.collectAsStateWithLifecycle()
    val interruption by viewModel.interruption.collectAsStateWithLifecycle()
    val lyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val lyricLines by viewModel.currentLyricLines.collectAsStateWithLifecycle()
    val lyricsBusy by viewModel.lyricsBusy.collectAsStateWithLifecycle()
    val lyricsMessage by viewModel.lyricsMessage.collectAsStateWithLifecycle()
    val sleep by viewModel.sleep.collectAsStateWithLifecycle()
    val crossfade by viewModel.crossfadeSeconds.collectAsStateWithLifecycle()
    val levelling by viewModel.levelling.collectAsStateWithLifecycle()
    val sleepFade by viewModel.sleepFade.collectAsStateWithLifecycle()
    val lyricsTerm by viewModel.lyricsTerm.collectAsStateWithLifecycle()
    val lyricsPattern by viewModel.lyricsPattern.collectAsStateWithLifecycle()
    val coverProvider by viewModel.coverProvider.collectAsStateWithLifecycle()
    val downloadProxy by viewModel.downloadProxy.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val discoverStack by discover.browser.stack.collectAsStateWithLifecycle()
    val queueFailure by download.queueFailure.collectAsStateWithLifecycle()

    // Selection actions work on what is on screen: an open album's songs, the
    // search results while a query is active, or the whole library.
    val visibleTracks = openGroup?.tracks ?: if (query.isNotBlank()) results else tracks

    // ---- Pickers -------------------------------------------------------------

    // Audio files chosen here go through the same import pipeline as a share.
    val audioImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.importFromUris(uris) }

    val folderImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.importFolder(treeUri, treeUri.lastPathSegment?.substringAfterLast('/'))
        }
    }

    val identifyVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { recognition.identify(it, it.lastPathSegment) } }

    val identifyAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { recognition.identify(it, it.lastPathSegment) } }

    // Playlists and backups go through the storage picker, so the file lands
    // wherever the user can actually find and share it from.
    var pendingText by remember { mutableStateOf<String?>(null) }
    val textExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val text = pendingText
        pendingText = null
        if (uri != null && text != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
        }
    }
    fun readText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    val playlistImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::readText)?.let { viewModel.importPlaylist(it, "Imported playlist") } }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::readText)?.let { viewModel.restore(it) } }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.refreshMood(force = true) }

    LaunchedEffect(tab) { if (tab == Tab.MOODS) viewModel.refreshMood() }

    LaunchedEffect(notice) {
        notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
    }

    // A song queued from Discover fails out of sight of the Download page.
    LaunchedEffect(queueFailure) {
        queueFailure?.let {
            Toast.makeText(context, it.take(220), Toast.LENGTH_LONG).show()
            download.queueFailureShown()
        }
    }

    // Starting the library's own music stops a preview, rather than playing over it.
    LaunchedEffect(isPlaying) { if (isPlaying) recognition.stopPreview() }

    fun share(songs: List<Track>) {
        val intent = ShareTracks.intentFor(context, songs)
        if (intent == null) {
            Toast.makeText(context, "Those files are missing from storage.", Toast.LENGTH_SHORT).show()
        } else {
            context.startActivity(
                Intent.createChooser(intent, if (songs.size == 1) "Share song" else "Share ${songs.size} songs")
            )
        }
    }

    // One menu for a song, wherever it's listed.
    val trackActions = remember(viewModel) {
        TrackActions(
            playNext = { viewModel.playNext(it) },
            addToQueue = { viewModel.addToQueue(it) },
            addToPlaylist = { addingToPlaylist = listOf(it) },
            toggleFavorite = { viewModel.toggleFavorite(it) },
            rate = { viewModel.rate(it) },
            fix = { viewModel.fixOne(it) },
            identify = { viewModel.startFixTags(it) },
            edit = { viewModel.editTrack(it) },
            revert = { viewModel.revertTrackToFile(it) },
            share = { share(listOf(it)) },
            delete = { pendingDelete = listOf(it) }
        )
    }

    CompositionLocalProvider(LocalTrackActions provides trackActions) {
        Scaffold(
            // The bars below handle their own insets; letting Scaffold consume the
            // status bar too would double-pad every screen.
            contentWindowInsets = WindowInsets.statusBars,
            containerColor = containerColor,
            contentColor = onContainerColor,
            bottomBar = {
                Column {
                    MiniPlayer(
                        track = currentTrack,
                        state = state,
                        onExpand = { playerOpen = true },
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onNext = { viewModel.next() }
                    )
                    NavigationBar {
                        Tab.entries.forEach { entry ->
                            NavigationBarItem(
                                selected = tab == entry,
                                onClick = {
                                    // Tapping the tab you're on goes back to its top.
                                    if (tab == entry) {
                                        if (entry == Tab.PLAYLISTS) {
                                            viewModel.showPlaylist(null)
                                            openAuto = null
                                        }
                                        if (entry == Tab.LIBRARY) viewModel.openGroup(null)
                                        if (entry == Tab.DISCOVER) discover.browser.home()
                                    }
                                    returnToDiscover = false
                                    tab = entry
                                },
                                icon = { Icon(entry.icon, contentDescription = entry.label) },
                                // Six tabs on a narrow phone leave each about 60dp:
                                // at the usual size "Playlists" wrapped onto two lines.
                                label = {
                                    Text(
                                        entry.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 11.sp,
                                            letterSpacing = 0.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Surface(
                color = containerColor,
                contentColor = onContainerColor,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (tab) {
                    Tab.LIBRARY -> LibraryScreen(
                        view = view,
                        onViewChange = viewModel::setView,
                        tracks = tracks,
                        searchResults = results,
                        query = query,
                        recentSearches = recentSearches,
                        sort = sort,
                        albums = albums,
                        artists = artists,
                        albumSort = albumSort,
                        artistSort = artistSort,
                        openGroup = openGroup,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        selectedIds = selectedIds,
                        onQueryChange = viewModel::setQuery,
                        onRememberSearch = viewModel::rememberSearch,
                        onForgetSearches = viewModel::forgetSearches,
                        onSortChange = viewModel::setSort,
                        onAlbumSortChange = viewModel::setAlbumSort,
                        onArtistSortChange = viewModel::setArtistSort,
                        onOpenGroup = viewModel::openGroup,
                        onAddFiles = { audioImportLauncher.launchOrExplain(AUDIO_MIME_TYPES, context) },
                        onAddFolder = { folderImportLauncher.launchOrExplain(null, context) },
                        onOpenDownload = { showDownload = true },
                        onOpenTools = { showTools = true },
                        onOpenSettings = { settingsRoute = SettingsRoute.HOME },
                        onPlayFrom = { list, index -> viewModel.playFrom(list, index) },
                        onShuffle = { list -> if (list.isNotEmpty()) viewModel.playFrom(list.shuffled(), 0) },
                        onToggleSelect = { viewModel.toggleSelected(it.id) },
                        onClearSelection = viewModel::clearSelection,
                        onSelectAll = { viewModel.selectAll(it) },
                        selection = SelectionActions(
                            play = { viewModel.playSelected(visibleTracks) },
                            playNext = { viewModel.playNextSelected(visibleTracks) },
                            queue = { viewModel.queueSelected(visibleTracks) },
                            favourite = { viewModel.favoriteSelected(visibleTracks) },
                            playlist = { addingToPlaylist = viewModel.selectedTracks(visibleTracks) },
                            edit = { viewModel.editSelection(visibleTracks) },
                            fix = { viewModel.fixSelection(visibleTracks) },
                            revert = { viewModel.revertSelected(visibleTracks) },
                            share = {
                                share(viewModel.selectedTracks(visibleTracks))
                                viewModel.clearSelection()
                            },
                            zip = { viewModel.zipSelected(visibleTracks) },
                            delete = {
                                pendingDelete = viewModel.selectedTracks(visibleTracks)
                                deleteSelection = true
                            }
                        )
                    )

                    Tab.PLAYLISTS -> {
                        val open = openPlaylist
                        val auto = openAuto
                        when {
                            open != null -> PlaylistDetailScreen(
                                name = open.name,
                                note = null,
                                tracks = openPlaylistTracks,
                                currentTrackId = currentTrackId,
                                isPlaying = isPlaying,
                                onBack = { viewModel.showPlaylist(null) },
                                onPlayFrom = { index -> viewModel.playFrom(openPlaylistTracks, index) },
                                onShuffle = { viewModel.playFrom(openPlaylistTracks.shuffled(), 0) },
                                onRemove = { viewModel.removeFromPlaylist(open.id, it.id) }
                            )
                            auto != null -> {
                                val songs = autoLists[auto].orEmpty()
                                PlaylistDetailScreen(
                                    name = auto.label,
                                    note = auto.note,
                                    tracks = songs,
                                    currentTrackId = currentTrackId,
                                    isPlaying = isPlaying,
                                    onBack = { openAuto = null },
                                    onPlayFrom = { index -> viewModel.playFrom(songs, index) },
                                    onShuffle = { viewModel.playAuto(auto, shuffled = true) },
                                    onRemove = null
                                )
                            }
                            else -> PlaylistsScreen(
                                playlists = playlists,
                                autoLists = autoLists,
                                smartLists = smartLists,
                                smartCounts = smartCounts,
                                prompt = genrePrompt,
                                promptCount = promptCount,
                                genres = genres,
                                actions = PlaylistActions(
                                    openPlaylist = { viewModel.showPlaylist(it.id) },
                                    openAuto = { openAuto = it },
                                    playPlaylist = { viewModel.playPlaylist(it.id) },
                                    playAuto = { viewModel.playAuto(it) },
                                    create = { viewModel.createPlaylist(it) },
                                    rename = { playlist, name -> viewModel.renamePlaylist(playlist.id, name) },
                                    delete = { viewModel.deletePlaylist(it.id) },
                                    export = { playlist ->
                                        viewModel.exportPlaylist(playlist) { name, text ->
                                            pendingText = text
                                            textExportLauncher.launchOrExplain("$name.txt", context)
                                            true
                                        }
                                    },
                                    zip = { viewModel.zipPlaylist(it) },
                                    import = { playlistImportLauncher.launchOrExplain(arrayOf("text/*"), context) },
                                    setPrompt = viewModel::setGenrePrompt,
                                    playPrompt = viewModel::playPrompt,
                                    savePrompt = viewModel::savePromptAsPlaylist,
                                    keepAsRule = { name, rule -> viewModel.createSmartPlaylist(name, rule) },
                                    countRule = viewModel::countRule,
                                    playSmart = { viewModel.playSmartPlaylist(it) },
                                    deleteSmart = { viewModel.deleteSmartPlaylist(it.id) }
                                )
                            )
                        }
                    }

                    Tab.DISCOVER -> {
                        val preview by recognition.preview.state.collectAsStateWithLifecycle()
                        val queued by download.queued.collectAsStateWithLifecycle()
                        val dlState by download.state.collectAsStateWithLifecycle()
                        // A clip left playing would carry on over whichever page came next.
                        DisposableEffect(Unit) { onDispose { recognition.stopPreview() } }

                        // The clip is the thing to hear, so the library's music makes way for it.
                        fun hear(key: String, address: suspend (String) -> String?) {
                            if (recognition.preview.state.value.videoId != key && isPlaying) viewModel.togglePlayPause()
                            recognition.preview.toggle(key, address)
                        }

                        DiscoverScreen(
                            viewModel = discover.browser,
                            preview = preview,
                            queued = queued,
                            download = dlState,
                            actions = DiscoverActions(
                                onPreview = { track -> hear(DiscoverBrowser.previewKey(track)) { track.previewUrl } },
                                onSample = { artist -> hear(DiscoverBrowser.sampleKey(artist)) { discover.browser.sample(artist) } },
                                onSampleGenre = { genre ->
                                    hear(DiscoverBrowser.genreSampleKey(genre)) { discover.browser.sampleGenre(genre) }
                                },
                                onDownload = { track ->
                                    // By name, so the finder picks the real upload
                                    // and checks it against the song's length.
                                    download.enqueue(
                                        DiscoverBrowser.previewKey(track),
                                        DownloadRequest(null, track.artist, track.title, track.durationMs)
                                    )
                                },
                                onYouTube = { text ->
                                    recognition.setMode(SearchMode.YOUTUBE)
                                    recognition.setQuery(text)
                                    recognition.searchYouTube()
                                    returnToDiscover = true
                                    tab = Tab.IDENTIFY
                                },
                                onOpenDownloads = { showDownload = true }
                            )
                        )
                    }

                    Tab.IDENTIFY -> {
                        val stage by recognition.stage.collectAsStateWithLifecycle()
                        val result by recognition.result.collectAsStateWithLifecycle()
                        val rquery by recognition.query.collectAsStateWithLifecycle()
                        val label by recognition.sourceLabel.collectAsStateWithLifecycle()
                        val rmode by recognition.mode.collectAsStateWithLifecycle()
                        val rartist by recognition.artist.collectAsStateWithLifecycle()
                        val rtitle by recognition.title.collectAsStateWithLifecycle()
                        val youtubeResults by recognition.youtubeResults.collectAsStateWithLifecycle()
                        val youtubeStatus by recognition.youtubeStatus.collectAsStateWithLifecycle()
                        val preview by recognition.preview.state.collectAsStateWithLifecycle()

                        RecognitionScreen(
                            stage = stage,
                            result = result,
                            query = rquery,
                            sourceLabel = label,
                            onQueryChange = recognition::setQuery,
                            artist = rartist,
                            onArtistChange = recognition::setArtist,
                            title = rtitle,
                            onTitleChange = recognition::setTitle,
                            onSearch = recognition::runSearch,
                            mode = rmode,
                            onModeChange = recognition::setMode,
                            onPickVideo = { identifyVideoLauncher.launchOrExplain(VIDEO_MIME_TYPES, context) },
                            onPickAudio = { identifyAudioLauncher.launchOrExplain(AUDIO_MIME_TYPES, context) },
                            onFindInLibrary = { match ->
                                viewModel.setQuery(match.title)
                                viewModel.setView(LibraryView.SONGS)
                                tab = Tab.LIBRARY
                            },
                            onDownload = { match ->
                                // The match's own fields rather than a joined-up
                                // string, so the YouTube finder can check the title,
                                // the artist and the length separately.
                                download.downloadMatch(
                                    downloadUrl = match.downloadUrl,
                                    artist = match.artist,
                                    title = match.title,
                                    durationMs = match.durationMs
                                )
                                showDownload = true
                            },
                            youtubeResults = youtubeResults,
                            youtubeStatus = youtubeStatus,
                            preview = preview,
                            onPreviewYouTube = recognition::previewYouTube,
                            onDownloadYouTube = { video ->
                                // A real watch URL, so this takes the direct path
                                // rather than the search-by-name fallback.
                                download.downloadMatch(
                                    downloadUrl = video.watchUrl,
                                    artist = null,
                                    title = video.title,
                                    durationMs = null
                                )
                                // The preview would otherwise keep playing over the
                                // download screen it just opened.
                                recognition.stopPreview()
                                showDownload = true
                            },
                            onDownloadMany = { matches, videos ->
                                download.downloadAll(
                                    matches.map { DownloadRequest(it.downloadUrl, it.artist, it.title, it.durationMs) } +
                                        videos.map { DownloadRequest(it.watchUrl, null, it.title, null) }
                                )
                                recognition.stopPreview()
                                showDownload = true
                            },
                            onCopy = { match ->
                                copyToClipboard(context, match.display)
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Tab.MOODS -> MoodsScreen(
                        state = mood,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        onRequestPermission = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                        onRefresh = { viewModel.refreshMood(force = true) },
                        onPlayMix = viewModel::playMood,
                        onPlayFrom = { index ->
                            (mood as? MoodState.Ready)?.let { viewModel.playFrom(it.tracks, index) }
                        }
                    )

                    Tab.STATS -> {
                        val totalMs by viewModel.totalListenedMs.collectAsStateWithLifecycle()
                        val plays by viewModel.totalPlays.collectAsStateWithLifecycle()
                        val distinct by viewModel.distinctTracksPlayed.collectAsStateWithLifecycle()
                        val top by viewModel.topTracks.collectAsStateWithLifecycle()
                        val week by viewModel.topThisWeek.collectAsStateWithLifecycle()
                        val hours by viewModel.listeningByHour.collectAsStateWithLifecycle()
                        val weathers by viewModel.listeningByWeather.collectAsStateWithLifecycle()

                        StatsScreen(
                            totalListenedMs = totalMs,
                            totalPlays = plays,
                            distinctTracks = distinct,
                            topTracks = top,
                            topThisWeek = week,
                            favorites = favorites,
                            byHour = hours,
                            byWeather = weathers,
                            currentTrackId = currentTrackId,
                            onPlayTrack = { list, index -> viewModel.playFrom(list, index) }
                        )
                    }
                }
            }
        }

        // ---- Overlays ---------------------------------------------------------

        // Rendered above the Scaffold, so it applies system-bar insets itself.
        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            PlayerScreen(
                track = currentTrack,
                state = state,
                queue = queue,
                lyrics = lyrics,
                lyricLines = lyricLines,
                lyricsBusy = lyricsBusy,
                lyricsMessage = lyricsMessage,
                fx = audioFx,
                fxRemembered = fxRemembered,
                outputs = audioOutputs,
                selectedOutputs = selectedOutputs,
                mirrorOutputs = mirrorOutputs,
                sleep = sleep,
                onCollapse = { playerOpen = false },
                onTogglePlayPause = viewModel::togglePlayPause,
                onNext = { viewModel.next() },
                onPrevious = { viewModel.previous() },
                onSeek = viewModel::seekToFraction,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeat,
                onToggleFavorite = { currentTrack?.let { viewModel.toggleFavorite(it) } },
                onRate = { stars -> currentTrack?.let { viewModel.setRating(it, stars) } },
                onSleep = viewModel::sleepIn,
                onSleepEndOfSong = viewModel::sleepAtEndOfSong,
                onCancelSleep = viewModel::cancelSleep,
                onFetchLyrics = { viewModel.fetchLyrics(force = true) },
                onSaveLyrics = { viewModel.saveLyrics(it) },
                onDeleteLyrics = { viewModel.deleteLyrics() },
                fxControls = FxControls(
                    onPreset = viewModel::applyFxPreset,
                    onSpeed = viewModel::setFxSpeed,
                    onPitch = viewModel::setFxPitch,
                    onReverbEnabled = viewModel::setReverbEnabled,
                    onReverbRoom = viewModel::setReverbRoom,
                    onReverbAmount = viewModel::setReverbAmount,
                    onEqEnabled = viewModel::setEqEnabled,
                    onEqBand = viewModel::setEqBand,
                    onEqFlat = viewModel::flattenEq,
                    onReset = viewModel::resetFx,
                    onRemember = viewModel::rememberFxForSong,
                    onForget = viewModel::forgetFxForSong,
                    onPickOutput = viewModel::pickOutput,
                    onMirrorOutputs = viewModel::setMirrorOutputs
                ),
                queueControls = QueueControls(
                    onJump = viewModel::jumpToQueueIndex,
                    onMove = viewModel::moveInQueue,
                    onRemove = viewModel::removeFromQueue,
                    onClear = viewModel::clearQueue
                )
            )
        }

        AnimatedVisibility(
            visible = showDownload,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            val dlState by download.state.collectAsStateWithLifecycle()
            val dlUrl by download.url.collectAsStateWithLifecycle()
            val dlQuality by download.quality.collectAsStateWithLifecycle()
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (starry) ScreenBackdrop()
                    Box(Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                        DownloadScreen(
                            state = dlState,
                            url = dlUrl,
                            onUrlChange = download::setUrl,
                            onDownload = download::download,
                            quality = dlQuality,
                            onQualityChange = download::setQuality,
                            onCancel = download::cancel,
                            onUpdate = download::update,
                            onBack = { showDownload = false }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = settingsRoute != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (starry) ScreenBackdrop()
                    Box(Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                        when (settingsRoute) {
                            SettingsRoute.APPEARANCE -> AppearanceScreen(
                                theme = theme,
                                onBack = { settingsRoute = SettingsRoute.HOME },
                                onPalette = themeSettings::setPalette,
                                onMode = themeSettings::setMode,
                                onDynamic = themeSettings::setDynamicColor,
                                onBackdrop = themeSettings::setBackdrop,
                                onReactiveMode = themeSettings::setReactiveMode,
                                onWallpaper = themeSettings::setWallpaper,
                                onClearWallpaper = themeSettings::clearWallpaper,
                                onWallpaperDim = themeSettings::setWallpaperDim,
                                onLyricsActive = themeSettings::setLyricsActive,
                                onLyricsInactive = themeSettings::setLyricsInactive
                            )

                            SettingsRoute.SOUND -> SoundScreen(
                                interruption = interruption,
                                onInterruption = viewModel::setInterruptionBehavior,
                                onPlayDuringCalls = viewModel::setPlayDuringCalls,
                                onBack = { settingsRoute = SettingsRoute.HOME }
                            )

                            else -> SettingsScreen(
                                values = SettingsValues(
                                    crossfadeSeconds = crossfade,
                                    levelling = levelling,
                                    sleepFade = sleepFade,
                                    lyricsTerm = lyricsTerm,
                                    lyricsPattern = lyricsPattern,
                                    coverProvider = coverProvider,
                                    coverProviders = viewModel.coverProviders,
                                    downloadProxy = downloadProxy,
                                    update = update
                                ),
                                actions = SettingsActions(
                                    onBack = { settingsRoute = null },
                                    onAppearance = { settingsRoute = SettingsRoute.APPEARANCE },
                                    onSound = { settingsRoute = SettingsRoute.SOUND },
                                    onContact = { openTelegram(context) },
                                    onCrossfade = viewModel::setCrossfadeSeconds,
                                    onLevelling = viewModel::setLevelling,
                                    onSleepFade = viewModel::setSleepFade,
                                    onLyricsTerm = viewModel::setLyricsTerm,
                                    onLyricsPattern = viewModel::setLyricsPattern,
                                    onCoverProvider = viewModel::setCoverProvider,
                                    onDownloadProxy = viewModel::setDownloadProxy,
                                    onBackUp = {
                                        viewModel.backUp { text ->
                                            pendingText = text
                                            textExportLauncher.launchOrExplain("Lucense backup ${java.time.LocalDate.now()}.txt", context)
                                            true
                                        }
                                    },
                                    onRestore = { restoreLauncher.launchOrExplain(arrayOf("text/*"), context) },
                                    onCheckUpdate = viewModel::checkForUpdate,
                                    onOpenUpdate = { openUrl(context, it) }
                                )
                            )
                        }
                    }
                }
            }
        }

        // ---- Dialogs ----------------------------------------------------------

        playlistImport?.let { pending ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissImport() },
                title = { Text("\"${pending.name}\"") },
                text = {
                    Column {
                        Text(
                            "${pending.matched.size} of ${counted(pending.total, "track")} are already " +
                                "on this device. Nothing is downloaded — the playlist just " +
                                "links up what you have."
                        )
                        if (pending.missing.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Missing", style = MaterialTheme.typography.labelLarge)
                            pending.missing.take(6).forEach {
                                Text(it.display, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            if (pending.missing.size > 6) {
                                Text("and ${pending.missing.size - 6} more", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.confirmImport(pending) },
                        enabled = pending.matched.isNotEmpty()
                    ) { Text("Add ${pending.matched.size} ${if (pending.matched.size == 1) "track" else "tracks"}") }
                },
                dismissButton = { TextButton(onClick = { viewModel.dismissImport() }) { Text("Cancel") } }
            )
        }

        if (archiveState.running || archiveState.note != null) {
            AlertDialog(
                onDismissRequest = { if (!archiveState.running) viewModel.dismissArchive() },
                title = { Text(if (archiveState.running) "Zipping" else "Zip and ship") },
                text = {
                    Column {
                        if (archiveState.running) {
                            LinearProgressIndicator(
                                progress = { archiveState.fraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(archiveState.current, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        archiveState.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        // The path is the whole point, so it is spelled out rather
                        // than left for the user to hunt for.
                        archiveState.file?.let { file ->
                            Spacer(Modifier.height(10.dp))
                            Text("Saved to", style = MaterialTheme.typography.labelMedium)
                            Text(
                                file.parent ?: file.absolutePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(file.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    val file = archiveState.file
                    if (archiveState.running) {
                        TextButton(onClick = { viewModel.cancelArchive() }) { Text("Stop") }
                    } else if (file != null) {
                        TextButton(onClick = {
                            val uri = runCatching {
                                androidx.core.content.FileProvider.getUriForFile(
                                    context, context.packageName + ".shared", file
                                )
                            }.getOrNull()
                            if (uri == null) {
                                Toast.makeText(context, "Couldn't share that file.", Toast.LENGTH_SHORT).show()
                            } else {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, file.name)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Send archive"))
                            }
                        }) { Text("Ship it") }
                    } else {
                        TextButton(onClick = { viewModel.dismissArchive() }) { Text("Close") }
                    }
                },
                dismissButton = if (archiveState.running) null else {
                    { TextButton(onClick = { viewModel.dismissArchive() }) { Text("Close") } }
                }
            )
        }

        if (addingToPlaylist.isNotEmpty()) {
            val chosen = addingToPlaylist
            AddToPlaylistDialog(
                playlists = playlists,
                onDismiss = { addingToPlaylist = emptyList() },
                onPick = { playlistId ->
                    if (chosen.size == 1) viewModel.addToPlaylist(playlistId, chosen.first().id)
                    else viewModel.addSelectedToPlaylist(playlistId, chosen)
                    addingToPlaylist = emptyList()
                },
                onCreate = { name ->
                    viewModel.createPlaylist(name, chosen.map { it.id })
                    viewModel.clearSelection()
                    addingToPlaylist = emptyList()
                }
            )
        }

        if (pendingDelete.isNotEmpty()) {
            ConfirmDeleteDialog(
                tracks = pendingDelete,
                onConfirm = {
                    if (deleteSelection) viewModel.deleteSelected(visibleTracks)
                    else pendingDelete.forEach { viewModel.deleteTrack(it) }
                    pendingDelete = emptyList()
                    deleteSelection = false
                },
                onDismiss = {
                    pendingDelete = emptyList()
                    deleteSelection = false
                }
            )
        }

        if (showTools) {
            LibraryToolsSheet(
                trackCount = tracks.size,
                counts = toolCounts,
                job = job?.takeIf { it.label != MainViewModel.FIX_LABEL },
                onRun = { tool, redo, names, tags -> viewModel.runTool(tool, redo, names, tags) },
                onStop = viewModel::cancelJob,
                onDismissJob = viewModel::dismissJob,
                onDuplicates = {
                    showTools = false
                    showDuplicates = true
                    viewModel.scanDuplicates()
                },
                onZip = {
                    showTools = false
                    viewModel.zipLibrary()
                },
                onDismiss = { showTools = false }
            )
        }

        if (fixTargets.isNotEmpty()) {
            FixDialog(
                targets = fixTargets,
                job = job,
                onRun = { tags, genresToo, lyricsToo -> viewModel.runFix(tags, genresToo, lyricsToo) },
                onStop = viewModel::cancelJob,
                onDismiss = {
                    // Closing after a run is done with those songs; Cancel
                    // before one keeps them picked, for a second go.
                    val ran = job != null
                    viewModel.dismissFix()
                    if (ran) viewModel.clearSelection()
                }
            )
        }

        if (showDuplicates) {
            DuplicatesDialog(
                groups = duplicates,
                scanning = duplicateScanning,
                listening = duplicateListening,
                onRemove = { chosen ->
                    viewModel.removeDuplicates(chosen)
                    showDuplicates = false
                },
                onDismiss = {
                    showDuplicates = false
                    viewModel.clearDuplicates()
                }
            )
        }

        bulk?.let { progress ->
            BulkImportDialog(progress = progress, onCancel = { viewModel.cancelBulkImport() })
        }

        editTarget?.let { track ->
            EditTrackDialog(
                track = track,
                onSave = { title, artist, album, year, genre ->
                    viewModel.saveTrackDetails(track, title, artist, album, year, genre)
                },
                onRevert = { viewModel.revertTrackToFile(track) },
                onDismiss = { viewModel.dismissEdit() }
            )
        }

        if (editMany.isNotEmpty()) {
            EditManyDialog(
                targets = editMany,
                onApply = viewModel::applyToMany,
                onDismiss = viewModel::dismissEditMany
            )
        }

        rateTarget?.let { track ->
            RateDialog(
                track = track,
                onRate = { stars ->
                    viewModel.setRating(track, stars)
                    viewModel.rate(null)
                },
                onDismiss = { viewModel.rate(null) }
            )
        }

        tagTarget?.let { track ->
            FixTagsDialog(
                track = track,
                busy = tagBusy,
                result = tagResult,
                onApply = { viewModel.applyTagMatch(it) },
                onDismiss = { viewModel.dismissFixTags() }
            )
        }
    }

    // Of the handlers that are enabled, the one declared last gets the press.
    // So these run from the bottom of what is on screen to the top: the tab,
    // then a page open inside it, then whatever is drawn over the tabs.
    BackHandler(enabled = tab != Tab.LIBRARY) {
        tab = if (returnToDiscover && tab == Tab.IDENTIFY) Tab.DISCOVER else Tab.LIBRARY
        returnToDiscover = false
    }
    BackHandler(enabled = tab == Tab.PLAYLISTS && (openPlaylist != null || openAuto != null)) {
        viewModel.showPlaylist(null)
        openAuto = null
    }
    BackHandler(enabled = tab == Tab.DISCOVER && discoverStack.size > 1) { discover.browser.back() }
    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = settingsRoute != null) {
        settingsRoute = if (settingsRoute == SettingsRoute.HOME) null else SettingsRoute.HOME
    }
    BackHandler(enabled = showDownload) { showDownload = false }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Song", text))
}

/**
 * Some providers report audio documents as octet-stream, so the picker asks for
 * that too; TrackImporter rejects anything that isn't really audio.
 */
private val AUDIO_MIME_TYPES = arrayOf("audio/*", "application/ogg", "application/octet-stream")
private val VIDEO_MIME_TYPES = arrayOf("video/*")
