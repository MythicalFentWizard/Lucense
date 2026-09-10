package com.exo.musicplayer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One listening occasion. Written when a track starts and updated as it plays,
 * so the library knows not just *that* a song was played but when, for how long,
 * and what the weather was doing at the time.
 */
@Entity(
    tableName = "play_events",
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["startedAt"]),
        Index(value = ["weatherGroup"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Track::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: Long,
    val startedAt: Long,
    /** Actual audio time heard, accumulated while playing. */
    val listenedMs: Long = 0L,
    /** Local hour 0-23, for the time-of-day breakdown. */
    val hourOfDay: Int,
    /** [WeatherCondition] name, or null when weather was unavailable. */
    val weatherGroup: String? = null,
    val weatherCode: Int? = null,
    val temperatureC: Double? = null,
    val isDay: Boolean? = null
)

/** Re-exported so DAO signatures keep the short name. */
typealias TrackPlayCount = com.exo.musicplayer.data.model.TrackPlayCount

/** Aggregate row: total time spent on a track. */
data class TrackListenTime(val trackId: Long, val totalMs: Long, val plays: Int)

/** Aggregate row: plays bucketed by hour of day. */
data class HourBucket(val hourOfDay: Int, val plays: Int)

/** Aggregate row: plays bucketed by weather group. */
data class WeatherBucket(val weatherGroup: String?, val plays: Int)
