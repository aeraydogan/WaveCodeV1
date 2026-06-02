package com.nandroid.wavecodev1.ui.listen

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.nandroid.wavecodev1.net.VoiceCodeMetadata
import com.nandroid.wavecodev1.net.WaveCodeBackendConfig
import com.nandroid.wavecodev1.net.WaveCodeNetworkResult
import com.nandroid.wavecodev1.net.WaveCodeRemoteRepository
import com.nandroid.wavecodev1.wavecode.WaveCodeSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListenUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    val resolved: Boolean = false,
    val title: String? = null,
    val durationLabel: String? = null,
    val publicCode: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val error: String? = null
) {
    /** A query is only allowed once the full 8-char code is entered. */
    val canListen: Boolean get() = code.length == WaveCodeSpec.PAYLOAD_LENGTH && !isLoading
}

/**
 * Drives the "Listen by code" screen: resolve a publicCode to metadata via the backend, then stream
 * the audio with ExoPlayer. The playback URL is built on the client with
 * [WaveCodeBackendConfig.audioUrl] — the server-provided audioUrl is never trusted for playback.
 */
class ListenByCodeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ListenUiState())
    val uiState: StateFlow<ListenUiState> = _uiState.asStateFlow()

    private val remote = WaveCodeRemoteRepository.get()
    private var player: ExoPlayer? = null

    /** Sanitizes input: uppercase, only the WaveCode alphabet, max 8 chars. */
    fun onCodeChange(input: String) {
        val cleaned = input.uppercase()
            .filter { WaveCodeSpec.ALPHABET.contains(it) }
            .take(WaveCodeSpec.PAYLOAD_LENGTH)
        _uiState.update {
            // Changing the code invalidates any previously resolved result.
            it.copy(code = cleaned, error = null, resolved = false, title = null, durationLabel = null, publicCode = null)
        }
        stopPlayback()
    }

    /** Resolve the code, then prepare + start playback. */
    fun listen(context: Context) {
        val code = _uiState.value.code
        if (code.length != WaveCodeSpec.PAYLOAD_LENGTH) {
            _uiState.update { it.copy(error = "8 karakterlik kodu girin.") }
            return
        }
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, resolved = false) }
            Log.d(TAG, "resolve requested: $code")
            when (val result = remote.resolve(code)) {
                is WaveCodeNetworkResult.Success -> {
                    val meta = result.data
                    Log.d(TAG, "resolve success: ${meta.publicCode} title=${meta.title} durationMs=${meta.durationMs}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            resolved = true,
                            title = meta.title,
                            durationLabel = formatDuration(meta.durationMs),
                            publicCode = meta.publicCode,
                            error = null
                        )
                    }
                    preparePlayer(context.applicationContext, meta)
                }
                is WaveCodeNetworkResult.Failure -> {
                    Log.w(TAG, "resolve failure: kind=${result.kind} http=${result.httpCode}")
                    _uiState.update { it.copy(isLoading = false, resolved = false, error = resolveErrorMessage(result)) }
                }
            }
        }
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    @OptIn(UnstableApi::class)
    private fun preparePlayer(appContext: Context, meta: VoiceCodeMetadata) {
        // Build the stream URL on the client — do NOT use meta.audioUrl (may be localhost).
        val url = WaveCodeBackendConfig.audioUrl(meta.publicCode)
        Log.d(TAG, "built audioPlayUrl=$url (server audioUrl=${meta.audioUrl})")

        val p = ensurePlayer(appContext)
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
    }

    private fun ensurePlayer(appContext: Context): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(appContext).build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "player isPlaying=$isPlaying")
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(state: Int) {
                Log.d(TAG, "player state=$state")
                _uiState.update { it.copy(isBuffering = state == Player.STATE_BUFFERING) }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "player error: ${error.errorCodeName} ${error.message}")
                _uiState.update {
                    it.copy(isPlaying = false, isBuffering = false, error = "Ses oynatılamadı. Tekrar deneyin.")
                }
            }
        })
        player = p
        return p
    }

    private fun stopPlayback() {
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
        _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
    }

    override fun onCleared() {
        player?.release()
        player = null
    }

    private fun resolveErrorMessage(failure: WaveCodeNetworkResult.Failure): String = when (failure.kind) {
        WaveCodeNetworkResult.Kind.NotFound -> "Bu kod bulunamadı."
        WaveCodeNetworkResult.Kind.Network ->
            "Sunucuya ulaşılamadı. Telefon ve bilgisayarın aynı Wi-Fi ağında olduğundan emin olun."
        WaveCodeNetworkResult.Kind.Timeout -> "İstek zaman aşımına uğradı. Tekrar deneyin."
        WaveCodeNetworkResult.Kind.Server -> "Sunucu hatası. Daha sonra tekrar deneyin."
        WaveCodeNetworkResult.Kind.Malformed -> "Sunucudan beklenmeyen bir yanıt geldi."
        else -> "Kod çözümlenemedi. Tekrar deneyin."
    }

    private fun formatDuration(durationMs: Long?): String? {
        if (durationMs == null || durationMs <= 0) return null
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private companion object {
        const val TAG = "WaveCodeListen"
    }
}