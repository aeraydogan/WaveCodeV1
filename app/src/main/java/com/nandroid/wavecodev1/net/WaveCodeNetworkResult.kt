package com.nandroid.wavecodev1.net

/**
 * Result of a backend call. Either [Success] with parsed data, or [Failure] tagged with a [Kind]
 * so the UI can map it to a localized message without inspecting exceptions.
 */
sealed class WaveCodeNetworkResult<out T> {

    data class Success<out T>(val data: T) : WaveCodeNetworkResult<T>()

    data class Failure(
        val kind: Kind,
        val httpCode: Int? = null,
        val detail: String? = null
    ) : WaveCodeNetworkResult<Nothing>()

    /** Coarse failure categories the UI knows how to phrase. */
    enum class Kind {
        Network,          // host unreachable / not on same Wi-Fi
        Timeout,          // connect/read timed out
        NotFound,         // 404 — code does not exist
        BadRequest,       // 400 — invalid request
        TooLarge,         // 413 — upload too large
        UnsupportedMedia, // 415 — invalid mime type
        Server,           // 5xx
        Malformed,        // 2xx but body could not be parsed
        Unknown
    }
}