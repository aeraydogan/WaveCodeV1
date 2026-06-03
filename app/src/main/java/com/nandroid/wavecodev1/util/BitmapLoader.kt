package com.nandroid.wavecodev1.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/** Shared bitmap loading / rotation helpers used by the scan and try-on flows. */
object BitmapLoader {

    /**
     * Loads the full-resolution bitmap from [uri], or null on failure.
     *
     * No EXIF normalization — used by the decoder, which tries its own orientation candidates.
     */
    fun loadFull(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    /**
     * Loads a display-sized (≤ [maxDim] px) bitmap from [uri], or null on failure.
     *
     * When [applyExif] is true, the photo's EXIF orientation is applied so portrait photos (whose
     * raw pixels are landscape with a "rotate" tag) come out upright.
     */
    fun loadDownsampled(context: Context, uri: Uri, maxDim: Int = 1600, applyExif: Boolean = true): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val cap = maxDim.coerceAtLeast(1)
        var sample = 1
        while (largest / sample > cap) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (decoded != null && applyExif) rotate(decoded, exifRotationDegrees(context, uri)) else decoded
    } catch (e: Exception) {
        null
    }

    /** Rotates [src] by [degrees] (0/90/180/270). Returns [src] unchanged when degrees is 0. */
    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val norm = ((degrees % 360) + 360) % 360
        if (norm == 0) return src
        val matrix = Matrix().apply { postRotate(norm.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
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
}
