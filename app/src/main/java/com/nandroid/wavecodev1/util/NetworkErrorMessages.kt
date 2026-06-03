package com.nandroid.wavecodev1.util

import com.nandroid.wavecodev1.net.WaveCodeNetworkResult

/** Central mapping of network failure kinds to user-facing Turkish messages. */
object NetworkErrorMessages {

    /** Message for resolving a publicCode (Tara & Dinle, camera scan). */
    fun resolve(kind: WaveCodeNetworkResult.Kind): String = when (kind) {
        WaveCodeNetworkResult.Kind.NotFound -> "Bu koda ait ses bulunamadı."
        WaveCodeNetworkResult.Kind.Network -> "Sunucuya ulaşılamadı. İnternet bağlantını kontrol et."
        WaveCodeNetworkResult.Kind.Timeout -> "İstek zaman aşımına uğradı. Tekrar deneyin."
        WaveCodeNetworkResult.Kind.Server -> "Sunucu hatası. Daha sonra tekrar deneyin."
        WaveCodeNetworkResult.Kind.Malformed -> "Sunucudan beklenmeyen bir yanıt geldi."
        else -> "Kod çözümlenemedi. Tekrar deneyin."
    }

    /** Message for uploading audio (Dövme Oluştur). Keeps the dev-LAN Wi-Fi hint. */
    fun upload(kind: WaveCodeNetworkResult.Kind): String = when (kind) {
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
}