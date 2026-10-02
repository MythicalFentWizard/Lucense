package com.exo.musicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder

/**
 * The queue, worked on the player directly.
 *
 * With shuffle on, the order songs play in is not the order of the list, and a
 * song added to the list lands wherever the shuffle puts it - so "Play next"
 * used to mean "play at some random point". Everything here edits the play
 * order itself, which only the player can do; a MediaController can't.
 *
 * Positions handed in and out are positions in the play order, which is what
 * the Up next panel shows.
 */
@UnstableApi
object QueueOps {

    /** Item indices in the order they will play, current one included. */
    fun order(player: Player): List<Int> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val shuffled = player.shuffleModeEnabled
        val result = ArrayList<Int>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(shuffled)
        while (index != C.INDEX_UNSET && result.size < timeline.windowCount) {
            result += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
        }
        return result
    }

    /** Replaces the queue and starts at [start]; with shuffle on, the rest is shuffled after it. */
    fun play(player: ExoPlayer, items: List<MediaItem>, start: Int, positionMs: Long = 0L) {
        if (items.isEmpty()) return
        val first = start.coerceIn(0, items.lastIndex)
        player.setMediaItems(items, first, positionMs)
        if (player.shuffleModeEnabled) shuffleAroundCurrent(player)
        player.prepare()
        player.play()
    }

    /**
     * The current song first, everything else shuffled after it - so turning
     * shuffle on never interrupts what is playing, and nothing already heard
     * is sitting "before" it where Previous would find it.
     */
    fun shuffleAroundCurrent(player: ExoPlayer) {
        val count = player.mediaItemCount
        if (count == 0) return
        val current = player.currentMediaItemIndex.coerceIn(0, count - 1)
        val rest = (0 until count).filter { it != current }.shuffled()
        setOrder(player, listOf(current) + rest)
    }

    fun setOrder(player: ExoPlayer, order: List<Int>) {
        if (order.size != player.mediaItemCount) return
        player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
    }

    /** Ids of the songs still to come, so adding one again moves it rather than doubling it. */
    private fun removeWaiting(player: ExoPlayer, ids: Set<String>) {
        val order = order(player)
        val currentPos = order.indexOf(player.currentMediaItemIndex)
        val waiting = order.drop(currentPos + 1)
            .filter { player.getMediaItemAt(it).mediaId in ids }
            .sortedDescending()
        waiting.forEach { player.removeMediaItem(it) }
    }

    /** Puts [items] straight after the song playing, in the order given. */
    fun playNext(player: ExoPlayer, items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (player.mediaItemCount == 0) {
            play(player, items, 0)
            return
        }
        removeWaiting(player, items.map { it.mediaId }.toSet())
        val shuffled = player.shuffleModeEnabled
        val before = if (shuffled) order(player) else emptyList()
        val insertAt = player.currentMediaItemIndex + 1
        player.addMediaItems(insertAt, items)
        if (shuffled) {
            // Old indices at or past the insertion point have moved up by the
            // number inserted; the new ones go right after the current song.
            val shifted = before.map { if (it >= insertAt) it + items.size else it }
            val currentPos = shifted.indexOf(player.currentMediaItemIndex)
            val added = items.indices.map { insertAt + it }
            setOrder(player, shifted.take(currentPos + 1) + added + shifted.drop(currentPos + 1))
        }
    }

    /** Puts [items] at the end of what is to come. */
    fun addToQueue(player: ExoPlayer, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val wasEmpty = player.mediaItemCount == 0
        removeWaiting(player, items.map { it.mediaId }.toSet())
        val shuffled = player.shuffleModeEnabled
        val before = if (shuffled) order(player) else emptyList()
        val start = player.mediaItemCount
        player.addMediaItems(items)
        if (shuffled) setOrder(player, before + items.indices.map { start + it })
        if (wasEmpty) player.prepare()
    }

    /** Moves the song at play-order position [from] to [to]; neither may be the current one. */
    fun move(player: ExoPlayer, from: Int, to: Int) {
        val order = order(player)
        if (from !in order.indices || to !in order.indices || from == to) return
        if (player.shuffleModeEnabled) {
            val next = order.toMutableList()
            next.add(to, next.removeAt(from))
            setOrder(player, next)
        } else {
            // Without shuffle the play order is the list, so the list itself moves.
            player.moveMediaItem(order[from], order[to])
        }
    }

    fun remove(player: ExoPlayer, position: Int) {
        val order = order(player)
        val index = order.getOrNull(position) ?: return
        if (index == player.currentMediaItemIndex) return
        player.removeMediaItem(index)
    }

    /** Empties everything but the song playing. */
    fun clearUpcoming(player: ExoPlayer) {
        val count = player.mediaItemCount
        if (count <= 1) return
        val current = player.currentMediaItemIndex
        if (current + 1 < count) player.removeMediaItems(current + 1, count)
        if (current > 0) player.removeMediaItems(0, current)
    }

    fun jumpTo(player: ExoPlayer, position: Int) {
        val index = order(player).getOrNull(position) ?: return
        player.seekTo(index, 0L)
        player.play()
    }
}
