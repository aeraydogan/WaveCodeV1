package com.nandroid.wavecodev1.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * App icon set as inline vectors (no material-icons dependency). Shapes mirror the design mockup.
 * Icons are tinted at the call site via [androidx.compose.material3.Icon] `tint`, so the intrinsic
 * color here is irrelevant.
 */
object WaveCodeIcons {

    private val WHITE = SolidColor(Color.White)

    val Mic      = stroked("mic", "M12 3 a3 3 0 0 1 3 3 v5 a3 3 0 0 1 -6 0 v-5 a3 3 0 0 1 3 -3 z M5.5 11 a6.5 6.5 0 0 0 13 0 M12 17.5 V21.5")

    val Scan     = stroked("scan", "M3 8.5 V6 a3 3 0 0 1 3 -3 h2.5 M15.5 3 H18 a3 3 0 0 1 3 3 v2.5 M21 15.5 V18 a3 3 0 0 1 -3 3 h-2.5 M8.5 21 H6 a3 3 0 0 1 -3 -3 v-2.5 M9 14 V10 M12 16 V8 M15 14 V10")

    val Camera   = stroked("camera", "M4 8 H6.8 L8.1 6 H15.9 L17.2 8 H20 A1 1 0 0 1 21 9 V18 A2 2 0 0 1 19 20 H5 A2 2 0 0 1 3 18 V9 A1 1 0 0 1 4 8 Z M12 9.7 a3.3 3.3 0 1 0 0 6.6 a3.3 3.3 0 1 0 0 -6.6 z")

    val Play     = filled("play", "M7 4.5 L20 12 L7 19.5 Z")

    val Replay   = stroked("replay", "M4 12 a8 8 0 1 1 2.4 5.7 M3 9.5 l1.2 3.2 l3.2 -1.2")

    val Back     = stroked("back", "M20 12 H5 M11 6 l-6 6 l6 6")

    val Copy     = stroked("copy", "M9 9 H18 A1.5 1.5 0 0 1 19.5 10.5 V19.5 A1.5 1.5 0 0 1 18 21 H9 A1.5 1.5 0 0 1 7.5 19.5 V10.5 A1.5 1.5 0 0 1 9 9 Z M5 15 H4.5 A1.5 1.5 0 0 1 3 13.5 V4.5 A1.5 1.5 0 0 1 4.5 3 H13.5 A1.5 1.5 0 0 1 15 4.5 V5")

    val Check    = stroked("check", "M5 13 l4 4 l10 -11")

    val Share    = stroked("share", "M16 6 l-4 -3 l-4 3 M12 3 V14 M5 12 v6 a1 1 0 0 0 1 1 h12 a1 1 0 0 0 1 -1 v-6")

    val Download = stroked("download", "M12 3 V15 M7 11 l5 5 l5 -5 M5 20 H19")

    val Rotate   = stroked("rotate", "M21 12 a9 9 0 1 1 -2.7 -6.4 M21 3 v4.5 h-4.5")

    val Swap     = stroked("swap", "M7 7 H17 l-3 -3 M17 17 H7 l3 3")

    // Gallery needs a filled dot + stroked frame/mountain, so it is built explicitly.
    val Gallery: ImageVector = ImageVector.Builder("gallery", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            PathParser().parsePathString(
                "M6.5 4 H17.5 A3.5 3.5 0 0 1 21 7.5 V16.5 A3.5 3.5 0 0 1 17.5 20 H6.5 A3.5 3.5 0 0 1 3 16.5 V7.5 A3.5 3.5 0 0 1 6.5 4 Z M4.5 18 L9 13.5 L12 16.5 L16 12 L19.5 16.5"
            ).toNodes(),
            stroke = WHITE, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            PathParser().parsePathString("M8.5 7.8 a1.7 1.7 0 1 0 0 3.4 a1.7 1.7 0 1 0 0 -3.4 z").toNodes(),
            fill = WHITE
        )
    }.build()

    private fun stroked(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                PathParser().parsePathString(d).toNodes(),
                stroke = WHITE, strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }.build()

    private fun filled(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(PathParser().parsePathString(d).toNodes(), fill = WHITE)
        }.build()
}