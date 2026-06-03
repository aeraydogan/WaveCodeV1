package com.nandroid.wavecodev1.util

/** Formats a duration in milliseconds as "m:ss", or null when unknown/non-positive. */
fun formatDuration(durationMs: Long?): String? {
    if (durationMs == null || durationMs <= 0) return null
    val totalSeconds = durationMs / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}