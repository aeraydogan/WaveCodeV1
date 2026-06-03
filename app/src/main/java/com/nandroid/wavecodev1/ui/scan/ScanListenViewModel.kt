package com.nandroid.wavecodev1.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nandroid.wavecodev1.audio.WaveCodeAudioController
import com.nandroid.wavecodev1.net.WaveCodeNetworkResult
import com.nandroid.wavecodev1.wavecode.decode.WaveCodeDecodeResult
import com.nandroid.wavecodev1.wavecode.decode.WaveCodeDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ScanListenVM"

data class ScanListenUiState(
    val isDecoding: Boolean = false,          // "WaveCode okunuyor..."
    val decodeError: String? = null,          // decode failed
    val publicCode: String? = null,           // decoded "Ses Kodu"
    val isResolving: Boolean = false,         // "Ses bulunuyor..."
    val resolveError: String? = null,
    val resolved: Boolean = false,
    val title: String? = null,
    val durationLabel: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false
)

/**
 * Drives the "Tara & Dinle" screen: pick a WaveCode image from the gallery → decode it with the
 * existing [WaveCodeDecoder] → resolve the publicCode against the backend and stream the audio.
 *
 * Resolve + playback are delegated to the shared [WaveCodeAudioController] (same logic the manual
 * "Listen by code" screen uses) via [resolveAndPrepareAudio].
 */
class ScanListenViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScanListenUiState())
    val uiState: StateFlow<ScanListenUiState> = _uiState.asStateFlow()

    private val audio = WaveCodeAudioController().apply {
        onPlayingChanged   = { playing -> _uiState.update { it.copy(isPlaying = playing) } }
        onBufferingChanged = { buffering -> _uiState.update { it.copy(isBuffering = buffering) } }
        onError            = { msg -> _uiState.update { it.copy(isPlaying = false, isBuffering = false, resolveError = msg) } }
    }

    /** Loads + decodes the picked image, then resolves and plays on success. */
    fun onImagePicked(context: Context, uri: Uri) {
        if (_uiState.value.isDecoding) return
        audio.stop()
        _uiState.value = ScanListenUiState(isDecoding = true)
        val appContext = context.applicationContext

        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                val bitmap = loadFullBitmap(appContext, uri)
                    ?: return@withContext null
                val result = WaveCodeDecoder.decode(bitmap)
                bitmap.recycle()
                result
            }

            when (decoded) {
                is WaveCodeDecodeResult.Success -> {
                    Log.d(TAG, "decode success: ${decoded.publicCode}")
                    _uiState.update { it.copy(isDecoding = false, publicCode = decoded.publicCode) }
                    resolveAndPrepareAudio(appContext, decoded.publicCode)
                }
                else -> {
                    Log.w(TAG, "decode failed: ${(decoded as? WaveCodeDecodeResult.Failure)?.reason}")
                    _uiState.update {
                        it.copy(
                            isDecoding = false,
                            decodeError = "WaveCode okunamadı. Daha net ve kırpılmamış bir görsel deneyin."
                        )
                    }
                }
            }
        }
    }

    /** Shared flow: resolve a publicCode against the backend and start streaming playback. */
    private fun resolveAndPrepareAudio(appContext: Context, publicCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true, resolveError = null, resolved = false) }
            when (val result = audio.resolve(publicCode)) {
                is WaveCodeNetworkResult.Success -> {
                    val meta = result.data
                    _uiState.update {
                        it.copy(
                            isResolving = false,
                            resolved = true,
                            title = meta.title,
                            durationLabel = formatDuration(meta.durationMs),
                            resolveError = null
                        )
                    }
                    audio.prepareAndPlay(appContext, meta)
                }
                is WaveCodeNetworkResult.Failure -> {
                    Log.w(TAG, "resolve failure: kind=${result.kind} http=${result.httpCode}")
                    _uiState.update { it.copy(isResolving = false, resolved = false, resolveError = resolveErrorMessage(result)) }
                }
            }
        }
    }

    fun togglePlayPause() = audio.togglePlayPause()

    /** Clears the current result so the user can scan another image. */
    fun reset() {
        audio.stop()
        _uiState.value = ScanListenUiState()
    }

    override fun onCleared() {
        audio.release()
    }

    private fun loadFullBitmap(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        Log.e(TAG, "loadFullBitmap failed", e); null
    }

    private fun resolveErrorMessage(failure: WaveCodeNetworkResult.Failure): String = when (failure.kind) {
        WaveCodeNetworkResult.Kind.NotFound -> "Bu koda ait ses bulunamadı."
        WaveCodeNetworkResult.Kind.Network -> "Sunucuya ulaşılamadı. İnternet bağlantını kontrol et."
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
}