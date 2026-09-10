package com.exo.musicplayer.share

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

object SharedUris {

    /**
     * Pulls the audio URIs out of whatever the sharing app sent us. Telegram uses
     * EXTRA_STREAM for shares and [Intent.getData] for "Open with"; some senders
     * only populate ClipData, so that is checked as a fallback.
     */
    fun from(intent: Intent): List<Uri> {
        val fromExtras = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                )

            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(
                    intent, Intent.EXTRA_STREAM, Uri::class.java
                ).orEmpty().filterNotNull()

            Intent.ACTION_VIEW -> listOfNotNull(intent.data)

            else -> emptyList()
        }
        if (fromExtras.isNotEmpty()) return fromExtras.distinct()

        val clip = intent.clipData ?: return emptyList()
        return (0 until clip.itemCount)
            .mapNotNull { clip.getItemAt(it).uri }
            .distinct()
    }

    /** Resolves "org.telegram.messenger" to "Telegram" for the library listing. */
    fun sourceLabel(activity: Activity): String? {
        val packageName = activity.referrer
            ?.takeIf { it.scheme == "android-app" }
            ?.host
            ?: return null
        val packageManager = activity.packageManager
        return runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrNull() ?: packageName
    }
}
