package com.nandroid.wavecodev1.audio

import android.media.MediaPlayer
import android.util.Log

private const val TAG = "AudioPlayer"

/**
 * Thin MediaPlayer wrapper for playing locally stored WaveCode audio files.
 *
 * One active player at a time; calling [play] stops any current playback first. Call [release]
 * when the owner (e.g. a ViewModel) is cleared.
 */
class AudioPlayer {

    private var player: MediaPlayer? = null

    val isPlaying: Boolean get() = try { player?.isPlaying == true } catch (_: Exception) { false }

    /**
     * Plays the audio file at [path]. [onCompletion] fires when playback finishes naturally.
     * Returns false if the file could not be opened.
     */
    fun play(path: String, onCompletion: () -> Unit = {}): Boolean {
        stop()
        return try {
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    Log.d(TAG, "playback complete: $path")
                    onCompletion()
                }
                prepare()
                start()
            }
            Log.d(TAG, "play: $path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "play failed: $path", e)
            stop()
            false
        }
    }

    fun stop() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    /** Alias for [stop]; releases native resources. */
    fun release() = stop()
}