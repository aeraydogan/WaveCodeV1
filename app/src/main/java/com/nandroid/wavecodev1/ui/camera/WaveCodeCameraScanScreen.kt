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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.nandroid.wavecodev1.wavecode.decode.WaveCodeDecodeResult

// Guide frame geometry constants (match WaveCodeCameraViewModel.cropToGuideFrame)
private const val GUIDE_WIDTH_FRAC = 0.88f
private const val GUIDE_ASPECT     = 3f    // width:height

@Composable
fun WaveCodeCameraScanScreen(
    onNavigateBack: () -> Unit = {},
    vm: WaveCodeCameraViewModel = viewModel()
) {
    val context  = LocalContext.current
    val uiState  by vm.uiState.collectAsState()

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
            .background(Color.Black)
    ) {
        if (!hasCameraPermission) {
            CameraPermissionScreen(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        } else {
            // ── Camera preview (always visible) ───────────────────────────────────────────────────
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                onImageCaptureReady = vm::onImageCaptureReady
            )

            // ── Guide overlay + scan button (shown when idle, no result) ──────────────────────────
            val showGuide = uiState.result == null
                    && uiState.errorMessage == null
                    && !uiState.isCapturing
                    && !uiState.isDecoding

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
                        text       = "WaveCode'u çerçevenin içine\nyatay şekilde hizalayın",
                        color      = Color.White.copy(alpha = 0.85f),
                        fontSize   = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign  = TextAlign.Center,
                        lineHeight = 19.sp,
                        modifier   = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = guideBottomDp + 14.dp, start = 24.dp, end = 24.dp)
                    )
                }

                Button(
                    onClick  = vm::capture,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 52.dp)
                        .fillMaxWidth(0.55f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor   = Color.Black
                    )
                ) {
                    Text("Tara", fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                }
            }

            // ── Loading state ──────────────────────────────────────────────────────────────────────
            if (uiState.isCapturing || uiState.isDecoding) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp))
                        Text(
                            text       = if (uiState.isCapturing) "Görüntü alınıyor…" else "Çözümleniyor…",
                            color      = Color.White,
                            fontSize   = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // ── Result overlay ─────────────────────────────────────────────────────────────────────
            if (uiState.result != null || uiState.errorMessage != null) {
                CameraScanResultOverlay(
                    uiState     = uiState,
                    onScanAgain = vm::clearResult,
                    onBack      = onNavigateBack,
                    onPlay      = { vm.play(context) },
                    onReplay    = { vm.replay(context) }
                )
            }
        }

        // ── Back button (always visible) ───────────────────────────────────────────────────────────
        TextButton(
            onClick  = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(4.dp)
        ) {
            Text(
                text       = "← Geri",
                color      = Color(0xFFDDDDDD),
                fontSize   = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ── Guide overlay ─────────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawGuideOverlay(
    guideLeft: Float, guideTop: Float, guideW: Float, guideH: Float
) {
    val mask = Color(0x80000000)   // 50 % black

    // Dark mask: four rects surrounding the guide rect
    drawRect(mask, topLeft = Offset.Zero,              size = Size(size.width, guideTop))
    drawRect(mask, topLeft = Offset(0f, guideTop + guideH), size = Size(size.width, size.height - guideTop - guideH))
    drawRect(mask, topLeft = Offset(0f, guideTop),     size = Size(guideLeft, guideH))
    drawRect(mask, topLeft = Offset(guideLeft + guideW, guideTop), size = Size(size.width - guideLeft - guideW, guideH))

    // Rounded guide border
    drawRoundRect(
        color       = Color.White,
        topLeft     = Offset(guideLeft, guideTop),
        size        = Size(guideW, guideH),
        cornerRadius = CornerRadius(8.dp.toPx()),
        style       = Stroke(width = 2.dp.toPx())
    )

    // Corner accent marks (L-shaped ticks at each corner)
    val cLen = 18.dp.toPx()
    val sw   = 4.dp.toPx()
    val gr   = guideLeft + guideW
    val gb   = guideTop  + guideH

    // Top-left
    drawLine(Color.White, Offset(guideLeft, guideTop + cLen), Offset(guideLeft, guideTop), sw)
    drawLine(Color.White, Offset(guideLeft, guideTop), Offset(guideLeft + cLen, guideTop), sw)
    // Top-right
    drawLine(Color.White, Offset(gr, guideTop + cLen), Offset(gr, guideTop), sw)
    drawLine(Color.White, Offset(gr - cLen, guideTop), Offset(gr, guideTop), sw)
    // Bottom-left
    drawLine(Color.White, Offset(guideLeft, gb - cLen), Offset(guideLeft, gb), sw)
    drawLine(Color.White, Offset(guideLeft, gb), Offset(guideLeft + cLen, gb), sw)
    // Bottom-right
    drawLine(Color.White, Offset(gr, gb - cLen), Offset(gr, gb), sw)
    drawLine(Color.White, Offset(gr - cLen, gb), Offset(gr, gb), sw)
}

// ── Permission screen ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraPermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Kamera izni gerekli",
            color      = Color.White,
            fontSize   = 18.sp,
            fontFamily = FontFamily.Monospace,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "WaveCode taramak için kamera erişim izni gereklidir.",
            color      = Color(0xFF888888),
            fontSize   = 13.sp,
            fontFamily = FontFamily.Monospace,
            textAlign  = TextAlign.Center,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            colors  = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text("İzin Ver", fontFamily = FontFamily.Monospace)
        }
    }
}

// ── Result overlay ────────────────────────────────────────────────────────────────────────────────

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
            .background(Color(0xCC0F0F0F))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Captured image, displayed prominently near the top.
            uiState.croppedPreviewBitmap?.let { bmp ->
                CapturedImagePreview(bmp)
            }

            // Capture / image-read error (no decode result at all).
            uiState.errorMessage?.let { msg -> ResultErrorText(msg) }

            when (val result = uiState.result) {
                is WaveCodeDecodeResult.Success -> {
                    // ── Decoded code ────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161616), RoundedCornerShape(10.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Ses Kodu", color = Color(0xFF888888), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text       = result.publicCode,
                            color      = Color.White,
                            fontSize   = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.fillMaxWidth()
                        )
                    }

                    // ── Resolve state ───────────────────────────────────────
                    if (uiState.isResolving) ResultStatusRow("Ses bulunuyor...")
                    uiState.resolveError?.let { ResultErrorText(it) }

                    // ── Metadata + playback ─────────────────────────────────
                    if (uiState.resolved) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D2B0D), RoundedCornerShape(10.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            uiState.title?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                            }
                            uiState.durationLabel?.let {
                                Text(it, color = Color(0xFF9DBF9D), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                            uiState.playbackError?.let { ResultErrorText(it) }
                            if (uiState.isBuffering) ResultStatusRow("Yükleniyor…")

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick  = onPlay,
                                    modifier = Modifier.weight(1f),
                                    colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                                ) {
                                    Text(if (uiState.isPlaying) "Oynatılıyor" else "Oynat", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                }
                                Button(
                                    onClick  = onReplay,
                                    modifier = Modifier.weight(1f),
                                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White)
                                ) {
                                    Text("Tekrar Oynat", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Button(
                        onClick  = onScanAgain,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White)
                    ) {
                        Text("Tekrar Tara", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }

                is WaveCodeDecodeResult.Failure -> {
                    ResultErrorText("WaveCode okunamadı.")
                    ResultActionRow(onScanAgain = onScanAgain, onBack = onBack)
                }

                null -> {
                    // Only a capture/image error was set.
                    ResultActionRow(onScanAgain = onScanAgain, onBack = onBack)
                }
            }
        }
    }
}

@Composable
private fun ResultActionRow(onScanAgain: () -> Unit, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick  = onScanAgain,
            modifier = Modifier.weight(1f),
            colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text("Tekrar Tara", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Button(
            onClick  = onBack,
            modifier = Modifier.weight(1f),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White)
        ) {
            Text("Geri", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CapturedImagePreview(bmp: Bitmap) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Image(
            painter            = BitmapPainter(bmp.asImageBitmap()),
            contentDescription = "Çekilen WaveCode görseli",
            contentScale       = ContentScale.Fit,
            modifier           = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
        )
    }
}

@Composable
private fun ResultStatusRow(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
        Spacer(Modifier.width(10.dp))
        Text(message, color = Color(0xFFBBBBBB), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ResultErrorText(message: String) {
    Text(
        text       = message,
        color      = Color(0xFFFF6060),
        fontSize   = 13.sp,
        fontFamily = FontFamily.Monospace,
        textAlign  = TextAlign.Center,
        modifier   = Modifier.fillMaxWidth()
    )
}
