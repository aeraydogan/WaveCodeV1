package com.nandroid.wavecodev1.data

import org.json.JSONObject

/**
 * One saved WaveCode → audio mapping.
 *
 * @param publicCode 8-char code encoded into the WaveCode visual (the lookup key).
 * @param audioPath  Absolute path to the locally stored audio file (app-private storage).
 * @param title      Human-readable label shown on resolve/playback.
 * @param createdAt  Epoch millis when the entry was created.
 */
data class WaveCodeEntry(
    val publicCode: String,
    val audioPath: String,
    val title: String,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_PUBLIC_CODE, publicCode)
        put(KEY_AUDIO_PATH, audioPath)
        put(KEY_TITLE, title)
        put(KEY_CREATED_AT, createdAt)
    }

    companion object {
        private const val KEY_PUBLIC_CODE = "publicCode"
        private const val KEY_AUDIO_PATH  = "audioPath"
        private const val KEY_TITLE       = "title"
        private const val KEY_CREATED_AT  = "createdAt"

        fun fromJson(obj: JSONObject): WaveCodeEntry = WaveCodeEntry(
            publicCode = obj.getString(KEY_PUBLIC_CODE),
            audioPath  = obj.getString(KEY_AUDIO_PATH),
            title      = obj.getString(KEY_TITLE),
            createdAt  = obj.getLong(KEY_CREATED_AT)
        )
    }
}