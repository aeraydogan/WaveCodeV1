package com.nandroid.wavecodev1.wavecode.decode

import android.util.Log

private const val TAG = "WaveCodeRoiFinder"

enum class WaveCodeRoiType { BrightCard, InkCluster, FullImage }

data class WaveCodeRoiCandidate(
    val type: WaveCodeRoiType,
    val xMin: Int,
    val yMin: Int,
    val xMax: Int,
    val yMax: Int,
    val confidence: Float,
    val debugDescription: String
)

/**
 * Finds plausible sub-regions of a [ProcessedImage] that may contain a WaveCode.
 *
 * Candidate types (tried in preference order):
 *   BrightCard  — large bright rectangle in an otherwise darker image (phone screen, paper on table)
 *   InkCluster  — architecture placeholder; not emitted until reliable detection is implemented
 *   FullImage   — always included as a safe fallback
 */
object WaveCodeRoiFinder {

    fun findCandidates(image: ProcessedImage): List<WaveCodeRoiCandidate> {
        val candidates = mutableListOf<WaveCodeRoiCandidate>()

        findBrightCard(image)?.let { candidates.add(it) }
        // InkCluster: TODO — add when reliable dark-cluster detection is implemented
        candidates.add(fullImageCandidate(image))

        Log.d(TAG, "findCandidates: ${candidates.size} candidates: ${candidates.map { it.type }}")
        return candidates
    }

    // ── BrightCard ────────────────────────────────────────────────────────────────────────────────

    private fun findBrightCard(image: ProcessedImage): WaveCodeRoiCandidate? {
        val brightThreshold = (image.binaryThreshold + 80).coerceAtMost(255)
        Log.d(TAG, "findBrightCard: brightThreshold=$brightThreshold imageSize=${image.width}×${image.height}")

        val brightCol = IntArray(image.width)
        val brightRow = IntArray(image.height)
        for (y in 0 until image.height) {
            val rowOff = y * image.width
            for (x in 0 until image.width) {
                if (image.pixels[rowOff + x] >= brightThreshold) {
                    brightCol[x]++
                    brightRow[y]++
                }
            }
        }

        // Relaxed from 0.30 — tight crops have less dark border, so fewer rows/cols span the full card
        val minColBright = (image.height * 0.20f).toInt()
        val minRowBright = (image.width  * 0.20f).toInt()

        val qualCols = brightCol.count { it >= minColBright }
        val qualRows = brightRow.count { it >= minRowBright }

        var xMin = -1; var xMax = -1
        for (x in 0 until image.width) {
            if (brightCol[x] >= minColBright) { if (xMin == -1) xMin = x; xMax = x }
        }
        var yMin = -1; var yMax = -1
        for (y in 0 until image.height) {
            if (brightRow[y] >= minRowBright) { if (yMin == -1) yMin = y; yMax = y }
        }

        Log.d(TAG, "findBrightCard: brightColRange=[$xMin,$xMax] qualCols=$qualCols " +
                "brightRowRange=[$yMin,$yMax] qualRows=$qualRows " +
                "minColBright=$minColBright minRowBright=$minRowBright")

        if (xMin == -1 || yMin == -1) {
            Log.d(TAG, "findBrightCard: rejected — no qualifying bright cols/rows")
            return null
        }

        val regionW = xMax - xMin + 1
        val regionH = yMax - yMin + 1

        if (regionW < image.width * 0.20f || regionH < image.height * 0.20f) {
            Log.d(TAG, "findBrightCard: rejected — region ${regionW}×${regionH} < 20% of image")
            return null
        }

        val coverage = (regionW * regionH).toFloat() / (image.width * image.height)
        // Raised from 0.95 — tight crop makes the WaveCode card fill more of the frame
        if (coverage > 0.98f) {
            Log.d(TAG, "findBrightCard: rejected — coverage=${"%.2f".format(coverage)} > 0.98 (basically full image)")
            return null
        }

        val padX = (image.width  * 0.02f).toInt().coerceAtLeast(4)
        val padY = (image.height * 0.02f).toInt().coerceAtLeast(4)
        val px0 = (xMin - padX).coerceAtLeast(0)
        val py0 = (yMin - padY).coerceAtLeast(0)
        val px1 = (xMax + padX).coerceAtMost(image.width  - 1)
        val py1 = (yMax + padY).coerceAtMost(image.height - 1)

        val conf = (1f - coverage).coerceIn(0.2f, 0.9f)

        Log.d(TAG, "BrightCard: bounds=[$px0,$py0,$px1,$py1] size=${px1-px0+1}×${py1-py0+1} " +
                "coverage=${"%.2f".format(coverage)} brightThreshold=$brightThreshold")

        return WaveCodeRoiCandidate(
            type = WaveCodeRoiType.BrightCard,
            xMin = px0, yMin = py0, xMax = px1, yMax = py1,
            confidence = conf,
            debugDescription = "BrightCard[$px0,$py0,$px1,$py1] cov=${"%.2f".format(coverage)}"
        )
    }

    // ── FullImage fallback ────────────────────────────────────────────────────────────────────────

    private fun fullImageCandidate(image: ProcessedImage) = WaveCodeRoiCandidate(
        type = WaveCodeRoiType.FullImage,
        xMin = 0, yMin = 0, xMax = image.width - 1, yMax = image.height - 1,
        confidence = 0.1f,
        debugDescription = "FullImage[${image.width}×${image.height}]"
    )
}
