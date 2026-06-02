package com.nandroid.wavecodev1.ui.tryon

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nandroid.wavecodev1.wavecode.WaveCodeData
import com.nandroid.wavecodev1.wavecode.WaveCodeRenderer
import com.nandroid.wavecodev1.wavecode.WaveCodeVisualVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tattoo placement preview: a user photo as the backdrop with the WaveCode overlaid as ink-only
 * bars the user can pan / pinch-zoom / rotate. Preview-only (no save). The overlay is rendered
 * with a transparent background ([WaveCodeRenderer] drawBackground = false) so it reads as ink.
 */
@Composable
fun WaveCodeTryOnScreen(
    data: WaveCodeData,
    variant: WaveCodeVisualVariant,
    visualOverrides: Map<Int, Int>,
    onNavigateBack: () -> Unit = {},
    vm: WaveCodeTryOnViewModel = viewModel()
) {
    val context  = LocalContext.current
    val uiState by vm.uiState.collectAsState()

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) vm.onPhotoPicked(uri) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success) vm.onCameraCaptured() }

    var pendingCamera by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingCamera) cameraLauncher.launch(vm.createCaptureUri(context))
        pendingCamera = false
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            cameraLauncher.launch(vm.createCaptureUri(context))
        } else {
            pendingCamera = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        val photoUri = uiState.photoUri
        if (photoUri == null) {
            SourcePicker(
                onCamera  = ::launchCamera,
                onGallery = { galleryPicker.launch("image/*") }
            )
        } else {
            // ── Photo backdrop ────────────────────────────────────────────────────────────────────
            val photoBitmap by produceState<ImageBitmap?>(initialValue = null, photoUri) {
                value = withContext(Dispatchers.IO) { loadDownsampledBitmap(context, photoUri) }
            }
            photoBitmap?.let { bmp ->
                Image(
                    painter            = BitmapPainter(bmp),
                    contentDescription = "Try-on photo",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize()
                )
            }

            // ── WaveCode overlay (ink-only, movable) ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, rotation ->
                            vm.onTransform(pan.x, pan.y, zoom, rotation)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                WaveCodeRenderer(
                    data            = data,
                    variant         = variant,
                    visualOverrides = visualOverrides,
                    drawBackground  = false,
                    barColor        = Color.Black.copy(alpha = uiState.inkOpacity),
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .graphicsLayer(
                            translationX = uiState.offsetX,
                            translationY = uiState.offsetY,
                            scaleX       = uiState.scale,
                            scaleY       = uiState.scale,
                            rotationZ    = uiState.rotationDeg
                        )
                )
            }
        }

        // ── Top bar ─────────────────────────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onNavigateBack) {
                Text("← Back", color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Text("Try on", color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(56.dp))
        }

        // ── Bottom controls (only when a photo is loaded) ──────────────────────────────────────────
        if (uiState.photoUri != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text       = "Ink opacity  ${"%.0f".format(uiState.inkOpacity * 100)}%",
                    color      = Color(0xFFAAAAAA),
                    fontSize   = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value         = uiState.inkOpacity,
                    onValueChange = vm::onInkOpacityChange,
                    valueRange    = 0.1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor      = Color.White,
                        activeTrackColor = Color.White
                    )
                )
                Text(
                    text       = "Drag to move · pinch to resize · two fingers to rotate",
                    color      = Color(0xFF777777),
                    fontSize   = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = vm::resetPlacement,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                    }
                    OutlinedButton(
                        onClick  = vm::clearPhoto,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Change photo", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                    }
                }
            }
        }
    }
}

/** Loads a display-sized (≤ ~1600 px) bitmap from [uri], or null on failure. */
private fun loadDownsampledBitmap(context: Context, uri: Uri): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
    var sample = 1
    while (largest / sample > 1600) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)
        ?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?.asImageBitmap()
} catch (e: Exception) {
    null
}

@Composable
private fun SourcePicker(
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text       = "See how it looks on skin",
            color      = Color.White,
            fontSize   = 18.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text       = "Take or pick a photo, then place the WaveCode where the tattoo would go.",
            color      = Color(0xFF888888),
            fontSize   = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = onCamera,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text("Take photo", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick  = onGallery,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White)
        ) {
            Text("Choose from gallery", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
    }
}