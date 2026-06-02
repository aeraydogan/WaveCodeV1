package com.nandroid.wavecodev1.ui.preview

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nandroid.wavecodev1.ui.export.WaveCodeExportPanel
import com.nandroid.wavecodev1.wavecode.WaveCodeData
import com.nandroid.wavecodev1.wavecode.WaveCodeRenderer
import com.nandroid.wavecodev1.wavecode.WaveCodeSpec
import com.nandroid.wavecodev1.wavecode.WaveCodeVisualVariant

@Composable
fun WaveCodePreviewScreen(
    onNavigateToDecode: () -> Unit = {},
    vm: WaveCodePreviewViewModel = viewModel()
) {
    val context         = LocalContext.current
    val publicCode      by vm.publicCode.collectAsState()
    val waveCodeData    by vm.waveCodeData.collectAsState()
    val error           by vm.error.collectAsState()
    val selectedVariant by vm.selectedVariant.collectAsState()
    val visualOverrides by vm.visualOverrides.collectAsState()
    val editMode        by vm.editMode.collectAsState()
    val exportSettings  by vm.exportSettings.collectAsState()
    val exportState     by vm.exportState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = "WaveCode  v1",
                color      = Color.White,
                fontSize   = 22.sp,
                fontFamily = FontFamily.Monospace
            )
            TextButton(onClick = onNavigateToDecode) {
                Text(
                    text       = "Decode →",
                    color      = Color(0xFF888888),
                    fontSize   = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        OutlinedTextField(
            value = publicCode,
            onValueChange = vm::onPublicCodeChange,
            label = { Text("Public Code  (8 chars)", color = Color.Gray) },
            singleLine = true,
            isError = error != null,
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

        if (error != null) {
            Text(
                text = error!!,
                color = Color(0xFFFF6060),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Variant selector
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            WaveCodeVisualVariant.entries.forEachIndexed { index, variant ->
                SegmentedButton(
                    selected = selectedVariant == variant,
                    onClick  = { vm.onVariantChange(variant) },
                    shape    = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = WaveCodeVisualVariant.entries.size
                    ),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor   = Color.White,
                        activeContentColor     = Color.Black,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor   = Color.Gray
                    )
                ) {
                    Text(
                        text       = variant.displayName,
                        fontSize   = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Edit mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = "Edit visual",
                color      = Color.White,
                fontSize   = 14.sp,
                fontFamily = FontFamily.Monospace
            )
            Switch(
                checked         = editMode,
                onCheckedChange = { vm.toggleEditMode() },
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = Color.Black,
                    checkedTrackColor   = Color.White,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF333333)
                )
            )
        }

        if (waveCodeData != null) {
            WaveCodeCard(
                data            = waveCodeData!!,
                variant         = selectedVariant,
                visualOverrides = visualOverrides,
                onBarTap        = if (editMode) vm::onBarTap else null
            )

            // Reset button — only shown when there are active overrides
            if (visualOverrides.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = vm::resetOverrides) {
                        Text(
                            text       = "Reset visual edits",
                            color      = Color(0xFFAA8888),
                            fontSize   = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            WaveCodeExportPanel(
                selectedSettings = exportSettings,
                onPresetSelect   = vm::onExportPresetSelect,
                exportState      = exportState,
                onExportClick    = { vm.exportPng(context) }
            )

            DebugPanel(
                data            = waveCodeData!!,
                variant         = selectedVariant,
                visualOverrides = visualOverrides,
                editMode        = editMode
            )
        }
    }
}

@Composable
private fun WaveCodeCard(
    data: WaveCodeData,
    variant: WaveCodeVisualVariant,
    visualOverrides: Map<Int, Int>,
    onBarTap: ((Int) -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (onBarTap != null) 1.dp else 1.dp,
                color = if (onBarTap != null) Color(0xFF555555) else Color(0xFF2A2A2A),
                shape = RoundedCornerShape(10.dp)
            )
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(vertical = 16.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        WaveCodeRenderer(
            data            = data,
            variant         = variant,
            visualOverrides = visualOverrides,
            onBarTap        = onBarTap
        )
    }
}

@Composable
private fun DebugPanel(
    data: WaveCodeData,
    variant: WaveCodeVisualVariant,
    visualOverrides: Map<Int, Int>,
    editMode: Boolean
) {
    val thresholds = WaveCodeSpec.decodeThresholds(variant)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DebugRow("visual mode",   variant.displayName)
        DebugRow("edit mode",     if (editMode) "on  — tap a bar to toggle its sub-level" else "off")
        DebugRow("overrides",     if (visualOverrides.isEmpty()) "none" else "${visualOverrides.size} bar(s) overridden")
        DebugRow("bar count",     "${data.bits.length / WaveCodeSpec.BITS_PER_BAR}")
        DebugRow("bits per bar",  "${WaveCodeSpec.BITS_PER_BAR}")
        DebugRow("bit length",    "${data.bits.length}")
        DebugRow(
            "thresholds",
            thresholds.joinToString(" · ") { "%.2f".format(it) }
        )
        DebugRow(
            "checksum",
            "${data.checksum}  (0x${data.checksum.toString(16).uppercase().padStart(2, '0')})"
        )
        HorizontalDivider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(vertical = 2.dp))
        Text(
            text       = data.bits,
            color      = Color(0xFF555555),
            fontSize   = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
            modifier   = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF666666), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color(0xFFAAAAAA), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
