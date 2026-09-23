package com.exo.musicplayer.desktop.data

import com.exo.musicplayer.data.model.TrackPlayCount
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.Calendar

data class StoredLyrics(
    val plain: String?,
    val synced: String?,
    val source: String
)

data class ListenRow(val path: String, val totalMs: Long, val plays: Int)

/** A list written as a rule rather than as a set of songs. */
data class StoredSmartPlaylist(val id: Long, val name: String, val rule: String)

data class StoredPlaylist(val id: Long, val name: String, val size: Int)

/**
 * Desktop persistence: listening history, lyrics and bulk-job bookkeeping.
 *
 * Plain SQLite over JDBC rather than Room, which is Android-only. Rows are keyed
 * by absolute file path instead of a generated id: the desktop library is read
 * straight off disk and has no row identity of its own, and a path survives
 * rescans where an index would not.
 */
class DesktopStore(databaseFile: File) {

    private val connection: Connection

    init {
        databaseFile.parentFile?.mkdirs()
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}")
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS play_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    path TEXT NOT NULL,
                    startedAt INTEGER NOT NULL,
                    listenedMs INTEGER NOT NULL,
                    hourOfDay INTEGER NOT NULL,
                    weatherGroup TEXT
                )
                """.trimIndent()
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS ix_events_path ON play_events(path)")
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS smart_playlists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    rule TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS track_fx (
                    path TEXT PRIMARY KEY,
                    settings TEXT NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS lyrics (
                    path TEXT PRIMARY KEY,
                    plain TEXT,
                    synced TEXT,
                    source TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS levels (
                    path TEXT PRIMARY KEY,
                    dbfs REAL NOT NULL,
                    peak REAL NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS ratings (
                    path TEXT PRIMARY KEY,
                    stars INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS marks (
                    path TEXT PRIMARY KEY,
                    artCheckedAt INTEGER,
                    identifiedAt INTEGER,
                    lyricsCheckedAt INTEGER,
                    fingerprintedAt INTEGER
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS playlists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS playlist_items (
                    playlistId INTEGER NOT NULL,
                    path TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    PRIMARY KEY (playlistId, path)
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS favourites (
                    path TEXT PRIMARY KEY,
                    addedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    // ---- Listening history ----

    /** Opens an event; returns its id so the duration can be filled in later. */
    fun startPlay(path: String, weatherGroup: String?): Long {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        connection.prepareStatement(
            "INSERT INTO play_events(path, startedAt, listenedMs, hourOfDay, weatherGroup) " +
                "VALUES (?,?,?,?,?)"
        ).use { ps ->
            ps.setString(1, path)
            ps.setLong(2, System.currentTimeMillis())
            ps.setLong(3, 0)
            ps.setInt(4, hour)
            ps.setString(5, weatherGroup)
            ps.executeUpdate()
        }
        connection.createStatement().use { st ->
            st.executeQuery("SELECT last_insert_rowid()").use { rs ->
                return if (rs.next()) rs.getLong(1) else -1L
            }
        }
    }

    fun updateListened(id: Long, listenedMs: Long) {
        connection.prepareStatement("UPDATE play_events SET listenedMs=? WHERE id=?").use {
            it.setLong(1, listenedMs); it.setLong(2, id); it.executeUpdate()
        }
    }

    fun totalListenedMs(): Long = queryLong("SELECT COALESCE(SUM(listenedMs),0) FROM play_events")

    fun playCount(): Int =
        queryLong("SELECT COUNT(*) FROM play_events WHERE listenedMs >= $QUALIFYING_MS").toInt()

    fun distinctTracks(): Int =
        queryLong(
            "SELECT COUNT(DISTINCT path) FROM play_events WHERE listenedMs >= $QUALIFYING_MS"
        ).toInt()

    fun topByListenTime(limit: Int): List<ListenRow> = buildList {
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT path, SUM(listenedMs) t, COUNT(*) c FROM play_events " +
                    "WHERE listenedMs >= $QUALIFYING_MS GROUP BY path ORDER BY t DESC LIMIT $limit"
            ).use { rs ->
                while (rs.next()) add(ListenRow(rs.getString(1), rs.getLong(2), rs.getInt(3)))
            }
        }
    }

    fun playsByHour(): Map<Int, Int> = buildMap {
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT hourOfDay, COUNT(*) FROM play_events " +
                    "WHERE listenedMs >= $QUALIFYING_MS GROUP BY hourOfDay"
            ).use { rs -> while (rs.next()) put(rs.getInt(1), rs.getInt(2)) }
        }
    }

    fun playsByWeather(): Map<String, Int> = buildMap {
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT weatherGroup, COUNT(*) FROM play_events WHERE weatherGroup IS NOT NULL " +
                    "AND listenedMs >= $QUALIFYING_MS GROUP BY weatherGroup"
            ).use { rs -> while (rs.next()) put(rs.getString(1), rs.getInt(2)) }
        }
    }

    // ---- Weather affinity inputs ----
    //
    // Returned as raw aggregates so the shared WeatherAffinity scorer, the same
    // one Android uses, can apply the smoothing and lift calculation.

    fun playsPerTrackInCondition(group: String): List<TrackPlayCount> =
        aggregate(
            "SELECT path, COUNT(*) FROM play_events WHERE weatherGroup = ? " +
                "AND listenedMs >= $QUALIFYING_MS GROUP BY path",
            group
        )

    fun playsPerTrackWithWeather(): List<TrackPlayCount> =
        aggregate(
            "SELECT path, COUNT(*) FROM play_events WHERE weatherGroup IS NOT NULL " +
                "AND listenedMs >= $QUALIFYING_MS GROUP BY path",
            null
        )

    fun totalPlaysInCondition(group: String): Int {
        connection.prepareStatement(
            "SELECT COUNT(*) FROM play_events WHERE weatherGroup = ? " +
                "AND listenedMs >= $QUALIFYING_MS"
        ).use { ps ->
            ps.setString(1, group)
            ps.executeQuery().use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun totalPlaysWithWeather(): Int = queryLong(
        "SELECT COUNT(*) FROM play_events WHERE weatherGroup IS NOT NULL " +
            "AND listenedMs >= $QUALIFYING_MS"
    ).toInt()

    /**
     * Paths are hashed to the Long ids the shared scorer expects, and mapped
     * back afterwards. Stable within a run, which is all the scoring needs.
     */
    private fun aggregate(sql: String, arg: String?): List<TrackPlayCount> = buildList {
        val ps = connection.prepareStatement(sql)
        arg?.let { ps.setString(1, it) }
        ps.use {
            it.executeQuery().use { rs ->
                while (rs.next()) {
                    add(TrackPlayCount(pathKey(rs.getString(1)), rs.getInt(2)))
                }
            }
        }
    }

    // ---- Lyrics ----

    fun lyricsFor(path: String): StoredLyrics? {
        connection.prepareStatement(
            "SELECT plain, synced, source FROM lyrics WHERE path = ?"
        ).use { ps ->
            ps.setString(1, path)
            ps.executeQuery().use { rs ->
                return if (rs.next()) {
                    StoredLyrics(rs.getString(1), rs.getString(2), rs.getString(3))
                } else {
                    null
                }
            }
        }
    }

    fun saveLyrics(path: String, plain: String?, synced: String?, source: String) {
        connection.prepareStatement(
            "INSERT INTO lyrics(path, plain, synced, source, updatedAt) VALUES (?,?,?,?,?) " +
                "ON CONFLICT(path) DO UPDATE SET plain=excluded.plain, synced=excluded.synced, " +
                "source=excluded.source, updatedAt=excluded.updatedAt"
        ).use {
            it.setString(1, path); it.setString(2, plain); it.setString(3, synced)
            it.setString(4, source); it.setLong(5, System.currentTimeMillis())
            it.executeUpdate()
        }
    }

    fun deleteLyrics(path: String) {
        connection.prepareStatement("DELETE FROM lyrics WHERE path = ?").use {
            it.setString(1, path); it.executeUpdate()
        }
    }

    // ---- Bulk-job marks ----

    fun mark(path: String, column: String) {
        require(column in MARK_COLUMNS)
        connection.prepareStatement(
            "INSERT INTO marks(path, $column) VALUES (?,?) " +
                "ON CONFLICT(path) DO UPDATE SET $column = excluded.$column"
        ).use {
            it.setString(1, path); it.setLong(2, System.currentTimeMillis()); it.executeUpdate()
        }
    }

    fun markedPaths(column: String): Set<String> {
        require(column in MARK_COLUMNS)
        return buildSet {
            connection.createStatement().use { st ->
                st.executeQuery("SELECT path FROM marks WHERE $column IS NOT NULL").use { rs ->
                    while (rs.next()) add(rs.getString(1))
                }
            }
        }
    }

    // ---- Playlists ----

    fun playlists(): List<StoredPlaylist> = buildList {
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT p.id, p.name, COUNT(i.path) FROM playlists p " +
                    "LEFT JOIN playlist_items i ON i.playlistId = p.id " +
                    "GROUP BY p.id, p.name ORDER BY p.createdAt"
            ).use { rs ->
                while (rs.next()) add(StoredPlaylist(rs.getLong(1), rs.getString(2), rs.getInt(3)))
            }
        }
    }

    fun createPlaylist(name: String): Long {
        connection.prepareStatement("INSERT INTO playlists(name, createdAt) VALUES (?,?)").use {
            it.setString(1, name); it.setLong(2, System.currentTimeMillis()); it.executeUpdate()
        }
        return queryLong("SELECT last_insert_rowid()")
    }

    fun renamePlaylist(id: Long, name: String) {
        connection.prepareStatement("UPDATE playlists SET name=? WHERE id=?").use {
            it.setString(1, name); it.setLong(2, id); it.executeUpdate()
        }
    }

    fun deletePlaylist(id: Long) {
        connection.prepareStatement("DELETE FROM playlist_items WHERE playlistId=?").use {
            it.setLong(1, id); it.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM playlists WHERE id=?").use {
            it.setLong(1, id); it.executeUpdate()
        }
    }

    fun playlistPaths(id: Long): List<String> = buildList {
        connection.prepareStatement(
            "SELECT path FROM playlist_items WHERE playlistId=? ORDER BY position"
        ).use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> while (rs.next()) add(rs.getString(1)) }
        }
    }

    fun addToPlaylist(id: Long, paths: List<String>) {
        var next = queryLong(
            "SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlistId=$id"
        )
        connection.prepareStatement(
            "INSERT INTO playlist_items(playlistId, path, position) VALUES (?,?,?) " +
                "ON CONFLICT(playlistId, path) DO NOTHING"
        ).use { ps ->
            for (path in paths) {
                ps.setLong(1, id); ps.setString(2, path); ps.setLong(3, next++)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    fun removeFromPlaylist(id: Long, path: String) {
        connection.prepareStatement(
            "DELETE FROM playlist_items WHERE playlistId=? AND path=?"
        ).use { it.setLong(1, id); it.setString(2, path); it.executeUpdate() }
    }

    // ---- Favourites ----

    fun saveLevel(path: String, dbfs: Float, peak: Float) {
        connection.prepareStatement(
            "INSERT OR REPLACE INTO levels(path, dbfs, peak) VALUES(?, ?, ?)"
        ).use { ps ->
            ps.setString(1, path)
            ps.setDouble(2, dbfs.toDouble())
            ps.setDouble(3, peak.toDouble())
            ps.executeUpdate()
        }
    }

    /** Every measured level, as loudness and peak. */
    fun levels(): Map<String, Pair<Float, Float>> = buildMap {
        connection.createStatement().use { st ->
            st.executeQuery("SELECT path, dbfs, peak FROM levels").use { rs ->
                while (rs.next()) {
                    put(rs.getString(1), rs.getDouble(2).toFloat() to rs.getDouble(3).toFloat())
                }
            }
        }
    }

    /** Stars out of five; zero clears it. */
    fun setRating(path: String, stars: Int) {
        if (stars <= 0) {
            connection.prepareStatement("DELETE FROM ratings WHERE path = ?").use { ps ->
                ps.setString(1, path)
                ps.executeUpdate()
            }
            return
        }
        connection.prepareStatement(
            "INSERT OR REPLACE INTO ratings(path, stars) VALUES(?, ?)"
        ).use { ps ->
            ps.setString(1, path)
            ps.setInt(2, stars.coerceAtMost(5))
            ps.executeUpdate()
        }
    }

    fun ratings(): Map<String, Int> = buildMap {
        connection.createStatement().use { st ->
            st.executeQuery("SELECT path, stars FROM ratings").use { rs ->
                while (rs.next()) put(rs.getString(1), rs.getInt(2))
            }
        }
    }

    fun favourites(): Set<String> = buildSet {
        connection.createStatement().use { st ->
            st.executeQuery("SELECT path FROM favourites").use { rs ->
                while (rs.next()) add(rs.getString(1))
            }
        }
    }

    fun setFavourite(path: String, favourite: Boolean) {
        if (favourite) {
            connection.prepareStatement(
                "INSERT INTO favourites(path, addedAt) VALUES (?,?) ON CONFLICT(path) DO NOTHING"
            ).use {
                it.setString(1, path); it.setLong(2, System.currentTimeMillis()); it.executeUpdate()
            }
        } else {
            connection.prepareStatement("DELETE FROM favourites WHERE path=?").use {
                it.setString(1, path); it.executeUpdate()
            }
        }
    }

    /** Path to total listened milliseconds, for the "most listened" sort. */
    fun listenTimeByPath(): Map<String, Long> = buildMap {
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT path, SUM(listenedMs) FROM play_events GROUP BY path"
            ).use { rs -> while (rs.next()) put(rs.getString(1), rs.getLong(2)) }
        }
    }

    fun playCountByPath(): Map<String, Int> = buildMap {
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT path, COUNT(*) FROM play_events WHERE listenedMs >= $QUALIFYING_MS " +
                    "GROUP BY path"
            ).use { rs -> while (rs.next()) put(rs.getString(1), rs.getInt(2)) }
        }
    }

    /** The songs played most recently, newest first and each one only once. */
    fun recentPlays(limit: Int): List<String> = buildList {
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT path, MAX(startedAt) AS last FROM play_events " +
                    "WHERE listenedMs >= $QUALIFYING_MS GROUP BY path " +
                    "ORDER BY last DESC LIMIT $limit"
            ).use { rs -> while (rs.next()) add(rs.getString(1)) }
        }
    }

    fun smartPlaylists(): List<StoredSmartPlaylist> = buildList {
        connection.createStatement().use { st ->
            st.executeQuery("SELECT id, name, rule FROM smart_playlists ORDER BY name COLLATE NOCASE")
                .use { rs ->
                    while (rs.next()) {
                        add(StoredSmartPlaylist(rs.getLong(1), rs.getString(2), rs.getString(3)))
                    }
                }
        }
    }

    fun createSmart(name: String, rule: String): Long {
        connection.prepareStatement(
            "INSERT INTO smart_playlists(name, rule, createdAt) VALUES (?,?,?)"
        ).use { ps ->
            ps.setString(1, name)
            ps.setString(2, rule)
            ps.setLong(3, System.currentTimeMillis())
            ps.executeUpdate()
        }
        return queryLong("SELECT last_insert_rowid()")
    }

    fun updateSmart(id: Long, name: String, rule: String) {
        connection.prepareStatement("UPDATE smart_playlists SET name = ?, rule = ? WHERE id = ?")
            .use { ps ->
                ps.setString(1, name)
                ps.setString(2, rule)
                ps.setLong(3, id)
                ps.executeUpdate()
            }
    }

    fun deleteSmart(id: Long) {
        connection.prepareStatement("DELETE FROM smart_playlists WHERE id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeUpdate()
        }
    }

    fun fxFor(path: String): String? {
        connection.prepareStatement("SELECT settings FROM track_fx WHERE path = ?").use { ps ->
            ps.setString(1, path)
            ps.executeQuery().use { rs -> return if (rs.next()) rs.getString(1) else null }
        }
    }

    fun saveFx(path: String, settings: String) {
        connection.prepareStatement(
            "INSERT INTO track_fx(path, settings) VALUES (?,?) " +
                "ON CONFLICT(path) DO UPDATE SET settings = excluded.settings"
        ).use { ps ->
            ps.setString(1, path)
            ps.setString(2, settings)
            ps.executeUpdate()
        }
    }

    fun deleteFx(path: String) {
        connection.prepareStatement("DELETE FROM track_fx WHERE path = ?").use { ps ->
            ps.setString(1, path)
            ps.executeUpdate()
        }
    }

    /** Drops every trace of files that no longer exist, after a delete. */
    fun forgetPaths(paths: Collection<String>) {
        if (paths.isEmpty()) return
        for (table in listOf(
            "play_events", "lyrics", "marks", "playlist_items", "favourites", "track_fx"
        )) {
            connection.prepareStatement("DELETE FROM $table WHERE path = ?").use { ps ->
                for (path in paths) { ps.setString(1, path); ps.addBatch() }
                ps.executeBatch()
            }
        }
    }

    private fun queryLong(sql: String): Long {
        connection.createStatement().use { st ->
            st.executeQuery(sql).use { rs -> return if (rs.next()) rs.getLong(1) else 0L }
        }
    }

    fun close() = runCatching { connection.close() }.let { }

    companion object {
        /** 30s of audio before a play counts, matching the Android build. */
        const val QUALIFYING_MS = 30_000L

        /** Interpolated into SQL, so the set of legal names is closed here. */
        val MARK_COLUMNS = setOf(
            "artCheckedAt", "identifiedAt", "lyricsCheckedAt", "fingerprintedAt"
        )

        fun pathKey(path: String): Long = path.hashCode().toLong() and 0xFFFFFFFFL
    }
}
