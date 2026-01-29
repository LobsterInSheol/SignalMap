package com.example.datasender.model

/**
 * Wynik pojedynczego testu prędkości sieci.
 *
 * Wszystkie pola są opcjonalne, bo:
 * - test może się przerwać w trakcie,
 * - niektóre fazy mogą się nie wykonać.
 */
data class SpeedTestResult(
    val latencyMs: Long?,        // opóźnienie (ping) w milisekundach
    val downloadMbps: Double?,   // prędkość pobierania w Mbps
    val uploadMbps: Double?,     // prędkość wysyłania w Mbps
    val jitterMs: Long? = null   // zmienność opóźnień (p90 - mediana), jeśli liczona
)

/**
 * Reprezentuje aktualny stan wykonywania testu prędkości.
 *
 * Używane głównie przez ViewModel/UI do:
 * - sterowania widokiem,
 * - pokazywania postępu,
 * - obsługi błędów.
 */
sealed class SpeedTestProgress {

    // Brak aktywnego testu
    object Idle : SpeedTestProgress()

    /**
     * Test w trakcie wykonywania.
     *
     * phase  – aktualna faza testu
     * percent – postęp w procentach (0..100)
     */
    data class Running(
        val phase: Phase,
        val percent: Int
    ) : SpeedTestProgress() {

        // Etapy testu prędkości
        enum class Phase {
            LATENCY,   // pomiar opóźnienia
            DOWNLOAD,  // pomiar pobierania
            UPLOAD     // pomiar wysyłania
        }
    }

    // Test zakończony poprawnie – zawiera wynik
    data class Done(val result: SpeedTestResult) : SpeedTestProgress()

    // Test zakończony błędem – komunikat do wyświetlenia w UI
    data class Error(val message: String) : SpeedTestProgress()
}
