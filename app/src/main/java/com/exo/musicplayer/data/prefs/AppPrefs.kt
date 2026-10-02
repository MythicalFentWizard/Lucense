package com.exo.musicplayer.data.prefs

import android.content.Context
import com.exo.musicplayer.data.lyrics.LyricsTerm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the song list is ordered. The same nine, in the same directions, as on Windows. */
enum class LibrarySort(val label: String) {
    ARTIST("Artist"),
    TITLE("Title"),
    ALBUM("Album"),
    DURATION("Duration"),
    PLAYS("Times played"),
    LISTEN_TIME("Time listened"),
    ADDED("Date added"),
    RATING("Rating"),
    YEAR("Year");

    companion object {
        fun of(name: String?): LibrarySort = entries.firstOrNull { it.name == name } ?: TITLE
    }
}

/** What the Library tab shows: every song, or them gathered into albums or artists. */
enum class LibraryView(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists");

    companion object {
        fun of(name: String?): LibraryView = entries.firstOrNull { it.name == name } ?: SONGS
    }
}

/** How album and artist cards are ordered, as on Windows. */
enum class GroupSort(val label: String, val albums: Boolean = true, val artists: Boolean = true) {
    NAME("Name"),
    ARTIST("Artist", artists = false),
    TRACKS("Most songs"),
    YEAR("Newest", artists = false),
    ADDED("Recently added");

    companion object {
        fun of(name: String?, forAlbums: Boolean): GroupSort =
            entries.firstOrNull { it.name == name && (if (forAlbums) it.albums else it.artists) } ?: NAME
    }
}

/**
 * Everything the app remembers that isn't a song, a list, a theme or an
 * effect: how things are ordered, what was searched for, and how playback
 * behaves.
 *
 * Each value is written the moment it changes and read back by name on the
 * way in, so an option renamed later falls back to its default rather than
 * stopping the app from starting.
 */
class AppPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("lucense", Context.MODE_PRIVATE)

    private val _sort = MutableStateFlow(LibrarySort.of(prefs.getString(KEY_SORT, null)))
    val sort: StateFlow<LibrarySort> = _sort.asStateFlow()
    fun setSort(value: LibrarySort) = put(_sort, value) { putString(KEY_SORT, value.name) }

    private val _view = MutableStateFlow(LibraryView.of(prefs.getString(KEY_VIEW, null)))
    val view: StateFlow<LibraryView> = _view.asStateFlow()
    fun setView(value: LibraryView) = put(_view, value) { putString(KEY_VIEW, value.name) }

    private val _albumSort = MutableStateFlow(GroupSort.of(prefs.getString(KEY_ALBUM_SORT, null), true))
    val albumSort: StateFlow<GroupSort> = _albumSort.asStateFlow()
    fun setAlbumSort(value: GroupSort) = put(_albumSort, value) { putString(KEY_ALBUM_SORT, value.name) }

    private val _artistSort = MutableStateFlow(GroupSort.of(prefs.getString(KEY_ARTIST_SORT, null), false))
    val artistSort: StateFlow<GroupSort> = _artistSort.asStateFlow()
    fun setArtistSort(value: GroupSort) = put(_artistSort, value) { putString(KEY_ARTIST_SORT, value.name) }

    /** The bottom tab the app was on, by name. */
    private val _tab = MutableStateFlow(prefs.getString(KEY_TAB, null).orEmpty())
    val tab: StateFlow<String> = _tab.asStateFlow()
    fun setTab(value: String) = put(_tab, value) { putString(KEY_TAB, value) }

    /** The last eight things typed into the search box, newest first. */
    private val _searches = MutableStateFlow(
        prefs.getString(KEY_SEARCHES, null).orEmpty().split('\n').filter { it.isNotBlank() }
    )
    val searches: StateFlow<List<String>> = _searches.asStateFlow()

    fun rememberSearch(text: String) {
        val clean = text.trim()
        if (clean.length < 2) return
        val next = (listOf(clean) + _searches.value.filterNot { it.equals(clean, ignoreCase = true) })
            .take(MAX_SEARCHES)
        put(_searches, next) { putString(KEY_SEARCHES, next.joinToString("\n")) }
    }

    fun forgetSearches() = put(_searches, emptyList()) { remove(KEY_SEARCHES) }

    /** Seconds of overlap between songs; 0 is gapless with no overlap. */
    private val _crossfade = MutableStateFlow(prefs.getInt(KEY_CROSSFADE, 0))
    val crossfadeSeconds: StateFlow<Int> = _crossfade.asStateFlow()
    fun setCrossfadeSeconds(value: Int) =
        put(_crossfade, value.coerceIn(0, 12)) { putInt(KEY_CROSSFADE, value.coerceIn(0, 12)) }

    /** Whether measured songs are brought to the same loudness. */
    private val _levelling = MutableStateFlow(prefs.getBoolean(KEY_LEVELLING, true))
    val levelling: StateFlow<Boolean> = _levelling.asStateFlow()
    fun setLevelling(value: Boolean) = put(_levelling, value) { putBoolean(KEY_LEVELLING, value) }

    /** Fade the last half minute out when the sleep timer runs down. */
    private val _sleepFade = MutableStateFlow(prefs.getBoolean(KEY_SLEEP_FADE, true))
    val sleepFade: StateFlow<Boolean> = _sleepFade.asStateFlow()
    fun setSleepFade(value: Boolean) = put(_sleepFade, value) { putBoolean(KEY_SLEEP_FADE, value) }

    private val _lyricsTerm = MutableStateFlow(LyricsTerm.of(prefs.getString(KEY_LYRICS_TERM, null)))
    val lyricsTerm: StateFlow<LyricsTerm> = _lyricsTerm.asStateFlow()
    fun setLyricsTerm(value: LyricsTerm) = put(_lyricsTerm, value) { putString(KEY_LYRICS_TERM, value.name) }

    private val _lyricsPattern = MutableStateFlow(prefs.getString(KEY_LYRICS_PATTERN, null) ?: "{artist} {title}")
    val lyricsPattern: StateFlow<String> = _lyricsPattern.asStateFlow()
    fun setLyricsPattern(value: String) = put(_lyricsPattern, value) { putString(KEY_LYRICS_PATTERN, value) }

    /** The cover service asked first in bulk; blank asks them all in their usual order. */
    private val _coverProvider = MutableStateFlow(prefs.getString(KEY_COVER_PROVIDER, null).orEmpty())
    val coverProvider: StateFlow<String> = _coverProvider.asStateFlow()
    fun setCoverProvider(value: String) = put(_coverProvider, value) { putString(KEY_COVER_PROVIDER, value) }

    /** A proxy for downloads only, as typed; blank sends them the way everything else goes. */
    private val _downloadProxy = MutableStateFlow(prefs.getString(KEY_DOWNLOAD_PROXY, null).orEmpty())
    val downloadProxy: StateFlow<String> = _downloadProxy.asStateFlow()
    fun setDownloadProxy(value: String) = put(_downloadProxy, value) { putString(KEY_DOWNLOAD_PROXY, value) }

    private inline fun <T> put(
        flow: MutableStateFlow<T>,
        value: T,
        crossinline write: android.content.SharedPreferences.Editor.() -> Unit
    ) {
        flow.value = value
        prefs.edit().apply { write() }.apply()
    }

    private companion object {
        const val MAX_SEARCHES = 8
        const val KEY_SORT = "library_sort"
        const val KEY_VIEW = "library_view"
        const val KEY_ALBUM_SORT = "album_sort"
        const val KEY_ARTIST_SORT = "artist_sort"
        const val KEY_TAB = "tab"
        const val KEY_SEARCHES = "search_history"
        const val KEY_CROSSFADE = "crossfade_seconds"
        const val KEY_LEVELLING = "levelling"
        const val KEY_SLEEP_FADE = "sleep_fade"
        const val KEY_LYRICS_TERM = "lyrics_term"
        const val KEY_LYRICS_PATTERN = "lyrics_term_custom"
        const val KEY_COVER_PROVIDER = "cover_provider"
        const val KEY_DOWNLOAD_PROXY = "download_proxy"
    }
}
