package com.nandroid.wavecodev1.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandroid.wavecodev1.ui.preview.WaveCodeExportState
import com.nandroid.wavecodev1.wavecode.WaveCodeExportSettings

@Composable
fun WaveCodeExportPanel(
    selectedSettings: WaveCodeExportSettings,
    onPresetSelect: (WaveCodeExportSettings) -> Unit,
    exportState: WaveCodeExportState,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text       = "Export / Tattoo Size",
            color      = Color(0xFF666666),
            fontSize   = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(6.dp))

        WaveCodeExportSettings.PRESETS.forEach { preset ->
            PresetRow(
                preset     = preset,
                isSelected = preset == selectedSettings,
                onClick    = { onPresetSelect(preset) }
            )
        }

        HorizontalDivider(
            color    = Color(0xFF2A2A2A),
            modifier = Modifier.padding(vertical = 6.dp)
        )

        // Selected preset summary
        Text(
            text = "${selectedSettings.exportPixelWidth} × ${selectedSettings.exportPixelHeight} px" +
                   "  ·  ${"%.0f".format(selectedSettings.physicalWidthCm)} cm" +
                   "  ·  ${selectedSettings.aspectRatio.toInt()}:1" +
                   "  ·  ${selectedSettings.dpi} DPI",
            color      = Color(0xFFAAAAAA),
            fontSize   = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        // Reliability warning for small physical sizes
        if (selectedSettings.physicalWidthCm < 5f) {
            Spacer(Modifier.height(4.dp))
            Text(
                text       = "Small tattoo sizes may reduce scan reliability. 5 cm or wider is recommended.",
                color      = Color(0xFFFFAA44),
                fontSize   = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        // Save PNG button
        val isExporting = exportState is WaveCodeExportState.Exporting
        Button(
            onClick  = onExportClick,
            enabled  = !isExporting,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = Color.White,
                contentColor           = Color.Black,
                disabledContainerColor = Color(0xFF2A2A2A),
                disabledContentColor   = Color(0xFF555555)
            )
        ) {
            Text(
                text       = if (isExporting) "Saving..." else "Save PNG",
                fontSize   = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Export state feedback
        when (val state = exportState) {
            is WaveCodeExportState.Success -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = "Saved: ${state.filename}",
                    color      = Color(0xFF88BB88),
                    fontSize   = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
                Text(
                    text       = "Location: Pictures/WaveCode",
                    color      = Color(0xFF557755),
                    fontSize   = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
            is WaveCodeExportState.Failure -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = state.message,
                    color      = Color(0xFFFF6060),
                    fontSize   = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun PresetRow(
    preset: WaveCodeExportSettings,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = if (isSelected) Color.White else Color(0xFF444444),
                        shape = CircleShape
                    )
            )
            Text(
                text       = preset.presetName,
                color      = if (isSelected) Color.White else Color(0xFF666666),
                fontSize   = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text       = "${"%.0f".format(preset.physicalWidthCm)} cm  ·  ${preset.exportPixelWidth} px",
            color      = if (isSelected) Color(0xFFAAAAAA) else Color(0xFF444444),
            fontSize   = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
