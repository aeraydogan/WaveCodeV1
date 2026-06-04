package com.nandroid.wavecodev1.ui.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nandroid.wavecodev1.ui.components.PrimaryButton
import com.nandroid.wavecodev1.ui.components.TonalButton
import com.nandroid.wavecodev1.ui.components.WaveCodeTopBar
import com.nandroid.wavecodev1.ui.theme.WaveCodeColors
import com.nandroid.wavecodev1.ui.theme.WaveCodeIcons
import com.nandroid.wavecodev1.ui.theme.rememberReducedMotion
import com.nandroid.wavecodev1.util.formatDuration

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
            .background(WaveCodeColors.Canvas)
            .statusBarsPadding()
    ) {
        WaveCodeTopBar(title = "Tara & Dinle", onNavigateBack = onNavigateBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(WaveCodeIcons.Scan, contentDescription = null, tint = WaveCodeColors.Accent, modifier = Modifier.size(44.dp))
                Text(
                    "WaveCode görselini seç, sesi dinle.",
                    color = WaveCodeColors.TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }

            PrimaryButton(
                text = "Galeriden Seç",
                onClick = { galleryPicker.launch("image/*") },
                enabled = !busy,
                leadingIcon = WaveCodeIcons.Gallery,
                modifier = Modifier.fillMaxWidth()
            )
            TonalButton(
                text = "Kameradan Tara",
                onClick = onNavigateToCamera,
                enabled = !busy,
                leadingIcon = WaveCodeIcons.Camera,
                modifier = Modifier.fillMaxWidth()
            )

            // Decode state
            if (uiState.isDecoding) StatusRow("WaveCode okunuyor...")
            uiState.decodeError?.let { ErrorText(it) }

            // Decoded code
            uiState.publicCode?.let { code ->
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
                        text = code,
                        color = WaveCodeColors.TextPrimary,
                        fontSize = 28.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Resolve state
            if (uiState.isResolving) StatusRow("Ses bulunuyor...")
            uiState.resolveError?.let { ErrorText(it) }

            // Now playing
            if (uiState.resolved) {
                NowPlayingCard(
                    title = uiState.title,
                    positionMs = uiState.positionMs,
                    totalMs = uiState.totalMs,
                    isPlaying = uiState.isPlaying,
                    isBuffering = uiState.isBuffering,
                    onToggle = vm::togglePlayPause,
                    onSeek = vm::seekToFraction,
                    onReplay = vm::replay
                )
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    title: String?,
    positionMs: Long,
    totalMs: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onReplay: () -> Unit
) {
    val hasDuration = totalMs > 0
    val fraction = if (hasDuration) (positionMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(WaveCodeColors.SuccessCard)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Şimdi çalıyor", color = WaveCodeColors.Accent, fontSize = 12.sp)
                Text(
                    text = title?.takeIf { it.isNotBlank() } ?: "Ses kaydı",
                    color = WaveCodeColors.TextPrimary, fontSize = 17.sp
                )
            }
            Equalizer(active = isPlaying)
        }

        // Scrubber: progress + seek
        Slider(
            value = fraction,
            onValueChange = onSeek,
            enabled = hasDuration,
            colors = SliderDefaults.colors(
                thumbColor = WaveCodeColors.Accent,
                activeTrackColor = WaveCodeColors.Accent,
                inactiveTrackColor = WaveCodeColors.Surface3,
                disabledThumbColor = WaveCodeColors.TextMuted,
                disabledActiveTrackColor = WaveCodeColors.Surface3,
                disabledInactiveTrackColor = WaveCodeColors.Surface3
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(positionMs) ?: "0:00", color = WaveCodeColors.TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text(formatDuration(totalMs) ?: "0:00", color = WaveCodeColors.TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }

        // Controls: replay + play/pause
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(WaveCodeColors.Surface3)
                    .clickable(onClick = onReplay),
                contentAlignment = Alignment.Center
            ) {
                Icon(WaveCodeIcons.Replay, contentDescription = "Baştan oynat", tint = WaveCodeColors.TextPrimary, modifier = Modifier.size(22.dp))
            }
            PrimaryButton(
                text = when { isBuffering -> "Yükleniyor…"; isPlaying -> "Duraklat"; else -> "Oynat" },
                onClick = onToggle,
                modifier = Modifier.weight(1f),
                leadingIcon = if (isBuffering) null else WaveCodeIcons.Play,
                loading = isBuffering,
                height = 52
            )
        }
    }
}

@Composable
private fun Equalizer(active: Boolean) {
    // Decorative motion — hold the bars static when the user has reduced motion enabled.
    val animate = active && !rememberReducedMotion()
    val t = rememberInfiniteTransition(label = "eq")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(34.dp)
    ) {
        repeat(5) { i ->
            val h by t.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(520 + i * 90, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ), label = "bar$i"
            )
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight(if (animate) h else 0.25f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(WaveCodeColors.Accent)
            )
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
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WaveCodeColors.Accent)
        Spacer(Modifier.width(10.dp))
        Text(message, color = WaveCodeColors.TextSecondary, fontSize = 14.sp)
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