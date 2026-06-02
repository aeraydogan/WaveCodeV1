package com.nandroid.wavecodev1.net

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * App-wide entry point for backend calls. Wraps [WaveCodeApiService] and keeps a small in-memory
 * metadata cache so a freshly uploaded or recently resolved code can be answered without a round
 * trip. This is an optional convenience cache only — there is no "my uploads" history UI.
 *
 * Audio bytes/duration extraction lives at the call site (it needs a Context); this layer only
 * speaks in already-prepared payloads.
 */
class WaveCodeRemoteRepository private constructor(
    private val api: WaveCodeApiService
) {
    private val metadataCache = ConcurrentHashMap<String, VoiceCodeMetadata>()

    suspend fun health(): Boolean = api.health()

    /** Uploads audio + metadata. Caches the result on success. */
    suspend fun upload(
        audioBytes: ByteArray,
        mimeType: String,
        fileName: String,
        title: String?,
        durationMs: Long?
    ): WaveCodeNetworkResult<VoiceCodeMetadata> {
        val result = api.upload(audioBytes, mimeType, fileName, title, durationMs)
        if (result is WaveCodeNetworkResult.Success) {
            metadataCache[result.data.publicCode] = result.data
            Log.d(TAG, "upload ok: publicCode=${result.data.publicCode}")
        }
        return result
    }

    /** Resolves a code: in-memory cache first, then the backend. Caches successful lookups. */
    suspend fun resolve(publicCode: String): WaveCodeNetworkResult<VoiceCodeMetadata> {
        metadataCache[publicCode]?.let {
            Log.d(TAG, "resolve cache hit: $publicCode")
            return WaveCodeNetworkResult.Success(it)
        }
        val result = api.getMetadata(publicCode)
        if (result is WaveCodeNetworkResult.Success) metadataCache[result.data.publicCode] = result.data
        Log.d(TAG, "resolve $publicCode -> ${if (result is WaveCodeNetworkResult.Success) "success" else "failure"}")
        return result
    }

    companion object {
        private const val TAG = "WaveCodeRemoteRepo"

        @Volatile private var instance: WaveCodeRemoteRepository? = null

        fun get(): WaveCodeRemoteRepository =
            instance ?: synchronized(this) {
                instance ?: WaveCodeRemoteRepository(WaveCodeApiService()).also { instance = it }
            }
    }
}
