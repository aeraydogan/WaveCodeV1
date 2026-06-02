package com.nandroid.wavecodev1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.nandroid.wavecodev1.ui.camera.WaveCodeCameraScanScreen
import com.nandroid.wavecodev1.ui.create.WaveCodeCreateScreen
import com.nandroid.wavecodev1.ui.decode.WaveCodeGalleryDecodeScreen
import com.nandroid.wavecodev1.ui.listen.ListenByCodeScreen
import com.nandroid.wavecodev1.ui.preview.WaveCodePreviewScreen
import com.nandroid.wavecodev1.ui.tryon.WaveCodeTryOnScreen
import com.nandroid.wavecodev1.wavecode.WaveCodeData
import com.nandroid.wavecodev1.wavecode.WaveCodeVisualVariant

private enum class AppScreen { Preview, Create, Decode, CameraScan, TryOn, Listen }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                var screen by remember { mutableStateOf(AppScreen.Preview) }

                // Snapshot passed from Preview into the Try-On placement screen.
                var tryOnData      by remember { mutableStateOf<WaveCodeData?>(null) }
                var tryOnVariant   by remember { mutableStateOf(WaveCodeVisualVariant.CalmMinimal) }
                var tryOnOverrides by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }

                when (screen) {
                    AppScreen.Preview -> WaveCodePreviewScreen(
                        onNavigateToDecode = { screen = AppScreen.Decode },
                        onNavigateToCreate = { screen = AppScreen.Create },
                        onNavigateToListen = { screen = AppScreen.Listen },
                        onNavigateToTryOn  = { data, variant, overrides ->
                            tryOnData = data
                            tryOnVariant = variant
                            tryOnOverrides = overrides
                            screen = AppScreen.TryOn
                        }
                    )
                    AppScreen.Create  -> WaveCodeCreateScreen(
                        onNavigateBack = { screen = AppScreen.Preview }
                    )
                    AppScreen.Decode  -> WaveCodeGalleryDecodeScreen(
                        onNavigateBack     = { screen = AppScreen.Preview },
                        onNavigateToCamera = { screen = AppScreen.CameraScan }
                    )
                    AppScreen.CameraScan -> WaveCodeCameraScanScreen(
                        onNavigateBack = { screen = AppScreen.Decode }
                    )
                    AppScreen.Listen -> ListenByCodeScreen(
                        onNavigateBack = { screen = AppScreen.Preview }
                    )
                    AppScreen.TryOn -> {
                        val data = tryOnData
                        if (data == null) {
                            screen = AppScreen.Preview
                        } else {
                            WaveCodeTryOnScreen(
                                data            = data,
                                variant         = tryOnVariant,
                                visualOverrides = tryOnOverrides,
                                onNavigateBack  = { screen = AppScreen.Preview }
                            )
                        }
                    }
                }
            }
        }
    }
}
