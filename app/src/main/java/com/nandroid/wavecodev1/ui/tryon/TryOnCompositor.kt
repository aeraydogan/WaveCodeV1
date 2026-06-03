package com.nandroid.wavecodev1.ui.tryon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Letterbox colour behind a Fit-scaled photo — mirrors the editor's dark backdrop. */
private const val BACKDROP_COLOR = 0xFF0F0F0F.toInt()

/** Overlay base width as a fraction of the canvas — mirrors Modifier.fillMaxWidth(0.7f) on screen. */
private const val OVERLAY_WIDTH_FRACTION = 0.7f

/**
 * Creates a FileProvider Uri (in cache/tryon_photos) for the camera to write a capture into.
 * Shared by the entry bottom-sheet and the in-editor "change photo" flow.
 */
fun createTryOnCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "tryon_photos").apply { if (!exists()) mkdirs() }
    val file = File(dir, "tryon_capture.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Renders the final composed image: the photo (Fit-scaled, centered) with the WaveCode [overlay]
 * placed using the same transform the user applied on screen (pan/scale/rotate/ink-opacity).
 *
 * Composited at the on-screen canvas size so the saved/shared image matches the preview 1:1.
 * Returns null if the canvas size is unknown or the photo cannot be loaded.
 */
fun composeTryOn(
    context: Context,
    photoUri: Uri,
    overlay: Bitmap,
    canvasW: Int,
    canvasH: Int,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    rotationDeg: Float,
    inkOpacity: Float,
    photoRotationDeg: Int = 0
): Bitmap? {
    if (canvasW <= 0 || canvasH <= 0) return null
    val photo = loadTryOnPhotoBitmap(context, photoUri, maxOf(canvasW, canvasH))
        ?.let { rotateBitmap(it, photoRotationDeg) } ?: return null

    val out = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(BACKDROP_COLOR)

    val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    // ── Photo: ContentScale.Fit, centered ───────────────────────────────────
    val fit = minOf(canvasW / photo.width.toFloat(), canvasH / photo.height.toFloat())
    val dw = photo.width * fit
    val dh = photo.height * fit
    val left = (canvasW - dw) / 2f
    val top  = (canvasH - dh) / 2f
    canvas.drawBitmap(photo, null, RectF(left, top, left + dw, top + dh), drawPaint)

    // ── WaveCode overlay: same transform as the on-screen graphicsLayer ──────
    val baseW = canvasW * OVERLAY_WIDTH_FRACTION
    val baseH = baseW * overlay.height / overlay.width
    val overlayPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        alpha = (inkOpacity.coerceIn(0f, 1f) * 255).toInt()
    }
    canvas.save()
    // graphicsLayer applies scale+rotation about the (centered) overlay center, then translation.
    canvas.translate(canvasW / 2f + offsetX, canvasH / 2f + offsetY)
    canvas.rotate(rotationDeg)
    canvas.scale(scale, scale)
    canvas.drawBitmap(overlay, null, RectF(-baseW / 2f, -baseH / 2f, baseW / 2f, baseH / 2f), overlayPaint)
    canvas.restore()

    if (photo != out) photo.recycle()
    return out
}

/** Writes [bitmap] to cache/tryon_photos and returns a shareable FileProvider Uri (PNG). */
fun writeShareImage(context: Context, bitmap: Bitmap): Uri? = try {
    val dir = File(context.cacheDir, "tryon_photos").apply { if (!exists()) mkdirs() }
    val file = File(dir, "tryon_share.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
} catch (e: Exception) {
    null
}

/** Rotates [src] by [degrees] (0/90/180/270). Returns [src] unchanged when degrees is 0. */
fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
    val norm = ((degrees % 360) + 360) % 360
    if (norm == 0) return src
    val matrix = Matrix().apply { postRotate(norm.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

/**
 * Loads a display-sized (≤ [maxDim] px) Android Bitmap from [uri], or null on failure.
 *
 * Applies the photo's EXIF orientation so portrait photos (whose raw pixels are landscape with a
 * "rotate" tag) are shown upright instead of sideways.
 */
fun loadTryOnPhotoBitmap(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
    val cap = maxDim.coerceAtLeast(1)
    var sample = 1
    while (largest / sample > cap) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    decoded?.let { rotateBitmap(it, exifRotationDegrees(context, uri)) }
} catch (e: Exception) {
    null
}

/** Reads the EXIF orientation of [uri] and returns the clockwise degrees needed to display upright. */
private fun exifRotationDegrees(context: Context, uri: Uri): Int = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val exif = ExifInterface(input)
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } ?: 0
} catch (e: Exception) {
    0
}