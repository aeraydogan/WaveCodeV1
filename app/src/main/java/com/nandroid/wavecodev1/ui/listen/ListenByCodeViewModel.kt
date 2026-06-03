package com.nandroid.wavecodev1.ui.listen

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nandroid.wavecodev1.audio.WaveCodeAudioController
import com.nandroid.wavecodev1.net.WaveCodeNetworkResult
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
 * the audio. Resolve + playback are delegated to the shared [WaveCodeAudioController].
 */
class ListenByCodeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ListenUiState())
    val uiState: StateFlow<ListenUiState> = _uiState.asStateFlow()

    private val audio = WaveCodeAudioController().apply {
        onPlayingChanged   = { playing -> _uiState.update { it.copy(isPlaying = playing) } }
        onBufferingChanged = { buffering -> _uiState.update { it.copy(isBuffering = buffering) } }
        onError            = { msg -> _uiState.update { it.copy(isPlaying = false, isBuffering = false, error = msg) } }
    }

    /** Sanitizes input: uppercase, only the WaveCode alphabet, max 8 chars. */
    fun onCodeChange(input: String) {
        val cleaned = input.uppercase()
            .filter { WaveCodeSpec.ALPHABET.contains(it) }
            .take(WaveCodeSpec.PAYLOAD_LENGTH)
        _uiState.update {
            // Changing the code invalidates any previously resolved result.
            it.copy(code = cleaned, error = null, resolved = false, title = null, durationLabel = null, publicCode = null)
        }
        audio.stop()
    }

    /** Resolve the code, then prepare + start playback. */
    fun listen(context: Context) {
        val code = _uiState.value.code
        if (code.length != WaveCodeSpec.PAYLOAD_LENGTH) {
            _uiState.update { it.copy(error = "8 karakterlik kodu girin.") }
            return
        }
        if (_uiState.value.isLoading) return
        val appContext = context.applicationContext

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, resolved = false) }
            Log.d(TAG, "resolve requested: $code")
            when (val result = audio.resolve(code)) {
                is WaveCodeNetworkResult.Success -> {
                    val meta = result.data
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
                    audio.prepareAndPlay(appContext, meta)
                }
                is WaveCodeNetworkResult.Failure -> {
                    Log.w(TAG, "resolve failure: kind=${result.kind} http=${result.httpCode}")
                    _uiState.update { it.copy(isLoading = false, resolved = false, error = resolveErrorMessage(result)) }
                }
            }
        }
    }

    fun togglePlayPause() = audio.togglePlayPause()

    override fun onCleared() {
        audio.release()
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