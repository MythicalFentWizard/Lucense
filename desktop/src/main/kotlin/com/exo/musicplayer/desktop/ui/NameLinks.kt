package com.exo.musicplayer.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.exo.musicplayer.desktop.data.TrackGroups
import com.exo.musicplayer.desktop.library.DesktopTrack

/**
 * A song's artist credit with each artist in it a link to their page, by card
 * key. It reads exactly as tagged: "A feat. B" keeps its "feat.", and only the
 * names take a click.
 */
@Composable
fun artistLinks(track: DesktopTrack, open: (key: String) -> Unit): AnnotatedString =
    linkedNames(track.displayArtist, TrackGroups.splitArtists(track.artist)) { open(TrackGroups.artistGroupKey(it)) }

/** A song's album, a link to the album's page by card key; plain for a song with no album. */
@Composable
fun albumLink(track: DesktopTrack, open: (key: String) -> Unit): AnnotatedString {
    val key = TrackGroups.albumGroupKey(track) ?: return AnnotatedString(track.displayAlbum)
    return linkedNames(track.displayAlbum, listOf(track.displayAlbum)) { open(key) }
}

/**
 * [text] with each of [names] in it a link, found in the order given; one not
 * found is left plain. A link is underlined and lit under the mouse, and takes
 * the click, so a row it sits in does not also play its song.
 */
@Composable
private fun linkedNames(text: String, names: List<String>, open: (name: String) -> Unit): AnnotatedString {
    val styles = TextLinkStyles(hoveredStyle = SpanStyle(color = Palette.Text, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        var at = 0
        for (name in names) {
            val start = text.indexOf(name, at)
            if (start < 0) continue
            append(text, at, start)
            withLink(LinkAnnotation.Clickable(name, styles) { open(name) }) { append(name) }
            at = start + name.length
        }
        append(text, at, text.length)
    }
}
