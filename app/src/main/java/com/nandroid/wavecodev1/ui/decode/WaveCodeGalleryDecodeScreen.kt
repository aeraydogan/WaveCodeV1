package com.nandroid.wavecodev1.ui.decode

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nandroid.wavecodev1.wavecode.decode.ConfidenceLevel
import com.nandroid.wavecodev1.wavecode.decode.DecodeDebugInfo
import com.nandroid.wavecodev1.wavecode.decode.WaveCodeDecodeResult

@Composable
fun WaveCodeGalleryDecodeScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {},
    vm: WaveCodeDecodeViewModel = viewModel()
) {
    val context  = LocalContext.current
    val uiState by vm.uiState.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) vm.loadAndDecode(context, uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onNavigateBack) {
                Text("← Back", color = Color(0xFF888888), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Text("Decode WaveCode", color = Color.White, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(72.dp))
        }

        // Self-test indicator
        uiState.selfTestPassed?.let { passed ->
            if (!passed) {
                Text(
                    text = "⚠ Parser self-test failed — decoder may be misconfigured",
                    color = Color(0xFFFF6060),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ── Camera scan button ───────────────────────────────────────────────────────────────────
        Button(
            onClick  = onNavigateToCamera,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor   = Color.Black
            )
        ) {
            Text(
                text       = "Kamera ile Tara",
                fontSize   = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // ── Gallery picker button ────────────────────────────────────────────────────────────────
        Button(
            onClick  = { imagePicker.launch("image/*") },
            enabled  = !uiState.isDecoding,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = Color.White,
                contentColor           = Color.Black,
                disabledContainerColor = Color(0xFF2A2A2A),
                disabledContentColor   = Color(0xFF555555)
            )
        ) {
            Text(
                text       = if (uiState.isDecoding) "Decoding…" else "Select WaveCode Image",
                fontSize   = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // ── Decoding progress ────────────────────────────────────────────────────────────────────
        if (uiState.isDecoding) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
        }

        // ── Image preview ────────────────────────────────────────────────────────────────────────
        uiState.previewBitmap?.let { bmp ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Image(
                    painter            = BitmapPainter(bmp.asImageBitmap()),
                    contentDescription = "Selected WaveCode image",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                )
            }
        }

        // ── Result ───────────────────────────────────────────────────────────────────────────────
        uiState.result?.let { result ->
            when (result) {
                is WaveCodeDecodeResult.Success -> {
                    SuccessCard(result)
                    ResolveCard(
                        status    = uiState.resolveStatus,
                        title     = uiState.resolvedEntry?.title,
                        isPlaying = uiState.isPlaying,
                        onPlay    = vm::playResolved,
                        onStop    = vm::stopPlayback
                    )
                }
                is WaveCodeDecodeResult.Failure -> FailureCard(result)
            }
            DebugInfoCard(
                info = when (result) {
                    is WaveCodeDecodeResult.Success -> result.debugInfo
                    is WaveCodeDecodeResult.Failure -> result.debugInfo
                }
            )
        }
    }
}

// ── Result cards ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuccessCard(result: WaveCodeDecodeResult.Success) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D2B0D), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Decode successful", color = Color(0xFF88BB88), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
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
            text = "Confidence: ${result.confidenceLevel.name.lowercase()}  " +
                    "(${"%.0f".format(result.confidence * 100)} %)",
            color      = confColor,
            fontSize   = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ResolveCard(
    status: ResolveStatus,
    title: String?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF14141A), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (status) {
            ResolveStatus.Found -> {
                Text("Matched audio", color = Color(0xFF88AACC), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(
                    text       = title ?: "(untitled)",
                    color      = Color.White,
                    fontSize   = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
                Button(
                    onClick  = if (isPlaying) onStop else onPlay,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor   = Color.Black
                    )
                ) {
                    Text(
                        text       = if (isPlaying) "■ Stop" else "▶ Play",
                        fontSize   = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            ResolveStatus.NotInLibrary -> {
                Text("No saved audio", color = Color(0xFFFFAA44), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(
                    text       = "This code is valid but not in your library on this device.",
                    color      = Color(0xFF888888),
                    fontSize   = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
            }
            ResolveStatus.AudioMissing -> {
                Text("Audio file missing", color = Color(0xFFFF6060), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(
                    text       = "Mapping found (${title ?: "untitled"}) but the audio file is gone.",
                    color      = Color(0xFF888888),
                    fontSize   = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
            }
            ResolveStatus.NotResolved -> {}
        }
    }
}

@Composable
private fun FailureCard(result: WaveCodeDecodeResult.Failure) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2B0D0D), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Decode failed", color = Color(0xFFFF6060), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(
            text = result.reason.name,
            color = Color(0xFFFF9999),
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace
        )
        val hint = when (result.reason) {
            com.nandroid.wavecodev1.wavecode.decode.FailureReason.NotFound ->
                "No WaveCode structure detected. Make sure the image is a WaveCode PNG."
            com.nandroid.wavecodev1.wavecode.decode.FailureReason.FormatMismatch ->
                "Image found but start/end markers or version do not match WaveCode v1 format."
            com.nandroid.wavecodev1.wavecode.decode.FailureReason.ChecksumError ->
                "WaveCode detected but the checksum is invalid. The image may be corrupted."
            com.nandroid.wavecodev1.wavecode.decode.FailureReason.UnsupportedImage ->
                "Could not load the image. Try exporting as PNG first."
            com.nandroid.wavecodev1.wavecode.decode.FailureReason.RegionError ->
                "Marker groups detected but the core region could not be established."
            else -> "An unexpected error occurred."
        }
        Text(hint, color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
    }
}

// ── Debug info card ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun DebugInfoCard(info: DecodeDebugInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Debug info", color = Color(0xFF555555), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(2.dp))
        DebugRow("bits length",  if (info.bitsLength > 0) "${info.bitsLength}" else "—")
        DebugRow("bar count",    if (info.barCount > 0) "${info.barCount}" else "—")
        DebugRow("start marker", boolLabel(info.startMarkerValid))
        DebugRow("version",      boolLabel(info.versionValid))
        DebugRow("end marker",   boolLabel(info.endMarkerValid))
        DebugRow("checksum",     boolLabel(info.checksumValid))
        DebugRow("median conf",  if (info.medianBarConfidence > 0f) "${"%.2f".format(info.medianBarConfidence)}" else "—")
        DebugRow("preprocess",   info.preprocessMode.ifEmpty { "—" })
        DebugRow("threshold",    "${info.appliedThreshold}")
        DebugRow("attempts",     "${info.totalAttempts}")
        DebugRow("rotation",     if (info.appliedRotationDeg != 0) "${info.appliedRotationDeg}°" else "0° (no rotation)")
        DebugRow("roi type",     info.roiType.ifEmpty { "—" })
        DebugRow("roi bounds",   info.roiBounds.ifEmpty { "—" })
        DebugRow("candidates",   if (info.candidateCount > 0) "${info.candidateCount}" else "—")
        if (info.detectedRegionDescription.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(vertical = 2.dp))
            Text(
                text       = info.detectedRegionDescription,
                color      = Color(0xFF444444),
                fontSize   = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        }
        if (info.bucketString.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(vertical = 2.dp))
            Text(
                text       = "buckets: ${info.bucketString}",
                color      = Color(0xFF444444),
                fontSize   = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp
            )
        }
        if (info.rawBits.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(vertical = 2.dp))
            Text(
                text       = info.rawBits,
                color      = Color(0xFF333333),
                fontSize   = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp
            )
        }
        if (info.detectorDebug.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(vertical = 2.dp))
            Text(
                text       = "detector: ${info.detectorDebug}",
                color      = Color(0xFF3A3A3A),
                fontSize   = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF555555), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color(0xFF888888), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun boolLabel(v: Boolean) = if (v) "valid" else "invalid"
