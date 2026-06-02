package com.nandroid.wavecodev1.ui.decode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nandroid.wavecodev1.audio.AudioPlayer
import com.nandroid.wavecodev1.data.WaveCodeEntry
import com.nandroid.wavecodev1.data.WaveCodeLibraryRepository
import com.nandroid.wavecodev1.wavecode.decode.DecodeDebugInfo
import com.nandroid.wavecodev1.wavecode.decode.FailureReason
import com.nandroid.wavecodev1.wavecode.decode.WaveCodeBitParser
import com.nandroid.wavecodev1.wavecode.decode.WaveCodeDecodeResult
import com.nandroid.wavecodev1.wavecode.decode.WaveCodeDecoder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "WaveCodeDecodeVM"

/** Outcome of resolving a decoded publicCode against the local library. */
enum class ResolveStatus { NotResolved, Found, NotInLibrary, AudioMissing }

data class DecodeUiState(
    val isDecoding: Boolean = false,
    val result: WaveCodeDecodeResult? = null,
    val previewBitmap: Bitmap? = null,
    val selfTestPassed: Boolean? = null,  // null = not yet run
    val resolveStatus: ResolveStatus = ResolveStatus.NotResolved,
    val resolvedEntry: WaveCodeEntry? = null,
    val isPlaying: Boolean = false
)

class WaveCodeDecodeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DecodeUiState())
    val uiState: StateFlow<DecodeUiState> = _uiState.asStateFlow()

    private val player = AudioPlayer()

    init {
        // Run self-test on init to verify the bit parser is consistent with the encoder.
        // Logs a warning if it fails; does not block the UI.
        viewModelScope.launch(Dispatchers.Default) {
            val ok = WaveCodeBitParser.selfTest()
            Log.d(TAG, "selfTest: $ok")
            _uiState.update { it.copy(selfTestPassed = ok) }
        }
    }

    fun loadAndDecode(context: Context, uri: Uri) {
        if (_uiState.value.isDecoding) return
        player.stop()
        _uiState.update {
            it.copy(
                isDecoding = true,
                result = null,
                previewBitmap = null,
                resolveStatus = ResolveStatus.NotResolved,
                resolvedEntry = null,
                isPlaying = false
            )
        }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    val appCtx = context.applicationContext

                    // Load a downsampled bitmap for the preview thumbnail.
                    val preview = loadPreviewBitmap(appCtx, uri)

                    // Load full-resolution bitmap for decoding.
                    val full = loadFullBitmap(appCtx, uri)
                    if (full == null) {
                        return@withContext DecodeOutcome(
                            preview,
                            WaveCodeDecodeResult.Failure(FailureReason.UnsupportedImage, DecodeDebugInfo())
                        )
                    }

                    val decodeResult = WaveCodeDecoder.decode(full)
                    full.recycle()

                    // Resolve a successful publicCode against the local library.
                    var status = ResolveStatus.NotResolved
                    var entry: WaveCodeEntry? = null
                    if (decodeResult is WaveCodeDecodeResult.Success) {
                        val found = WaveCodeLibraryRepository.get(appCtx).findByCode(decodeResult.publicCode)
                        when {
                            found == null -> status = ResolveStatus.NotInLibrary
                            !File(found.audioPath).exists() -> { status = ResolveStatus.AudioMissing; entry = found }
                            else -> { status = ResolveStatus.Found; entry = found }
                        }
                        Log.d(TAG, "resolve: code=${decodeResult.publicCode} status=$status")
                    }
                    DecodeOutcome(preview, decodeResult, status, entry)
                } catch (e: Exception) {
                    Log.e(TAG, "loadAndDecode failed", e)
                    DecodeOutcome(
                        null,
                        WaveCodeDecodeResult.Failure(FailureReason.Unknown, DecodeDebugInfo())
                    )
                }
            }

            _uiState.update {
                it.copy(
                    isDecoding = false,
                    result = outcome.result,
                    previewBitmap = outcome.preview,
                    resolveStatus = outcome.status,
                    resolvedEntry = outcome.entry
                )
            }
        }
    }

    /** Plays the resolved audio, if any. */
    fun playResolved() {
        val entry = _uiState.value.resolvedEntry ?: return
        val ok = player.play(entry.audioPath) {
            _uiState.update { it.copy(isPlaying = false) }
        }
        _uiState.update { it.copy(isPlaying = ok) }
    }

    fun stopPlayback() {
        player.stop()
        _uiState.update { it.copy(isPlaying = false) }
    }

    private data class DecodeOutcome(
        val preview: Bitmap?,
        val result: WaveCodeDecodeResult,
        val status: ResolveStatus = ResolveStatus.NotResolved,
        val entry: WaveCodeEntry? = null
    )

    fun clearResult() {
        player.stop()
        _uiState.update {
            it.copy(
                result = null,
                previewBitmap = null,
                resolveStatus = ResolveStatus.NotResolved,
                resolvedEntry = null,
                isPlaying = false
            )
        }
    }

    override fun onCleared() {
        player.release()
    }

    // ── Bitmap loading helpers ────────────────────────────────────────────────────────────────────

    private fun loadFullBitmap(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        Log.e(TAG, "loadFullBitmap failed", e); null
    }

    private fun loadPreviewBitmap(context: Context, uri: Uri): Bitmap? = try {
        // First pass: read dimensions only.
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

        // Second pass: load at reduced size targeting ≤ 800 px width for display.
        val sampleSize = (opts.outWidth / 800f).toInt().coerceAtLeast(1)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
    } catch (e: Exception) {
        Log.e(TAG, "loadPreviewBitmap failed", e); null
    }
}
