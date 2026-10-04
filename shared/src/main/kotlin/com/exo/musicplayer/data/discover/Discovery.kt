package com.exo.musicplayer.data.discover

import com.exo.musicplayer.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Finding music you don't have yet: a map of genres to wander, artists to
 * follow from one to the next, and what's new from the ones you already play.
 *
 * Two open services, neither needing an account or a key:
 *
 *  - MusicBrainz for the genres: a couple of thousand of them, down to the
 *    likes of digicore and pirate metal, and which artists are tagged with
 *    each. Its search returns anyone carrying a tag, best-known first, so
 *    "shoegaze" opens with bands that are mostly something else; the artists
 *    are re-ordered here by how much of each one the genre is.
 *  - Deezer for the music itself: an artist's songs with half-minute previews,
 *    their releases with dates, and who is similar.
 *
 * Every Noise at Once would have been the obvious source for the genre map,
 * but it has no API, its robots.txt forbids automated access outright, and it
 * stopped being updated in 2023; these two are live.
 *
 * Both services ask clients to keep their pace down - MusicBrainz to one
 * request a second, Deezer to fifty in five - so calls queue behind a small
 * delay, and answers are kept on disk for as long as each kind stays true.
 */
class Discovery(cacheDir: File?) {

    private val cache = DiscoverCache(cacheDir)
    private val musicBrainz = Pace(1_100)
    private val deezer = Pace(130)

    // ---- Genres ----------------------------------------------------------------

    /** Every genre MusicBrainz knows, alphabetical. Empty only when offline with nothing kept. */
    suspend fun genres(): List<String> {
        genreList?.let { return it }
        val url = "https://musicbrainz.org/ws/2/genre/all?fmt=txt"
        val text = withContext(Dispatchers.IO) {
            cache.get(url, 30 * DAY)
                ?: musicBrainz.run { fetch(url, MB_HEADERS) }?.also { cache.put(url, it) }
                ?: cache.get(url, Long.MAX_VALUE)
        } ?: return emptyList()
        return text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList().also { genreList = it }
    }

    /** The genre list once read: every genre and artist page checks tags against it. */
    @Volatile private var genreList: List<String>? = null

    /**
     * The artists of a genre, most of-the-genre first, and the genres that
     * keep turning up beside it. A hundred artists at a time: [offset] is how
     * many have been shown already.
     */
    suspend fun genre(name: String, offset: Int = 0): GenrePage? {
        val genre = name.trim().lowercase(Locale.ROOT)
        if (genre.isEmpty()) return null
        val url = "https://musicbrainz.org/ws/2/artist?fmt=json&limit=100" +
            (if (offset > 0) "&offset=$offset" else "") + "&query=" + encode("tag:\"$genre\"")
        val json = musicBrainzJson(url, 7 * DAY) ?: return null
        val known = genres().toHashSet()

        class Row(val name: String, val weight: Float, val count: Int, val score: Int, val tags: Map<String, Int>)

        val rows = json.optJSONArray("artists").objects().mapNotNull { artist ->
            val tags = artist.optJSONArray("tags").objects()
                .associate { it.optString("name").lowercase(Locale.ROOT) to it.optInt("count") }
            val count = tags[genre] ?: 0
            val top = tags.values.maxOrNull() ?: 0
            if (count <= 0 || top <= 0) return@mapNotNull null
            Row(artist.optString("name"), count.toFloat() / top, count, artist.optInt("score"), tags)
        }.filter { it.name.isNotBlank() }
            // How much of the artist the genre is, then how many people said
            // so, then MusicBrainz's own idea of how well known they are.
            .sortedWith(compareByDescending<Row> { it.weight }.thenByDescending { it.count }.thenByDescending { it.score })

        // The genres these artists are also filed under, weighted the same way,
        // counting only those the genre is a real part of.
        val beside = HashMap<String, Float>()
        rows.filter { it.weight >= 0.5f }.forEach { row ->
            val top = row.tags.values.max().toFloat()
            row.tags.forEach { (tag, count) ->
                if (tag != genre && tag in known && count > 0) beside.merge(tag, count / top, Float::plus)
            }
        }

        return GenrePage(
            genre = genre,
            artists = rows.map { DiscoverArtist(name = it.name, weight = it.weight) },
            nearby = beside.entries.sortedByDescending { it.value }.take(14).map { it.key },
            total = json.optInt("count", rows.size)
        )
    }

    // ---- Artists ---------------------------------------------------------------

    /** The artist on Deezer whose name this is, or null when there isn't exactly one such name. */
    suspend fun find(name: String): DiscoverArtist? {
        val wanted = plain(name)
        if (wanted.isEmpty()) return null
        val json = deezerJson("/search/artist?limit=8&q=" + encode(name.trim()), 30 * DAY) ?: return null
        return json.optJSONArray("data").objects()
            .filter { plain(it.optString("name")) == wanted }
            .maxByOrNull { it.optInt("nb_fan") }
            ?.let(::artistOf)
    }

    /** Artists by name, for a search box: Deezer's own order, which is the best known first. */
    suspend fun searchArtists(query: String): List<DiscoverArtist> {
        val text = query.trim()
        if (text.length < 2) return emptyList()
        return deezerJson("/search/artist?limit=12&q=" + encode(text), DAY)
            ?.optJSONArray("data").objects().map(::artistOf).filter { it.name.isNotBlank() }.orEmpty()
    }

    /**
     * Everything Discover shows about one artist.
     *
     * An artist Deezer doesn't list - plenty on a genre page are too small for
     * it - still gets a page if MusicBrainz knows their genres: there is
     * nothing to play, but "which genre is this" is answered. Null only when
     * neither knows the name.
     */
    suspend fun artist(artist: DiscoverArtist): ArtistPage? = coroutineScope {
        val found = when {
            artist.deezerId == null -> find(artist.name)
            // Reached from a song, which names its artist but carries no picture.
            artist.pictureUrl == null ->
                deezerJson("/artist/${artist.deezerId}", 30 * DAY)?.let(::artistOf) ?: artist
            else -> artist
        }
        val id = found?.deezerId
        if (found == null || id == null) {
            val genres = genresOf(artist.name)
            return@coroutineScope if (genres.isEmpty()) null
            else ArtistPage(artist.copy(deezerId = null), emptyList(), emptyList(), emptyList(), genres)
        }

        val tagged = async { genresOf(found.name) }
        val similar = async { related(id) }
        val releases = releases(id, found.name)

        var popular = popular(found.name, id)
        // A name that is also an ordinary word finds other people's songs, so
        // the list is topped up from the newest releases.
        if (popular.size < 5) {
            val more = releases.take(3).flatMap { tracks(it) }
            popular = (popular + more).distinctBy { plain(it.title) }.take(12)
        }

        // Small artists often carry no tags at all; the broad genre their
        // newest release is filed under is better than nothing.
        val genres = tagged.await().ifEmpty { releases.firstOrNull()?.let { filedUnder(it.id) }.orEmpty() }

        ArtistPage(found, popular, releases, similar.await(), genres)
    }

    /**
     * One song to hear an artist by: their most played with a preview. This is
     * what a tap on an artist in a genre plays, as on Every Noise at Once.
     */
    suspend fun sample(artist: DiscoverArtist): DiscoverTrack? {
        val found = if (artist.deezerId != null) artist else find(artist.name) ?: return null
        val id = found.deezerId ?: return null
        popular(found.name, id).firstOrNull { it.previewUrl != null }?.let { return it }
        for (release in releases(id, found.name).take(2)) {
            tracks(release).firstOrNull { it.previewUrl != null }?.let { return it }
        }
        return null
    }

    /**
     * An artist's songs, most played first. Deezer's own "top tracks" answers
     * nothing from some regions, so they are taken from a track search.
     */
    private suspend fun popular(name: String, id: Long): List<DiscoverTrack> =
        deezerJson("/search/track?limit=100&q=" + encode(name), 15 * MINUTE)
            ?.optJSONArray("data").objects()
            .filter { it.optJSONObject("artist")?.optLong("id") == id }
            .map(::trackOf)
            .sortedByDescending { it.rank }
            .distinctBy { plain(it.title) }
            .take(12)
            .orEmpty()

    /** The broad genres Deezer files a release under, as far as they are genres known here. */
    private suspend fun filedUnder(releaseId: Long): List<String> {
        val known = genres().toHashSet()
        return deezerJson("/album/$releaseId", 30 * DAY)
            ?.optJSONObject("genres")?.optJSONArray("data").objects()
            .flatMap { it.optString("name").lowercase(Locale.ROOT).split('/') }
            .map { it.trim() }
            .filter { it in known }
            .distinct()
            .orEmpty()
    }

    /** The songs on one release, in its own order. */
    suspend fun tracks(release: DiscoverRelease): List<DiscoverTrack> =
        deezerJson("/album/${release.id}/tracks?limit=60", 15 * MINUTE)
            ?.optJSONArray("data").objects()
            .map { trackOf(it).copy(album = release.title, coverUrl = release.coverUrl) }
            .orEmpty()

    private suspend fun releases(id: Long, artist: String): List<DiscoverRelease> =
        deezerJson("/artist/$id/albums?limit=100", 12 * HOUR)
            ?.optJSONArray("data").objects()
            .map { album ->
                DiscoverRelease(
                    id = album.optLong("id"),
                    title = album.optString("title"),
                    artist = artist,
                    date = album.optString("release_date").takeIf { DATE.matches(it) }.orEmpty(),
                    kind = album.optString("record_type").ifBlank { "album" },
                    coverUrl = album.optString("cover_medium").takeIf { it.startsWith("http") }
                )
            }
            .sortedByDescending { it.date }
            // The deluxe and the remastered edition are the same release to someone browsing.
            .distinctBy { plain(it.title.replace(EDITION, "")) }
            .orEmpty()

    private suspend fun related(id: Long): List<DiscoverArtist> =
        deezerJson("/artist/$id/related?limit=24", 7 * DAY)?.optJSONArray("data").objects().map(::artistOf).orEmpty()

    /** The genres an artist is tagged with on MusicBrainz, the strongest first. */
    private suspend fun genresOf(name: String): List<String> {
        val url = "https://musicbrainz.org/ws/2/artist?fmt=json&limit=5&query=" + encode("artist:\"$name\"")
        val json = musicBrainzJson(url, 30 * DAY) ?: return emptyList()
        val known = genres().toHashSet()
        val wanted = plain(name)
        return json.optJSONArray("artists").objects()
            .firstOrNull { plain(it.optString("name")) == wanted }
            ?.optJSONArray("tags").objects()
            .filter { it.optInt("count") > 0 && it.optString("name").lowercase(Locale.ROOT) in known }
            .sortedByDescending { it.optInt("count") }
            .map { it.optString("name").lowercase(Locale.ROOT) }
            .take(8)
            .orEmpty()
    }

    // ---- For you ---------------------------------------------------------------

    /**
     * What the artists in a library have put out lately, newest first.
     *
     * [libraryArtists] are the artist fields as the songs carry them, most
     * songs first. A field naming several people is tried whole before it is
     * split - "Tyler, The Creator" is one artist, "kets4eki, asteria" is two.
     * [onProgress] is handed the list so far as it grows, since the first look
     * at a big library takes a while at the pace Deezer allows.
     */
    suspend fun newFrom(
        libraryArtists: List<String>,
        days: Int = 120,
        limit: Int = 150,
        onProgress: (found: List<DiscoverRelease>, done: Int, of: Int) -> Unit = { _, _, _ -> }
    ): List<DiscoverRelease> {
        val since = LocalDate.now().minusDays(days.toLong()).toString()
        val names = libraryArtists.take(limit)
        val seenArtists = HashSet<Long>()
        val found = ArrayList<DiscoverRelease>()
        val shared = Mutex()
        val next = AtomicInteger(0)
        var done = 0

        // A song by three people is on each of their pages: one release, shown once.
        fun sorted() = found.sortedByDescending { it.date }.distinctBy { it.id }
            .distinctBy { plain(it.title) + it.date }

        // A few artists at a time. The requests still start no closer together
        // than Deezer allows; what overlaps is the waiting for each answer.
        coroutineScope {
            repeat(WORKERS) {
                launch {
                    while (true) {
                        val index = next.getAndIncrement()
                        if (index >= names.size) break
                        val fresh = ArrayList<DiscoverRelease>()
                        for (artist in resolve(names[index])) {
                            val id = artist.deezerId ?: continue
                            if (!shared.withLock { seenArtists.add(id) }) continue
                            fresh += releases(id, artist.name).filter { it.date >= since }
                        }
                        shared.withLock {
                            found += fresh
                            done++
                            if (done % 4 == 0 || done == names.size) onProgress(sorted(), done, names.size)
                        }
                    }
                }
            }
        }
        return sorted()
    }

    /**
     * Artists like the ones in a library that aren't in it: whoever is "similar"
     * to the most of them comes first.
     */
    suspend fun similarTo(libraryArtists: List<String>, seeds: Int = 14): List<DiscoverArtist> {
        val mine = libraryArtists.flatMap { listOf(it) + split(it) }.map(::plain).toHashSet()
        val votes = LinkedHashMap<Long, Pair<DiscoverArtist, Int>>()
        var used = 0
        for (field in libraryArtists) {
            if (used >= seeds) break
            for (artist in resolve(field)) {
                val id = artist.deezerId ?: continue
                used++
                related(id).forEach { other ->
                    val otherId = other.deezerId ?: return@forEach
                    if (plain(other.name) in mine) return@forEach
                    votes[otherId] = other to ((votes[otherId]?.second ?: 0) + 1)
                }
            }
        }
        return votes.values
            .sortedWith(compareByDescending<Pair<DiscoverArtist, Int>> { it.second }.thenByDescending { it.first.fans ?: 0 })
            .map { it.first }
            .take(40)
    }

    // ---- Charts ----------------------------------------------------------------

    /** The broad genres Deezer keeps a chart for: id and name, "All" first. */
    suspend fun chartGenres(): List<Pair<Long, String>> =
        deezerJson("/genre", 30 * DAY)?.optJSONArray("data").objects()
            .map { it.optLong("id") to it.optString("name") }
            .filter { it.second.isNotBlank() }
            .orEmpty()

    /** What is being played most right now, overall (0) or in one of [chartGenres]. */
    suspend fun chart(genreId: Long = 0): List<DiscoverTrack> =
        deezerJson("/chart/$genreId/tracks?limit=60", HOUR)?.optJSONArray("data").objects().map(::trackOf).orEmpty()

    /**
     * Whether Deezer answers at all. It is blocked in some countries, and "no
     * such artist" and "couldn't ask" need different words on the page.
     */
    suspend fun reachable(): Boolean {
        val now = System.currentTimeMillis()
        if (now - reachedAt < MINUTE) return reached
        reached = deezer.run { fetch("https://api.deezer.com/infos") } != null
        reachedAt = System.currentTimeMillis()
        return reached
    }

    @Volatile private var reached = false
    @Volatile private var reachedAt = 0L

    /** The artist field as one artist if Deezer knows it whole, as its parts otherwise. */
    private suspend fun resolve(field: String): List<DiscoverArtist> {
        find(field)?.let { return listOf(it) }
        val parts = split(field)
        return if (parts.size < 2) emptyList() else parts.mapNotNull { find(it) }
    }

    // ---- Plumbing --------------------------------------------------------------

    // Reading what was kept and parsing an answer both happen off the caller's
    // thread: on a phone the caller is the one drawing the screen.

    private suspend fun deezerJson(path: String, maxAge: Long): JSONObject? = withContext(Dispatchers.IO) {
        val url = "https://api.deezer.com$path"
        cache.get(url, maxAge)?.let { kept ->
            runCatching { JSONObject(kept) }.getOrNull()?.let { return@withContext it }
        }
        repeat(3) {
            val text = deezer.run { fetch(url) } ?: return@withContext null
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext null
            val error = json.optJSONObject("error")
            if (error == null) {
                cache.put(url, text)
                return@withContext json
            }
            // 4 is "quota exceeded": the pace was still too fast, so wait and ask again.
            if (error.optInt("code") != 4) return@withContext null
            delay(1_500)
        }
        null
    }

    private suspend fun musicBrainzJson(url: String, maxAge: Long): JSONObject? = withContext(Dispatchers.IO) {
        cache.get(url, maxAge)?.let { kept ->
            runCatching { JSONObject(kept) }.getOrNull()?.let { return@withContext it }
        }
        val text = musicBrainz.run { fetch(url, MB_HEADERS) } ?: return@withContext null
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext null
        if (json.has("error")) return@withContext null
        cache.put(url, text)
        json
    }

    private suspend fun fetch(url: String, headers: Map<String, String> = emptyMap()): String? =
        withContext(Dispatchers.IO) { Http.get(url, headers) }

    private fun artistOf(json: JSONObject) = DiscoverArtist(
        name = json.optString("name"),
        deezerId = json.optLong("id").takeIf { it > 0 },
        pictureUrl = json.optString("picture_medium").takeIf { it.startsWith("http") },
        fans = json.optInt("nb_fan", -1).takeIf { it >= 0 }
    )

    private fun trackOf(json: JSONObject) = DiscoverTrack(
        id = json.optLong("id"),
        title = json.optString("title"),
        artist = json.optJSONObject("artist")?.optString("name").orEmpty(),
        album = json.optJSONObject("album")?.optString("title")?.takeIf { it.isNotBlank() },
        durationMs = json.optLong("duration") * 1000L,
        previewUrl = json.optString("preview").takeIf { it.startsWith("http") },
        coverUrl = json.optJSONObject("album")?.optString("cover_medium")?.takeIf { it.startsWith("http") },
        rank = json.optInt("rank"),
        artistId = json.optJSONObject("artist")?.optLong("id")?.takeIf { it > 0 }
    )

    companion object {
        /** Artists looked up side by side for "new from your artists". */
        private const val WORKERS = 6

        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR

        /** MusicBrainz asks every client to say who it is and where to find it. */
        private val MB_HEADERS = mapOf(
            "User-Agent" to "Lucense (https://github.com/MythicalFentWizard/Lucense)",
            "Accept" to "application/json"
        )

        private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
        private val EDITION = Regex("""\s*[(\[][^)\]]*(deluxe|remaster|edition|version|bonus|expanded)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
        private val MARKS = Regex("""\p{Mn}+""")
        private val NOT_WORD = Regex("""[^\p{L}\p{N}]+""")
        private val JOINS = Regex("""\s*(?:,|;|/|&|\bx\b|\bfeat\.?|\bft\.?|\bwith\b)\s*""", RegexOption.IGNORE_CASE)

        /** A name for comparing: lower case, no accents, no punctuation. "SVÄRD" is "svard". */
        fun plain(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "")
                .lowercase(Locale.ROOT).replace(NOT_WORD, " ").trim()

        /** The people named in an artist field that names several. */
        fun split(field: String): List<String> =
            field.split(JOINS).map { it.trim() }.filter { it.length > 1 }.distinct()

        /**
         * The words genres are built from, for browsing by family: "core"
         * finds hardcore, metalcore and digicore alike.
         */
        val FAMILIES = listOf(
            "rock", "pop", "metal", "punk", "core", "house", "techno", "trance", "jazz", "blues",
            "folk", "country", "hip hop", "rap", "trap", "soul", "funk", "classical", "ambient",
            "electro", "wave", "bass", "disco", "reggae", "gaze", "industrial", "noise", "drill",
            "garage", "dub", "emo", "indie", "psych", "synth", "latin", "gospel"
        )

        /**
         * The genres a typed word finds, the closest first: the genre of that
         * exact name, then those starting with it, then those with a word
         * starting with it, then any that merely contain it.
         */
        fun searchGenres(genres: List<String>, query: String): List<String> {
            val text = query.trim().lowercase(Locale.ROOT)
            if (text.isEmpty()) return genres
            fun closeness(genre: String): Int = when {
                genre == text -> 0
                genre.startsWith(text) -> 1
                genre.contains(" $text") || genre.contains("-$text") -> 2
                genre.contains(text) -> 3
                else -> 4
            }
            return genres.map { it to closeness(it) }.filter { it.second < 4 }
                .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first.length })
                .map { it.first }
        }

        /**
         * A song title for telling whether it is already owned: without the
         * bracketed extras ("feat. X", "Remastered 2011") that one source adds
         * and another leaves off.
         */
        fun titleKey(title: String): String = plain(title.replace(BRACKETED, " ")).ifEmpty { plain(title) }

        private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")

        /**
         * The artist fields of a library as [newFrom] and [similarTo] want
         * them: one entry per distinct field, the most songs first.
         */
        fun rankArtists(artistOfEachSong: List<String?>): List<String> =
            artistOfEachSong.mapNotNull { it?.trim()?.takeIf { name -> name.isNotEmpty() } }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }
                .map { it.key }

        private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}

/**
 * Requests that start no closer together than [gapMs]. Only the starts are
 * spaced: one request doesn't wait for the one before it to be answered.
 */
private class Pace(private val gapMs: Long) {
    private val mutex = Mutex()
    private var last = 0L

    suspend fun <T> run(block: suspend () -> T): T {
        mutex.withLock {
            val wait = last + gapMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            last = System.currentTimeMillis()
        }
        return block()
    }
}

/**
 * Answers kept as files, named by a hash of what was asked. Without a
 * directory nothing is kept and everything is asked afresh.
 */
private class DiscoverCache(private val dir: File?) {

    fun get(key: String, maxAgeMs: Long): String? {
        val file = file(key) ?: return null
        if (!file.isFile) return null
        val age = System.currentTimeMillis() - file.lastModified()
        if (maxAgeMs != Long.MAX_VALUE && age > maxAgeMs) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun put(key: String, text: String) {
        val file = file(key) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(text)
        }
    }

    private fun file(key: String): File? {
        val root = dir ?: return null
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return File(root, digest.joinToString("") { "%02x".format(it) } + ".json")
    }
}
