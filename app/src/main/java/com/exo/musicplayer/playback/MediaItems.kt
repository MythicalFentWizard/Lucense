package com.exo.musicplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.exo.musicplayer.data.db.Track
import java.io.File

/** Media3 addresses items by string id; ours is the Room row id. */
fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(Uri.fromFile(File(filePath)))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(albumArtist)
            .setTrackNumber(trackNumber)
            .setRecordingYear(year)
            .setArtworkUri(artPath?.let { Uri.fromFile(File(it)) })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
    )
    .build()

fun MediaItem.trackId(): Long? = mediaId.toLongOrNull()
