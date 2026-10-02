package com.exo.musicplayer.playback

import android.content.Context

/**
 * What was playing, where, and in what order: the queue as track ids, the
 * order it plays in (which differs from the list when shuffle is on), the song
 * it was on and how far in, and the shuffle and repeat settings.
 *
 * Android can stop the player whenever it likes once the music is paused - a
 * swipe from the recents screen, a phone short of memory - and before this
 * nothing survived that: the next launch started from an empty queue. Saved
 * as the queue changes and every few seconds while playing, so the worst a
 * sudden stop can lose is a few seconds of position.
 */
class PlaybackMemory(context: Context) {

    data class Saved(
        val trackIds: List<Long>,
        /** Indices into [trackIds] in the order they play. */
        val order: List<Int>,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeatMode: Int
    )

    private val prefs = context.applicationContext.getSharedPreferences("playback", Context.MODE_PRIVATE)

    fun save(saved: Saved) {
        prefs.edit()
            .putString(KEY_IDS, saved.trackIds.joinToString(","))
            .putString(KEY_ORDER, saved.order.joinToString(","))
            .putInt(KEY_INDEX, saved.index)
            .putLong(KEY_POSITION, saved.positionMs)
            .putBoolean(KEY_SHUFFLE, saved.shuffle)
            .putInt(KEY_REPEAT, saved.repeatMode)
            .apply()
    }

    fun load(): Saved? {
        val ids = prefs.getString(KEY_IDS, null).orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return null
        val order = prefs.getString(KEY_ORDER, null).orEmpty()
            .split(',').mapNotNull { it.toIntOrNull() }
            .takeIf { it.size == ids.size && it.toSet() == ids.indices.toSet() }
            ?: ids.indices.toList()
        return Saved(
            trackIds = ids,
            order = order,
            index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, ids.lastIndex),
            positionMs = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L),
            shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
            repeatMode = prefs.getInt(KEY_REPEAT, 0)
        )
    }

    private companion object {
        const val KEY_IDS = "queue"
        const val KEY_ORDER = "order"
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat"
    }
}
