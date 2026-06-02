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
import com.nandroid.wavecodev1.wavecode.decode.ConfidenceLevel
import com.nandroid.wavecodev1.wavecode.decode.DecodeDebugInfo
import com.nandroid.wavecodev1.wavecode.decode.FailureReason
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
                CameraScanResultOverlay(uiState = uiState, onScanAgain = vm::clearResult)
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
    onScanAgain: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cropped bitmap preview — confirms crop region visually
            uiState.croppedPreviewBitmap?.let { bmp ->
                CroppedBitmapPreview(bmp)
            }

            uiState.errorMessage?.let { msg ->
                Text(msg, color = Color(0xFFFF6060), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            uiState.result?.let { result ->
                when (result) {
                    is WaveCodeDecodeResult.Success -> CameraSuccessCard(result)
                    is WaveCodeDecodeResult.Failure -> CameraFailureCard(result)
                }
                CameraDebugCard(
                    info = when (result) {
                        is WaveCodeDecodeResult.Success -> result.debugInfo
                        is WaveCodeDecodeResult.Failure -> result.debugInfo
                    }
                )
            }

            Button(
                onClick  = onScanAgain,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("Tekrar Tara", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CroppedBitmapPreview(bmp: Bitmap) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Image(
            painter            = BitmapPainter(bmp.asImageBitmap()),
            contentDescription = "Kırpılmış WaveCode bölgesi",
            contentScale       = ContentScale.Fit,
            modifier           = Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp)
        )
    }
}

@Composable
private fun CameraSuccessCard(result: WaveCodeDecodeResult.Success) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D2B0D), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Çözümleme başarılı", color = Color(0xFF88BB88), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(
            text       = result.publicCode,
            color      = Color.White,
            fontSize   = 28.sp,
            fontFamily = FontFamily.Monospace,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth()
        )
        val confColor = if (result.confidenceLevel == ConfidenceLevel.High) Color(0xFF88BB88) else Color(0xFFFFAA44)
        Text(
            text       = "Güven: ${result.confidenceLevel.name.lowercase()}  " +
                    "(${"%.0f".format(result.confidence * 100)} %)",
            color      = confColor,
            fontSize   = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun CameraFailureCard(result: WaveCodeDecodeResult.Failure) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2B0D0D), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Çözümleme başarısız", color = Color(0xFFFF6060), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(result.reason.name, color = Color(0xFFFF9999), fontSize = 16.sp, fontFamily = FontFamily.Monospace)
        val hint = when (result.reason) {
            FailureReason.NotFound       -> "WaveCode yapısı bulunamadı. Çerçeveyi yatay hizalayın."
            FailureReason.FormatMismatch -> "Başlangıç/bitiş işaretleri veya sürüm uyuşmuyor."
            FailureReason.ChecksumError  -> "WaveCode algılandı ancak sağlama toplamı hatalı. Görüntü bozuk olabilir."
            FailureReason.RegionError    -> "İşaret grupları algılandı ancak çekirdek bölge kurulamadı."
            else                         -> "Beklenmeyen hata."
        }
        Text(hint, color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
    }
}

@Composable
private fun CameraDebugCard(info: DecodeDebugInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Hata ayıklama", color = Color(0xFF555555), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(2.dp))
        DbgRow("rotation",  if (info.appliedRotationDeg != 0) "${info.appliedRotationDeg}°" else "0° (no rotation)")
        DbgRow("roi type",   info.roiType.ifEmpty { "—" })
        DbgRow("roi bounds", info.roiBounds.ifEmpty { "—" })
        DbgRow("candidates", if (info.candidateCount > 0) "${info.candidateCount}" else "—")
        DbgRow("attempts",   "${info.totalAttempts}")
        DbgRow("threshold",  "${info.appliedThreshold}")
        DbgRow("preprocess", info.preprocessMode.ifEmpty { "—" })
        DbgRow("median conf", if (info.medianBarConfidence > 0f) "${"%.2f".format(info.medianBarConfidence)}" else "—")
        DbgRow("start marker", if (info.startMarkerValid) "valid" else "invalid")
        DbgRow("version",      if (info.versionValid)     "valid" else "invalid")
        DbgRow("end marker",   if (info.endMarkerValid)   "valid" else "invalid")
        DbgRow("checksum",     if (info.checksumValid)    "valid" else "invalid")
        if (info.detectedRegionDescription.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(vertical = 2.dp))
            Text(info.detectedRegionDescription, color = Color(0xFF444444), fontSize = 9.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
        }
        if (info.bucketString.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(vertical = 2.dp))
            Text("buckets: ${info.bucketString}", color = Color(0xFF3A3A3A), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun DbgRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF555555), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color(0xFF888888), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}
