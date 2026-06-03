package com.nandroid.wavecodev1.ui.create

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.nandroid.wavecodev1.ui.components.OutlineButton
import com.nandroid.wavecodev1.ui.components.PrimaryButton
import com.nandroid.wavecodev1.ui.components.TonalButton
import com.nandroid.wavecodev1.ui.components.WaveCodeTopBar
import com.nandroid.wavecodev1.ui.theme.WaveCodeColors
import com.nandroid.wavecodev1.ui.theme.WaveCodeIcons
import com.nandroid.wavecodev1.ui.tryon.createTryOnCaptureUri
import com.nandroid.wavecodev1.wavecode.WaveCodeData
import com.nandroid.wavecodev1.wavecode.WaveCodeExportBackground
import com.nandroid.wavecodev1.wavecode.WaveCodeRenderer
import com.nandroid.wavecodev1.wavecode.WaveCodeVisualVariant
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveCodeTattooCreateScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToTryOn: (WaveCodeData, Uri) -> Unit = { _, _ -> },
    vm: WaveCodeTattooCreateViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsState()
    val waveCodeData by vm.waveCodeData.collectAsState()

    val micPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.startRecording(context) }

    fun requestRecord() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.startRecording(context) else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // ── "Dövmeyi Teninde Gör" entry: bottom sheet → gallery / camera → editor ───
    var showTryOnSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    fun openEditor(uri: Uri) {
        showTryOnSheet = false
        vm.waveCodeData.value?.let { onNavigateToTryOn(it, uri) }
    }

    val tryOnGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) openEditor(uri) }

    val tryOnCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> pendingCaptureUri?.let { if (success) openEditor(it) } }

    var pendingTryOnCamera by remember { mutableStateOf(false) }
    val tryOnCameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingTryOnCamera) {
            val uri = createTryOnCaptureUri(context)
            pendingCaptureUri = uri
            tryOnCameraLauncher.launch(uri)
        }
        pendingTryOnCamera = false
    }

    fun launchTryOnCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = createTryOnCaptureUri(context)
            pendingCaptureUri = uri
            tryOnCameraLauncher.launch(uri)
        } else {
            pendingTryOnCamera = true
            tryOnCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WaveCodeColors.Canvas)
            .statusBarsPadding()
    ) {
        WaveCodeTopBar(title = "Dövme Oluştur", onNavigateBack = onNavigateBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Title input ─────────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.title,
                onValueChange = vm::onTitleChange,
                label = { Text("Başlık") },
                placeholder = { Text("Bu ses için bir başlık gir", color = WaveCodeColors.TextMuted) },
                singleLine = true,
                enabled = !uiState.isUploading,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor      = WaveCodeColors.TextPrimary,
                    unfocusedTextColor    = WaveCodeColors.TextPrimary,
                    focusedBorderColor    = WaveCodeColors.Accent,
                    unfocusedBorderColor  = WaveCodeColors.Outline,
                    focusedLabelColor     = WaveCodeColors.Accent,
                    unfocusedLabelColor   = WaveCodeColors.TextSecondary,
                    cursorColor           = WaveCodeColors.Accent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // ── Record / stop ───────────────────────────────────────────────
            RecordControl(
                isRecording = uiState.isRecording,
                enabled = !uiState.isUploading,
                onToggle = { if (uiState.isRecording) vm.stopRecording(context) else requestRecord() }
            )

            // ── Uploading ───────────────────────────────────────────────────
            if (uiState.isUploading) {
                StatusRow("Kaydediliyor…")
            }

            // ── Error + retry ───────────────────────────────────────────────
            uiState.error?.let { err -> FeedbackText(err, isError = true) }
            if (uiState.canRetry && !uiState.isUploading) {
                PrimaryButton(
                    text = "Tekrar Dene",
                    onClick = { vm.retryUpload(context) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = WaveCodeIcons.Replay
                )
            }

            // ── Result ──────────────────────────────────────────────────────
            val data = waveCodeData
            if (uiState.previewReady && data != null) {

                SegmentedTabs(
                    selectedSkin = uiState.selectedBackground == PreviewBackground.Skin,
                    onSelect = { skin -> vm.selectBackground(if (skin) PreviewBackground.Skin else PreviewBackground.White) }
                )

                val previewBg = if (uiState.selectedBackground == PreviewBackground.White)
                    Color.White else Color(WaveCodeExportBackground.Skin.color)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(previewBg)
                        .padding(vertical = 18.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WaveCodeRenderer(
                        data = data,
                        variant = WaveCodeVisualVariant.CalmMinimal,
                        drawBackground = false,
                        barColor = Color.Black
                    )
                }

                CodeCard(code = uiState.code)

                TonalButton(
                    text = "Görseli Kaydet",
                    onClick = { vm.saveImage(context) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = WaveCodeIcons.Download,
                    height = 52
                )
                uiState.imageFeedback?.let { FeedbackText(it.message, isError = it.isError) }

                OutlineButton(
                    text = "Dövmeyi Teninde Gör",
                    onClick = { showTryOnSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    height = 52
                )
            }
        }
    }

    // ── Photo source bottom sheet ───────────────────────────────────────────
    if (showTryOnSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTryOnSheet = false },
            sheetState = sheetState,
            containerColor = WaveCodeColors.Surface2
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Fotoğraf seç", color = WaveCodeColors.TextPrimary, fontSize = 16.sp)
                PrimaryButton(
                    text = "Galeriden Seç",
                    onClick = { tryOnGalleryPicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = WaveCodeIcons.Gallery,
                    height = 52
                )
                TonalButton(
                    text = "Kamera Aç",
                    onClick = { launchTryOnCamera() },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = WaveCodeIcons.Camera,
                    height = 52
                )
            }
        }
    }
}

@Composable
private fun RecordControl(isRecording: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale"
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(WaveCodeColors.Recording.copy(alpha = 0.18f))
                )
            }
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !enabled -> WaveCodeColors.Surface3
                            isRecording -> WaveCodeColors.Recording
                            else -> WaveCodeColors.Accent
                        }
                    )
                    .clickable(enabled = enabled, onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.White)
                    )
                } else {
                    Icon(
                        WaveCodeIcons.Mic, contentDescription = "Kaydet",
                        tint = if (enabled) WaveCodeColors.OnAccent else WaveCodeColors.TextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Text(
            text = if (isRecording) "Kaydı Durdur" else "Ses Kaydet",
            color = WaveCodeColors.TextPrimary,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun SegmentedTabs(selectedSkin: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaveCodeColors.Surface1)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegOption("Beyaz Zemin", active = !selectedSkin, modifier = Modifier.weight(1f)) { onSelect(false) }
        SegOption("Ten Rengi Zemin", active = selectedSkin, modifier = Modifier.weight(1f)) { onSelect(true) }
    }
}

@Composable
private fun SegOption(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) WaveCodeColors.Surface3 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (active) WaveCodeColors.TextPrimary else WaveCodeColors.TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun CodeCard(code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(1500); copied = false }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(WaveCodeColors.Surface1)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Ses Kodu", color = WaveCodeColors.TextSecondary, fontSize = 12.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WaveCodeColors.Canvas)
                .border(1.dp, WaveCodeColors.Outline, RoundedCornerShape(12.dp))
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = code,
                color = WaveCodeColors.TextPrimary,
                fontSize = 30.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
        TonalButton(
            text = if (copied) "Kopyalandı" else "Kopyala",
            onClick = { clipboard.setText(AnnotatedString(code)); copied = true },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = if (copied) WaveCodeIcons.Check else WaveCodeIcons.Copy,
            height = 48
        )
    }
}

@Composable
private fun StatusRow(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = WaveCodeColors.Accent)
        Spacer(Modifier.width(12.dp))
        Text(message, color = WaveCodeColors.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun FeedbackText(message: String, isError: Boolean) {
    Text(
        text = message,
        color = if (isError) WaveCodeColors.Error else WaveCodeColors.Success,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth()
    )
}