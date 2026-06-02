package com.nandroid.wavecodev1.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Thin OkHttp wrapper over the WaveCode backend. Each call runs on [Dispatchers.IO] and maps HTTP
 * status codes / network exceptions onto [WaveCodeNetworkResult]. JSON is parsed with org.json
 * (no Retrofit/converter dependency).
 */
class WaveCodeApiService(
    private val client: OkHttpClient = defaultClient()
) {
    /** GET /health → true when the backend reports `{"status":"ok"}`. */
    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(WaveCodeBackendConfig.HEALTH_URL).get().build())
                .execute().use { resp ->
                    val ok = resp.isSuccessful &&
                        (resp.body?.string()?.contains("\"ok\"") == true)
                    Log.d(TAG, "health: ${resp.code} ok=$ok")
                    ok
                }
        } catch (e: Exception) {
            Log.w(TAG, "health failed: ${e.message}")
            false
        }
    }

    /**
     * POST /api/voice-codes (multipart/form-data). [audioBytes] is sent as `audioFile`; [title] and
     * [durationMs] are sent when present.
     */
    suspend fun upload(
        audioBytes: ByteArray,
        mimeType: String,
        fileName: String,
        title: String?,
        durationMs: Long?
    ): WaveCodeNetworkResult<VoiceCodeMetadata> = withContext(Dispatchers.IO) {
        Log.d(TAG, "upload: file=$fileName mime=$mimeType bytes=${audioBytes.size} durationMs=$durationMs title=$title")
        val media = mimeType.toMediaTypeOrNull() ?: "audio/*".toMediaTypeOrNull()
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audioFile", fileName, audioBytes.toRequestBody(media))
        if (!title.isNullOrBlank()) bodyBuilder.addFormDataPart("title", title.take(200))
        if (durationMs != null && durationMs > 0) bodyBuilder.addFormDataPart("durationMs", durationMs.toString())

        val request = Request.Builder()
            .url(WaveCodeBackendConfig.UPLOAD_URL)
            .post(bodyBuilder.build())
            .build()

        execute(request, "upload")
    }

    /** GET /api/voice-codes/{publicCode}. */
    suspend fun getMetadata(publicCode: String): WaveCodeNetworkResult<VoiceCodeMetadata> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "getMetadata: requested=$publicCode")
            val request = Request.Builder()
                .url(WaveCodeBackendConfig.metadataUrl(publicCode))
                .get()
                .build()
            execute(request, "getMetadata")
        }

    /** Runs [request], maps status → result, and parses the body as [VoiceCodeMetadata]. */
    private fun execute(request: Request, tag: String): WaveCodeNetworkResult<VoiceCodeMetadata> {
        return try {
            client.newCall(request).execute().use { resp ->
                val code = resp.code
                Log.d(TAG, "$tag: http=$code")
                when {
                    resp.isSuccessful -> {
                        val raw = resp.body?.string().orEmpty()
                        try {
                            WaveCodeNetworkResult.Success(VoiceCodeMetadata.fromJson(JSONObject(raw)))
                        } catch (e: Exception) {
                            Log.w(TAG, "$tag: malformed body: ${e.message}")
                            WaveCodeNetworkResult.Failure(WaveCodeNetworkResult.Kind.Malformed, code, e.message)
                        }
                    }
                    code == 400 -> WaveCodeNetworkResult.Failure(WaveCodeNetworkResult.Kind.BadRequest, code)
                    code == 404 -> WaveCodeNetworkResult.Failure(WaveCodeNetworkResult.Kind.NotFound, code)
                    code == 413 -> WaveCodeNetworkResult.Failure(WaveCodeNetworkResult.Kind.TooLarge, code)
                    code == 415 -> WaveCodeNetworkResult.Failure(WaveCodeNetworkResult.Kind.UnsupportedMedia, code)
                    code in 500..599 -> WaveCodeNetworkResult.Failure(WaveCodeNetworkResult.Kind.Server, code)
                    else -> WaveCodeNetworkResult.Failure(WaveCodeNetworkResult.Kind.Unknown, code)
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "$tag: timeout: ${e.message}")
            WaveCodeNetworkResult.Failure(WaveCodeNetworkResult.Kind.Timeout, detail = e.message)
        } catch (e: IOException) {
            Log.w(TAG, "$tag: network error: ${e.message}")
            WaveCodeNetworkResult.Failure(WaveCodeNetworkResult.Kind.Network, detail = e.message)
        } catch (e: Exception) {
            Log.w(TAG, "$tag: unknown error: ${e.message}")
            WaveCodeNetworkResult.Failure(WaveCodeNetworkResult.Kind.Unknown, detail = e.message)
        }
    }

    companion object {
        private const val TAG = "WaveCodeApi"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS) // uploads
            .build()
    }
}
