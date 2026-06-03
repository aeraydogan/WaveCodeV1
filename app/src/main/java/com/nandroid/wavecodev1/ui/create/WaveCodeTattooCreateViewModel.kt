package com.nandroid.wavecodev1.ui.create

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nandroid.wavecodev1.audio.AudioRecorder
import com.nandroid.wavecodev1.audio.AudioStorage
import com.nandroid.wavecodev1.data.WaveCodeEntry
import com.nandroid.wavecodev1.data.WaveCodeLibraryRepository
import com.nandroid.wavecodev1.net.WaveCodeNetworkResult
import com.nandroid.wavecodev1.net.WaveCodeRemoteRepository
import com.nandroid.wavecodev1.wavecode.WaveCodeData
import com.nandroid.wavecodev1.wavecode.WaveCodeEncoder
import com.nandroid.wavecodev1.wavecode.WaveCodeExportBackground
import com.nandroid.wavecodev1.wavecode.WaveCodeExportSettings
import com.nandroid.wavecodev1.wavecode.WaveCodeImageExporter
import com.nandroid.wavecodev1.wavecode.WaveCodeVisualVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Which background the WaveCode preview/export currently uses. */
enum class PreviewBackground { White, Skin }

data class TattooCreateUiState(
    val title: String = "",
    val isRecording: Boolean = false,
    val isUploading: Boolean = false,         // saving audio + getting the code from the server
    val canRetry: Boolean = false,            // a recording is staged but the upload failed
    val previewReady: Boolean = false,        // server returned a code → WaveCode is shown
    val code: String = "",                    // shown as "Ses Kodu" — the server-generated code
    val selectedBackground: PreviewBackground = PreviewBackground.White,
    val imageFeedback: Feedback? = null,      // "Görseli Kaydet" result
    val error: String? = null                 // recording / upload error
) {
    data class Feedback(val message: String, val isError: Boolean)
}

/**
 * Drives the "Dövme Oluştur" screen.
 *
 * Flow: enter title → record audio → on stop the audio is uploaded to the server, which generates
 * the publicCode → the WaveCode is rendered from that SERVER code and shown to the user, along with
 * the code and a "save image" action.
 *
 * The publicCode is always produced by the backend (never locally). The binary format, encoder and
 * backend upload are reused unchanged.
 */
class WaveCodeTattooCreateViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TattooCreateUiState())
    val uiState: StateFlow<TattooCreateUiState> = _uiState.asStateFlow()

    // The generated WaveCode for the server code; consumed by the preview renderer.
    private val _waveCodeData = MutableStateFlow<WaveCodeData?>(null)
    val waveCodeData: StateFlow<WaveCodeData?> = _waveCodeData.asStateFlow()

    private val remote = WaveCodeRemoteRepository.get()

    private var recorder: AudioRecorder? = null
    private var stagedFile: File? = null

    fun onTitleChange(value: String) {
        _uiState.update { it.copy(title = value, error = null) }
    }

    fun selectBackground(background: PreviewBackground) {
        _uiState.update { it.copy(selectedBackground = background) }
    }

    fun startRecording(context: Context) {
        if (_uiState.value.isRecording) return
        val staging = File(File(context.filesDir, "audio").apply { mkdirs() }, "_staging_record.m4a")
        val rec = AudioRecorder(context.applicationContext)
        if (rec.start(staging)) {
            recorder = rec
            _uiState.update {
                it.copy(
                    isRecording = true,
                    error = null,
                    imageFeedback = null,
                    previewReady = false,
                    canRetry = false,
                    code = ""
                )
            }
            _waveCodeData.value = null
        } else {
            _uiState.update { it.copy(error = "Kayıt başlatılamadı (mikrofon izni gerekli).") }
        }
    }

    /** Stops recording, then immediately uploads the audio so the server can generate the code. */
    fun stopRecording(context: Context) {
        val file = recorder?.stop()
        recorder = null
        if (file == null) {
            _uiState.update { it.copy(isRecording = false, error = "Kayıt başarısız oldu, tekrar deneyin.") }
            return
        }
        stagedFile = file
        _uiState.update { it.copy(isRecording = false) }
        uploadStaged(context)
    }

    /** Retries the upload of the already-recorded audio after a failure. */
    fun retryUpload(context: Context) {
        if (stagedFile == null) return
        uploadStaged(context)
    }

    private fun uploadStaged(context: Context) {
        val file = stagedFile ?: return
        if (_uiState.value.isUploading) return
        val title = _uiState.value.title.trim().ifEmpty { "Untitled" }
        val ctx = context.applicationContext
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, canRetry = false, error = null) }
            val result = withContext(Dispatchers.IO) { doUpload(ctx, file, title) }
            _uiState.update {
                when (result) {
                    is UploadOutcome.Ok -> it.copy(
                        isUploading = false,
                        previewReady = true,
                        code = result.code,
                        canRetry = false,
                        error = null
                    )
                    is UploadOutcome.Err -> it.copy(
                        isUploading = false,
                        previewReady = false,
                        canRetry = true,
                        error = result.message
                    )
                }
            }
        }
    }

    private suspend fun doUpload(ctx: Context, file: File, title: String): UploadOutcome {
        val bytes = try { file.readBytes() } catch (e: Exception) {
            return UploadOutcome.Err("Ses okunamadı: ${e.message}")
        }
        val durationMs = durationOf(file)

        // 1. Upload to the backend — the server generates the publicCode.
        val upload = remote.upload(
            audioBytes = bytes,
            mimeType = "audio/mp4",
            fileName = "recording.m4a",
            title = title,
            durationMs = durationMs
        )
        val metadata = when (upload) {
            is WaveCodeNetworkResult.Success -> upload.data
            is WaveCodeNetworkResult.Failure -> {
                Log.w(TAG, "upload failed: kind=${upload.kind} http=${upload.httpCode} detail=${upload.detail}")
                return UploadOutcome.Err(uploadErrorMessage(upload))
            }
        }
        val code = metadata.publicCode

        // 2. Render the WaveCode from the SERVER code.
        val data = WaveCodeEncoder.encode(code).getOrElse {
            return UploadOutcome.Err("Kaydedildi ($code), ancak WaveCode oluşturulamadı: ${it.message}")
        }
        _waveCodeData.value = data

        // 3. Cache the audio + mapping locally (best-effort).
        val dest = AudioStorage.fileForCode(ctx, code, "m4a")
        try {
            file.copyTo(dest, overwrite = true)
        } catch (e: Exception) {
            Log.w(TAG, "local cache copy failed: ${e.message}")
        }
        WaveCodeLibraryRepository.get(ctx)
            .add(WaveCodeEntry(code, dest.absolutePath, metadata.title ?: title, System.currentTimeMillis()))
        return UploadOutcome.Ok(code)
    }

    /** Exports the currently selected background to the gallery. */
    fun saveImage(context: Context) {
        val data = _waveCodeData.value ?: return
        val code = _uiState.value.code
        val background = when (_uiState.value.selectedBackground) {
            PreviewBackground.White -> WaveCodeExportBackground.White
            PreviewBackground.Skin  -> WaveCodeExportBackground.Skin
        }
        val ctx = context.applicationContext
        viewModelScope.launch {
            val feedback = withContext(Dispatchers.IO) {
                try {
                    val bitmap = WaveCodeImageExporter.exportToBitmap(
                        data = data,
                        variant = WaveCodeVisualVariant.CalmMinimal,
                        overrides = emptyMap(),
                        settings = WaveCodeExportSettings.DEFAULT,
                        background = background
                    )
                    when (val save = WaveCodeImageExporter.savePng(ctx, bitmap, code)) {
                        is WaveCodeImageExporter.SaveResult.Success ->
                            TattooCreateUiState.Feedback("Görsel galeriye kaydedildi.", isError = false)
                        is WaveCodeImageExporter.SaveResult.Failure ->
                            TattooCreateUiState.Feedback("Görsel kaydedilemedi: ${save.message}", isError = true)
                    }
                } catch (e: Exception) {
                    TattooCreateUiState.Feedback("Görsel kaydedilemedi: ${e.message}", isError = true)
                }
            }
            _uiState.update { it.copy(imageFeedback = feedback) }
        }
    }

    private fun durationOf(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun uploadErrorMessage(failure: WaveCodeNetworkResult.Failure): String = when (failure.kind) {
        WaveCodeNetworkResult.Kind.Network ->
            "Sunucuya ulaşılamadı. Telefon ve bilgisayarın aynı Wi-Fi ağında olduğundan emin olun."
        WaveCodeNetworkResult.Kind.Timeout -> "Yükleme zaman aşımına uğradı. Tekrar deneyin."
        WaveCodeNetworkResult.Kind.TooLarge -> "Ses dosyası çok büyük."
        WaveCodeNetworkResult.Kind.UnsupportedMedia -> "Bu ses biçimi desteklenmiyor."
        WaveCodeNetworkResult.Kind.BadRequest -> "Geçersiz istek. Ses dosyasını kontrol edin."
        WaveCodeNetworkResult.Kind.Server -> "Sunucu hatası. Daha sonra tekrar deneyin."
        WaveCodeNetworkResult.Kind.Malformed -> "Sunucudan beklenmeyen bir yanıt geldi."
        WaveCodeNetworkResult.Kind.NotFound -> "Yükleme başarısız oldu."
        WaveCodeNetworkResult.Kind.Unknown -> "Yükleme başarısız oldu."
    }

    override fun onCleared() {
        try { recorder?.cancel() } catch (_: Exception) {}
    }

    private sealed class UploadOutcome {
        data class Ok(val code: String) : UploadOutcome()
        data class Err(val message: String) : UploadOutcome()
    }

    private companion object {
        const val TAG = "WaveCodeTattooCreate"
    }
}