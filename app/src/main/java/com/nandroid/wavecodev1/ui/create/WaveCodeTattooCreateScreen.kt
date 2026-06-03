package com.nandroid.wavecodev1.ui.create

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    ) { uri -> if (uri != null) openEditor(uri) }  // cancelled → uri null → stay on screen

    val tryOnCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> pendingCaptureUri?.let { if (success) openEditor(it) } }  // cancelled → success false

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
            Text("Dövme Oluştur", color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.width(56.dp))
        }

        // ── Title input ─────────────────────────────────────────────────────
        OutlinedTextField(
            value = uiState.title,
            onValueChange = vm::onTitleChange,
            label = { Text("Başlık", color = Color.Gray) },
            placeholder = { Text("Bu ses için bir başlık gir", color = Color(0xFF666666)) },
            singleLine = true,
            enabled = !uiState.isUploading,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White,
                focusedBorderColor   = Color.White,
                unfocusedBorderColor = Color.Gray,
                cursorColor          = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // ── Record / stop ───────────────────────────────────────────────────
        val isRecording = uiState.isRecording
        Button(
            onClick  = { if (isRecording) vm.stopRecording(context) else requestRecord() },
            enabled  = !uiState.isUploading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = if (isRecording) Color(0xFFCC4444) else Color.White,
                contentColor           = if (isRecording) Color.White else Color.Black,
                disabledContainerColor = Color(0xFF2A2A2A),
                disabledContentColor   = Color(0xFF777777)
            )
        ) {
            Text(
                text     = if (isRecording) "■ Kaydı Durdur" else "● Ses Kaydet",
                fontSize = 16.sp
            )
        }

        // ── Uploading (saving audio + getting the code from the server) ─────
        if (uiState.isUploading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(Modifier.width(12.dp))
                Text("Kaydediliyor…", color = Color(0xFFBBBBBB), fontSize = 14.sp)
            }
        }

        // ── Error + retry ───────────────────────────────────────────────────
        uiState.error?.let { err ->
            Text(err, color = Color(0xFFFF6060), fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
        }
        if (uiState.canRetry && !uiState.isUploading) {
            Button(
                onClick  = { vm.retryUpload(context) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor   = Color.Black
                )
            ) {
                Text("Tekrar Dene", fontSize = 15.sp)
            }
        }

        // ── Result: preview + code + actions (after the server returns a code) ─
        val data = waveCodeData
        if (uiState.previewReady && data != null) {

            // Tabs: white vs skin-tone background
            val selectedIndex = if (uiState.selectedBackground == PreviewBackground.White) 0 else 1
            TabRow(
                selectedTabIndex = selectedIndex,
                containerColor   = Color(0xFF1A1A1A),
                contentColor     = Color.White
            ) {
                Tab(
                    selected = selectedIndex == 0,
                    onClick  = { vm.selectBackground(PreviewBackground.White) },
                    text     = { Text("Beyaz Zemin", fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedIndex == 1,
                    onClick  = { vm.selectBackground(PreviewBackground.Skin) },
                    text     = { Text("Ten Rengi Zemin", fontSize = 13.sp) }
                )
            }

            // Preview card — background follows the selected tab.
            val skinColor = Color(WaveCodeExportBackground.Skin.color)
            val previewBg = if (uiState.selectedBackground == PreviewBackground.White) Color.White else skinColor
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(10.dp))
                    .background(previewBg, RoundedCornerShape(10.dp))
                    .padding(vertical = 16.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // drawBackground = false so the tab background (white or skin) shows behind the bars.
                WaveCodeRenderer(
                    data           = data,
                    variant        = WaveCodeVisualVariant.CalmMinimal,
                    drawBackground = false,
                    barColor       = Color.Black
                )
            }

            // ── Code display ("Ses Kodu") + copy ────────────────────────────
            CodeCard(code = uiState.code)

            // ── Save image ──────────────────────────────────────────────────
            Button(
                onClick  = { vm.saveImage(context) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A2A2A),
                    contentColor   = Color.White
                )
            ) {
                Text("Görseli Kaydet", fontSize = 15.sp)
            }
            uiState.imageFeedback?.let { FeedbackText(it) }

            Spacer(Modifier.height(4.dp))

            // ── Bottom: tattoo try-on entry ─────────────────────────────────
            OutlinedButton(
                onClick  = { showTryOnSheet = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Dövmeyi Teninde Gör", fontSize = 15.sp)
            }
        }
    }

    // ── Photo source bottom sheet ───────────────────────────────────────────
    if (showTryOnSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTryOnSheet = false },
            sheetState       = sheetState,
            containerColor   = Color(0xFF1A1A1A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Fotoğraf seç", color = Color.White, fontSize = 16.sp)
                Button(
                    onClick  = { tryOnGalleryPicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Galeriden Seç", fontSize = 15.sp)
                }
                Button(
                    onClick  = { launchTryOnCamera() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White)
                ) {
                    Text("Kamera Aç", fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun CodeCard(code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    // Reset the "Kopyalandı" success state after a short moment.
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161616), RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Ses Kodu", color = Color(0xFF888888), fontSize = 12.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F0F), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = code,
                color      = Color.White,
                fontSize   = 30.sp,
                fontFamily = FontFamily.Monospace,
                textAlign  = TextAlign.Center
            )
        }
        Button(
            onClick  = {
                clipboard.setText(AnnotatedString(code))
                copied = true
            },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape    = RoundedCornerShape(10.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (copied) Color(0xFF1E7E34) else Color(0xFF2A2A2A),
                contentColor   = Color.White
            )
        ) {
            Text(
                text     = if (copied) "✓ Kopyalandı" else "Kopyala",
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun FeedbackText(feedback: TattooCreateUiState.Feedback) {
    Text(
        text     = feedback.message,
        color    = if (feedback.isError) Color(0xFFFF6060) else Color(0xFF66BB6A),
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth()
    )
}