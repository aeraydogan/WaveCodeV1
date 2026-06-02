package com.nandroid.wavecodev1.wavecode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * WaveCode v1.5 visual renderer — dual-variant floating Spotify-like style, 8 visual levels.
 *
 * Canvas layout (left → right):
 *   [quiet] [3 start markers] [edge gap] [34 core bars] [edge gap] [3 end markers] [quiet]
 *
 * ── CORE BARS (34) ───────────────────────────────────────────────────────────────────────────────
 * Machine-readable. Each bar represents one 2-bit group encoded using 8 visual height levels
 * that map to 4 logical decode buckets. Floating vertical capsules centred on canvas midpoint.
 * See WaveCodeSpec for height ratios, decode thresholds, and variant-specific level logic.
 *
 * ── DECORATIVE EDGE MARKERS ──────────────────────────────────────────────────────────────────────
 * Start: [level 0, level 0, level 1]  outer → inner
 * End:   [level 1, level 0, level 0]  inner → outer
 * NOT machine-readable — outside the core region edge gap. Not editable via tap.
 *
 * ── VISUAL TUNING OVERRIDES ──────────────────────────────────────────────────────────────────────
 * visualOverrides: map of coreBarIndex (0–33) → overridden visual level (0–7).
 *
 * Overrides are DECODE-SAFE: each override must stay within the same logical bucket as the
 * bar's encoded group. Levels 0/1 both decode "00", 2/3 → "01", 4/5 → "10", 6/7 → "11".
 * The future decoder reads bucket thresholds, not exact visual levels — an override at level 1
 * is indistinguishable from level 0 to the decoder. The payload is never altered.
 *
 * Override priority per bar:
 *   1. User override (validated to stay in the correct decode bucket)
 *   2. Dynamic smoothing for non-overridden bars (DynamicSmoothed variant only)
 *   3. Default base level from WaveCodeSpec
 *
 * ── TAP HANDLING ─────────────────────────────────────────────────────────────────────────────────
 * When onBarTap is non-null, taps on the core region fire onBarTap(coreBarIndex).
 * Taps on quiet zones or decorative markers (outside coreStartX…coreEndX) are ignored.
 * The geometry computation in pointerInput mirrors the DrawScope geometry exactly.
 */
@Composable
fun WaveCodeRenderer(
    data: WaveCodeData,
    variant: WaveCodeVisualVariant = WaveCodeVisualVariant.CalmMinimal,
    visualOverrides: Map<Int, Int> = emptyMap(),
    onBarTap: ((barIndex: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val groups = data.bits.chunked(WaveCodeSpec.BITS_PER_BAR)  // 34 groups

    // Shared with WaveCodeImageExporter — both must call this function so preview
    // and exported PNG always render identical bar heights.
    val levels = WaveCodeSpec.computeLevels(data, variant, visualOverrides)

    // Capture density and callback outside pointerInput so they survive recomposition.
    val density        = LocalDensity.current
    val currentOnBarTap by rememberUpdatedState(onBarTap)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cb = currentOnBarTap ?: return@detectTapGestures
                    // Mirror DrawScope geometry using captured density.
                    with(density) {
                        val quietZonePx       = 12.dp.toPx()
                        val edgeGapPx         = 12.dp.toPx()
                        val canvasWidth       = size.width.toFloat()
                        val usableForBars     = canvasWidth - 2f * quietZonePx - 2f * edgeGapPx
                        val coreSlotWidth     = usableForBars / (groups.size + 6f * WaveCodeSpec.MARKER_SLOT_FRACTION)
                        val markerSlotWidth   = coreSlotWidth * WaveCodeSpec.MARKER_SLOT_FRACTION
                        val markerRegionWidth = 3 * markerSlotWidth
                        val coreStartX        = quietZonePx + markerRegionWidth + edgeGapPx
                        val coreRegionWidth   = groups.size * coreSlotWidth
                        val relX              = offset.x - coreStartX
                        // Taps outside the core region (quiet zone, decorative markers) are ignored.
                        if (relX >= 0f && relX < coreRegionWidth) {
                            val barIndex = (relX / coreSlotWidth).toInt().coerceIn(0, groups.size - 1)
                            cb(barIndex)
                        }
                    }
                }
            }
    ) {
        val quietZonePx  = 12.dp.toPx()
        val markerCount  = 3
        val edgeGapPx    = 12.dp.toPx()
        val vertPadPx    = 12.dp.toPx()
        val usableHeight = size.height - 2f * vertPadPx
        val centerY      = size.height / 2f

        // ── Core + marker geometry (proportional — marker slot scales with core slot) ─────────────
        // 6 decorative marker slots total (3 start + 3 end), each MARKER_SLOT_FRACTION × coreSlot.
        val usableForBars     = size.width - 2f * quietZonePx - 2f * edgeGapPx
        val coreSlotWidth     = usableForBars / (groups.size + 6f * WaveCodeSpec.MARKER_SLOT_FRACTION)
        val markerSlotWidth   = coreSlotWidth * WaveCodeSpec.MARKER_SLOT_FRACTION
        val coreBarWidth      = (coreSlotWidth * WaveCodeSpec.CORE_BAR_FILL).coerceAtLeast(2f)
        val coreCornerRadius  = CornerRadius(coreBarWidth / 2f, coreBarWidth / 2f)
        val markerRegionWidth = markerCount * markerSlotWidth
        val coreStartX        = quietZonePx + markerRegionWidth + edgeGapPx

        // ── Decorative marker geometry ────────────────────────────────────────────────────────────
        val markerBarWidth     = (coreBarWidth * WaveCodeSpec.MARKER_BAR_FILL).coerceAtLeast(1.5f)
        val markerCornerRadius = CornerRadius(markerBarWidth / 2f, markerBarWidth / 2f)

        val lvl0H = usableHeight * WaveCodeSpec.heightForLevel(0, variant)
        val lvl1H = usableHeight * WaveCodeSpec.heightForLevel(1, variant)

        // White background
        drawRect(color = Color.White, topLeft = Offset.Zero, size = size)

        // ── Start decorative edge markers: [level 0, level 0, level 1] outer → inner ────────────
        val startHeights = floatArrayOf(lvl0H, lvl0H, lvl1H)
        repeat(markerCount) { i ->
            val slotLeft = quietZonePx + i * markerSlotWidth
            val barLeft  = slotLeft + (markerSlotWidth - markerBarWidth) / 2f
            val mh       = startHeights[i]
            drawRoundRect(
                color        = Color.Black,
                topLeft      = Offset(barLeft, centerY - mh / 2f),
                size         = Size(markerBarWidth, mh),
                cornerRadius = markerCornerRadius
            )
        }

        // ── End decorative edge markers: [level 1, level 0, level 0] inner → outer ──────────────
        val endMarkerStartX = coreStartX + groups.size * coreSlotWidth + edgeGapPx
        val endHeights      = floatArrayOf(lvl1H, lvl0H, lvl0H)
        repeat(markerCount) { i ->
            val slotLeft = endMarkerStartX + i * markerSlotWidth
            val barLeft  = slotLeft + (markerSlotWidth - markerBarWidth) / 2f
            val mh       = endHeights[i]
            drawRoundRect(
                color        = Color.Black,
                topLeft      = Offset(barLeft, centerY - mh / 2f),
                size         = Size(markerBarWidth, mh),
                cornerRadius = markerCornerRadius
            )
        }

        // ── Core bars — 34 machine-readable floating capsules ─────────────────────────────────────
        levels.forEachIndexed { index, level ->
            val barHeight = usableHeight * WaveCodeSpec.heightForLevel(level, variant)
            val slotLeft  = coreStartX + index * coreSlotWidth
            val barLeft   = slotLeft + (coreSlotWidth - coreBarWidth) / 2f
            drawRoundRect(
                color        = Color.Black,
                topLeft      = Offset(barLeft, centerY - barHeight / 2f),
                size         = Size(coreBarWidth, barHeight),
                cornerRadius = coreCornerRadius
            )
        }
    }
}
