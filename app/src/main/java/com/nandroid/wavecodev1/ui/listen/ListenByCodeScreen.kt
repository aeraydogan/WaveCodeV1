package com.nandroid.wavecodev1.ui.listen

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ListenByCodeScreen(
    onNavigateBack: () -> Unit = {},
    vm: ListenByCodeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsState()

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
                Text("← Geri", color = Color(0xFF888888), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Text("Kodla Dinle", color = Color.White, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(56.dp))
        }

        // ── Code input ───────────────────────────────────────────────────────────────────────────
        OutlinedTextField(
            value = uiState.code,
            onValueChange = vm::onCodeChange,
            label = { Text("Kod  (8 karakter)", color = Color.Gray) },
            singleLine = true,
            isError = uiState.error != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White,
                focusedBorderColor   = Color.White,
                unfocusedBorderColor = Color.Gray,
                errorBorderColor     = Color(0xFFFF5555),
                cursorColor          = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // ── Listen / search ──────────────────────────────────────────────────────────────────────
        Button(
            onClick  = { vm.listen(context) },
            enabled  = uiState.canListen,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = Color.White,
                contentColor           = Color.Black,
                disabledContainerColor = Color(0xFF2A2A2A),
                disabledContentColor   = Color(0xFF555555)
            )
        ) {
            Text(
                text       = if (uiState.isLoading) "Aranıyor…" else "Dinle",
                fontSize   = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // ── Error ────────────────────────────────────────────────────────────────────────────────
        uiState.error?.let { err ->
            Text(
                text       = err,
                color      = Color(0xFFFF6060),
                fontSize   = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.fillMaxWidth()
            )
        }

        // ── Resolved result + player ─────────────────────────────────────────────────────────────
        if (uiState.resolved) {
            ResolvedCard(
                title         = uiState.title,
                durationLabel = uiState.durationLabel,
                publicCode    = uiState.publicCode ?: uiState.code,
                isPlaying     = uiState.isPlaying,
                isBuffering   = uiState.isBuffering,
                onTogglePlay  = vm::togglePlayPause
            )
        }
    }
}

@Composable
private fun ResolvedCard(
    title: String?,
    durationLabel: String?,
    publicCode: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onTogglePlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161616), RoundedCornerShape(8.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text       = title?.takeIf { it.isNotBlank() } ?: "(başlıksız)",
            color      = Color.White,
            fontSize   = 18.sp,
            fontFamily = FontFamily.Monospace,
            textAlign  = TextAlign.Center,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.fillMaxWidth()
        )
        Text(
            text       = publicCode,
            color      = Color(0xFF888888),
            fontSize   = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        if (durationLabel != null) {
            Text("Süre: $durationLabel", color = Color(0xFF888888), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick  = onTogglePlay,
            enabled  = !isBuffering,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text(
                text = when {
                    isBuffering -> "Yükleniyor…"
                    isPlaying   -> "⏸ Duraklat"
                    else        -> "▶ Oynat"
                },
                fontSize   = 14.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}