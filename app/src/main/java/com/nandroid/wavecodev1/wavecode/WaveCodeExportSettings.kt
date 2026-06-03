package com.nandroid.wavecodev1.wavecode

/**
 * Background fill used when exporting a WaveCode PNG.
 *
 * [White] is the production default (max contrast). [Skin] simulates black ink on skin so the
 * decoder can be tested against a non-white, lower-contrast background without printing —
 * closer to the real tattoo use case. Decoder uses a relative (Otsu) threshold, so as long as
 * the bars stay clearly darker than the background, both should decode.
 */
enum class WaveCodeExportBackground(val displayName: String, val color: Int) {
    White("White",     0xFFFFFFFF.toInt()),
    Skin ("Skin tone", 0xFFE8C4A0.toInt()),
    // Fully transparent — used for the tattoo try-on overlay (ink-only bars over a photo).
    // Excluded from the export-panel background picker.
    Transparent("Transparent", 0x00000000)
}

/**
 * Describes the physical and pixel dimensions for a WaveCode PNG export.
 *
 * [exportPixelWidth] is set per-preset to a quality floor (not derived from dpi alone),
 * ensuring the 34 core bars have enough pixels to survive tattoo-ink spread and camera scan.
 * [exportPixelHeight] is derived from [exportPixelWidth] / [aspectRatio].
 *
 * DECODER NOTE: the export geometry scales the same visual-level logic as the renderer.
 * A wider export just adds more pixels per bar — it does not change decode bucket mapping.
 */
data class WaveCodeExportSettings(
    val presetName: String,
    val physicalWidthCm: Float,
    val aspectRatio: Float,       // e.g. 3f means width is 3× height
    val dpi: Int,
    val exportPixelWidth: Int
) {
    val exportPixelHeight: Int = (exportPixelWidth / aspectRatio).toInt()

    companion object {
        val PRESETS: List<WaveCodeExportSettings> = listOf(
            WaveCodeExportSettings(
                presetName      = "Small Tattoo",
                physicalWidthCm = 4f,
                aspectRatio     = 3f,
                dpi             = 300,
                exportPixelWidth = 1536
            ),
            WaveCodeExportSettings(
                presetName      = "Medium Tattoo",
                physicalWidthCm = 6f,
                aspectRatio     = 3f,
                dpi             = 300,
                exportPixelWidth = 2048
            ),
            WaveCodeExportSettings(
                presetName      = "Large Tattoo",
                physicalWidthCm = 8f,
                aspectRatio     = 3f,
                dpi             = 300,
                exportPixelWidth = 2560
            ),
            WaveCodeExportSettings(
                presetName      = "Stencil High Quality",
                physicalWidthCm = 10f,
                aspectRatio     = 3f,
                dpi             = 300,
                exportPixelWidth = 4096
            )
        )

        val DEFAULT: WaveCodeExportSettings = PRESETS[1]  // Medium Tattoo
    }
}
