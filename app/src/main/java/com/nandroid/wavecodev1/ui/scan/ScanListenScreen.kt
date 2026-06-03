package com.nandroid.wavecodev1.ui.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ScanListenScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {},
    vm: ScanListenViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsState()

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) vm.onImagePicked(context, uri) }

    val busy = uiState.isDecoding || uiState.isResolving

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onNavigateBack) {
                Text("← Geri", color = Color(0xFF888888), fontSize = 13.sp)
            }
            Text("Tara & Dinle", color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.width(56.dp))
        }

        Text(
            text      = "WaveCode görselini seç, sesi dinle.",
            color     = Color(0xFFB0B0B0),
            fontSize  = 15.sp,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )

        // ── Source actions (no bottom sheet) ────────────────────────────────
        Button(
            onClick  = { galleryPicker.launch("image/*") },
            enabled  = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text("Galeriden Seç", fontSize = 16.sp)
        }

        Button(
            onClick  = onNavigateToCamera,
            enabled  = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2A2A2A),
                contentColor   = Color.White
            )
        ) {
            Text("Kameradan Tara", fontSize = 16.sp)
        }

        // ── Decode state ────────────────────────────────────────────────────
        if (uiState.isDecoding) {
            StatusRow("WaveCode okunuyor...")
        }
        uiState.decodeError?.let { ErrorText(it) }

        // ── Decoded code ────────────────────────────────────────────────────
        uiState.publicCode?.let { code ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161616), RoundedCornerShape(10.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Ses Kodu", color = Color(0xFF888888), fontSize = 12.sp)
                Text(
                    text       = code,
                    color      = Color.White,
                    fontSize   = 28.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
            }
        }

        // ── Resolve state ───────────────────────────────────────────────────
        if (uiState.isResolving) {
            StatusRow("Ses bulunuyor...")
        }
        uiState.resolveError?.let { ErrorText(it) }

        // ── Resolved result + playback ──────────────────────────────────────
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
                Button(
                    onClick  = vm::togglePlayPause,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    if (uiState.isBuffering) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                        Spacer(Modifier.width(10.dp))
                        Text("Yükleniyor…", fontSize = 15.sp)
                    } else {
                        Text(if (uiState.isPlaying) "⏸  Duraklat" else "▶  Oynat", fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        Spacer(Modifier.width(10.dp))
        Text(message, color = Color(0xFFBBBBBB), fontSize = 14.sp)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text      = message,
        color     = Color(0xFFFF6060),
        fontSize  = 13.sp,
        textAlign = TextAlign.Center,
        modifier  = Modifier.fillMaxWidth()
    )
}