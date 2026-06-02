package com.nandroid.wavecodev1.wavecode.decode

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Lightweight wrapper around a grayscale pixel array extracted from a Bitmap.
 * Row-major order: pixel at (x, y) = pixels[y * width + x], value 0–255.
 *
 * Phase 3A-2: [binaryThreshold] is computed by the preprocessor (Otsu's method after contrast
 * stretching) and propagated to all downstream stages instead of a hardcoded value.
 * This ensures consistent ink detection regardless of image brightness or print density.
 *
 * TODO Phase 3B/3C: add a local adaptive threshold (Bernsen or Sauvola) alongside the global
 *   Otsu threshold to handle tattoo photos where illumination varies strongly across the image
 *   (skin surface curvature, directional lighting, shadows).
 */
data class ProcessedImage(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val binaryThreshold: Int = 128
) {
    fun luminance(x: Int, y: Int): Int = pixels[y * width + x]

    fun isInk(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && pixels[y * width + x] < binaryThreshold

    // equals/hashCode needed because IntArray uses reference equality by default.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProcessedImage) return false
        return width == other.width && height == other.height &&
                binaryThreshold == other.binaryThreshold && pixels.contentEquals(other.pixels)
    }
    override fun hashCode(): Int =
        31 * (31 * (31 * pixels.contentHashCode() + width) + height) + binaryThreshold
}

/**
 * Converts a Bitmap to a grayscale [ProcessedImage] ready for region detection.
 *
 * Phase 3A-2 pipeline:
 *   1. ITU-R BT.601 grayscale — standard perceptual luminance weights.
 *   2. Contrast stretching — remaps the observed luminance range [min, max] → [0, 255].
 *      Skipped when the image is already near full-range (range ≥ 200) to avoid noise amplification
 *      and to preserve Phase 3A-1 behaviour on clean exported PNGs (pure black on white).
 *      Handles faded or low-contrast prints without affecting sharp exports.
 *   3. Otsu's global threshold — finds the luminance split T that maximises between-class variance
 *      (ink vs. background). Stored in [ProcessedImage.binaryThreshold]; used by all downstream
 *      stages. Adapts to the actual image content rather than assuming a fixed midpoint.
 *
 * TODO Phase 3A-2 retry: if Otsu decode fails, caller can retry with threshold ± 15 by creating
 *   a new ProcessedImage wrapping the same pixel array with a shifted binaryThreshold.
 * TODO Phase 3B/3C: after Otsu, apply local adaptive threshold (Sauvola) for tattoo images
 *   with strong skin-tone gradient. Keep Otsu as fast path for clean images.
 */
// ── ProcessedImage geometry helpers ──────────────────────────────────────────────────────────────

fun ProcessedImage.cropped(xMin: Int, yMin: Int, xMax: Int, yMax: Int): ProcessedImage {
    val x0 = xMin.coerceIn(0, width - 1)
    val y0 = yMin.coerceIn(0, height - 1)
    val x1 = xMax.coerceIn(x0, width - 1)
    val y1 = yMax.coerceIn(y0, height - 1)
    val newW = x1 - x0 + 1
    val newH = y1 - y0 + 1
    val newPixels = IntArray(newW * newH) { i ->
        pixels[(y0 + i / newW) * width + (x0 + i % newW)]
    }
    return ProcessedImage(newPixels, newW, newH, binaryThreshold)
}

fun ProcessedImage.rotated(degrees: Int): ProcessedImage {
    return when (((degrees % 360) + 360) % 360) {
        0 -> this
        90 -> {
            // 90° CW: new(newX,newY) = old(newY, oldH-1-newX); newW=oldH, newH=oldW
            val newW = height; val newH = width
            val newPixels = IntArray(newW * newH) { i ->
                val newX = i % newW; val newY = i / newW
                pixels[(height - 1 - newX) * width + newY]
            }
            ProcessedImage(newPixels, newW, newH, binaryThreshold)
        }
        180 -> {
            ProcessedImage(IntArray(pixels.size) { i -> pixels[pixels.size - 1 - i] }, width, height, binaryThreshold)
        }
        270 -> {
            // 270° CW (90° CCW): new(newX,newY) = old(oldW-1-newY, newX); newW=oldH, newH=oldW
            val newW = height; val newH = width
            val newPixels = IntArray(newW * newH) { i ->
                val newX = i % newW; val newY = i / newW
                pixels[newX * width + (width - 1 - newY)]
            }
            ProcessedImage(newPixels, newW, newH, binaryThreshold)
        }
        else -> this
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────

object WaveCodeImagePreprocessor {

    fun preprocess(bitmap: Bitmap): Result<ProcessedImage> = runCatching {
        val w = bitmap.width
        val h = bitmap.height
        require(w > 0 && h > 0) { "Bitmap dimensions are zero" }

        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)

        // Stage 1: ITU-R BT.601 grayscale: 0.299R + 0.587G + 0.114B
        val gray = IntArray(w * h) { i ->
            val px = argb[i]
            (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
        }

        // Stage 2: Contrast stretching
        val stretched = contrastStretch(gray)

        // Stage 3: Otsu threshold on the stretched image
        val threshold = otsuThreshold(stretched)

        ProcessedImage(stretched, w, h, threshold)
    }

    /**
     * Remaps luminance [min, max] → [0, 255].
     * Skipped if the existing range is already ≥ 200 — clean PNGs are pure black/white
     * (range = 255) and stretching would amplify compression noise without benefit.
     */
    private fun contrastStretch(gray: IntArray): IntArray {
        var min = 255; var max = 0
        for (v in gray) {
            if (v < min) min = v
            if (v > max) max = v
        }
        val range = max - min
        if (range < 1 || range >= 200) return gray
        return IntArray(gray.size) { (gray[it] - min) * 255 / range }
    }

    /**
     * Otsu's global threshold: finds T ∈ [0,255] that maximises between-class variance.
     * Pure Kotlin, single histogram pass. Falls back to 128 for degenerate images.
     * Pixels < T are treated as ink by downstream stages.
     */
    private fun otsuThreshold(pixels: IntArray): Int {
        val hist = IntArray(256)
        for (v in pixels) hist[v]++
        val total = pixels.size.toLong()

        var weightedSum = 0L
        for (i in 0..255) weightedSum += i.toLong() * hist[i]

        var wBackground = 0L
        var sumBackground = 0L
        var maxVariance = 0.0
        var threshold = 128

        for (t in 0..255) {
            wBackground += hist[t]
            if (wBackground == 0L) continue
            val wForeground = total - wBackground
            if (wForeground == 0L) break

            sumBackground += t.toLong() * hist[t]
            val meanBackground = sumBackground.toDouble() / wBackground
            val meanForeground = (weightedSum - sumBackground).toDouble() / wForeground
            val diff = meanBackground - meanForeground
            val betweenClassVariance = wBackground.toDouble() * wForeground * diff * diff
            if (betweenClassVariance > maxVariance) {
                maxVariance = betweenClassVariance
                threshold = t
            }
        }
        return threshold
    }
}
