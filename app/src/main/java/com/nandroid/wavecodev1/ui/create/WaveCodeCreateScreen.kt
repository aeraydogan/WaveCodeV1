package com.nandroid.wavecodev1.ui.create

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun WaveCodeCreateScreen(
    onNavigateBack: () -> Unit = {},
    vm: WaveCodeCreateViewModel = viewModel()
) {
    val context  = LocalContext.current
    val uiState by vm.uiState.collectAsState()

    // Pending action after a permission request resolves.
    var pendingRecord by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingRecord) vm.startRecording(context)
        pendingRecord = false
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) vm.onAudioPicked(context, uri)
    }

    fun requestRecord() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            vm.startRecording(context)
        } else {
            pendingRecord = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
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
            Text("Create WaveCode", color = Color.White, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(72.dp))
        }

        // ── Success result ───────────────────────────────────────────────────────────────────────
        if (uiState.savedCode != null) {
            val code = uiState.savedCode!!
            val clipboard = LocalClipboardManager.current
            SavedCard(
                code        = code,
                title       = uiState.savedTitle,
                pngFilename = uiState.savedPngFilename,
                warning     = uiState.error,
                onCopy      = { clipboard.setText(AnnotatedString(code)) },
                onShare     = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "WaveCode ses kodum: $code")
                    }
                    context.startActivity(Intent.createChooser(share, null))
                },
                onCreateAnother = vm::reset
            )
            return@Column
        }

        // ── Title ────────────────────────────────────────────────────────────────────────────────
        OutlinedTextField(
            value = uiState.title,
            onValueChange = vm::onTitleChange,
            label = { Text("Title", color = Color.Gray) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White,
                focusedBorderColor   = Color.White,
                unfocusedBorderColor = Color.Gray,
                cursorColor          = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // ── Audio source ─────────────────────────────────────────────────────────────────────────
        val isRecording = uiState.isRecording
        Button(
            onClick  = { if (isRecording) vm.stopRecording() else requestRecord() },
            enabled  = !uiState.isSaving,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color(0xFFCC4444) else Color.White,
                contentColor   = if (isRecording) Color.White else Color.Black
            )
        ) {
            Text(
                text       = if (isRecording) "■ Stop recording" else "● Record audio",
                fontSize   = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Button(
            onClick  = { audioPicker.launch("audio/*") },
            enabled  = !uiState.isSaving && !isRecording,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = Color(0xFF2A2A2A),
                contentColor           = Color.White,
                disabledContainerColor = Color(0xFF1A1A1A),
                disabledContentColor   = Color(0xFF555555)
            )
        ) {
            Text("Select audio file", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }

        // ── Staged audio indicator ───────────────────────────────────────────────────────────────
        if (uiState.audioReady) {
            Text(
                text       = "✓ Audio ready: ${uiState.audioSource}",
                color      = Color(0xFF88BB88),
                fontSize   = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.fillMaxWidth()
            )
        }

        // ── Error ────────────────────────────────────────────────────────────────────────────────
        uiState.error?.let { err ->
            Text(err, color = Color(0xFFFF6060), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth())
        }

        // ── Save ─────────────────────────────────────────────────────────────────────────────────
        Button(
            onClick  = { vm.save(context) },
            enabled  = uiState.audioReady && !uiState.isSaving && !isRecording,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = Color.White,
                contentColor           = Color.Black,
                disabledContainerColor = Color(0xFF2A2A2A),
                disabledContentColor   = Color(0xFF555555)
            )
        ) {
            Text(
                text       = if (uiState.isSaving) "Saving…" else "Save & Generate WaveCode",
                fontSize   = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SavedCard(
    code: String,
    title: String?,
    pngFilename: String?,
    warning: String?,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onCreateAnother: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D2B0D), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("WaveCode kodun hazır", color = Color(0xFF88BB88), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(
            text       = code,
            color      = Color.White,
            fontSize   = 40.sp,
            fontFamily = FontFamily.Monospace,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth()
        )
        if (!title.isNullOrBlank()) {
            Text(
                text       = title,
                color      = Color(0xFFBBBBBB),
                fontSize   = 13.sp,
                fontFamily = FontFamily.Monospace,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth()
            )
        }
        if (pngFilename != null) {
            Text("PNG: $pngFilename", color = Color(0xFF557755), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("Location: Pictures/WaveCode", color = Color(0xFF557755), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        if (warning != null) {
            Text(warning, color = Color(0xFFFFAA44), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick  = onCopy,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Kopyala", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Button(
                onClick  = onShare,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("Paylaş", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Button(
            onClick  = onCreateAnother,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White)
        ) {
            Text("Yeni oluştur", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
