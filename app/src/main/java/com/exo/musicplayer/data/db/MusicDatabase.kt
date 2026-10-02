package com.exo.musicplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Track::class, Playlist::class, PlaylistEntry::class,
        PlayEvent::class, Lyrics::class, SmartPlaylist::class, TrackFx::class,
        TrackPrint::class
    ],
    version = 7,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun smartPlaylistDao(): SmartPlaylistDao
    abstract fun trackFxDao(): TrackFxDao
    abstract fun trackPrintDao(): TrackPrintDao

    companion object {
        /**
         * Adds listening history. Written by hand rather than falling back to a
         * destructive migration: v1 users already have an imported library, and
         * wiping it to add a stats table would be an absurd trade.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `play_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trackId` INTEGER NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `listenedMs` INTEGER NOT NULL,
                        `hourOfDay` INTEGER NOT NULL,
                        `weatherGroup` TEXT,
                        `weatherCode` INTEGER,
                        `temperatureC` REAL,
                        `isDay` INTEGER,
                        FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_play_events_trackId` ON `play_events` (`trackId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_play_events_startedAt` ON `play_events` (`startedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_play_events_weatherGroup` ON `play_events` (`weatherGroup`)"
                )
            }
        }

        /** Adds lyrics storage; existing tracks and history are untouched. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `lyrics` (
                        `trackId` INTEGER PRIMARY KEY NOT NULL,
                        `plainText` TEXT,
                        `syncedText` TEXT,
                        `source` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        /** Adds bulk-job bookkeeping columns; nothing existing is touched. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `artCheckedAt` INTEGER")
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `identifiedAt` INTEGER")
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `lyricsCheckedAt` INTEGER")
            }
        }

        /**
         * Adds genres and smart lists.
         *
         * By hand like the rest: by now a library is somebody's own imported
         * music with its play history attached, and throwing that away to add
         * two things would be an absurd trade.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `genre` TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `smart_playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `rule` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_smart_playlists_name` " +
                        "ON `smart_playlists` (`name`)"
                )
            }
        }

        /**
         * Ratings, measured loudness, and effects kept for one song. The
         * rating's DEFAULT 0 matches the entity's declared default exactly:
         * Room compares the two after migrating and refuses to open on a
         * mismatch.
         */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `rating` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `levelDb` REAL")
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `levelPeak` REAL")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_fx` " +
                        "(`trackId` INTEGER NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`trackId`))"
                )
            }
        }

        /** Sound prints, for finding duplicates by how songs sound rather than what they're called. */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_prints` " +
                        "(`trackId` INTEGER NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`trackId`))"
                )
            }
        }

        @Volatile
        private var instance: MusicDatabase? = null

        fun get(context: Context): MusicDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
    }
}
