package com.exo.musicplayer.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher

/**
 * Opens a system file or photo picker, or says there isn't one. Some phones
 * ship without a Files app, or have it switched off, and launching a picker
 * there throws instead of opening anything - which used to close Lucense.
 */
fun <I> ActivityResultLauncher<I>.launchOrExplain(input: I, context: Context) {
    try {
        launch(input)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "This phone has no file picker to open.", Toast.LENGTH_LONG).show()
    }
}
