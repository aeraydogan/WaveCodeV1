package com.nandroid.wavecodev1.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandroid.wavecodev1.ui.theme.WaveCodeColors
import com.nandroid.wavecodev1.ui.theme.WaveCodeIcons

/**
 * Home / landing — "Ink & Signal": decorative waveform, wordmark + value proposition, and two
 * large action cards (Dövme Oluştur = accent, Tara & Dinle = tonal).
 */
@Composable
fun WaveCodeHomeScreen(
    onNavigateToCreate: () -> Unit = {},
    onNavigateToScan: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WaveCodeColors.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(24.dp))

        WaveformArt(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = "WaveCode",
            color = WaveCodeColors.TextPrimary,
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Sesini koda dönüştür,\npaylaş, dinlet.",
            color = WaveCodeColors.TextSecondary,
            fontSize = 16.sp,
            lineHeight = 24.sp
        )

        Spacer(Modifier.weight(1f))

        ActionCard(
            icon = WaveCodeIcons.Mic,
            title = "Dövme Oluştur",
            subtitle = "Sesini kaydet",
            accent = true,
            onClick = onNavigateToCreate
        )
        Spacer(Modifier.height(14.dp))
        ActionCard(
            icon = WaveCodeIcons.Scan,
            title = "Tara & Dinle",
            subtitle = "Kodu okut, dinle",
            accent = false,
            onClick = onNavigateToScan
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Boolean,
    onClick: () -> Unit
) {
    val container = if (accent) WaveCodeColors.Accent else WaveCodeColors.Surface2
    val content   = if (accent) WaveCodeColors.OnAccent else WaveCodeColors.TextPrimary
    val glyphBg   = if (accent) WaveCodeColors.OnAccent.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.06f)
    val glyphTint = if (accent) WaveCodeColors.OnAccent else WaveCodeColors.Accent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(18.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(glyphBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = glyphTint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = content, fontSize = 18.sp, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = content.copy(alpha = 0.75f), fontSize = 13.sp)
        }
        Text("→", color = content.copy(alpha = 0.7f), fontSize = 22.sp)
    }
}

/** Decorative, non-functional waveform (NOT a WaveCode) — centered rounded capsule bars. */
@Composable
private fun WaveformArt(modifier: Modifier = Modifier) {
    val heights = listOf(0.25f, 0.45f, 0.7f, 1f, 0.8f, 0.55f, 0.35f, 0.6f, 0.9f, 0.65f,
        0.4f, 0.75f, 1f, 0.5f, 0.3f, 0.55f, 0.85f, 0.6f, 0.4f, 0.7f, 0.95f, 0.5f, 0.3f, 0.6f)
    Canvas(modifier) {
        val n = heights.size
        val gap = 4.dp.toPx()
        val barW = (size.width - gap * (n - 1)) / n
        val cx = size.height / 2f
        val r = CornerRadius(barW / 2f, barW / 2f)
        heights.forEachIndexed { i, h ->
            val bh = size.height * h
            val x = i * (barW + gap)
            drawRoundRect(
                color = WaveCodeColors.Accent.copy(alpha = 0.85f),
                topLeft = Offset(x, cx - bh / 2f),
                size = Size(barW, bh),
                cornerRadius = r
            )
        }
    }
}