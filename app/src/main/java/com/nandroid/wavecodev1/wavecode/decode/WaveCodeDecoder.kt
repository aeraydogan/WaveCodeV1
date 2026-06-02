package com.nandroid.wavecodev1.wavecode.decode

import android.graphics.Bitmap
import android.util.Log

private const val TAG = "WaveCodeDecoder"

/**
 * Orchestrates the Phase 3A-2.2 decode pipeline for gallery images (clean exports +
 * photographed/printed WaveCode images).
 *
 * Pipeline:
 *   1. Preprocess  — grayscale + contrast stretching + Otsu threshold (WaveCodeImagePreprocessor)
 *   2. For each orientation (0°, 90°, 180°, 270°):
 *      a. Rotate the preprocessed image.
 *      b. Find ROI candidates (WaveCodeRoiFinder): BrightCard, then FullImage fallback.
 *      c. For each candidate:
 *         i.  Crop to candidate bounds (or use full oriented image for FullImage).
 *         ii. Detect region (WaveCodeRegionDetector).
 *         iii.Sample bars (WaveCodeBarSampler).
 *         iv. Parse bits (WaveCodeBitParser).
 *         v.  Return immediately on first Success.
 *   3. If no success found, return the most informative failure.
 *
 * Must be called on a background thread (IO dispatcher) — bitmap processing is heavy.
 */
object WaveCodeDecoder {

    private val ORIENTATIONS = intArrayOf(0, 90, 180, 270)

    fun decode(bitmap: Bitmap): WaveCodeDecodeResult {
        Log.d(TAG, "decode: start size=${bitmap.width}×${bitmap.height}")

        val processed = WaveCodeImagePreprocessor.preprocess(bitmap).getOrElse { e ->
            Log.e(TAG, "preprocess failed", e)
            return WaveCodeDecodeResult.Failure(FailureReason.UnsupportedImage, DecodeDebugInfo())
        }
        val preprocessMode = "stretch+otsu-${processed.binaryThreshold}"
        Log.d(TAG, "decode: preprocessed size=${processed.width}×${processed.height} " +
                "threshold=${processed.binaryThreshold}")

        var bestFailure: WaveCodeDecodeResult.Failure? = null
        var totalAttempts = 0

        for (orientDeg in ORIENTATIONS) {
            val oriented = processed.rotated(orientDeg)
            val candidates = WaveCodeRoiFinder.findCandidates(oriented)

            Log.d(TAG, "decode: orientation=$orientDeg roi candidates=${candidates.size} " +
                    "[${candidates.joinToString { it.type.name }}]")

            for (candidate in candidates) {
                totalAttempts++

                val candidateImage = if (candidate.type == WaveCodeRoiType.FullImage) {
                    oriented
                } else {
                    oriented.cropped(candidate.xMin, candidate.yMin, candidate.xMax, candidate.yMax)
                }

                Log.d(TAG, "decode: attempt #$totalAttempts orientation=$orientDeg " +
                        "roi=${candidate.type} " +
                        "bounds=[${candidate.xMin},${candidate.yMin},${candidate.xMax},${candidate.yMax}] " +
                        "size=${candidateImage.width}×${candidateImage.height}")

                val region = WaveCodeRegionDetector.detect(candidateImage)
                if (region == null) {
                    Log.d(TAG, "decode: region failed orientation=$orientDeg roi=${candidate.type}")
                    val f = WaveCodeDecodeResult.Failure(
                        FailureReason.NotFound,
                        DecodeDebugInfo(
                            preprocessMode       = preprocessMode,
                            appliedThreshold     = processed.binaryThreshold,
                            appliedRotationDeg   = orientDeg,
                            roiType              = candidate.type.name,
                            roiBounds            = roiBoundsStr(candidate),
                            roiConfidence        = candidate.confidence,
                            candidateCount       = candidates.size,
                            totalAttempts        = totalAttempts,
                            detectedRegionDescription = "No WaveCode bar structure found"
                        )
                    )
                    if (bestFailure == null) bestFailure = f
                    continue
                }

                Log.d(TAG, "decode: region found orientation=$orientDeg conf=${region.confidence} " +
                        region.debugDescription)

                val sampled = WaveCodeBarSampler.sample(candidateImage, region)
                val bucketString = sampled.buckets.joinToString("") { it.toString() }
                val sortedConf = sampled.barConfidences.copyOf().also { java.util.Arrays.sort(it) }
                val medianConf = sortedConf[sortedConf.size / 2]

                val parsed = WaveCodeBitParser.parse(sampled.buckets, medianConf)

                val roiBounds = roiBoundsStr(candidate)

                when (parsed) {
                    is WaveCodeDecodeResult.Success -> {
                        Log.d(TAG, "decode: parse success publicCode=${parsed.publicCode} " +
                                "confidence=${parsed.confidence} orientation=$orientDeg roi=${candidate.type}")
                        return parsed.copy(
                            debugInfo = parsed.debugInfo.copy(
                                detectedRegionDescription = region.debugDescription,
                                preprocessMode            = preprocessMode,
                                appliedThreshold          = processed.binaryThreshold,
                                appliedRotationDeg        = orientDeg,
                                roiType                   = candidate.type.name,
                                roiBounds                 = roiBounds,
                                roiConfidence             = candidate.confidence,
                                candidateCount            = candidates.size,
                                totalAttempts             = totalAttempts,
                                bucketString              = bucketString,
                                detectorDebug             = region.debugDescription
                            )
                        )
                    }
                    is WaveCodeDecodeResult.Failure -> {
                        Log.d(TAG, "decode: parse failed orientation=$orientDeg roi=${candidate.type} " +
                                "reason=${parsed.reason} " +
                                "start=${parsed.debugInfo.startMarkerValid} " +
                                "version=${parsed.debugInfo.versionValid} " +
                                "end=${parsed.debugInfo.endMarkerValid} " +
                                "checksum=${parsed.debugInfo.checksumValid} " +
                                "rawBits=${parsed.debugInfo.rawBits}")
                        val f = parsed.copy(
                            debugInfo = parsed.debugInfo.copy(
                                detectedRegionDescription = region.debugDescription,
                                preprocessMode            = preprocessMode,
                                appliedThreshold          = processed.binaryThreshold,
                                appliedRotationDeg        = orientDeg,
                                roiType                   = candidate.type.name,
                                roiBounds                 = roiBounds,
                                roiConfidence             = candidate.confidence,
                                candidateCount            = candidates.size,
                                totalAttempts             = totalAttempts,
                                bucketString              = bucketString,
                                detectorDebug             = region.debugDescription
                            )
                        )
                        // Prefer failures that got furthest: ChecksumError > FormatMismatch > NotFound
                        if (bestFailure == null || failureRank(f.reason) > failureRank(bestFailure!!.reason)) {
                            bestFailure = f
                        }
                    }
                }
            }
        }

        Log.d(TAG, "decode: all $totalAttempts attempts exhausted bestFailure=${bestFailure?.reason}")
        return bestFailure ?: WaveCodeDecodeResult.Failure(
            FailureReason.NotFound,
            DecodeDebugInfo(
                preprocessMode = preprocessMode,
                appliedThreshold = processed.binaryThreshold,
                totalAttempts = totalAttempts,
                detectedRegionDescription = "All $totalAttempts attempts failed"
            )
        )
    }

    private fun roiBoundsStr(c: WaveCodeRoiCandidate) = "[${c.xMin},${c.yMin},${c.xMax},${c.yMax}]"

    private fun failureRank(r: FailureReason) = when (r) {
        FailureReason.ChecksumError  -> 3
        FailureReason.FormatMismatch -> 2
        FailureReason.RegionError    -> 1
        else                         -> 0
    }
}
