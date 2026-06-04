package com.nandroid.wavecodev1.ui.create

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/** Max recording length; recording auto-stops here. */
private const val MAX_RECORD_SECONDS = 30

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
            val data = waveCodeData
            when {
                // ── Result: WaveCode preview, code and actions ───────────────
                uiState.previewReady && data != null -> {
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

                // ── Recording in progress: live timer + level meter + stop/cancel ──
                uiState.isRecording -> {
                    RecordingHero(
                        amplitudeProvider = vm::currentAmplitude,
                        onStop = { vm.stopRecording(context) },
                        onCancel = { vm.cancelRecording() }
                    )
                }

                // ── Recorded: name it, then create (title comes AFTER recording) ──
                uiState.recorded -> {
                    ReviewSection(
                        title = uiState.title,
                        isUploading = uiState.isUploading,
                        error = uiState.error,
                        onTitleChange = vm::onTitleChange,
                        onCreate = { vm.createCode(context) },
                        onReRecord = { vm.reRecord() }
                    )
                }

                // ── Idle: record is the hero ─────────────────────────────────
                else -> {
                    IdleHero(onStart = ::requestRecord)
                    uiState.error?.let { FeedbackText(it, isError = true) }
                }
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

/** Idle state — the record button is the focal hero. */
@Composable
private fun IdleHero(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(WaveCodeColors.Accent)
                .clickable(onClick = onStart),
            contentAlignment = Alignment.Center
        ) {
            Icon(WaveCodeIcons.Mic, contentDescription = "Ses kaydet", tint = WaveCodeColors.OnAccent, modifier = Modifier.size(48.dp))
        }
        Text("Ses Kaydet", color = WaveCodeColors.TextPrimary, fontSize = 18.sp)
        Text(
            "Sevdiğin bir sesi kaydet,\ndövmelik koda dönüştürelim.",
            color = WaveCodeColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Active recording — pulsing red button, live elapsed timer, live amplitude meter, stop + cancel. */
@Composable
private fun RecordingHero(
    amplitudeProvider: () -> Int,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    var elapsedMs by remember { mutableStateOf(0L) }
    val levels = remember { mutableStateListOf<Float>().apply { repeat(28) { add(0.06f) } } }

    // Drives both the elapsed timer and the live level meter; auto-stops at the max length.
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            elapsedMs = System.currentTimeMillis() - start
            val norm = (amplitudeProvider().toFloat() / 22000f).coerceIn(0.06f, 1f)
            if (levels.isNotEmpty()) levels.removeAt(0)
            levels.add(norm)
            if (elapsedMs >= MAX_RECORD_SECONDS * 1000L) { onStop(); break }
            delay(70)
        }
    }

    val totalSec = (elapsedMs / 1000).toInt()
    val timeLabel = "%d:%02d".format(totalSec / 60, totalSec % 60)

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("DİNLİYORUM…", color = WaveCodeColors.Recording, fontSize = 12.sp, letterSpacing = 2.sp)
        Text(
            timeLabel,
            color = WaveCodeColors.TextPrimary,
            fontSize = 52.sp,
            fontFamily = FontFamily.Monospace
        )
        AmplitudeMeter(
            levels = levels,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
        )

        Spacer(Modifier.height(8.dp))

        // Stop button
        Box(modifier = Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(WaveCodeColors.Recording.copy(alpha = 0.18f))
            )
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(WaveCodeColors.Recording)
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                )
            }
        }
        Text("Kaydı Durdur", color = WaveCodeColors.TextPrimary, fontSize = 16.sp)
        Text(
            "En fazla $MAX_RECORD_SECONDS sn · istediğinde durdur",
            color = WaveCodeColors.TextSecondary,
            fontSize = 13.sp
        )

        OutlineButton(
            text = "İptal",
            onClick = onCancel,
            modifier = Modifier.width(160.dp),
            height = 46
        )
    }
}

/** Live mic level meter — centered red capsule bars driven by [levels]. */
@Composable
private fun AmplitudeMeter(levels: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = levels.size
        if (n == 0) return@Canvas
        val gap = 6.dp.toPx()
        val barW = (size.width - gap * (n - 1)) / n
        val cy = size.height / 2f
        val r = CornerRadius(barW / 2f, barW / 2f)
        val minH = 6.dp.toPx()
        levels.forEachIndexed { i, lvl ->
            val bh = (size.height * lvl).coerceAtLeast(minH)
            val x = i * (barW + gap)
            drawRoundRect(
                color = WaveCodeColors.Recording,
                topLeft = Offset(x, cy - bh / 2f),
                size = Size(barW, bh),
                cornerRadius = r
            )
        }
    }
}

/** After recording — name the sound, then create the code (title is entered here, post-recording). */
@Composable
private fun ReviewSection(
    title: String,
    isUploading: Boolean,
    error: String?,
    onTitleChange: (String) -> Unit,
    onCreate: () -> Unit,
    onReRecord: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(WaveCodeIcons.Check, contentDescription = null, tint = WaveCodeColors.Success, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Kaydın hazır", color = WaveCodeColors.TextPrimary, fontSize = 16.sp)
        }

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Başlık") },
            placeholder = { Text("Bu ses için bir başlık gir", color = WaveCodeColors.TextMuted) },
            singleLine = true,
            enabled = !isUploading,
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

        error?.let { FeedbackText(it, isError = true) }

        PrimaryButton(
            text = "Ses Kodunu Oluştur",
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
            loading = isUploading,
            leadingIcon = WaveCodeIcons.Scan
        )
        OutlineButton(
            text = "Yeniden Kaydet",
            onClick = onReRecord,
            enabled = !isUploading,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = WaveCodeIcons.Replay,
            height = 52
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
private fun FeedbackText(message: String, isError: Boolean) {
    Text(
        text = message,
        color = if (isError) WaveCodeColors.Error else WaveCodeColors.Success,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth()
    )
}
