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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nandroid.wavecodev1.ui.components.PrimaryButton
import com.nandroid.wavecodev1.ui.components.TonalButton
import com.nandroid.wavecodev1.ui.components.WaveCodeTopBar
import com.nandroid.wavecodev1.ui.theme.WaveCodeColors
import com.nandroid.wavecodev1.ui.theme.WaveCodeIcons
import com.nandroid.wavecodev1.util.BitmapLoader
import com.nandroid.wavecodev1.wavecode.WaveCodeData
import com.nandroid.wavecodev1.wavecode.WaveCodeExportBackground
import com.nandroid.wavecodev1.wavecode.WaveCodeExportSettings
import com.nandroid.wavecodev1.wavecode.WaveCodeImageExporter
import com.nandroid.wavecodev1.wavecode.WaveCodeVisualVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tattoo placement editor ("Dövmeyi Teninde Gör"): a user photo backdrop with the WaveCode overlaid
 * as ink-only bars the user can pan / pinch-zoom / rotate, then save or share the composed image.
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
                data = data, variant = variant, overrides = visualOverrides,
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
        try { context.startActivity(Intent.createChooser(share, null)) } catch (_: Exception) {}
        vm.consumeShareUri()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WaveCodeColors.Canvas)
            .statusBarsPadding()
    ) {
        WaveCodeTopBar(title = "Dövmeyi Teninde Gör", onNavigateBack = onNavigateBack)

        val photoUri = uiState.photoUri
        if (photoUri == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SourcePicker(onCamera = ::launchCamera, onGallery = { galleryPicker.launch("image/*") })
            }
        } else {
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
                        painter = BitmapPainter(bmp),
                        contentDescription = "Dövme fotoğrafı",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (photoBitmap == null) {
                    Text("Görsel yüklenemedi.", color = WaveCodeColors.Error, fontSize = 13.sp)
                }

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
                            painter = BitmapPainter(overlay.asImageBitmap()),
                            contentDescription = "WaveCode dövme",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .aspectRatio(overlay.width.toFloat() / overlay.height.toFloat())
                                .graphicsLayer(
                                    translationX = uiState.offsetX,
                                    translationY = uiState.offsetY,
                                    scaleX = uiState.scale,
                                    scaleY = uiState.scale,
                                    rotationZ = uiState.rotationDeg,
                                    alpha = uiState.inkOpacity
                                )
                        )
                    }
                }
            }

            // ── Bottom control sheet ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(WaveCodeColors.Surface2)
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Mürekkep koyuluğu · ${"%.0f".format(uiState.inkOpacity * 100)}%",
                    color = WaveCodeColors.TextSecondary, fontSize = 12.sp
                )
                Slider(
                    value = uiState.inkOpacity,
                    onValueChange = vm::onInkOpacityChange,
                    valueRange = 0.1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = WaveCodeColors.Accent,
                        activeTrackColor = WaveCodeColors.Accent,
                        inactiveTrackColor = WaveCodeColors.Surface3
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControlPill(WaveCodeIcons.Rotate, "Döndür", Modifier.weight(1f), vm::rotatePhoto)
                    ControlPill(WaveCodeIcons.Replay, "Sıfırla", Modifier.weight(1f), vm::resetPlacement)
                    ControlPill(WaveCodeIcons.Swap, "Değiştir", Modifier.weight(1f), vm::clearPhoto)
                }

                uiState.message?.let { msg ->
                    Text(
                        msg,
                        color = if (uiState.isError) WaveCodeColors.Error else WaveCodeColors.Success,
                        fontSize = 12.sp
                    )
                }
                if (uiState.isProcessing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WaveCodeColors.Accent)
                        Spacer(Modifier.width(8.dp))
                        Text("Görsel hazırlanıyor…", color = WaveCodeColors.TextSecondary, fontSize = 11.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Kaydet",
                        onClick = { overlayBitmap?.let { vm.saveComposite(context, it, data.publicCode) } },
                        enabled = !uiState.isProcessing && overlayBitmap != null,
                        leadingIcon = WaveCodeIcons.Download,
                        modifier = Modifier.weight(1f),
                        height = 52
                    )
                    TonalButton(
                        text = "Paylaş",
                        onClick = { overlayBitmap?.let { vm.shareComposite(context, it, data.publicCode) } },
                        enabled = !uiState.isProcessing && overlayBitmap != null,
                        leadingIcon = WaveCodeIcons.Share,
                        modifier = Modifier.weight(1f),
                        height = 52
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlPill(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(WaveCodeColors.Surface3)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = WaveCodeColors.TextPrimary, modifier = Modifier.size(20.dp))
        Text(label, color = WaveCodeColors.TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun SourcePicker(onCamera: () -> Unit, onGallery: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(WaveCodeIcons.Camera, contentDescription = null, tint = WaveCodeColors.Accent, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text("Dövmeyi teninde gör", color = WaveCodeColors.TextPrimary, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Bir fotoğraf çek veya galeriden seç, sonra WaveCode'u dövmenin olacağı yere yerleştir.",
            color = WaveCodeColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Galeriden Seç", onGallery, Modifier.fillMaxWidth(), leadingIcon = WaveCodeIcons.Gallery)
        Spacer(Modifier.height(10.dp))
        TonalButton("Kamera Aç", onCamera, Modifier.fillMaxWidth(), leadingIcon = WaveCodeIcons.Camera)
    }
}
