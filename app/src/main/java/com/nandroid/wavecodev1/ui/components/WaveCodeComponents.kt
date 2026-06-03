package com.nandroid.wavecodev1.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandroid.wavecodev1.ui.theme.WaveCodeColors
import com.nandroid.wavecodev1.ui.theme.WaveCodeIcons

private val PillShape = RoundedCornerShape(30.dp)

/** Top app bar: back button + title, status-bar aware. */
@Composable
fun WaveCodeTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(WaveCodeIcons.Back, contentDescription = "Geri", tint = WaveCodeColors.TextPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, color = WaveCodeColors.TextPrimary, fontSize = 20.sp)
    }
}

/** Primary filled (accent) pill button. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    loading: Boolean = false,
    height: Int = 56
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(height.dp),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = WaveCodeColors.Accent,
            contentColor = WaveCodeColors.OnAccent,
            disabledContainerColor = WaveCodeColors.Surface3,
            disabledContentColor = WaveCodeColors.TextMuted
        )
    ) {
        ButtonContent(text, leadingIcon, loading, spinnerColor = WaveCodeColors.OnAccent)
    }
}

/** Secondary tonal (surface) pill button. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    loading: Boolean = false,
    height: Int = 56
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(height.dp),
        shape = PillShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = WaveCodeColors.Surface3,
            contentColor = WaveCodeColors.TextPrimary
        )
    ) {
        ButtonContent(text, leadingIcon, loading, spinnerColor = WaveCodeColors.TextPrimary)
    }
}

/** Tertiary outlined pill button. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    height: Int = 56
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height.dp),
        shape = PillShape,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, WaveCodeColors.Outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = WaveCodeColors.TextPrimary)
    ) {
        ButtonContent(text, leadingIcon, loading = false, spinnerColor = WaveCodeColors.TextPrimary)
    }
}

@Composable
private fun ButtonContent(
    text: String,
    leadingIcon: ImageVector?,
    loading: Boolean,
    spinnerColor: Color
) {
    if (loading) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = spinnerColor)
        Spacer(Modifier.width(10.dp))
    } else if (leadingIcon != null) {
        Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
    }
    Text(text, fontSize = 15.sp)
}