package com.nandroid.wavecodev1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.nandroid.wavecodev1.ui.camera.WaveCodeCameraScanScreen
import com.nandroid.wavecodev1.ui.create.WaveCodeCreateScreen
import com.nandroid.wavecodev1.ui.create.WaveCodeTattooCreateScreen
import com.nandroid.wavecodev1.ui.decode.WaveCodeGalleryDecodeScreen
import com.nandroid.wavecodev1.ui.home.WaveCodeHomeScreen
import com.nandroid.wavecodev1.ui.listen.ListenByCodeScreen
import android.net.Uri
import com.nandroid.wavecodev1.ui.preview.WaveCodePreviewScreen
import com.nandroid.wavecodev1.ui.scan.ScanListenScreen
import com.nandroid.wavecodev1.ui.tryon.WaveCodeTryOnScreen
import com.nandroid.wavecodev1.wavecode.WaveCodeData
import com.nandroid.wavecodev1.wavecode.WaveCodeVisualVariant

private enum class AppScreen { Home, TattooCreate, ScanListen, Preview, Create, Decode, CameraScan, TryOn, Listen }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                // Simple navigation back stack — the top entry is the visible screen.
                val backStack = remember { mutableStateListOf(AppScreen.Home) }
                val screen = backStack.last()

                fun navigateTo(target: AppScreen) { backStack.add(target) }
                fun goBack() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

                // Hardware/system back: pop to the previous screen. Disabled on the root (Home)
                // so the system performs its default action (leave the app).
                BackHandler(enabled = backStack.size > 1) { goBack() }

                // Snapshot passed into the Try-On placement screen.
                var tryOnData         by remember { mutableStateOf<WaveCodeData?>(null) }
                var tryOnVariant      by remember { mutableStateOf(WaveCodeVisualVariant.CalmMinimal) }
                var tryOnOverrides    by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
                var tryOnInitialPhoto by remember { mutableStateOf<Uri?>(null) }

                when (screen) {
                    AppScreen.Home -> WaveCodeHomeScreen(
                        onNavigateToCreate = { navigateTo(AppScreen.TattooCreate) },
                        onNavigateToScan   = { navigateTo(AppScreen.ScanListen) }
                    )
                    AppScreen.ScanListen -> ScanListenScreen(
                        onNavigateBack     = { goBack() },
                        onNavigateToCamera = { navigateTo(AppScreen.CameraScan) }
                    )
                    AppScreen.TattooCreate -> WaveCodeTattooCreateScreen(
                        onNavigateBack    = { goBack() },
                        onNavigateToTryOn = { data, photoUri ->
                            tryOnData = data
                            tryOnVariant = WaveCodeVisualVariant.CalmMinimal
                            tryOnOverrides = emptyMap()
                            tryOnInitialPhoto = photoUri
                            navigateTo(AppScreen.TryOn)
                        }
                    )
                    AppScreen.Preview -> WaveCodePreviewScreen(
                        onNavigateToDecode = { navigateTo(AppScreen.Decode) },
                        onNavigateToCreate = { navigateTo(AppScreen.Create) },
                        onNavigateToListen = { navigateTo(AppScreen.Listen) },
                        onNavigateToTryOn  = { data, variant, overrides ->
                            tryOnData = data
                            tryOnVariant = variant
                            tryOnOverrides = overrides
                            tryOnInitialPhoto = null
                            navigateTo(AppScreen.TryOn)
                        }
                    )
                    AppScreen.Create  -> WaveCodeCreateScreen(
                        onNavigateBack = { goBack() }
                    )
                    AppScreen.Decode  -> WaveCodeGalleryDecodeScreen(
                        onNavigateBack     = { goBack() },
                        onNavigateToCamera = { navigateTo(AppScreen.CameraScan) }
                    )
                    AppScreen.CameraScan -> WaveCodeCameraScanScreen(
                        onNavigateBack = { goBack() }
                    )
                    AppScreen.Listen -> ListenByCodeScreen(
                        onNavigateBack = { goBack() }
                    )
                    AppScreen.TryOn -> {
                        val data = tryOnData
                        if (data == null) {
                            // No generated WaveCode → nothing to place; return to the previous screen.
                            goBack()
                        } else {
                            WaveCodeTryOnScreen(
                                data            = data,
                                variant         = tryOnVariant,
                                visualOverrides = tryOnOverrides,
                                initialPhotoUri = tryOnInitialPhoto,
                                onNavigateBack  = { goBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
