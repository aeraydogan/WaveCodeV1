package com.nandroid.wavecodev1.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user has turned animations off at the system level
 * (Settings → Developer/Accessibility → "Animator duration scale" = Off).
 *
 * Decorative, looping motion (pulse rings, equalizers) should be skipped when this is true, per the
 * `reduced-motion` accessibility guideline. Meaningful, data-driven motion (e.g. the live recording
 * level meter) may still update.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
