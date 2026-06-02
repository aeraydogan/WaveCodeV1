package com.nandroid.wavecodev1.wavecode

enum class WaveCodeVisualVariant(val displayName: String) {
    CalmMinimal("Calm / Minimal"),
    DynamicSmoothed("Dynamic / Smoothed")
}

/**
 * WaveCode v1 bit-string format:
 *
 *   START_MARKER (8) + VERSION (4) + PAYLOAD (8 chars × 5 bits = 40) + CHECKSUM (8) + END_MARKER (8)
 *   ──────────────────────────────────────────────────────────────────────────────────────────────────
 *   Total: 68 bits
 *
 * PAYLOAD : each character is mapped to its 5-bit index (0–31) in ALPHABET.
 * CHECKSUM: sum of all character indices modulo 256, encoded as 8 bits (MSB first).
 * ALPHABET: 32 unambiguous characters — excludes 0, 1, I, O to prevent visual confusion.
 *
 * ── Visual rendering (dual-variant — 8 visual levels, 4 logical decode buckets) ───────────────────
 * BITS_PER_BAR  : 2  — consecutive bits read in pairs → one machine-readable core bar per group.
 * BAR_LEVELS    : 4  — logical decode buckets.
 * VISUAL_LEVELS : 8  — distinct rendered capsule heights (2 sub-levels per decode bucket).
 *
 * ── Variant A: Calm / Minimal ───────────────────────────────────────────────────────────────────────
 *   level 0 = 0.09f  ┐  decode "00"  →  height < 0.18
 *   level 1 = 0.14f  ┘
 *   level 2 = 0.22f  ┐  decode "01"  →  0.18 ≤ height < 0.37
 *   level 3 = 0.31f  ┘
 *   level 4 = 0.42f  ┐  decode "10"  →  0.37 ≤ height < 0.60
 *   level 5 = 0.54f  ┘
 *   level 6 = 0.66f  ┐  decode "11"  →  height ≥ 0.60
 *   level 7 = 0.78f  ┘  ("11" always renders as level 6 — decode-safe)
 *
 * ── Variant B: Dynamic / Smoothed ──────────────────────────────────────────────────────────────────
 *   level 0 = 0.09f  ┐  decode "00"  →  height < 0.18
 *   level 1 = 0.14f  ┘
 *   level 2 = 0.22f  ┐  decode "01"  →  0.18 ≤ height < 0.37
 *   level 3 = 0.31f  ┘
 *   level 4 = 0.42f  ┐  decode "10"  →  0.37 ≤ height < 0.61
 *   level 5 = 0.54f  ┘
 *   level 6 = 0.68f  ┐  decode "11"  →  height ≥ 0.61
 *   level 7 = 0.82f  ┘
 *   Smoothing: if prevLevel ≥ 6 and current "11" wants level 7 → render level 6 instead.
 *
 * DECODER NOTE: use variant-specific DECODE_THRESHOLDS for bucketing. Do NOT decode the decorative
 * edge markers outside the edge gaps on each side.
 */
object WaveCodeSpec {
    const val START_MARKER   = "11100101"
    const val END_MARKER     = "10100111"
    const val VERSION_BITS   = "0001"
    const val PAYLOAD_LENGTH = 8
    const val CHECKSUM_BITS  = 8

    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  // 32 chars = 2^5

    const val BITS_PER_BAR  = 2
    const val BAR_LEVELS    = 4
    const val VISUAL_LEVELS = 8

    // ── Rendering geometry — shared by WaveCodeRenderer and WaveCodeImageExporter ───────────────
    // MARKER_SLOT_FRACTION: marker slot width as a fraction of core slot width.
    // Must be proportional — a fixed-dp marker slot only scales with DPI, while coreBarWidth
    // scales with canvas width. At high-resolution export (2048–4096 px) this caused
    // markerBarWidth > markerSlotPx, making marker bars physically overlap each other.
    const val MARKER_SLOT_FRACTION = 0.90f // marker slot = 90% of core slot width
    const val CORE_BAR_FILL        = 0.60f // core bar width as fraction of coreSlotWidth
    const val MARKER_BAR_FILL      = 0.80f // marker bar width as fraction of coreBarWidth

    // Variant A: Calm / Minimal — "11" always renders as level 6 (sub-level suppressed)
    val CALM_HEIGHT_RATIOS = floatArrayOf(
        0.09f, 0.14f,   // bucket "00"
        0.22f, 0.31f,   // bucket "01"
        0.42f, 0.54f,   // bucket "10"
        0.66f, 0.78f    // bucket "11" (index 7 reserved; not used in rendering)
    )
    val CALM_DECODE_THRESHOLDS = floatArrayOf(0.18f, 0.37f, 0.60f)

    // Variant B: Dynamic / Smoothed — "11" uses levels 6 and 7; smoothing caps consecutive peaks
    val DYNAMIC_HEIGHT_RATIOS = floatArrayOf(
        0.09f, 0.14f,   // bucket "00"
        0.22f, 0.31f,   // bucket "01"
        0.42f, 0.54f,   // bucket "10"
        0.68f, 0.82f    // bucket "11"
    )
    val DYNAMIC_DECODE_THRESHOLDS = floatArrayOf(0.18f, 0.37f, 0.61f)

    /**
     * Returns the base visual level index (0–7) for a 2-bit group, before smoothing.
     *
     * CalmMinimal: "11" always returns 6 — one sub-level only, decode-safe.
     * DynamicSmoothed: "11" returns 6 or 7 deterministically; caller applies smoothing.
     */
    fun baseVisualLevelIndex(
        group: String,
        barIndex: Int,
        publicCode: String,
        variant: WaveCodeVisualVariant
    ): Int {
        val sub = subLevelBit(publicCode, barIndex)
        return when (group) {
            "00" -> 0 + sub
            "01" -> 2 + sub
            "10" -> 4 + sub
            "11" -> when (variant) {
                WaveCodeVisualVariant.CalmMinimal    -> 6
                WaveCodeVisualVariant.DynamicSmoothed -> 6 + sub
            }
            else -> 3
        }
    }

    /**
     * Pre-computes the final visual level (0–7) for each of the 34 core bars.
     *
     * Shared by WaveCodeRenderer (Compose Canvas) and WaveCodeImageExporter (Android Canvas)
     * so that the preview and the exported PNG always produce identical bar heights.
     *
     * Priority order per bar:
     *   1. User override — accepted only if it falls within the bar's decode bucket.
     *      Overrides are visual-only: both sub-levels in a bucket decode identically.
     *   2. Dynamic smoothing (DynamicSmoothed variant only) — caps level 7 to 6 after
     *      a previous bar at level 6+. Both decode as "11"; correctness is preserved.
     *   3. Default base level from baseVisualLevelIndex().
     *
     * DECODER NOTE: decode the 34 core bars using DECODE_THRESHOLDS.
     * Decorative edge markers are outside the core region and must NOT be decoded.
     */
    fun computeLevels(
        data: WaveCodeData,
        variant: WaveCodeVisualVariant,
        visualOverrides: Map<Int, Int>
    ): List<Int> {
        val groups = data.bits.chunked(BITS_PER_BAR)
        return buildList {
            var prevLevel = -1
            groups.forEachIndexed { index, group ->
                val baseLevel  = baseVisualLevelIndex(group, index, data.publicCode, variant)
                val bucketBase = when (group) { "00" -> 0; "01" -> 2; "10" -> 4; else -> 6 }
                val override   = visualOverrides[index]
                val level = when {
                    override != null && (override == bucketBase || override == bucketBase + 1) -> override
                    variant == WaveCodeVisualVariant.DynamicSmoothed && baseLevel == 7 && prevLevel >= 6 -> 6
                    else -> baseLevel
                }
                add(level)
                prevLevel = level
            }
        }
    }

    fun heightForLevel(level: Int, variant: WaveCodeVisualVariant): Float {
        val ratios = when (variant) {
            WaveCodeVisualVariant.CalmMinimal    -> CALM_HEIGHT_RATIOS
            WaveCodeVisualVariant.DynamicSmoothed -> DYNAMIC_HEIGHT_RATIOS
        }
        return ratios[level.coerceIn(0, 7)]
    }

    fun decodeThresholds(variant: WaveCodeVisualVariant): FloatArray = when (variant) {
        WaveCodeVisualVariant.CalmMinimal    -> CALM_DECODE_THRESHOLDS
        WaveCodeVisualVariant.DynamicSmoothed -> DYNAMIC_DECODE_THRESHOLDS
    }

    // Returns 0 or 1 deterministically from publicCode and bar position.
    private fun subLevelBit(publicCode: String, barIndex: Int): Int =
        (publicCode.fold(0) { acc, c -> acc * 31 + c.code } + barIndex) and 1
}
