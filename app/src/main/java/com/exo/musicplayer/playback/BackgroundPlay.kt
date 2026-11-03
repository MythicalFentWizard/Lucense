package com.exo.musicplayer.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Whether Android lets Lucense run in the background as it likes.
 *
 * Music playing is a foreground service, which Android itself leaves alone;
 * but battery saving still stops apps it thinks are idle, and many phones
 * add their own on top that cut a foreground service off too. And once
 * playback has paused - for a call, say - Android won't let an app that it
 * is saving battery on start playing again by itself. An app let off battery
 * saving is spared all of that.
 */
object BackgroundPlay {

    fun allowed(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)

    /**
     * Asks Android, with its own yes-or-no. Where a phone has no such
     * question it opens the list that's set in, and failing that Lucense's
     * own page in Settings, where phones like Samsung's keep their battery
     * choice for each app.
     */
    fun ask(context: Context) {
        val app = Uri.parse("package:${context.packageName}")
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app)
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
        runCatching { context.startActivity(direct) }
            .recoverCatching { context.startActivity(list) }
            .recoverCatching { context.startActivity(details) }
    }
}
