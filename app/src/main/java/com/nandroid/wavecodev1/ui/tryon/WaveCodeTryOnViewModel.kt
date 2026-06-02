package com.nandroid.wavecodev1.ui.tryon

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * State for the tattoo placement preview (Try-On): a user photo as the backdrop with the
 * WaveCode overlaid as ink-only bars the user can move, scale and rotate.
 *
 * Transform values are applied to the overlay via graphicsLayer:
 *   - [offsetX]/[offsetY] in pixels (pan)
 *   - [scale] multiplier (pinch-zoom)
 *   - [rotationDeg] in degrees (two-finger rotate)
 *   - [inkOpacity] 0..1 — simulates how dark the tattoo ink looks
 *
 * Preview-only: no compositing/export here (kept for a later step).
 */
data class TryOnUiState(
    val photoUri: Uri? = null,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val inkOpacity: Float = 0.85f
)

class WaveCodeTryOnViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TryOnUiState())
    val uiState: StateFlow<TryOnUiState> = _uiState.asStateFlow()

    /** Pending camera-output Uri, created before launching the camera intent. */
    private var pendingCaptureUri: Uri? = null

    fun onPhotoPicked(uri: Uri) {
        // New backdrop → reset placement so the overlay starts centered.
        _uiState.value = TryOnUiState(photoUri = uri)
    }

    /**
     * Creates a temp file in cache/tryon_photos and returns a FileProvider Uri for the camera
     * to write into. The Uri is remembered and applied by [onCameraCaptured] on success.
     */
    fun createCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "tryon_photos").apply { if (!exists()) mkdirs() }
        // Single reusable temp file — each capture overwrites the previous preview photo.
        val file = File(dir, "tryon_capture.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        pendingCaptureUri = uri
        return uri
    }

    /** Called when the camera intent reports success; promotes the pending Uri to the backdrop. */
    fun onCameraCaptured() {
        pendingCaptureUri?.let { _uiState.value = TryOnUiState(photoUri = it) }
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

    /** Resets placement (offset/scale/rotation) but keeps the current photo. */
    fun resetPlacement() {
        _uiState.update { it.copy(offsetX = 0f, offsetY = 0f, scale = 1f, rotationDeg = 0f) }
    }

    /** Clears the photo entirely, returning to the source-selection state. */
    fun clearPhoto() {
        pendingCaptureUri = null
        _uiState.value = TryOnUiState()
    }

    companion object {
        private const val MIN_SCALE = 0.25f
        private const val MAX_SCALE = 6f
    }
}