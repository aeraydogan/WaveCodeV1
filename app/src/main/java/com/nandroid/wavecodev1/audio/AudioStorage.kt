package com.nandroid.wavecodev1.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

private const val TAG = "AudioStorage"
private const val AUDIO_DIR = "audio"

/**
 * App-private storage for WaveCode audio files (filesDir/audio/<publicCode>.<ext>).
 *
 * Local-first: keeping a copy inside the app sandbox gives a stable, permission-free path that
 * outlives the original picked-file Uri (which may be revoked). The library JSON stores this
 * absolute path.
 */
object AudioStorage {

    private fun dir(context: Context): File =
        File(context.filesDir, AUDIO_DIR).apply { if (!exists()) mkdirs() }

    /** Destination file for a recording of [publicCode] (default m4a container). */
    fun fileForCode(context: Context, publicCode: String, ext: String = "m4a"): File =
        File(dir(context), "$publicCode.$ext")

    /**
     * Copies the audio at [uri] into app storage as <publicCode>.<ext> and returns the file,
     * or null on failure. [ext] should match the source MIME (defaults to m4a).
     */
    fun copyFromUri(context: Context, uri: Uri, publicCode: String, ext: String = "m4a"): File? {
        return try {
            val dest = fileForCode(context, publicCode, ext)
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
            Log.d(TAG, "copyFromUri: saved ${dest.absolutePath} (${dest.length()} bytes)")
            dest
        } catch (e: Exception) {
            Log.e(TAG, "copyFromUri failed", e); null
        }
    }

    /** Best-effort extension guess from a content Uri's MIME type; falls back to m4a. */
    fun extensionFor(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: return "m4a"
        return when {
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("aac") || mime.contains("mp4") || mime.contains("m4a") -> "m4a"
            mime.contains("ogg") -> "ogg"
            mime.contains("wav") -> "wav"
            else -> "m4a"
        }
    }
}