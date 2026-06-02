package com.nandroid.wavecodev1.ui.preview

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nandroid.wavecodev1.wavecode.WaveCodeData
import com.nandroid.wavecodev1.wavecode.WaveCodeEncoder
import com.nandroid.wavecodev1.wavecode.WaveCodeExportBackground
import com.nandroid.wavecodev1.wavecode.WaveCodeExportSettings
import com.nandroid.wavecodev1.wavecode.WaveCodeImageExporter
import com.nandroid.wavecodev1.wavecode.WaveCodeSpec
import com.nandroid.wavecodev1.wavecode.WaveCodeVisualVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class WaveCodeExportState {
    data object Idle      : WaveCodeExportState()
    data object Exporting : WaveCodeExportState()
    data class  Success(val filename: String, val uri: Uri) : WaveCodeExportState()
    data class  Failure(val message: String)                : WaveCodeExportState()
}

class WaveCodePreviewViewModel : ViewModel() {

    private val _publicCode = MutableStateFlow("A7K29XQ4")
    val publicCode: StateFlow<String> = _publicCode.asStateFlow()

    private val _waveCodeData = MutableStateFlow<WaveCodeData?>(null)
    val waveCodeData: StateFlow<WaveCodeData?> = _waveCodeData.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedVariant = MutableStateFlow(WaveCodeVisualVariant.CalmMinimal)
    val selectedVariant: StateFlow<WaveCodeVisualVariant> = _selectedVariant.asStateFlow()

    // key = coreBarIndex (0–33), value = overridden visual level (0–7).
    // Overrides are always within the bar's logical decode bucket, so the future decoder
    // reads the same payload regardless of which sub-level is displayed.
    private val _visualOverrides = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val visualOverrides: StateFlow<Map<Int, Int>> = _visualOverrides.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    private val _exportSettings = MutableStateFlow(WaveCodeExportSettings.DEFAULT)
    val exportSettings: StateFlow<WaveCodeExportSettings> = _exportSettings.asStateFlow()

    private val _exportBackground = MutableStateFlow(WaveCodeExportBackground.White)
    val exportBackground: StateFlow<WaveCodeExportBackground> = _exportBackground.asStateFlow()

    private val _exportState = MutableStateFlow<WaveCodeExportState>(WaveCodeExportState.Idle)
    val exportState: StateFlow<WaveCodeExportState> = _exportState.asStateFlow()

    init {
        runEncode("A7K29XQ4")
    }

    fun onPublicCodeChange(input: String) {
        val upper = input.uppercase()
        _publicCode.value = upper
        runEncode(upper)
    }

    fun onVariantChange(variant: WaveCodeVisualVariant) {
        _selectedVariant.value = variant
        _visualOverrides.value = emptyMap()  // overrides are variant-scoped; reset on switch
    }

    fun onExportPresetSelect(preset: WaveCodeExportSettings) {
        _exportSettings.value = preset
    }

    fun onExportBackgroundSelect(background: WaveCodeExportBackground) {
        _exportBackground.value = background
    }

    fun toggleEditMode() {
        _editMode.value = !_editMode.value
    }

    fun resetOverrides() {
        _visualOverrides.value = emptyMap()
    }

    /**
     * Toggles the visual sub-level of a core bar by XOR-ing with 1.
     *
     * This is decode-safe: XOR-1 flips only the sub-level bit within the bucket:
     *   0↔1 (group "00"), 2↔3 (group "01"), 4↔5 (group "10"), 6↔7 (group "11")
     * Bucket boundaries sit at even indices, so the operation can never cross them.
     * The future decoder maps by threshold ranges, not exact levels — both sub-levels
     * in a bucket produce identical decoded output.
     */
    fun onBarTap(barIndex: Int) {
        if (!_editMode.value) return
        val data = _waveCodeData.value ?: return
        val variant = _selectedVariant.value
        val groups = data.bits.chunked(WaveCodeSpec.BITS_PER_BAR)
        if (barIndex !in groups.indices) return
        val group = groups[barIndex]

        val baseLevel    = WaveCodeSpec.baseVisualLevelIndex(group, barIndex, data.publicCode, variant)
        val currentLevel = _visualOverrides.value[barIndex] ?: baseLevel
        val newLevel     = currentLevel xor 1  // toggles sub-level; never crosses bucket boundary

        _visualOverrides.update { it + (barIndex to newLevel) }
    }

    /**
     * Renders the current WaveCode to a Bitmap using [WaveCodeImageExporter] and saves it.
     *
     * The export uses the same computeLevels() function as WaveCodeRenderer, so the PNG
     * always matches the preview — including manual bar overrides and variant selection.
     *
     * Guard against double-tap: if an export is already running, the call is ignored.
     */
    fun exportPng(context: Context) {
        if (_exportState.value is WaveCodeExportState.Exporting) return
        viewModelScope.launch {
            _exportState.value = WaveCodeExportState.Exporting
            val data = _waveCodeData.value
            if (data == null) {
                _exportState.value = WaveCodeExportState.Failure("No WaveCode data to export")
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                try {
                    val bitmap = WaveCodeImageExporter.exportToBitmap(
                        data       = data,
                        variant    = _selectedVariant.value,
                        overrides  = _visualOverrides.value,
                        settings   = _exportSettings.value,
                        background = _exportBackground.value
                    )
                    WaveCodeImageExporter.savePng(
                        context    = context.applicationContext,
                        bitmap     = bitmap,
                        publicCode = data.publicCode
                    )
                } catch (e: Exception) {
                    WaveCodeImageExporter.SaveResult.Failure("Export failed: ${e.message}")
                }
            }
            _exportState.value = when (result) {
                is WaveCodeImageExporter.SaveResult.Success ->
                    WaveCodeExportState.Success(result.filename, result.uri)
                is WaveCodeImageExporter.SaveResult.Failure ->
                    WaveCodeExportState.Failure(result.message)
            }
            // Auto-reset success banner after 4 seconds
            if (_exportState.value is WaveCodeExportState.Success) {
                delay(4_000L)
                _exportState.value = WaveCodeExportState.Idle
            }
        }
    }

    private fun runEncode(code: String) {
        WaveCodeEncoder.encode(code).fold(
            onSuccess = { data ->
                _waveCodeData.value = data
                _error.value = null
                _visualOverrides.value = emptyMap()  // reset overrides on new publicCode
            },
            onFailure = { err ->
                _waveCodeData.value = null
                _error.value = err.message
                _visualOverrides.value = emptyMap()
            }
        )
    }
}
