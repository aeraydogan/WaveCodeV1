package com.nandroid.wavecodev1.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nandroid.wavecodev1.audio.WaveCodeAudioController
import com.nandroid.wavecodev1.net.VoiceCodeMetadata
import com.nandroid.wavecodev1.net.WaveCodeNetworkResult
import com.nandroid.wavecodev1.util.NetworkErrorMessages
import com.nandroid.wavecodev1.util.formatDuration
import com.nandroid.wavecodev1.wavecode.decode.WaveCodeDecodeResult
import com.nandroid.wavecodev1.wavecode.decode.WaveCodeDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "WaveCodeCameraVM"

data class CameraScanUiState(
    val isCapturing: Boolean = false,
    val isDecoding: Boolean = false,
    val croppedPreviewBitmap: Bitmap? = null,
    val result: WaveCodeDecodeResult? = null,
    val errorMessage: String? = null,
    // Resolve + playback (after a successful decode)
    val isResolving: Boolean = false,
    val resolved: Boolean = false,
    val title: String? = null,
    val durationLabel: String? = null,
    val resolveError: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackError: String? = null
)

class WaveCodeCameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraScanUiState())
    val uiState: StateFlow<CameraScanUiState> = _uiState.asStateFlow()

    private var imageCapture: ImageCapture? = null
    private val captureExecutor = Executors.newSingleThreadExecutor()

    // Resolve + streaming playback reuse the same shared controller as the other listen screens.
    private val audio = WaveCodeAudioController().apply {
        onPlayingChanged   = { playing -> _uiState.update { it.copy(isPlaying = playing) } }
        onBufferingChanged = { buffering -> _uiState.update { it.copy(isBuffering = buffering) } }
        onError            = { _uiState.update { it.copy(isPlaying = false, isBuffering = false, playbackError = "Kayıt oynatılamadı.") } }
    }
    private var resolvedMeta: VoiceCodeMetadata? = null

    fun onImageCaptureReady(capture: ImageCapture) {
        imageCapture = capture
    }

    fun capture() {
        val capture = imageCapture ?: run {
            Log.w(TAG, "capture called but imageCapture is null — camera not ready yet")
            return
        }
        val state = _uiState.value
        if (state.isCapturing || state.isDecoding) return

        _uiState.update { it.copy(
            isCapturing = true,
            result = null,
            errorMessage = null,
            croppedPreviewBitmap = null
        )}

        capture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                viewModelScope.launch(Dispatchers.IO) { processImage(imageProxy) }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "takePicture failed", exception)
                _uiState.update { it.copy(
                    isCapturing = false,
                    errorMessage = "Çekim başarısız: ${exception.message}"
                )}
            }
        })
    }

    private fun processImage(imageProxy: ImageProxy) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        Log.d(TAG, "captured: ${imageProxy.width}×${imageProxy.height} rotationDegrees=$rotationDegrees")

        // ImageCapture with OnImageCapturedCallback produces JPEG in planes[0]
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        imageProxy.close()

        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (rawBitmap == null) {
            Log.e(TAG, "BitmapFactory.decodeByteArray returned null")
            _uiState.update { it.copy(isCapturing = false, errorMessage = "Görüntü okunamadı") }
            return
        }
        Log.d(TAG, "raw bitmap: ${rawBitmap.width}×${rawBitmap.height}")

        // Apply EXIF rotation so bitmap is visually upright as seen in the viewfinder
        val uprightBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                .also { rawBitmap.recycle() }
        } else rawBitmap
        Log.d(TAG, "upright bitmap: ${uprightBitmap.width}×${uprightBitmap.height}")

        // TODO Phase 3B-2: replace with exact PreviewView→bitmap coordinate mapping using
        //   CameraX SurfaceRequest metrics, accounting for FILL_CENTER crop offsets.
        val croppedBitmap = cropToGuideFrame(uprightBitmap)
        uprightBitmap.recycle()
        Log.d(TAG, "cropped bitmap: ${croppedBitmap.width}×${croppedBitmap.height}")

        _uiState.update { it.copy(
            isCapturing = false,
            isDecoding = true,
            croppedPreviewBitmap = croppedBitmap
        )}

        val result = WaveCodeDecoder.decode(croppedBitmap)
        Log.d(TAG, "decode result: ${result::class.simpleName}")
        _uiState.update { it.copy(isDecoding = false, result = result) }

        // On a successful decode, resolve the code against the backend (same logic as the
        // gallery / manual-code listen flows).
        if (result is WaveCodeDecodeResult.Success) resolve(result.publicCode)
    }

    /** Resolves the decoded publicCode to metadata via the backend. */
    private fun resolve(publicCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true, resolved = false, resolveError = null, playbackError = null) }
            when (val r = audio.resolve(publicCode)) {
                is WaveCodeNetworkResult.Success -> {
                    resolvedMeta = r.data
                    _uiState.update {
                        it.copy(
                            isResolving = false,
                            resolved = true,
                            title = r.data.title,
                            durationLabel = formatDuration(r.data.durationMs)
                        )
                    }
                }
                is WaveCodeNetworkResult.Failure -> {
                    Log.w(TAG, "resolve failure: kind=${r.kind} http=${r.httpCode}")
                    _uiState.update { it.copy(isResolving = false, resolved = false, resolveError = NetworkErrorMessages.resolve(r.kind)) }
                }
            }
        }
    }

    /** Oynat — starts (or resumes) playback. */
    fun play(context: Context) {
        val meta = resolvedMeta ?: return
        _uiState.update { it.copy(playbackError = null) }
        audio.start(context.applicationContext, meta)
    }

    /** Tekrar Oynat — restarts playback from the beginning. */
    fun replay(context: Context) {
        val meta = resolvedMeta ?: return
        _uiState.update { it.copy(playbackError = null) }
        audio.restart(context.applicationContext, meta)
    }

    fun clearResult() {
        audio.stop()
        resolvedMeta = null
        _uiState.update { it.copy(
            result = null,
            croppedPreviewBitmap = null,
            errorMessage = null,
            isResolving = false,
            resolved = false,
            title = null,
            durationLabel = null,
            resolveError = null,
            isPlaying = false,
            isBuffering = false,
            playbackError = null
        )}
    }

    override fun onCleared() {
        super.onCleared()
        audio.release()
        captureExecutor.shutdown()
    }

    companion object {
        /**
         * Crops the upright bitmap to the guide frame region: 88% width, 3:1 aspect, centered,
         * with a 10% safety margin on each side so WaveCode edge markers are not clipped.
         *
         * The guide frame in the UI is defined identically (88% width, 3:1 aspect, centered), so
         * the fractions align. This is an approximation: FILL_CENTER PreviewView may crop the
         * sensor image differently from how it displays on screen. For clean WaveCode capture
         * with a user-aligned guide frame, this is sufficient.
         *
         * TODO Phase 3B-2: exact mapping via CameraX SurfaceRequest/ViewPort metrics.
         * TODO Phase 3C: perspective correction for curved tattoo surfaces.
         * TODO Phase 3C: Sauvola local adaptive threshold for skin-tone gradient illumination.
         */
        fun cropToGuideFrame(bitmap: Bitmap): Bitmap {
            val bw = bitmap.width
            val bh = bitmap.height

            // 88% width, 3:1 aspect. Safety margin of 10% per side to avoid clipping edge markers.
            val guideWidthFrac = 0.88f
            val marginFrac = 0.04f

            val cropWidthFrac = (guideWidthFrac * (1f + 2f * marginFrac)).coerceAtMost(1.0f)
            val cropWidth = (bw * cropWidthFrac).toInt()
            val cropHeight = ((cropWidth / 3f) * (1f + 2f * marginFrac)).toInt()

            val cropLeft = ((bw - cropWidth) / 2).coerceAtLeast(0)
            val cropTop  = ((bh - cropHeight) / 2).coerceAtLeast(0)
            val safeW = cropWidth.coerceAtMost(bw - cropLeft)
            val safeH = cropHeight.coerceAtMost(bh - cropTop)

            Log.d(TAG, "cropToGuideFrame: src=${bw}×${bh} " +
                    "rect=[$cropLeft,$cropTop,${cropLeft + safeW},${cropTop + safeH}]")

            return Bitmap.createBitmap(bitmap, cropLeft, cropTop, safeW, safeH)
        }
    }
}
