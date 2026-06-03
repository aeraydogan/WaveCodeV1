package com.nandroid.wavecodev1.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home / landing screen.
 *
 * User-facing MVP entry point — no technical jargon (no publicCode / backend / upload).
 * Just the app name, a short value proposition, and two clear actions:
 *  - "Dövme Oluştur" → create a new Ses Kodu from a recording
 *  - "Tara & Dinle"  → scan an existing code and listen to it
 */
@Composable
fun WaveCodeHomeScreen(
    onNavigateToCreate: () -> Unit = {},
    onNavigateToScan: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // ── Brand + value proposition ───────────────────────────────────────
        Text(
            text       = "WaveCode",
            color      = Color.White,
            fontSize   = 40.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text      = "Sesini özel bir koda dönüştür. Kodu paylaş, " +
                        "isteyen kişi bu kodla sesini dinlesin.",
            color     = Color(0xFFB0B0B0),
            fontSize  = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // ── Primary action ──────────────────────────────────────────────────
        Button(
            onClick = onNavigateToCreate,
            shape   = RoundedCornerShape(14.dp),
            colors  = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor   = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(text = "Dövme Oluştur", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Secondary action ────────────────────────────────────────────────
        OutlinedButton(
            onClick = onNavigateToScan,
            shape   = RoundedCornerShape(14.dp),
            colors  = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            ),
            border  = BorderStroke(1.dp, Color(0xFF444444)),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(text = "Tara & Dinle", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
