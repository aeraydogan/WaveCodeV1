package com.nandroid.wavecodev1.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Expressive shapes — generously rounded cards, sheets and inputs. */
val WaveCodeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small      = RoundedCornerShape(14.dp),
    medium     = RoundedCornerShape(18.dp),
    large      = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Roboto-based type scale tuned for the bold, expressive direction. */
val WaveCodeTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp
    )
)

/** App theme wrapper — apply once at the root instead of bare MaterialTheme. */
@Composable
fun WaveCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WaveCodeColorScheme,
        typography  = WaveCodeTypography,
        shapes      = WaveCodeShapes,
        content     = content
    )
}