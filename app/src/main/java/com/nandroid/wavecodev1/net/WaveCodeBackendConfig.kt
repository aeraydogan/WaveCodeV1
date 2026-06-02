package com.nandroid.wavecodev1.net

/**
 * Single source of truth for the WaveCode backend.
 *
 * To point the app at a different backend, change [BASE_URL] only — everything else derives from it.
 * The dev backend runs on the developer PC and is reached over the LAN by IP, so the phone and the
 * PC must be on the same Wi-Fi network. Cleartext HTTP is enabled in the manifest for this reason.
 *
 * IMPORTANT — audioUrl trap: the server's response may contain an `audioUrl` whose host is
 * `localhost`/`127.0.0.1`, which is meaningless on the phone. NEVER use the server-provided audioUrl
 * for playback. Always build the stream URL on the client with [audioUrl] below.
 */
object WaveCodeBackendConfig {

    /** Change this to repoint the whole app. No trailing slash. */
    const val BASE_URL = "http://192.168.1.8:3000"

    /** Health probe — `GET /health` → `{ "status": "ok" }`. */
    const val HEALTH_URL = "$BASE_URL/health"

    /** Upload endpoint — `POST /api/voice-codes` (multipart/form-data). */
    const val UPLOAD_URL = "$BASE_URL/api/voice-codes"

    /** Metadata lookup — `GET /api/voice-codes/{publicCode}`. */
    fun metadataUrl(publicCode: String): String = "$BASE_URL/api/voice-codes/$publicCode"

    /**
     * Client-built audio stream URL — `GET /api/voice-codes/{publicCode}/audio`.
     * This is the only URL that may be handed to the player.
     */
    fun audioUrl(publicCode: String): String = "$BASE_URL/api/voice-codes/$publicCode/audio"
}
