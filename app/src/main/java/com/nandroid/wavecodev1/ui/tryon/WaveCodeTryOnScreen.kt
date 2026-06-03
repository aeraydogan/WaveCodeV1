package com.nandroid.wavecodev1.ui.tryon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nandroid.wavecodev1.util.BitmapLoader
import com.nandroid.wavecodev1.wavecode.WaveCodeData
import com.nandroid.wavecodev1.wavecode.WaveCodeExportBackground
import com.nandroid.wavecodev1.wavecode.WaveCodeExportSettings
import com.nandroid.wavecodev1.wavecode.WaveCodeImageExporter
import com.nandroid.wavecodev1.wavecode.WaveCodeVisualVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tattoo placement editor ("Dövmeyi Teninde Gör"): a user photo as the backdrop with the WaveCode
 * overlaid as ink-only bars the user can pan / pinch-zoom / rotate, then save or share the composed
 * image. The overlay is a transparent-background WaveCode bitmap so it reads as ink.
 *
 * [initialPhotoUri] (chosen from the entry bottom sheet) opens straight into the editor; when null
 * the in-screen source picker is shown (e.g. after "Fotoğrafı değiştir").
 */
@Composable
fun WaveCodeTryOnScreen(
    data: WaveCodeData,
    variant: WaveCodeVisualVariant,
    visualOverrides: Map<Int, Int>,
    initialPhotoUri: Uri? = null,
    onNavigateBack: () -> Unit = {},
    vm: WaveCodeTryOnViewModel = viewModel()
) {
    val context  = LocalContext.current
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(initialPhotoUri) { initialPhotoUri?.let(vm::initPhoto) }

    // Transparent-background WaveCode overlay bitmap, shared by the preview and the composed export.
    val overlayBitmap by produceState<Bitmap?>(initialValue = null, data, variant, visualOverrides) {
        value = withContext(Dispatchers.IO) {
            WaveCodeImageExporter.exportToBitmap(
                data = data,
                variant = variant,
                overrides = visualOverrides,
                settings = WaveCodeExportSettings.DEFAULT,
                background = WaveCodeExportBackground.Transparent
            )
        }
    }

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
        if (granted) cameraLauncher.launch(vm.createCaptureUri(context))
        else { pendingCamera = true; cameraPermission.launch(Manifest.permission.CAMERA) }
    }

    // Share: when the VM produces a composed image Uri, fire the Android share sheet.
    LaunchedEffect(uiState.shareUri) {
        val uri = uiState.shareUri ?: return@LaunchedEffect
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(share, null))
        } catch (_: Exception) { /* no share target */ }
        vm.consumeShareUri()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        // ── Top bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onNavigateBack) {
                Text("← Geri", color = Color.White, fontSize = 13.sp)
            }
            Text("Dövmeyi Teninde Gör", color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.width(56.dp))
        }

        val photoUri = uiState.photoUri
        if (photoUri == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SourcePicker(
                    onCamera  = ::launchCamera,
                    onGallery = { galleryPicker.launch("image/*") }
                )
            }
        } else {
            // ── Photo + overlay area — full photo fit between the bars ──────
            val photoBitmap by produceState<ImageBitmap?>(initialValue = null, photoUri, uiState.photoRotationDeg) {
                value = withContext(Dispatchers.IO) {
                    BitmapLoader.loadDownsampled(context, photoUri)
                        ?.let { BitmapLoader.rotate(it, uiState.photoRotationDeg) }
                        ?.asImageBitmap()
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { vm.onCanvasSize(it.width, it.height) },
                contentAlignment = Alignment.Center
            ) {
                photoBitmap?.let { bmp ->
                    Image(
                        painter            = BitmapPainter(bmp),
                        contentDescription = "Dövme fotoğrafı",
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.fillMaxSize()
                    )
                }
                if (photoBitmap == null) {
                    Text(text = "Görsel yüklenemedi.", color = Color(0xFFFF6060), fontSize = 13.sp)
                }

                // ── WaveCode overlay (ink-only, movable) ────────────────────
                val overlay = overlayBitmap
                if (overlay != null) {
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
                        Image(
                            painter            = BitmapPainter(overlay.asImageBitmap()),
                            contentDescription = "WaveCode dövme",
                            contentScale       = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .aspectRatio(overlay.width.toFloat() / overlay.height.toFloat())
                                .graphicsLayer(
                                    translationX = uiState.offsetX,
                                    translationY = uiState.offsetY,
                                    scaleX       = uiState.scale,
                                    scaleY       = uiState.scale,
                                    rotationZ    = uiState.rotationDeg,
                                    alpha        = uiState.inkOpacity
                                )
                        )
                    }
                }
            }

            // ── Bottom controls ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text     = "Mürekkep koyuluğu  ${"%.0f".format(uiState.inkOpacity * 100)}%",
                    color    = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
                Slider(
                    value         = uiState.inkOpacity,
                    onValueChange = vm::onInkOpacityChange,
                    valueRange    = 0.1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor       = Color.White,
                        activeTrackColor = Color.White
                    )
                )
                Text(
                    text     = "Sürükle taşı · sıkıştır boyutlandır · iki parmak döndür",
                    color    = Color(0xFF888888),
                    fontSize = 11.sp
                )

                uiState.message?.let { msg ->
                    Text(
                        text     = msg,
                        color    = if (uiState.isError) Color(0xFFFF6060) else Color(0xFF66BB6A),
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::resetPlacement, modifier = Modifier.weight(1f)) {
                        Text("Sıfırla", fontSize = 12.sp, color = Color.White)
                    }
                    OutlinedButton(onClick = vm::rotatePhoto, modifier = Modifier.weight(1f)) {
                        Text("Fotoğrafı döndür", fontSize = 11.sp, color = Color.White)
                    }
                    OutlinedButton(onClick = vm::clearPhoto, modifier = Modifier.weight(1f)) {
                        Text("Değiştir", fontSize = 12.sp, color = Color.White)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = { overlayBitmap?.let { vm.saveComposite(context, it, data.publicCode) } },
                        enabled  = !uiState.isProcessing && overlayBitmap != null,
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("Kaydet", fontSize = 13.sp)
                    }
                    Button(
                        onClick  = { overlayBitmap?.let { vm.shareComposite(context, it, data.publicCode) } },
                        enabled  = !uiState.isProcessing && overlayBitmap != null,
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White)
                    ) {
                        Text("Paylaş", fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onNavigateBack, modifier = Modifier.weight(1f)) {
                        Text("Geri", fontSize = 13.sp, color = Color.White)
                    }
                }

                if (uiState.isProcessing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Görsel hazırlanıyor…", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    }
                }
            }
        }
    }
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
        Text(text = "Dövmeyi teninde gör", color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text       = "Bir fotoğraf çek veya galeriden seç, sonra WaveCode'u dövmenin olacağı yere yerleştir.",
            color      = Color(0xFF888888),
            fontSize   = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = onGallery,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text("Galeriden Seç", fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick  = onCamera,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White)
        ) {
            Text("Kamera Aç", fontSize = 14.sp)
        }
    }
}
