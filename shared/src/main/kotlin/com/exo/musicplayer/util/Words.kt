package com.exo.musicplayer.util

/** "1 song", "2 songs": a count with its noun, singular when there's one. */
fun counted(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"
