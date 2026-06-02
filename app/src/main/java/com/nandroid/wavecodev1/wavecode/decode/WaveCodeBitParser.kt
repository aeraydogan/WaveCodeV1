package com.nandroid.wavecodev1.wavecode.decode

import com.nandroid.wavecodev1.wavecode.WaveCodeSpec

/**
 * Pure Kotlin — no Android dependencies. Fully testable on JVM.
 *
 * Input : IntArray of 34 bucket values (each 0–3), one per core bar.
 *   Bucket 0 → "00", 1 → "01", 2 → "10", 3 → "11"
 *
 * Builds the 68-bit string and validates:
 *   bits[0..7]   START_MARKER  "11100101"
 *   bits[8..11]  VERSION_BITS  "0001"
 *   bits[12..51] PAYLOAD       8 chars × 5 bits (MSB first)
 *   bits[52..59] CHECKSUM      sum of char indices mod 256
 *   bits[60..67] END_MARKER    "10100111"
 *
 * Internal debug check: encode "A7K29XQ4" with WaveCodeEncoder and verify
 * that parsing its buckets round-trips to the same publicCode.
 * Run via WaveCodeBitParser.selfTest() — returns true if the round-trip passes.
 */
object WaveCodeBitParser {

    private const val EXPECTED_BARS = 34
    private const val EXPECTED_BITS = EXPECTED_BARS * WaveCodeSpec.BITS_PER_BAR  // 68

    fun parse(buckets: IntArray, medianBarConfidence: Float = 0f): WaveCodeDecodeResult {
        if (buckets.size != EXPECTED_BARS) {
            return WaveCodeDecodeResult.Failure(
                FailureReason.FormatMismatch,
                DecodeDebugInfo(bitsLength = buckets.size * 2, barCount = buckets.size)
            )
        }

        val bits = buildString(EXPECTED_BITS) {
            for (b in buckets) append(when (b) { 0 -> "00"; 1 -> "01"; 2 -> "10"; else -> "11" })
        }

        val startValid   = bits.substring(0, 8)  == WaveCodeSpec.START_MARKER
        val versionValid = bits.substring(8, 12) == WaveCodeSpec.VERSION_BITS
        val endValid     = bits.substring(60, 68) == WaveCodeSpec.END_MARKER

        val debugBase = DecodeDebugInfo(
            bitsLength              = bits.length,
            rawBits                 = bits,
            startMarkerValid        = startValid,
            versionValid            = versionValid,
            endMarkerValid          = endValid,
            barCount                = EXPECTED_BARS,
            medianBarConfidence     = medianBarConfidence
        )

        if (!startValid || !versionValid || !endValid) {
            return WaveCodeDecodeResult.Failure(FailureReason.FormatMismatch, debugBase)
        }

        // Decode payload: bits[12..51], 8 chars × 5 bits, MSB first
        val payloadBits = bits.substring(12, 52)
        val publicCode = buildString {
            for (group in payloadBits.chunked(5)) {
                val idx = group.toInt(2)
                if (idx >= WaveCodeSpec.ALPHABET.length) {
                    return WaveCodeDecodeResult.Failure(FailureReason.FormatMismatch, debugBase)
                }
                append(WaveCodeSpec.ALPHABET[idx])
            }
        }

        // Validate checksum: bits[52..59]
        val decodedChecksum  = bits.substring(52, 60).toInt(2)
        val computedChecksum = publicCode.sumOf { WaveCodeSpec.ALPHABET.indexOf(it) } % 256
        val checksumValid    = decodedChecksum == computedChecksum

        val debug = debugBase.copy(checksumValid = checksumValid)

        if (!checksumValid) {
            return WaveCodeDecodeResult.Failure(FailureReason.ChecksumError, debug)
        }

        val level = if (medianBarConfidence > 0.70f) ConfidenceLevel.High else ConfidenceLevel.Medium

        return WaveCodeDecodeResult.Success(
            publicCode      = publicCode,
            bits            = bits,
            checksum        = decodedChecksum,
            confidence      = medianBarConfidence,
            confidenceLevel = level,
            debugInfo       = debug
        )
    }

    /**
     * Round-trip self-test: encodes a known code with WaveCodeEncoder, extracts buckets
     * from the bit string, parses them, and checks the recovered publicCode matches.
     * Returns true if the pipeline is internally consistent.
     * Safe to call from any thread; no IO.
     */
    fun selfTest(): Boolean {
        val testCode = "A7K29XQ4"
        val encoded = com.nandroid.wavecodev1.wavecode.WaveCodeEncoder.encode(testCode)
            .getOrNull() ?: return false
        val buckets = encoded.bits.chunked(2).map { group ->
            when (group) { "00" -> 0; "01" -> 1; "10" -> 2; else -> 3 }
        }.toIntArray()
        val result = parse(buckets, medianBarConfidence = 1.0f)
        return result is WaveCodeDecodeResult.Success && result.publicCode == testCode
    }
}
