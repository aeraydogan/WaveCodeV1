package com.nandroid.wavecodev1.audio

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.nandroid.wavecodev1.net.VoiceCodeMetadata
import com.nandroid.wavecodev1.net.WaveCodeBackendConfig
import com.nandroid.wavecodev1.net.WaveCodeNetworkResult
import com.nandroid.wavecodev1.net.WaveCodeRemoteRepository

private const val TAG = "WaveCodeAudioCtrl"

/**
 * Shared resolve + stream-playback logic for any screen that turns a publicCode into playing audio
 * (manual "Listen by code" and gallery "Tara & Dinle").
 *
 * Resolve hits `GET /api/voice-codes/{publicCode}`; playback streams with ExoPlayer. The stream URL
 * is ALWAYS built on the client via [WaveCodeBackendConfig.audioUrl] — the server-provided audioUrl
 * (which may be localhost) is never trusted for playback.
 *
 * Callbacks report player state to the owning ViewModel; assign them before calling [prepareAndPlay].
 */
class WaveCodeAudioController {

    private val remote = WaveCodeRemoteRepository.get()
    private var player: ExoPlayer? = null
    private var currentUrl: String? = null

    var onPlayingChanged: (Boolean) -> Unit = {}
    var onBufferingChanged: (Boolean) -> Unit = {}
    var onError: (String) -> Unit = {}

    /** Resolves a publicCode to its metadata via the backend. */
    suspend fun resolve(publicCode: String): WaveCodeNetworkResult<VoiceCodeMetadata> =
        remote.resolve(publicCode)

    /** Builds the client-side stream URL for [meta] and starts playback. */
    fun prepareAndPlay(appContext: Context, meta: VoiceCodeMetadata) {
        // Build the stream URL on the client — do NOT use meta.audioUrl (may be localhost).
        val url = WaveCodeBackendConfig.audioUrl(meta.publicCode)
        Log.d(TAG, "built audioPlayUrl=$url (server audioUrl=${meta.audioUrl})")
        currentUrl = url
        val p = ensurePlayer(appContext)
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
    }

    /** Starts (or resumes) playback of [meta]; prepares the stream first if not already loaded. */
    fun start(appContext: Context, meta: VoiceCodeMetadata) {
        val url = WaveCodeBackendConfig.audioUrl(meta.publicCode)
        val p = player
        if (p != null && currentUrl == url && p.playbackState != Player.STATE_IDLE) {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play()
        } else {
            prepareAndPlay(appContext, meta)
        }
    }

    /** Restarts playback of [meta] from the beginning. */
    fun restart(appContext: Context, meta: VoiceCodeMetadata) {
        val url = WaveCodeBackendConfig.audioUrl(meta.publicCode)
        val p = player
        if (p != null && currentUrl == url && p.playbackState != Player.STATE_IDLE) {
            p.seekTo(0)
            p.play()
        } else {
            prepareAndPlay(appContext, meta)
        }
    }

    /**
     * Play/pause toggle that also recovers from terminal states: replays from the start when the
     * track has ended, and re-prepares the stream if the player went idle (e.g. a transient error).
     */
    fun togglePlayPause() {
        val p = player ?: return
        when (p.playbackState) {
            Player.STATE_ENDED -> { p.seekTo(0); p.play() }
            Player.STATE_IDLE -> {
                currentUrl?.let { p.setMediaItem(MediaItem.fromUri(it)) }
                p.prepare()
                p.play()
            }
            else -> if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun stop() {
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
        onPlayingChanged(false)
        onBufferingChanged(false)
    }

    fun release() {
        player?.release()
        player = null
    }

    @OptIn(UnstableApi::class)
    private fun ensurePlayer(appContext: Context): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(appContext).build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "player isPlaying=$isPlaying")
                onPlayingChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(state: Int) {
                Log.d(TAG, "player state=$state")
                onBufferingChanged(state == Player.STATE_BUFFERING)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "player error: ${error.errorCodeName} ${error.message}")
                onError("Ses oynatılamadı. Tekrar deneyin.")
            }
        })
        player = p
        return p
    }
}