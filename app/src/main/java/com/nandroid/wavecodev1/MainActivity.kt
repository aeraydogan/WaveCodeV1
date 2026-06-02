package com.nandroid.wavecodev1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.nandroid.wavecodev1.ui.camera.WaveCodeCameraScanScreen
import com.nandroid.wavecodev1.ui.decode.WaveCodeGalleryDecodeScreen
import com.nandroid.wavecodev1.ui.preview.WaveCodePreviewScreen

private enum class AppScreen { Preview, Decode, CameraScan }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                var screen by remember { mutableStateOf(AppScreen.Preview) }
                when (screen) {
                    AppScreen.Preview -> WaveCodePreviewScreen(
                        onNavigateToDecode = { screen = AppScreen.Decode }
                    )
                    AppScreen.Decode  -> WaveCodeGalleryDecodeScreen(
                        onNavigateBack     = { screen = AppScreen.Preview },
                        onNavigateToCamera = { screen = AppScreen.CameraScan }
                    )
                    AppScreen.CameraScan -> WaveCodeCameraScanScreen(
                        onNavigateBack = { screen = AppScreen.Decode }
                    )
                }
            }
        }
    }
}
