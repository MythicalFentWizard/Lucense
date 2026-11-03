package com.exo.musicplayer.data.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Translations kept once they have been fetched, so a song translated before
 * shows its translation again the moment it plays, without asking Google.
 *
 * One small file per lyrics and language, named for a hash of both: lyrics
 * corrected or replaced are new lyrics, and get translated afresh. Each file
 * remembers whether the translation was left showing, so hiding one keeps it
 * hidden next time, while it stays saved for when it's wanted again.
 */
class SavedTranslations(private val folder: File) {

    /** A translation as saved: one line for each line of the lyrics, the language it was from, and whether it shows. */
    data class Saved(val lines: List<String>, val from: String?, val shown: Boolean)

    fun get(lyrics: List<String>, language: String): Saved? = runCatching {
        val file = fileFor(lyrics, language)
        if (!file.isFile) return null
        val json = JSONObject(file.readText())
        val array = json.getJSONArray("lines")
        val lines = List(array.length()) { array.optString(it) }
        // A file from other lyrics that happened to hash alike, or cut short: not this translation.
        if (lines.size != lyrics.size) return null
        Saved(lines, json.optString("from").takeIf { it.isNotBlank() }, json.optBoolean("shown", true))
    }.getOrNull()

    fun put(lyrics: List<String>, language: String, translation: LyricsTranslator.Translation, shown: Boolean = true) {
        runCatching {
            folder.mkdirs()
            val json = JSONObject()
                .put("lines", JSONArray(translation.lines))
                .put("from", translation.from ?: "")
                .put("shown", shown)
            // Written beside and moved over, so a crash mid-write never leaves half a file.
            val file = fileFor(lyrics, language)
            val partial = File(folder, file.name + ".part")
            partial.writeText(json.toString())
            if (!partial.renameTo(file)) {
                file.delete()
                partial.renameTo(file)
            }
        }
    }

    /** Shows or hides a saved translation next time its lyrics come up; nothing happens to one never saved. */
    fun setShown(lyrics: List<String>, language: String, shown: Boolean) {
        val saved = get(lyrics, language) ?: return
        if (saved.shown != shown) put(lyrics, language, LyricsTranslator.Translation(saved.lines, saved.from), shown)
    }

    private fun fileFor(lyrics: List<String>, language: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(language.lowercase().toByteArray())
        digest.update(0)
        // Spacing at either end of a line doesn't make it other lyrics.
        digest.update(lyrics.joinToString("\n") { it.trim() }.toByteArray())
        val name = digest.digest().take(16).joinToString("") { "%02x".format(it) }
        return File(folder, "$name.json")
    }
}
