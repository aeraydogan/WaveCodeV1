package com.nandroid.wavecodev1.ui.create

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nandroid.wavecodev1.audio.AudioRecorder
import com.nandroid.wavecodev1.audio.AudioStorage
import com.nandroid.wavecodev1.data.WaveCodeEntry
import com.nandroid.wavecodev1.data.WaveCodeLibraryRepository
import com.nandroid.wavecodev1.net.WaveCodeNetworkResult
import com.nandroid.wavecodev1.net.WaveCodeRemoteRepository
import com.nandroid.wavecodev1.wavecode.WaveCodeEncoder
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

data class CreateUiState(
    val title: String = "",
    val isRecording: Boolean = false,
    val audioReady: Boolean = false,
    val audioSource: String = "",      // "Recording" / "Selected file"
    val isSaving: Boolean = false,
    val savedCode: String? = null,     // non-null after a successful upload
    val savedTitle: String? = null,
    val savedPngFilename: String? = null,
    val error: String? = null
)

/**
 * Drives the "Save & Share" lifecycle: record or pick audio → optional title →
 * upload to the backend → the server returns the publicCode → render/export the WaveCode PNG with
 * that server code → cache the mapping locally.
 *
 * The publicCode is produced by the backend (NOT locally). Audio is staged first (recorded to a temp
 * file, or a picked Uri held) and uploaded on save.
 */
class WaveCodeCreateViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private val remote = WaveCodeRemoteRepository.get()

    private var recorder: AudioRecorder? = null
    private var stagedFile: File? = null
    private var stagedUri: Uri? = null
    private var stagedExt: String = "m4a"

    fun onTitleChange(value: String) {
        _uiState.update { it.copy(title = value, error = null) }
    }

    fun startRecording(context: Context) {
        if (_uiState.value.isRecording) return
        val staging = File(File(context.filesDir, "audio").apply { mkdirs() }, "_staging_record.m4a")
        val rec = AudioRecorder(context.applicationContext)
        if (rec.start(staging)) {
            recorder = rec
            stagedUri = null
            _uiState.update {
                it.copy(isRecording = true, audioReady = false, audioSource = "", error = null, savedCode = null)
            }
        } else {
            _uiState.update { it.copy(error = "Could not start recording (check microphone permission)") }
        }
    }

    fun stopRecording() {
        val file = recorder?.stop()
        recorder = null
        if (file != null) {
            stagedFile = file
            stagedUri = null
            stagedExt = "m4a"
            _uiState.update { it.copy(isRecording = false, audioReady = true, audioSource = "Recording") }
        } else {
            _uiState.update { it.copy(isRecording = false, audioReady = false, error = "Recording failed") }
        }
    }

    fun onAudioPicked(context: Context, uri: Uri) {
        stagedUri = uri
        stagedFile = null
        stagedExt = AudioStorage.extensionFor(context, uri)
        _uiState.update {
            it.copy(audioReady = true, audioSource = "Selected file", isRecording = false, error = null, savedCode = null)
        }
    }

    fun save(context: Context) {
        val state = _uiState.value
        if (state.isSaving) return
        if (!state.audioReady) {
            _uiState.update { it.copy(error = "Record or select audio first") }
            return
        }
        val title = state.title.trim().ifEmpty { "Untitled" }
        val ctx = context.applicationContext
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = doSave(ctx, title)
            _uiState.update {
                when (result) {
                    is SaveResult.Ok -> it.copy(
                        isSaving = false,
                        savedCode = result.code,
                        savedTitle = result.title,
                        savedPngFilename = result.pngFilename,
                        error = result.warning
                    )
                    is SaveResult.Err -> it.copy(isSaving = false, error = result.message)
                }
            }
        }
    }

    /** Clears staged audio and result so the user can create another. */
    fun reset() {
        try { recorder?.cancel() } catch (_: Exception) {}
        recorder = null
        stagedFile?.delete()
        stagedFile = null
        stagedUri = null
        stagedExt = "m4a"
        _uiState.value = CreateUiState()
    }

    override fun onCleared() {
        try { recorder?.cancel() } catch (_: Exception) {}
    }

    private suspend fun doSave(ctx: Context, title: String): SaveResult {
        // 1. Build the upload payload from the staged source (bytes + mime + duration).
        val payload = withContext(Dispatchers.IO) { buildPayload(ctx) }
            ?: return SaveResult.Err("No audio to upload")
        Log.d(TAG, "save: source path/uri=${stagedFile?.absolutePath ?: stagedUri} mime=${payload.mime} durationMs=${payload.durationMs}")

        // 2. Upload to the backend — the server generates the publicCode.
        val upload = remote.upload(
            audioBytes = payload.bytes,
            mimeType = payload.mime,
            fileName = payload.fileName,
            title = title,
            durationMs = payload.durationMs
        )
        val metadata = when (upload) {
            is WaveCodeNetworkResult.Success -> upload.data
            is WaveCodeNetworkResult.Failure -> {
                Log.w(TAG, "upload failed: kind=${upload.kind} http=${upload.httpCode} detail=${upload.detail}")
                return SaveResult.Err(uploadErrorMessage(upload))
            }
        }
        val code = metadata.publicCode
        Log.d(TAG, "upload success: publicCode=$code")

        // 3. Render/export the WaveCode PNG and cache the mapping locally (off the main thread).
        return withContext(Dispatchers.IO) { finishLocal(ctx, code, metadata.title ?: title, payload) }
    }

    /** Commits the staged audio to local storage, exports the PNG, and caches the mapping. */
    private fun finishLocal(ctx: Context, code: String, title: String, payload: UploadPayload): SaveResult {
        val dest = AudioStorage.fileForCode(ctx, code, payload.ext)
        val sf = stagedFile
        val su = stagedUri
        when {
            sf != null -> try {
                sf.copyTo(dest, overwrite = true)
                sf.delete()
            } catch (e: Exception) {
                return SaveResult.Err("Uploaded, but could not cache audio locally: ${e.message}")
            }
            su != null -> if (AudioStorage.copyFromUri(ctx, su, code, payload.ext) == null) {
                return SaveResult.Err("Uploaded, but could not cache selected audio locally")
            }
            else -> return SaveResult.Err("No audio to save")
        }

        val data = WaveCodeEncoder.encode(code).getOrElse {
            return SaveResult.Err("Uploaded ($code), but WaveCode encode failed: ${it.message}")
        }
        var warning: String? = null
        val pngFilename = try {
            val bitmap = WaveCodeImageExporter.exportToBitmap(
                data = data,
                variant = WaveCodeVisualVariant.CalmMinimal,
                overrides = emptyMap(),
                settings = WaveCodeExportSettings.DEFAULT
            )
            when (val save = WaveCodeImageExporter.savePng(ctx, bitmap, code)) {
                is WaveCodeImageExporter.SaveResult.Success -> save.filename
                is WaveCodeImageExporter.SaveResult.Failure -> {
                    warning = "Code created, but PNG export failed: ${save.message}"
                    null
                }
            }
        } catch (e: Exception) {
            warning = "Code created, but PNG export failed: ${e.message}"
            null
        }

        WaveCodeLibraryRepository.get(ctx)
            .add(WaveCodeEntry(code, dest.absolutePath, title, System.currentTimeMillis()))
        return SaveResult.Ok(code, title, pngFilename, warning)
    }

    /** Reads bytes, mime, extension and duration from the staged source. Returns null if none. */
    private fun buildPayload(ctx: Context): UploadPayload? {
        val sf = stagedFile
        val su = stagedUri
        return when {
            sf != null -> {
                val bytes = try { sf.readBytes() } catch (e: Exception) { Log.w(TAG, "readBytes failed: ${e.message}"); return null }
                UploadPayload(bytes, "audio/mp4", "m4a", "recording.m4a", durationOf(ctx, sf, null))
            }
            su != null -> {
                val bytes = try {
                    ctx.contentResolver.openInputStream(su)?.use { it.readBytes() }
                } catch (e: Exception) { Log.w(TAG, "read uri failed: ${e.message}"); null } ?: return null
                val mime = ctx.contentResolver.getType(su) ?: mimeForExt(stagedExt)
                UploadPayload(bytes, mime, stagedExt, "audio.$stagedExt", durationOf(ctx, null, su))
            }
            else -> null
        }
    }

    private fun durationOf(ctx: Context, file: File?, uri: Uri?): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            when {
                file != null -> retriever.setDataSource(file.absolutePath)
                uri != null -> retriever.setDataSource(ctx, uri)
                else -> return null
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "duration extraction failed: ${e.message}")
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun mimeForExt(ext: String): String = when (ext.lowercase()) {
        "m4a", "mp4", "aac" -> "audio/mp4"
        "mp3", "mpeg" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> "audio/*"
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

    private data class UploadPayload(
        val bytes: ByteArray,
        val mime: String,
        val ext: String,
        val fileName: String,
        val durationMs: Long?
    )

    private sealed class SaveResult {
        data class Ok(val code: String, val title: String, val pngFilename: String?, val warning: String?) : SaveResult()
        data class Err(val message: String) : SaveResult()
    }

    private companion object {
        const val TAG = "WaveCodeCreate"
    }
}