package com.nandroid.wavecodev1.wavecode.decode

import android.util.Log
import com.nandroid.wavecodev1.wavecode.WaveCodeSpec
import kotlin.math.abs

private const val TAG = "WaveCodeRegionDetector"

/**
 * Detected WaveCode region derived entirely from the image — no assumed pixel coordinates.
 *
 * @param coreStartX   Left edge of core bar slot 0 (pixels, full-image coordinates).
 * @param coreEndX     Right edge of core bar slot 33 (pixels, full-image coordinates).
 * @param centerY      Vertical midpoint of all bars (pixels).
 * @param usableHeight Calibrated usable height from the marker bars (pixels).
 * @param confidence   Detection confidence in [0, 1].
 */
data class DetectedRegion(
    val coreStartX: Float,
    val coreEndX: Float,
    val centerY: Float,
    val usableHeight: Float,
    val confidence: Float,
    val debugDescription: String
)

/**
 * Locates the WaveCode region within a [ProcessedImage] using a horizontal projection histogram.
 *
 * Layout expected (left → right):
 *   quiet zone | 3 start markers | edge gap | 34 core bars | edge gap | 3 end markers | quiet zone
 *
 * Total distinct bar segments: 3 + 34 + 3 = 40.
 *
 * Phase 3A-2 detection strategy:
 *   1. Build horizontal projection using [ProcessedImage.binaryThreshold] (not hardcoded 128).
 *   2. Trim large empty margins via content-bounds detection (handles photos with wide whitespace).
 *      Falls back to full image if no significant margins are found.
 *   3. Graduated merge-gap retry: try merging bar segments separated by ≤ 3, 6, or 10 columns.
 *      This handles blur and ink spread from print/photography without relaxing the 40-bar count.
 *   4. Require exactly 40 segments in all attempts.
 *   5. Derive coreStartX / coreEndX from bar center positions (more robust than using bar edges).
 *   6. Calibrate usableHeight from the known height ratios of the decorative markers.
 *   7. Compute detection confidence from how closely marker heights match the spec.
 *
 * TODO Phase 3A-2 (next): relax the exact-40 requirement as a last-resort fallback; scan for
 *   the longest evenly-spaced run of segments when all merge-gap attempts fail.
 * TODO Phase 3B: add rotation detection and correction before projection.
 * TODO Phase 3C: add perspective / curvature correction for tattoo photos on curved surfaces.
 */
object WaveCodeRegionDetector {

    private const val TOTAL_BAR_COUNT = 40   // 3 start + 34 core + 3 end
    private const val CORE_BAR_COUNT  = 34
    private const val MARKER_COUNT    = 3

    // Merge-gap candidates tried in order (columns). First match wins.
    private val MERGE_GAP_CANDIDATES = intArrayOf(3, 6, 10)

    // Height ratios for the decorative marker levels (from WaveCodeSpec):
    //   start markers = [level 0, level 0, level 1]  → heights [0.09, 0.09, 0.14]
    //   end markers   = [level 1, level 0, level 0]  → heights [0.14, 0.09, 0.09]
    private val OUTER_MARKER_RATIO = WaveCodeSpec.CALM_HEIGHT_RATIOS[0]  // 0.09
    private val INNER_MARKER_RATIO = WaveCodeSpec.CALM_HEIGHT_RATIOS[1]  // 0.14

    fun detect(image: ProcessedImage): DetectedRegion? {
        // Build full-image projection once — reused for content-bounds detection and as fallback.
        val fullProj = horizontalProjection(image)

        // Content-bounds: trim columns with near-zero dark-pixel counts (large empty margins).
        // Only pre-crop when the active region is clearly narrower than the full image width.
        val contentRange = trimmedContentRange(image, fullProj)
        val hasSignificantMargins = contentRange.first > image.width / 15 ||
                contentRange.last < image.width - image.width / 15

        val xRangesToTry: List<IntRange> = if (
            hasSignificantMargins &&
            contentRange.last - contentRange.first >= image.width / 5
        ) {
            listOf(contentRange, 0 until image.width)
        } else {
            listOf(0 until image.width)
        }

        for (xRange in xRangesToTry) {
            val xMin = xRange.first; val xMax = xRange.last
            val rangeLabel = if (xMin == 0 && xMax == image.width - 1) "full" else "[$xMin,$xMax]"

            val yBand = detectWaveCodeYBand(image, xMin, xMax)
            val yMin = yBand?.first ?: 0
            val yMax = yBand?.last ?: (image.height - 1)
            Log.d(TAG, "detect: xRange=$rangeLabel yBand=[$yMin,$yMax] imageH=${image.height} hasMargins=$hasSignificantMargins")

            val isFullRange = xMin == 0 && xMax == image.width - 1 && yMin == 0 && yMax == image.height - 1
            val proj = if (isFullRange) fullProj
                       else horizontalProjection(image, xMin, xMax, yMin, yMax)

            val minBarWidth = (xMax - xMin + 1) / (TOTAL_BAR_COUNT * 6)
            for (thresh in buildThresholdCandidates(proj, xMin, xMax, image.height)) {
                val rawSegments = findBarSegments(proj, thresh)
                // Remove 1–4 column blur spikes — real bars are ≥14 px wide at any useful scale
                val filtered = if (minBarWidth > 1) rawSegments.filter { it.last - it.first + 1 >= minBarWidth }
                               else rawSegments
                Log.d(TAG, "detect: thresh=$thresh rawSegs=${rawSegments.size} filtered=${filtered.size} xRange=$rangeLabel")
                var firstMerge: List<IntRange>? = null
                for (mergeGap in MERGE_GAP_CANDIDATES) {
                    val segments = mergeCloseSegments(filtered, mergeGap)
                    if (firstMerge == null) firstMerge = segments
                    Log.d(TAG, "detect: segs=${segments.size} gap=$mergeGap thresh=$thresh xRange=$rangeLabel")
                    if (segments.size == TOTAL_BAR_COUNT) {
                        return buildRegion(image, segments, xRange, mergeGap)
                    }
                }
                // Pitch-fit: when count is close, remove noise spikes via anomalous center-gap detection
                firstMerge?.let { segs ->
                    val fitted = fitToExpectedCount(segs)
                    if (fitted != null) {
                        Log.d(TAG, "detect: pitchFit ${segs.size}→40 thresh=$thresh xRange=$rangeLabel")
                        return buildRegion(image, fitted, xRange, MERGE_GAP_CANDIDATES[0])
                    }
                }
            }
        }

        Log.d(TAG, "detect: returning null — no 40-segment match in any xRange/thresh/gap combo")
        return null
    }

    private fun buildThresholdCandidates(proj: IntArray, xMin: Int, xMax: Int, imageHeight: Int): List<Int> {
        val values = (xMin..xMax).map { proj[it] }.filter { it > 0 }.sorted()
        if (values.isEmpty()) return listOf(1)
        val max = values.last()
        if (max <= 1) return listOf(1)
        val p10 = values[(values.size * 0.10).toInt().coerceAtMost(values.size - 1)]
        val p50 = values[values.size / 2]
        val p90 = values[(values.size * 0.90).toInt().coerceAtMost(values.size - 1)]
        Log.d(TAG, "buildThresholdCandidates: p10=$p10 p50=$p50 p90=$p90 max=$max imageH=$imageHeight")
        val set = mutableListOf<Int>()
        // p50-based: the bar/gap boundary is near p50 — level-0 bars (0.09×height) sit just above it.
        // Offsets 1–20 cover the narrow [p50, shortest_bar] window where all bars can be detected.
        for (offset in listOf(1, 5, 10, 15, 20)) {
            set.add((p50 + offset).coerceIn(1, max - 1))
        }
        // p10-based: wider range catches distributions where the gap floor is lower
        for (frac in listOf(0.10f, 0.20f, 0.30f, 0.45f, 0.60f)) {
            set.add((p10 + (p90 - p10) * frac).toInt().coerceIn(1, max - 1))
        }
        // Legacy fallback: 2% of image height
        set.add((imageHeight * 0.02f).toInt().coerceAtLeast(1).coerceAtMost(max - 1))
        return set.distinct().sorted()
    }

    private fun detectWaveCodeYBand(image: ProcessedImage, xMin: Int, xMax: Int): IntRange? {
        // Count BRIGHT pixels per row — WaveCode paper rows have bright gap columns.
        // Background rows (dark phone/desk above/below the card) have no bright pixels.
        val rowBrightProj = IntArray(image.height) { y ->
            var count = 0
            val rowOff = y * image.width
            for (x in xMin..xMax) {
                if (image.pixels[rowOff + x] >= image.binaryThreshold) count++
            }
            count
        }
        val maxBright = rowBrightProj.maxOrNull() ?: 0
        if (maxBright <= 0) {
            Log.d(TAG, "detectWaveCodeYBand: no bright rows in x=[$xMin,$xMax] — fallback to full height")
            return null
        }
        val sortedBright = rowBrightProj.sorted()
        val p10b = sortedBright[(sortedBright.size * 0.10).toInt().coerceAtMost(sortedBright.size - 1)]
        val p50b = sortedBright[sortedBright.size / 2]
        val p90b = sortedBright[(sortedBright.size * 0.90).toInt().coerceAtMost(sortedBright.size - 1)]
        val rowThreshold = (maxBright * 0.25f).toInt().coerceAtLeast(1)
        Log.d(TAG, "detectWaveCodeYBand: brightRowProj min=${sortedBright.first()} max=$maxBright p10=$p10b p50=$p50b p90=$p90b threshold=$rowThreshold")

        var yMin = -1; var yMax = -1
        for (y in rowBrightProj.indices) {
            if (rowBrightProj[y] >= rowThreshold) { if (yMin == -1) yMin = y; yMax = y }
        }
        if (yMin == -1) {
            Log.d(TAG, "detectWaveCodeYBand: no qualifying bright rows — fallback to full height")
            return null
        }
        val bandH = yMax - yMin + 1
        if (bandH >= image.height * 0.93f) {
            Log.d(TAG, "detectWaveCodeYBand: band=[$yMin,$yMax] bandH=$bandH is ${bandH * 100 / image.height}% — not useful, fallback to full height")
            return null
        }
        val pad = (image.height * 0.05f).toInt().coerceAtLeast(4)
        val result = (yMin - pad).coerceAtLeast(0)..(yMax + pad).coerceAtMost(image.height - 1)
        Log.d(TAG, "detectWaveCodeYBand: selected yBand=[${result.first},${result.last}] bandH=${result.last - result.first + 1}")
        return result
    }

    // ── Private helpers ───────────────────────────────────────────────────────────────────────────

    /**
     * Builds a [DetectedRegion] from a validated 40-segment list.
     * All x coordinates are in full-image space (xRange offset already embedded in segment values
     * because [horizontalProjection] returns a full-width array with zeros outside the range).
     */
    private fun buildRegion(
        image: ProcessedImage,
        segments: List<IntRange>,
        xRange: IntRange,
        mergeGap: Int
    ): DetectedRegion? {
        val barCenters     = segments.map { (it.first + it.last) / 2.0f }
        val coreBarCenters = barCenters.subList(MARKER_COUNT, MARKER_COUNT + CORE_BAR_COUNT)

        val spanBetweenCenters = coreBarCenters.last() - coreBarCenters.first()
        if (spanBetweenCenters <= 0f) return null

        val coreSlotWidth = spanBetweenCenters / (CORE_BAR_COUNT - 1).toFloat()
        val coreStartX    = coreBarCenters.first() - coreSlotWidth / 2f
        val coreEndX      = coreBarCenters.last()  + coreSlotWidth / 2f

        val centerY = computeCenterY(image, segments)
        val (usableHeight, confidence) = calibrateAndScore(image, segments)

        // Sanity: marker calibration must not exceed the image height. A usableHeight far larger
        // than the image means the marker bars were mis-measured — reject so other thresholds /
        // orientations get a chance instead of sampling garbage (all-zero buckets → FormatMismatch).
        if (usableHeight > image.height * 1.1f) {
            Log.d(TAG, "buildRegion: rejecting — usableHeight=${usableHeight.toInt()} > imageH=${image.height}")
            return null
        }

        val xRangeDesc = if (xRange.first == 0 && xRange.last == image.width - 1) "full"
                         else "crop[${xRange.first},${xRange.last}]"

        val debug = "segs=40 gap=$mergeGap xRange=$xRangeDesc " +
                "coreX=[${coreStartX.toInt()},${coreEndX.toInt()}] " +
                "centerY=${centerY.toInt()} " +
                "usableH=${usableHeight.toInt()} " +
                "slotW=${coreSlotWidth.toInt()} " +
                "conf=${"%.2f".format(confidence)}"

        return DetectedRegion(coreStartX, coreEndX, centerY, usableHeight, confidence, debug)
    }

    /**
     * Horizontal projection restricted to [xMin..xMax].
     * Returns a full-width array (image.width elements) with zeros outside the range.
     * Segment x values are therefore always in full-image coordinates — no offset adjustment needed.
     */
    private fun horizontalProjection(
        image: ProcessedImage,
        xMin: Int = 0,
        xMax: Int = image.width - 1,
        yMin: Int = 0,
        yMax: Int = image.height - 1
    ): IntArray {
        val proj = IntArray(image.width)
        for (y in yMin..yMax) {
            val rowOffset = y * image.width
            for (x in xMin..xMax) {
                if (image.pixels[rowOffset + x] < image.binaryThreshold) proj[x]++
            }
        }
        return proj
    }

    /**
     * Finds the contiguous x-range that contains meaningful dark content by trimming
     * leading and trailing columns whose projection falls below 1 % of image height.
     * Adds a 5 % padding on each side so the WaveCode quiet zone is always included.
     * Returns the full image range if the trimmed region is too narrow to be useful.
     */
    private fun trimmedContentRange(image: ProcessedImage, proj: IntArray): IntRange {
        val minContent = (image.height * 0.01f).toInt().coerceAtLeast(1)
        var xMin = 0
        while (xMin < image.width - 1 && proj[xMin] < minContent) xMin++
        var xMax = image.width - 1
        while (xMax > xMin && proj[xMax] < minContent) xMax--

        if (xMax - xMin < image.width / 10) return 0 until image.width  // degenerate — use full

        val pad = (image.width * 0.05f).toInt()
        return (xMin - pad).coerceAtLeast(0)..(xMax + pad).coerceAtMost(image.width - 1)
    }

    private fun findBarSegments(proj: IntArray, threshold: Int): List<IntRange> {
        val segments = mutableListOf<IntRange>()
        var start = -1
        for (x in proj.indices) {
            if (proj[x] > threshold) {
                if (start == -1) start = x
            } else {
                if (start != -1) {
                    segments.add(start..(x - 1))
                    start = -1
                }
            }
        }
        if (start != -1) segments.add(start..(proj.size - 1))
        return segments
    }

    private fun mergeCloseSegments(segments: List<IntRange>, maxGap: Int): List<IntRange> {
        if (segments.isEmpty()) return emptyList()
        val merged = mutableListOf<IntRange>()
        var current = segments[0]
        for (i in 1 until segments.size) {
            val next = segments[i]
            val gap  = next.first - current.last - 1
            current  = if (gap <= maxGap) current.first..next.last else { merged.add(current); next }
        }
        merged.add(current)
        return merged
    }

    private fun computeCenterY(image: ProcessedImage, segments: List<IntRange>): Float {
        val centers = segments.map { xRange ->
            val cs = ArrayList<Float>(xRange.last - xRange.first + 1)
            for (x in xRange) {
                val run = columnLongestDarkRun(image, x)
                if (run.length > 0) cs.add(run.center)
            }
            if (cs.isEmpty()) image.height / 2.0f else { cs.sort(); cs[cs.size / 2] }
        }
        return centers.average().toFloat()
    }

    /**
     * Robust per-bar pixel height: the median across the segment's columns of each column's
     * longest contiguous dark run. Replaces the previous top→bottom extent, which inflated to
     * near-full image height when a column contained stray dark pixels (card edge / shadow)
     * far from the actual bar — the root cause of impossible usableHeight values on photos.
     */
    private fun barPixelHeight(image: ProcessedImage, xRange: IntRange): Int {
        val runs = ArrayList<Int>(xRange.last - xRange.first + 1)
        for (x in xRange) {
            val run = columnLongestDarkRun(image, x)
            if (run.length > 0) runs.add(run.length)
        }
        if (runs.isEmpty()) return 0
        runs.sort()
        return runs[runs.size / 2]
    }

    /** Longest contiguous vertical run of dark pixels in column [x], with its center y. */
    private data class DarkRun(val length: Int, val center: Float)

    private fun columnLongestDarkRun(image: ProcessedImage, x: Int): DarkRun {
        var best = 0; var bestStart = -1
        var cur = 0; var curStart = -1
        for (y in 0 until image.height) {
            if (image.pixels[y * image.width + x] < image.binaryThreshold) {
                if (cur == 0) curStart = y
                cur++
                if (cur > best) { best = cur; bestStart = curStart }
            } else cur = 0
        }
        return if (best > 0) DarkRun(best, bestStart + best / 2.0f) else DarkRun(0, -1f)
    }

    /**
     * Uses the decorative markers — whose height ratios are fixed by the spec — as calibration
     * references to derive the true usableHeight and a detection confidence score.
     *
     * Start markers:  segments[0]=outer(0.09), [1]=outer(0.09), [2]=inner(0.14)
     * End markers:    segments[37]=inner(0.14), [38]=outer(0.09), [39]=outer(0.09)
     */
    private fun calibrateAndScore(
        image: ProcessedImage,
        segments: List<IntRange>
    ): Pair<Float, Float> {
        val outerIdx = listOf(0, 1, 38, 39)
        val innerIdx = listOf(2, 37)

        val outerHeightsPx = outerIdx.map { barPixelHeight(image, segments[it]) }
        val innerHeightsPx = innerIdx.map { barPixelHeight(image, segments[it]) }

        val avgOuter = outerHeightsPx.filter { it > 0 }.average().toFloat()
        val avgInner = innerHeightsPx.filter { it > 0 }.average().toFloat()

        val usableFromOuter = if (avgOuter > 0f) avgOuter / OUTER_MARKER_RATIO else 0f
        val usableFromInner = if (avgInner > 0f) avgInner / INNER_MARKER_RATIO else 0f

        val usableHeight = when {
            usableFromOuter > 0f && usableFromInner > 0f -> (usableFromOuter + usableFromInner) / 2f
            usableFromOuter > 0f -> usableFromOuter
            usableFromInner > 0f -> usableFromInner
            else -> image.height * 0.93f
        }

        val outerRatio = if (usableHeight > 0f) avgOuter / usableHeight else 0f
        val innerRatio = if (usableHeight > 0f) avgInner / usableHeight else 0f
        val outerErr   = abs(outerRatio - OUTER_MARKER_RATIO) / OUTER_MARKER_RATIO
        val innerErr   = abs(innerRatio - INNER_MARKER_RATIO) / INNER_MARKER_RATIO
        val confidence = ((1f - outerErr) * 0.5f + (1f - innerErr) * 0.5f).coerceIn(0f, 1f)

        Log.d(TAG, "calibrate: avgOuter=${avgOuter.toInt()} avgInner=${avgInner.toInt()} " +
                "usableFromOuter=${usableFromOuter.toInt()} usableFromInner=${usableFromInner.toInt()} " +
                "usableHeight=${usableHeight.toInt()} imageH=${image.height} conf=${"%.2f".format(confidence)}")

        return Pair(usableHeight, confidence)
    }

    /**
     * When the segment count is close to TOTAL_BAR_COUNT but not exact, uses center-to-center
     * pitch analysis to remove noise spikes.  A real bar-to-bar gap equals roughly xRange / 40;
     * a noise spike adjacent to a tall bar sits much closer (blur halo), producing an anomalously
     * short center-to-center distance.  We remove the shorter of the two segments on either side
     * of any such narrow gap, repeating until count == TOTAL_BAR_COUNT or no narrow gap remains.
     *
     * Only acts when count is in [TOTAL_BAR_COUNT-2 .. TOTAL_BAR_COUNT+5]; returns null otherwise
     * so callers can skip the pitch-fit path entirely when the count is far off.
     */
    private fun fitToExpectedCount(segments: List<IntRange>): List<IntRange>? {
        val count = segments.size
        if (count == TOTAL_BAR_COUNT) return segments
        if (count < TOTAL_BAR_COUNT - 2 || count > TOTAL_BAR_COUNT + 5) return null
        if (count < TOTAL_BAR_COUNT) return null  // under-count: no spike to remove

        val mutableSegs = segments.toMutableList()
        var maxRemovals = mutableSegs.size - TOTAL_BAR_COUNT + 2
        while (mutableSegs.size > TOTAL_BAR_COUNT && maxRemovals-- > 0) {
            val centers = mutableSegs.map { (it.first + it.last) / 2.0f }
            val gaps = (1 until centers.size).map { centers[it] - centers[it - 1] }
            if (gaps.isEmpty()) break
            val sortedGaps = gaps.sorted()
            val medianGap = sortedGaps[sortedGaps.size / 2]
            val narrowIdx = gaps.indexOfFirst { it < medianGap * 0.50f }
            if (narrowIdx == -1) break
            val s1Width = mutableSegs[narrowIdx].last - mutableSegs[narrowIdx].first
            val s2Width = mutableSegs[narrowIdx + 1].last - mutableSegs[narrowIdx + 1].first
            val removeIdx = if (s1Width <= s2Width) narrowIdx else narrowIdx + 1
            Log.d(TAG, "fitToExpectedCount: removing seg[$removeIdx] " +
                    "gap[$narrowIdx]=${gaps[narrowIdx].toInt()} medianGap=${medianGap.toInt()}")
            mutableSegs.removeAt(removeIdx)
        }
        return if (mutableSegs.size == TOTAL_BAR_COUNT) mutableSegs else null
    }
}
