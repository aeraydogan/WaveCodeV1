package com.nandroid.wavecodev1.ui.tryon

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nandroid.wavecodev1.wavecode.WaveCodeImageExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for the tattoo placement preview / editor ("Dövmeyi Teninde Gör"): a user photo as the
 * backdrop with the WaveCode overlaid as ink-only bars the user can move, scale and rotate, then
 * save or share the composed image.
 *
 * Transform values are applied to the overlay via graphicsLayer:
 *   - [offsetX]/[offsetY] in pixels (pan)
 *   - [scale] multiplier (pinch-zoom)
 *   - [rotationDeg] in degrees (two-finger rotate)
 *   - [inkOpacity] 0..1 — simulates how dark the tattoo ink looks
 *
 * [canvasW]/[canvasH] capture the on-screen editor size so the composed export matches the preview.
 */
data class TryOnUiState(
    val photoUri: Uri? = null,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val inkOpacity: Float = 0.85f,
    val photoRotationDeg: Int = 0,            // background photo rotation (0/90/180/270)
    val canvasW: Int = 0,
    val canvasH: Int = 0,
    val isProcessing: Boolean = false,        // composing for save/share
    val message: String? = null,
    val isError: Boolean = false,
    val shareUri: Uri? = null                 // one-shot: consumed by the screen to launch the share sheet
)

class WaveCodeTryOnViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TryOnUiState())
    val uiState: StateFlow<TryOnUiState> = _uiState.asStateFlow()

    /** Pending camera-output Uri, created before launching the camera intent. */
    private var pendingCaptureUri: Uri? = null

    /** Initializes the backdrop with a photo chosen at the entry point (bottom sheet), once. */
    fun initPhoto(uri: Uri) {
        if (_uiState.value.photoUri == null) _uiState.update { it.copy(photoUri = uri) }
    }

    fun onPhotoPicked(uri: Uri) {
        // New backdrop → reset placement so the overlay starts centered.
        _uiState.value = TryOnUiState(photoUri = uri, canvasW = _uiState.value.canvasW, canvasH = _uiState.value.canvasH)
    }

    /**
     * Creates a temp file in cache/tryon_photos and returns a FileProvider Uri for the camera
     * to write into. The Uri is remembered and applied by [onCameraCaptured] on success.
     */
    fun createCaptureUri(context: Context): Uri {
        val uri = createTryOnCaptureUri(context)
        pendingCaptureUri = uri
        return uri
    }

    /** Called when the camera intent reports success; promotes the pending Uri to the backdrop. */
    fun onCameraCaptured() {
        pendingCaptureUri?.let { onPhotoPicked(it) }
    }

    /** Records the on-screen editor canvas size so the composed export matches the preview. */
    fun onCanvasSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        _uiState.update { it.copy(canvasW = width, canvasH = height) }
    }

    /** Applies a combined pan/zoom/rotate gesture delta. */
    fun onTransform(panX: Float, panY: Float, zoom: Float, rotation: Float) {
        _uiState.update {
            it.copy(
                offsetX     = it.offsetX + panX,
                offsetY     = it.offsetY + panY,
                scale       = (it.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE),
                rotationDeg = it.rotationDeg + rotation
            )
        }
    }

    fun onInkOpacityChange(value: Float) {
        _uiState.update { it.copy(inkOpacity = value.coerceIn(0.1f, 1f)) }
    }

    /** Rotates the background photo by 90° clockwise. */
    fun rotatePhoto() {
        _uiState.update { it.copy(photoRotationDeg = (it.photoRotationDeg + 90) % 360) }
    }

    /** Resets placement (offset/scale/rotation) but keeps the current photo. */
    fun resetPlacement() {
        _uiState.update { it.copy(offsetX = 0f, offsetY = 0f, scale = 1f, rotationDeg = 0f) }
    }

    /** Clears the photo entirely, returning to the source-selection state. */
    fun clearPhoto() {
        pendingCaptureUri = null
        _uiState.value = TryOnUiState(canvasW = _uiState.value.canvasW, canvasH = _uiState.value.canvasH)
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    fun consumeShareUri() {
        _uiState.update { it.copy(shareUri = null) }
    }

    /** Composes photo + overlay and saves the result to the gallery. */
    fun saveComposite(context: Context, overlay: Bitmap, publicCode: String) {
        val state = _uiState.value
        val photoUri = state.photoUri ?: return
        if (state.isProcessing) return
        val ctx = context.applicationContext
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, message = null) }
            val result = withContext(Dispatchers.IO) {
                val composed = composeTryOn(
                    ctx, photoUri, overlay,
                    state.canvasW, state.canvasH,
                    state.offsetX, state.offsetY, state.scale, state.rotationDeg, state.inkOpacity,
                    state.photoRotationDeg
                ) ?: return@withContext "Görsel oluşturulamadı." to true
                when (val save = WaveCodeImageExporter.savePng(ctx, composed, publicCode)) {
                    is WaveCodeImageExporter.SaveResult.Success -> "Görsel galeriye kaydedildi." to false
                    is WaveCodeImageExporter.SaveResult.Failure -> "Kaydedilemedi: ${save.message}" to true
                }
            }
            _uiState.update { it.copy(isProcessing = false, message = result.first, isError = result.second) }
        }
    }

    /** Composes photo + overlay, writes a temp PNG and exposes a share Uri for the screen. */
    fun shareComposite(context: Context, overlay: Bitmap, @Suppress("UNUSED_PARAMETER") publicCode: String) {
        val state = _uiState.value
        val photoUri = state.photoUri ?: return
        if (state.isProcessing) return
        val ctx = context.applicationContext
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, message = null) }
            val uri = withContext(Dispatchers.IO) {
                val composed = composeTryOn(
                    ctx, photoUri, overlay,
                    state.canvasW, state.canvasH,
                    state.offsetX, state.offsetY, state.scale, state.rotationDeg, state.inkOpacity,
                    state.photoRotationDeg
                ) ?: return@withContext null
                writeShareImage(ctx, composed)
            }
            _uiState.update {
                if (uri != null) it.copy(isProcessing = false, shareUri = uri)
                else it.copy(isProcessing = false, message = "Paylaşım görseli oluşturulamadı.", isError = true)
            }
        }
    }

    companion object {
        private const val MIN_SCALE = 0.25f
        private const val MAX_SCALE = 6f
    }
}