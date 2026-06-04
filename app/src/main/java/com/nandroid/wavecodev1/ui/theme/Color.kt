package com.nandroid.wavecodev1.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * "Ink & Signal" design tokens — dark-first (OLED true black), single electric accent.
 * These are the single source of truth for app colors; screens reference these, not raw literals.
 */
object WaveCodeColors {
    val Canvas        = Color(0xFF000000)   // app background (OLED true black)
    val Surface1      = Color(0xFF121214)   // cards
    val Surface2      = Color(0xFF1C1C20)   // sheets / raised
    val Surface3      = Color(0xFF26262B)   // inputs / tonal buttons / active
    val Outline       = Color(0xFF2E2E34)   // hairline borders / dividers

    val TextPrimary   = Color(0xFFECECEC)
    val TextSecondary = Color(0xFFA0A0A6)
    val TextMuted     = Color(0xFF8A8A92)   // AA (~5.4:1 on Canvas) — lifted from #6E6E76 for readability

    val Accent        = Color(0xFFC8FF3D)   // Electric Lime
    val OnAccent      = Color(0xFF0A0A0A)

    val Success       = Color(0xFF4ADE80)
    val Error         = Color(0xFFFF5D5D)
    val Recording     = Color(0xFFFF4D4D)

    val Skin          = Color(0xFFE8C4A0)   // skin-tone preview background
    val SuccessCard   = Color(0xFF15240C)   // tinted accent surface for resolved/result cards
}

/** Material 3 dark color scheme mapped from the tokens (drives built-in components). */
val WaveCodeColorScheme = darkColorScheme(
    primary             = WaveCodeColors.Accent,
    onPrimary           = WaveCodeColors.OnAccent,
    secondary           = WaveCodeColors.Accent,
    onSecondary         = WaveCodeColors.OnAccent,
    background          = WaveCodeColors.Canvas,
    onBackground        = WaveCodeColors.TextPrimary,
    surface             = WaveCodeColors.Surface1,
    onSurface           = WaveCodeColors.TextPrimary,
    surfaceVariant      = WaveCodeColors.Surface2,
    onSurfaceVariant    = WaveCodeColors.TextSecondary,
    secondaryContainer  = WaveCodeColors.Surface3,
    onSecondaryContainer = WaveCodeColors.TextPrimary,
    outline             = WaveCodeColors.Outline,
    outlineVariant      = WaveCodeColors.Outline,
    error               = WaveCodeColors.Error,
    onError             = WaveCodeColors.OnAccent,
    scrim               = Color(0xCC000000)
)