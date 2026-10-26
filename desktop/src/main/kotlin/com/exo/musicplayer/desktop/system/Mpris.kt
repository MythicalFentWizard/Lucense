@file:Suppress("FunctionName") // D-Bus members are named as the MPRIS specification names them.

package com.exo.musicplayer.desktop.system

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant

/**
 * Lucense as a media player the Linux desktop knows: MPRIS, over D-Bus.
 *
 * On Linux the keyboard's media keys aren't keys an app can take for itself,
 * as it can on Windows. The desktop (GNOME, KDE, Cinnamon and the rest) sends
 * them to the media player that announced itself on D-Bus. Announced, Lucense
 * gets them, and shows in the desktop's media controls with its song, its
 * cover, and play, pause, next and previous.
 */
class Mpris(private val actions: Actions) {

    /** What the desktop can ask for; each is called on a D-Bus thread and must not block. */
    interface Actions {
        fun playPause()
        fun play()
        fun pause()
        fun next()
        fun previous()
        fun stop()
        fun seekTo(ms: Long)
        fun positionMs(): Long
    }

    /** The song as the desktop shows it. */
    data class NowPlaying(
        /** Anything that tells songs apart: the file's path. */
        val key: String? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val lengthMs: Long = 0L,
        val playing: Boolean = false,
        /** A `file://` address of the cover. */
        val artUrl: String? = null,
        val canGoNext: Boolean = true,
        val canGoPrevious: Boolean = true
    )

    @Volatile private var connection: DBusConnection? = null
    @Volatile private var now = NowPlaying()
    private var lastPositionMs = 0L
    private var lastPositionAt = 0L

    /** Whether the desktop can see Lucense. */
    val running: Boolean get() = connection != null

    /** Why the last start didn't work, or null. */
    @Volatile var problem: Throwable? = null
        private set

    /** Announces Lucense on the session bus; false where there is none to announce on. */
    @Synchronized
    fun start(): Boolean {
        if (connection != null) return true
        return runCatching {
            val bus = sessionBus()
            // A second Lucense takes a name of its own, as the specification asks.
            val name = runCatching { bus.requestBusName(BUS_NAME); BUS_NAME }.getOrElse {
                val other = "$BUS_NAME.instance${ProcessHandle.current().pid()}"
                bus.requestBusName(other)
                other
            }
            bus.exportObject(PATH, Exported())
            connection = bus
            name
        }.onFailure { problem = it }.onSuccess { problem = null }.isSuccess
    }

    /**
     * The session bus. Its address may name an abstract socket (dbus-launch
     * and dbus-run-session give one), which Java's own Unix sockets can't
     * open; a systemd desktop's bus is also at /run/user/<uid>/bus, which they can.
     */
    private fun sessionBus(): DBusConnection {
        val address = System.getenv("DBUS_SESSION_BUS_ADDRESS").orEmpty()
        if ("abstract=" in address || address.isBlank()) {
            val uid = runCatching {
                java.io.File("/proc/self/status").readLines().first { it.startsWith("Uid:") }.split('\t', ' ').filter { it.isNotEmpty() }[1]
            }.getOrNull()
            val socket = uid?.let { java.io.File("/run/user/$it/bus") }
            if (socket != null && socket.exists()) {
                return DBusConnectionBuilder.forAddress("unix:path=" + socket.path).build()
            }
        }
        return DBusConnectionBuilder.forSessionBus().build()
    }

    @Synchronized
    fun stop() {
        val bus = connection ?: return
        connection = null
        runCatching { bus.unExportObject(PATH) }
        runCatching { bus.close() }
    }

    /** What is playing now; the desktop is told what changed. */
    fun update(next: NowPlaying, positionMs: Long) {
        val before = now
        now = next
        val bus = connection ?: return
        val changed = LinkedHashMap<String, Variant<*>>()
        if (before.playing != next.playing || (before.key == null) != (next.key == null)) {
            changed["PlaybackStatus"] = Variant(status(next))
        }
        if (before.key != next.key || before.title != next.title || before.artist != next.artist ||
            before.album != next.album || before.artUrl != next.artUrl || before.lengthMs != next.lengthMs
        ) {
            changed["Metadata"] = Variant(metadata(next), "a{sv}")
        }
        if (before.canGoNext != next.canGoNext) changed["CanGoNext"] = Variant(next.canGoNext)
        if (before.canGoPrevious != next.canGoPrevious) changed["CanGoPrevious"] = Variant(next.canGoPrevious)
        if (changed.isNotEmpty()) {
            runCatching { bus.sendMessage(Properties.PropertiesChanged(PATH, PLAYER, changed, emptyList())) }
        }

        // The desktop works the position out from the time that passed; a jump is announced.
        val nowAt = System.currentTimeMillis()
        val expected = lastPositionMs + if (before.playing) nowAt - lastPositionAt else 0L
        if (before.key == next.key && next.key != null && kotlin.math.abs(positionMs - expected) > SEEK_THRESHOLD_MS) {
            runCatching { bus.sendMessage(MediaPlayer2Player.Seeked(PATH, positionMs * 1000L)) }
        }
        lastPositionMs = positionMs
        lastPositionAt = nowAt
    }

    private fun status(of: NowPlaying): String = when {
        of.key == null -> "Stopped"
        of.playing -> "Playing"
        else -> "Paused"
    }

    private fun trackId(of: NowPlaying): DBusPath =
        DBusPath(if (of.key == null) NO_TRACK else "/org/lucense/track/t" + Integer.toHexString(of.key.hashCode()).padStart(8, '0'))

    private fun metadata(of: NowPlaying): Map<String, Variant<*>> {
        val map = LinkedHashMap<String, Variant<*>>()
        map["mpris:trackid"] = Variant(trackId(of), "o")
        if (of.key == null) return map
        of.title?.let { map["xesam:title"] = Variant(it) }
        of.artist?.let { map["xesam:artist"] = Variant(listOf(it), "as") }
        of.album?.let { map["xesam:album"] = Variant(it) }
        if (of.lengthMs > 0) map["mpris:length"] = Variant(of.lengthMs * 1000L)
        of.artUrl?.let { map["mpris:artUrl"] = Variant(it) }
        return map
    }

    /** Every property of [face], as the desktop reads them. */
    private fun properties(face: String): Map<String, Variant<*>> = when (face) {
        ROOT -> linkedMapOf(
            "CanQuit" to Variant(false),
            "CanRaise" to Variant(false),
            "HasTrackList" to Variant(false),
            "Identity" to Variant("Lucense"),
            "DesktopEntry" to Variant(DESKTOP_ENTRY),
            "SupportedUriSchemes" to Variant(listOf("file"), "as"),
            "SupportedMimeTypes" to Variant(MIME_TYPES, "as")
        )
        PLAYER -> {
            val current = now
            val playable = current.key != null
            linkedMapOf(
                "PlaybackStatus" to Variant(status(current)),
                "Rate" to Variant(1.0),
                "MinimumRate" to Variant(1.0),
                "MaximumRate" to Variant(1.0),
                "Metadata" to Variant(metadata(current), "a{sv}"),
                "Volume" to Variant(1.0),
                "Position" to Variant(actions.positionMs() * 1000L),
                "CanGoNext" to Variant(current.canGoNext),
                "CanGoPrevious" to Variant(current.canGoPrevious),
                "CanPlay" to Variant(playable),
                "CanPause" to Variant(playable),
                "CanSeek" to Variant(playable),
                "CanControl" to Variant(true)
            )
        }
        else -> throw DBusExecutionException("No such interface: $face")
    }

    @DBusInterfaceName(ROOT)
    interface MediaPlayer2 : DBusInterface {
        fun Raise()
        fun Quit()
    }

    @DBusInterfaceName(PLAYER)
    interface MediaPlayer2Player : DBusInterface {
        fun Next()
        fun Previous()
        fun Pause()
        fun PlayPause()
        fun Stop()
        fun Play()
        fun Seek(offset: Long)
        fun SetPosition(trackId: DBusPath, position: Long)
        fun OpenUri(uri: String)

        /** Sent when the position jumps: a seek, from here or anywhere else. */
        class Seeked(path: String, val position: Long) : DBusSignal(path, position)
    }

    /** The one object the desktop talks to. */
    private inner class Exported : MediaPlayer2, MediaPlayer2Player, Properties {
        override fun getObjectPath(): String = PATH

        override fun Raise() = Unit
        override fun Quit() = Unit

        override fun Next() = actions.next()
        override fun Previous() = actions.previous()
        override fun Pause() = actions.pause()
        override fun PlayPause() = actions.playPause()
        override fun Stop() = actions.stop()
        override fun Play() = actions.play()

        override fun Seek(offset: Long) {
            if (now.key == null) return
            actions.seekTo((actions.positionMs() + offset / 1000L).coerceAtLeast(0L))
        }

        override fun SetPosition(trackId: DBusPath, position: Long) {
            // Only for the song it was meant for, as the specification says: it may have changed meanwhile.
            if (trackId.path != trackId(now).path || position < 0) return
            val length = now.lengthMs
            if (length > 0 && position / 1000L > length) return
            actions.seekTo(position / 1000L)
        }

        override fun OpenUri(uri: String) = Unit

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
            (properties(interfaceName)[propertyName] ?: throw DBusExecutionException("No such property: $propertyName")) as A

        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) = Unit

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties(interfaceName)
    }

    companion object {
        const val BUS_NAME = "org.mpris.MediaPlayer2.lucense"
        const val PATH = "/org/mpris/MediaPlayer2"
        const val ROOT = "org.mpris.MediaPlayer2"
        const val PLAYER = "org.mpris.MediaPlayer2.Player"
        private const val NO_TRACK = "/org/mpris/MediaPlayer2/TrackList/NoTrack"

        /** The installed menu entry's name, without .desktop: jpackage's package-name, a dash, the app's name. */
        const val DESKTOP_ENTRY = "lucense-Lucense"

        /** A jump in position bigger than this is a seek, not the clock drifting. */
        private const val SEEK_THRESHOLD_MS = 1_500L

        private val MIME_TYPES = listOf(
            "audio/mpeg", "audio/flac", "audio/x-flac", "audio/ogg", "audio/x-vorbis+ogg", "audio/opus",
            "audio/mp4", "audio/x-m4a", "audio/aac", "audio/wav", "audio/x-wav", "audio/aiff", "audio/x-aiff"
        )
    }
}
