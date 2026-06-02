package com.nandroid.wavecodev1.wavecode.decode

enum class ConfidenceLevel { High, Medium }

enum class FailureReason {
    NotFound,         // No WaveCode bar structure detected in the image
    RegionError,      // Marker groups found but core region could not be established
    FormatMismatch,   // START/END markers or VERSION bits do not match the spec
    ChecksumError,    // Format valid but checksum does not match the decoded payload
    UnsupportedImage, // Bitmap could not be loaded or has unsupported format
    Unknown
}

data class DecodeDebugInfo(
    val bitsLength: Int = 0,
    val rawBits: String = "",
    val startMarkerValid: Boolean = false,
    val versionValid: Boolean = false,
    val endMarkerValid: Boolean = false,
    val checksumValid: Boolean = false,
    val detectedRegionDescription: String = "",
    val medianBarConfidence: Float = 0f,
    val barCount: Int = 0,
    // Phase 3A-2 additions
    val preprocessMode: String = "",      // e.g. "stretch+otsu-112"
    val appliedThreshold: Int = 128,      // binarization threshold that was used
    val totalAttempts: Int = 1,           // decode attempts tried (>1 when retry orchestrator active)
    val bucketString: String = "",        // 34 bucket values as a compact string, e.g. "3300112233…"
    // Phase 3A-2.2 candidate pipeline additions
    val appliedRotationDeg: Int = 0,      // orientation that produced this result
    val roiType: String = "",             // WaveCodeRoiType name
    val roiBounds: String = "",           // "[xMin,yMin,xMax,yMax]"
    val roiConfidence: Float = 0f,        // ROI candidate confidence
    val candidateCount: Int = 0,          // number of ROI candidates tried for this orientation
    val detectorDebug: String = ""        // region detector debug description
)

sealed class WaveCodeDecodeResult {
    data class Success(
        val publicCode: String,
        val bits: String,
        val checksum: Int,
        val confidence: Float,
        val confidenceLevel: ConfidenceLevel,
        val debugInfo: DecodeDebugInfo
    ) : WaveCodeDecodeResult()

    data class Failure(
        val reason: FailureReason,
        val debugInfo: DecodeDebugInfo
    ) : WaveCodeDecodeResult()
}
