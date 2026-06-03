package com.nandroid.wavecodev1.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nandroid.wavecodev1.ui.components.OutlineButton
import com.nandroid.wavecodev1.ui.components.PrimaryButton
import com.nandroid.wavecodev1.ui.components.TonalButton
import com.nandroid.wavecodev1.ui.theme.WaveCodeColors
import com.nandroid.wavecodev1.ui.theme.WaveCodeIcons
import com.nandroid.wavecodev1.wavecode.decode.WaveCodeDecodeResult

// Guide frame geometry constants (match WaveCodeCameraViewModel.cropToGuideFrame)
private const val GUIDE_WIDTH_FRAC = 0.88f
private const val GUIDE_ASPECT     = 3f

@Composable
fun WaveCodeCameraScanScreen(
    onNavigateBack: () -> Unit = {},
    vm: WaveCodeCameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WaveCodeColors.Canvas)
    ) {
        if (!hasCameraPermission) {
            CameraPermissionScreen(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        } else {
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                onImageCaptureReady = vm::onImageCaptureReady
            )

            val showGuide = uiState.result == null && uiState.errorMessage == null &&
                    !uiState.isCapturing && !uiState.isDecoding

            if (showGuide) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val screenW = constraints.maxWidth.toFloat()
                    val screenH = constraints.maxHeight.toFloat()
                    val guideW    = screenW * GUIDE_WIDTH_FRAC
                    val guideH    = guideW / GUIDE_ASPECT
                    val guideLeft = (screenW - guideW) / 2f
                    val guideTop  = (screenH - guideH) / 2f
                    val density       = LocalDensity.current
                    val guideBottomDp = with(density) { (guideTop + guideH).toDp() }

                    Canvas(Modifier.fillMaxSize()) {
                        drawGuideOverlay(guideLeft, guideTop, guideW, guideH)
                    }
                    Text(
                        text = "WaveCode'u çerçevenin içine\nyatay şekilde hizalayın",
                        color = WaveCodeColors.TextPrimary.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = guideBottomDp + 14.dp, start = 24.dp, end = 24.dp)
                    )
                }

                PrimaryButton(
                    text = "Tara",
                    onClick = vm::capture,
                    leadingIcon = WaveCodeIcons.Scan,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 40.dp)
                        .fillMaxWidth(0.62f)
                )
            }

            if (uiState.isCapturing || uiState.isDecoding) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = WaveCodeColors.Accent, modifier = Modifier.size(40.dp))
                        Text(
                            text = if (uiState.isCapturing) "Görüntü alınıyor…" else "WaveCode okunuyor…",
                            color = WaveCodeColors.TextSecondary, fontSize = 13.sp
                        )
                    }
                }
            }

            if (uiState.result != null || uiState.errorMessage != null) {
                CameraScanResultOverlay(
                    uiState = uiState,
                    onScanAgain = vm::clearResult,
                    onBack = onNavigateBack,
                    onPlay = { vm.play(context) },
                    onReplay = { vm.replay(context) }
                )
            }
        }

        // Back button (always visible)
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp)
        ) {
            Icon(WaveCodeIcons.Back, contentDescription = "Geri", tint = WaveCodeColors.TextPrimary)
        }
    }
}

// ── Guide overlay ───────────────────────────────────────────────────────────
private fun DrawScope.drawGuideOverlay(guideLeft: Float, guideTop: Float, guideW: Float, guideH: Float) {
    val mask = Color(0x99000000)
    drawRect(mask, topLeft = Offset.Zero, size = Size(size.width, guideTop))
    drawRect(mask, topLeft = Offset(0f, guideTop + guideH), size = Size(size.width, size.height - guideTop - guideH))
    drawRect(mask, topLeft = Offset(0f, guideTop), size = Size(guideLeft, guideH))
    drawRect(mask, topLeft = Offset(guideLeft + guideW, guideTop), size = Size(size.width - guideLeft - guideW, guideH))

    // Subtle frame
    drawRoundRect(
        color = Color.White.copy(alpha = 0.35f),
        topLeft = Offset(guideLeft, guideTop),
        size = Size(guideW, guideH),
        cornerRadius = CornerRadius(10.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Accent L-shaped corner ticks
    val accent = WaveCodeColors.Accent
    val cLen = 20.dp.toPx()
    val sw   = 4.dp.toPx()
    val gr = guideLeft + guideW
    val gb = guideTop + guideH
    drawLine(accent, Offset(guideLeft, guideTop + cLen), Offset(guideLeft, guideTop), sw)
    drawLine(accent, Offset(guideLeft, guideTop), Offset(guideLeft + cLen, guideTop), sw)
    drawLine(accent, Offset(gr, guideTop + cLen), Offset(gr, guideTop), sw)
    drawLine(accent, Offset(gr - cLen, guideTop), Offset(gr, guideTop), sw)
    drawLine(accent, Offset(guideLeft, gb - cLen), Offset(guideLeft, gb), sw)
    drawLine(accent, Offset(guideLeft, gb), Offset(guideLeft + cLen, gb), sw)
    drawLine(accent, Offset(gr, gb - cLen), Offset(gr, gb), sw)
    drawLine(accent, Offset(gr - cLen, gb), Offset(gr, gb), sw)
}

// ── Permission screen ───────────────────────────────────────────────────────
@Composable
private fun CameraPermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(WaveCodeIcons.Camera, contentDescription = null, tint = WaveCodeColors.Accent, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text("Kamera izni gerekli", color = WaveCodeColors.TextPrimary, fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "WaveCode taramak için kamera erişim izni gereklidir.",
            color = WaveCodeColors.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("İzin Ver", onRequestPermission, Modifier.fillMaxWidth(0.7f))
    }
}

// ── Result overlay ──────────────────────────────────────────────────────────
@Composable
private fun CameraScanResultOverlay(
    uiState: CameraScanUiState,
    onScanAgain: () -> Unit,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onReplay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WaveCodeColors.Canvas.copy(alpha = 0.94f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            uiState.croppedPreviewBitmap?.let { CapturedImagePreview(it) }
            uiState.errorMessage?.let { ErrorText(it) }

            when (val result = uiState.result) {
                is WaveCodeDecodeResult.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(WaveCodeColors.Surface1)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Ses Kodu", color = WaveCodeColors.TextSecondary, fontSize = 12.sp)
                        Text(
                            text = result.publicCode,
                            color = WaveCodeColors.TextPrimary,
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (uiState.isResolving) StatusRow("Ses bulunuyor...")
                    uiState.resolveError?.let { ErrorText(it) }

                    if (uiState.resolved) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(WaveCodeColors.SuccessCard)
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            uiState.title?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = WaveCodeColors.TextPrimary, fontSize = 16.sp, textAlign = TextAlign.Center)
                            }
                            uiState.durationLabel?.let {
                                Text(it, color = WaveCodeColors.TextSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                            uiState.playbackError?.let { ErrorText(it) }
                            if (uiState.isBuffering) StatusRow("Yükleniyor…")

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton(
                                    text = if (uiState.isPlaying) "Oynatılıyor" else "Oynat",
                                    onClick = onPlay,
                                    leadingIcon = WaveCodeIcons.Play,
                                    modifier = Modifier.weight(1f),
                                    height = 50
                                )
                                TonalButton(
                                    text = "Tekrar Oynat",
                                    onClick = onReplay,
                                    leadingIcon = WaveCodeIcons.Replay,
                                    modifier = Modifier.weight(1f),
                                    height = 50
                                )
                            }
                        }
                    }

                    TonalButton("Tekrar Tara", onScanAgain, Modifier.fillMaxWidth(), leadingIcon = WaveCodeIcons.Scan, height = 52)
                }

                is WaveCodeDecodeResult.Failure -> {
                    ErrorText("WaveCode okunamadı.")
                    ActionRow(onScanAgain, onBack)
                }

                null -> ActionRow(onScanAgain, onBack)
            }
        }
    }
}

@Composable
private fun ActionRow(onScanAgain: () -> Unit, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("Tekrar Tara", onScanAgain, Modifier.weight(1f), leadingIcon = WaveCodeIcons.Scan, height = 52)
        OutlineButton("Geri", onBack, Modifier.weight(1f), height = 52)
    }
}

@Composable
private fun CapturedImagePreview(bmp: Bitmap) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(10.dp)
    ) {
        Image(
            painter = BitmapPainter(bmp.asImageBitmap()),
            contentDescription = "Çekilen WaveCode görseli",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
        )
    }
}

@Composable
private fun StatusRow(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WaveCodeColors.Accent)
        Spacer(Modifier.width(10.dp))
        Text(message, color = WaveCodeColors.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = WaveCodeColors.Error,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}