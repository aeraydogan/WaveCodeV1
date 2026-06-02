package com.nandroid.wavecodev1.wavecode.decode

import com.nandroid.wavecodev1.wavecode.WaveCodeSpec
import kotlin.math.abs

/**
 * Sampled heights and decoded buckets for the 34 core bars.
 */
data class SampledBars(
    val buckets: IntArray,          // 34 values, each 0–3
    val heightRatios: FloatArray,   // 34 normalised height ratios [0, 1]
    val barConfidences: FloatArray  // 34 per-bar confidence values [0, 1]
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SampledBars) return false
        return buckets.contentEquals(other.buckets) &&
                heightRatios.contentEquals(other.heightRatios) &&
                barConfidences.contentEquals(other.barConfidences)
    }
    override fun hashCode(): Int =
        31 * (31 * buckets.contentHashCode() + heightRatios.contentHashCode()) +
                barConfidences.contentHashCode()
}

/**
 * Samples each of the 34 core bar slots from a [ProcessedImage] using the geometry established
 * by [DetectedRegion]. All height measurements are normalised to [region.usableHeight] so the
 * result is scale-independent and can be compared directly to [WaveCodeSpec.CALM_DECODE_THRESHOLDS].
 *
 * Phase 3A-2 improvements over Phase 3A-1:
 *   - 9 sample columns per bar (was 5) — denser sampling for better noise resistance.
 *   - Vertical scan bounded to [centerY ± usableHeight × 0.55] — prevents picking up background
 *     content (table surface, paper margins) that lies above or below the WaveCode.
 *   - Outlier rejection — column heights that deviate more than 2.5× MAD from the per-bar median
 *     are discarded before the final median is computed. Handles ink bleed from adjacent bars.
 *   - Uses [ProcessedImage.binaryThreshold] instead of hardcoded 128.
 *
 * TODO Phase 3B/3C: increase sample columns further and add curvature correction — sample at
 *   multiple horizontal positions per bar and take the maximum height to compensate for tattoo
 *   surface curvature that compresses bars at the image edges.
 */
object WaveCodeBarSampler {

    private const val SAMPLE_COLUMNS   = 9
    private const val INNER_FILL_START = 0.20f  // skip outer 20 % of slot on each side
    private const val INNER_FILL_END   = 0.80f

    // Use Calm thresholds for decoding; both variants differ by ≤ 0.01 on the third threshold.
    // TODO: if a variant flag is ever added, switch to DYNAMIC_DECODE_THRESHOLDS where needed.
    private val THRESHOLDS = WaveCodeSpec.CALM_DECODE_THRESHOLDS  // [0.18, 0.37, 0.60]

    // Half-width of the narrowest bucket (bucket 0: 0 → 0.18, half = 0.09) — used for confidence.
    private const val BUCKET_HALF_WIDTH = 0.09f

    fun sample(image: ProcessedImage, region: DetectedRegion): SampledBars {
        val coreSlotWidth = (region.coreEndX - region.coreStartX) / 34f
        val buckets       = IntArray(34)
        val heightRatios  = FloatArray(34)
        val confidences   = FloatArray(34)

        // Restrict vertical scan to the WaveCode area — avoids background noise from surrounding
        // image content (table, paper, screen bezel) above or below the code.
        val yMin = (region.centerY - region.usableHeight * 0.55f)
            .toInt().coerceAtLeast(0)
        val yMax = (region.centerY + region.usableHeight * 0.55f)
            .toInt().coerceAtMost(image.height - 1)

        for (i in 0 until 34) {
            val slotLeft   = region.coreStartX + i * coreSlotWidth
            val innerLeft  = slotLeft + coreSlotWidth * INNER_FILL_START
            val innerRight = slotLeft + coreSlotWidth * INNER_FILL_END
            val step       = (innerRight - innerLeft) / (SAMPLE_COLUMNS - 1).toFloat()

            val heights = ArrayList<Float>(SAMPLE_COLUMNS)
            for (s in 0 until SAMPLE_COLUMNS) {
                val x = (innerLeft + s * step).toInt().coerceIn(0, image.width - 1)
                val h = columnBarHeight(image, x, yMin, yMax)
                if (h > 0f) heights.add(h)
            }

            val barPx  = median(withOutlierRejection(heights))
            val ratio  = if (region.usableHeight > 0f) barPx / region.usableHeight else 0f
            val bucket = ratioBucket(ratio)
            val conf   = bucketConfidence(ratio)

            heightRatios[i] = ratio
            buckets[i]       = bucket
            confidences[i]   = conf
        }

        return SampledBars(buckets, heightRatios, confidences)
    }

    /**
     * Vertical extent of dark pixels in a single column, bounded to [yMin, yMax].
     * Bounding prevents background pixels far from the WaveCode from inflating bar heights.
     * Returns 0 if no ink pixels are found within the bounds.
     */
    private fun columnBarHeight(image: ProcessedImage, x: Int, yMin: Int, yMax: Int): Float {
        var top = -1; var bot = -1
        for (y in yMin..yMax) {
            if (image.pixels[y * image.width + x] < image.binaryThreshold) {
                if (top == -1) top = y
                bot = y
            }
        }
        return if (top != -1 && bot >= top) (bot - top + 1).toFloat() else 0f
    }

    /**
     * Removes outlier height readings that deviate more than 2.5 × MAD from the per-bar median.
     * Primary target: a column near a slot edge that picks up ink from an adjacent taller bar.
     * Falls back to the original list if fewer than 3 readings survive rejection.
     */
    private fun withOutlierRejection(heights: List<Float>): List<Float> {
        if (heights.size < 5) return heights
        val med = median(heights)
        if (med <= 0f) return heights
        val deviations = heights.map { abs(it - med) }
        // MAD floor of 2 px prevents over-rejection when all readings are nearly identical.
        val mad = median(deviations).coerceAtLeast(2f)
        val survivors = heights.filter { abs(it - med) <= 2.5f * mad }
        return if (survivors.size >= 3) survivors else heights
    }

    private fun ratioBucket(ratio: Float): Int = when {
        ratio < THRESHOLDS[0] -> 0  // < 0.18  → "00"
        ratio < THRESHOLDS[1] -> 1  // < 0.37  → "01"
        ratio < THRESHOLDS[2] -> 2  // < 0.60  → "10"
        else                  -> 3  //  ≥ 0.60  → "11"
    }

    /** Distance-from-boundary confidence: 1.0 = well inside bucket, 0.0 = exactly on threshold. */
    fun bucketConfidence(ratio: Float): Float {
        val minDist = THRESHOLDS.minOf { abs(ratio - it) }
        return (minDist / BUCKET_HALF_WIDTH).coerceIn(0f, 1f)
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
