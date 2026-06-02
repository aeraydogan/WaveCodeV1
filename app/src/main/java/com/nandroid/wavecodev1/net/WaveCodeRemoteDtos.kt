package com.nandroid.wavecodev1.net

import org.json.JSONObject

/**
 * Metadata for one voice code as returned by the backend
 * (`POST /api/voice-codes` 201 and `GET /api/voice-codes/{publicCode}` 200).
 *
 * NOTE: [audioUrl] is whatever the server returned and may point at localhost — it is kept only for
 * debugging/completeness. Playback must use [WaveCodeBackendConfig.audioUrl] instead.
 */
data class VoiceCodeMetadata(
    val id: String?,
    val publicCode: String,
    val title: String?,
    val durationMs: Long?,
    val audioUrl: String?,
    val createdAt: String?
) {
    companion object {
        /** Parses the backend JSON. Throws [org.json.JSONException] if [publicCode] is missing. */
        fun fromJson(obj: JSONObject): VoiceCodeMetadata = VoiceCodeMetadata(
            id = obj.optString("id").ifBlank { null },
            publicCode = obj.getString("publicCode"),
            title = obj.optString("title").ifBlank { null },
            durationMs = if (obj.has("durationMs") && !obj.isNull("durationMs")) obj.optLong("durationMs") else null,
            audioUrl = obj.optString("audioUrl").ifBlank { null },
            createdAt = obj.optString("createdAt").ifBlank { null }
        )
    }
}
