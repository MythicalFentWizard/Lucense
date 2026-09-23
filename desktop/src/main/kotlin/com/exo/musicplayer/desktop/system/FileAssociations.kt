package com.exo.musicplayer.desktop.system

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.io.File

/**
 * Putting Lucense in the list Windows offers for a music file.
 *
 * What this deliberately does not do is make itself the default. Windows 10
 * and 11 block an application from claiming a file type on its own - the user
 * has to pick it, in the Open with dialog or in Settings. An app that tried
 * anyway would either fail silently or be undone by Windows at the next
 * opportunity, so this registers itself as an option and says so plainly.
 *
 * Everything lives under HKEY_CURRENT_USER, so nothing here needs an
 * administrator and nothing is written that affects anybody else on the machine.
 */
object FileAssociations {

    /** The identity Windows knows this program by for a file type. */
    const val PROG_ID = "Lucense.Audio"

    val AUDIO = listOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "wma", "m4b", "aiff")

    private const val CLASSES = "Software\\Classes"

    private interface Shell32Ext : StdCallLibrary {
        fun SHChangeNotify(eventId: Int, flags: Int, item1: Pointer?, item2: Pointer?)
    }

    private val shell: Shell32Ext? by lazy {
        runCatching {
            Native.load("shell32", Shell32Ext::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }.getOrNull()
    }

    /**
     * The executable Windows should be told to run, or null when there is not
     * one to name.
     *
     * Started from a classpath rather than through the packaged launcher, this
     * is java.exe, and registering that would hand every MP3 on the machine to
     * a bare JVM. So it only answers for the real launcher.
     */
    fun launcher(): String? {
        val command = ProcessHandle.current().info().command().orElse(null) ?: return null
        return command.takeIf { File(it).name.equals("Lucense.exe", ignoreCase = true) }
    }

    fun registered(
        extensions: List<String> = AUDIO,
        progId: String = PROG_ID
    ): Boolean = runCatching {
        Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, "$CLASSES\\$progId") &&
            extensions.any { extension ->
                Advapi32Util.registryValueExists(
                    WinReg.HKEY_CURRENT_USER,
                    "$CLASSES\\.$extension\\OpenWithProgids",
                    progId
                )
            }
    }.getOrDefault(false)

    /**
     * Adds Lucense to the Open with list for each extension.
     *
     * The value written under OpenWithProgids is empty on purpose: Windows
     * takes the presence of the name, not anything it holds.
     */
    fun register(
        extensions: List<String> = AUDIO,
        progId: String = PROG_ID,
        exe: String? = launcher()
    ): Result<Unit> = runCatching {
        val command = exe ?: error("Lucense is not running from its installed launcher.")
        val root = WinReg.HKEY_CURRENT_USER

        Advapi32Util.registryCreateKey(root, "$CLASSES\\$progId")
        Advapi32Util.registrySetStringValue(root, "$CLASSES\\$progId", "", "Audio file")
        Advapi32Util.registryCreateKey(root, "$CLASSES\\$progId\\DefaultIcon")
        Advapi32Util.registrySetStringValue(root, "$CLASSES\\$progId\\DefaultIcon", "", "\"$command\",0")
        Advapi32Util.registryCreateKey(root, "$CLASSES\\$progId\\shell")
        Advapi32Util.registryCreateKey(root, "$CLASSES\\$progId\\shell\\open")
        Advapi32Util.registryCreateKey(root, "$CLASSES\\$progId\\shell\\open\\command")
        Advapi32Util.registrySetStringValue(
            root,
            "$CLASSES\\$progId\\shell\\open\\command",
            "",
            "\"$command\" \"%1\""
        )

        extensions.forEach { extension ->
            val key = "$CLASSES\\.$extension\\OpenWithProgids"
            Advapi32Util.registryCreateKey(root, "$CLASSES\\.$extension")
            Advapi32Util.registryCreateKey(root, key)
            Advapi32Util.registrySetStringValue(root, key, progId, "")
        }
        announce()
    }

    fun unregister(
        extensions: List<String> = AUDIO,
        progId: String = PROG_ID
    ): Result<Unit> = runCatching {
        val root = WinReg.HKEY_CURRENT_USER
        extensions.forEach { extension ->
            val key = "$CLASSES\\.$extension\\OpenWithProgids"
            runCatching {
                if (Advapi32Util.registryValueExists(root, key, progId)) {
                    Advapi32Util.registryDeleteValue(root, key, progId)
                }
            }
        }
        // Deepest first: Windows will not remove a key that still has children.
        listOf(
            "$CLASSES\\$progId\\shell\\open\\command",
            "$CLASSES\\$progId\\shell\\open",
            "$CLASSES\\$progId\\shell",
            "$CLASSES\\$progId\\DefaultIcon",
            "$CLASSES\\$progId"
        ).forEach { key ->
            runCatching {
                if (Advapi32Util.registryKeyExists(root, key)) {
                    Advapi32Util.registryDeleteKey(root, key)
                }
            }
        }
        announce()
    }

    /** Tells Explorer the associations changed, so it does not need restarting. */
    private fun announce() {
        // SHCNE_ASSOCCHANGED, SHCNF_IDLIST
        runCatching { shell?.SHChangeNotify(0x08000000, 0x0000, null, null) }
    }
}
