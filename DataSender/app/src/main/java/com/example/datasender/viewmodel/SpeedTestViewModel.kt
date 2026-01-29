package com.example.datasender.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datasender.data.AzureClient
import com.example.datasender.data.SpeedTestRepository
import com.example.datasender.model.SpeedTestProgress
import com.example.datasender.model.SpeedTestResult
import com.example.datasender.util.SettingsDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ViewModel odpowiedzialny za wykonanie speedtestu oraz raportowanie postępu do UI.
 *
 * - Uruchamia pomiar opóźnienia (ping + jitter), pobierania i opcjonalnie wysyłania.
 * - W trakcie testu emituje bieżącą prędkość (instantSpeed) do animacji/wykresu.
 * - Po zakończeniu buduje JSON i wysyła wynik na serwer przez AzureClient.
 */
class SpeedTestViewModel(
    private val repo: SpeedTestRepository = SpeedTestRepository()
) : ViewModel() {

    // Referencja do aktualnie wykonywanego testu (żeby móc go przerwać / zastąpić).
    private var testJob: Job? = null

    // Stan postępu testu (Idle / Running / Done / Error).
    private val _progress = MutableStateFlow<SpeedTestProgress>(SpeedTestProgress.Idle)
    val progress: StateFlow<SpeedTestProgress> = _progress

    // Bieżąca chwilowa prędkość (Mb/s) z download/upload, używana w UI.
    private val _instantSpeed = MutableStateFlow<Double?>(null)
    val instantSpeed: StateFlow<Double?> = _instantSpeed

    /**
     * Uruchamia pełny test.
     *
     * includeUpload  - czy wykonywać upload (jeśli false, test kończy się po downloadzie)
     * shortCode      - identyfikator użytkownika; jeśli brak, spróbujemy się zarejestrować
     * lat/lon        - pozycja do wysyłki w JSON (opcjonalnie)
     * operator       - nazwa operatora (opcjonalnie)
     * context        - potrzebny do /register i zapisu ShortCode w DataStore
     */
    fun runFullTest(
        includeUpload: Boolean = false,
        shortCode: String?,
        lat: Double?,
        lon: Double?,
        operator: String?,
        context: Context
    ) {
        // Jeśli poprzedni test jeszcze trwa, przerywamy go i startujemy od nowa.
        testJob?.cancel()

        testJob = viewModelScope.launch {

            // ShortCode: jeśli nie ma, próbujemy wykonać rejestrację i zapisać wynik lokalnie.
            val effectiveShortCode = if (shortCode.isNullOrBlank()) {
                try {
                    Log.d("Speedtest", "No shortCode → calling /register from speedtest")
                    val sc = AzureClient.register(context)
                    Log.d("Speedtest", "Registered new shortCode=$sc (speedtest)")
                    SettingsDataStore(context).saveShortCode(sc)
                    sc
                } catch (e: Exception) {
                    Log.e("Speedtest", "register failed", e)
                    null
                }
            } else {
                Log.d("Speedtest", "Using existing shortCode=$shortCode")
                shortCode
            }

            try {
                // UI: start testu → zaczynamy od opóźnienia.
                _instantSpeed.value = 0.0
                _progress.value = SpeedTestProgress.Running(
                    SpeedTestProgress.Running.Phase.LATENCY,
                    0
                )

                // 1) PING + JITTER (statystyki z wielu próbek)
                val ping = repo.measurePingStats()
                val latency = ping.medianMs
                val jitter = ping.jitterMs

                // 2) DOWNLOAD
                _progress.value = SpeedTestProgress.Running(
                    SpeedTestProgress.Running.Phase.DOWNLOAD,
                    25
                )
                val down = repo.measureDownloadMbps { mbps ->
                    _instantSpeed.value = mbps
                }

                // 3) UPLOAD (opcjonalnie)
                var up: Double? = null
                if (includeUpload) {
                    _progress.value = SpeedTestProgress.Running(
                        SpeedTestProgress.Running.Phase.UPLOAD,
                        75
                    )
                    up = repo.measureUploadMbps { mbps ->
                        _instantSpeed.value = mbps
                    }
                }

                // 4) Finalny wynik do UI
                val result = SpeedTestResult(latency, down, up, jitter)
                _progress.value = SpeedTestProgress.Done(result)

                // 5) Timestamp w formacie ISO (UTC). Na API 26+ używamy Instant, na starszych SimpleDateFormat.
                val sentTime = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    java.time.Instant.now()
                        .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                        .toString()
                } else {
                    val sdf = java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss'Z'",
                        java.util.Locale.US
                    )
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    sdf.format(java.util.Date())
                }

                // 6) Budowa JSON do wysłania na serwer.
                // Uwaga: tam gdzie pole może być puste, wysyłamy JSONObject.NULL (żeby JSON był poprawny).
                val idempotencyKey = java.util.UUID.randomUUID().toString()
                val json = JSONObject().apply {
                    put("idempotencyKey", idempotencyKey)
                    put("shortCode", effectiveShortCode ?: JSONObject.NULL)
                    put("sentTime", sentTime)

                    if (lat != null && lon != null) {
                        put("position", JSONObject().apply {
                            put("lat", lat)
                            put("lon", lon)
                        })
                        put("lat", lat)
                        put("lon", lon)
                    }

                    put("latencyMs", result.latencyMs ?: JSONObject.NULL)
                    put("downloadMbps", result.downloadMbps ?: JSONObject.NULL)
                    put("uploadMbps", result.uploadMbps ?: JSONObject.NULL)
                    put("jitterMs", result.jitterMs ?: JSONObject.NULL)
                    put("operator", operator ?: JSONObject.NULL)
                }

                Log.d("Speedtest", "SEND shortCode=$effectiveShortCode ping=$latency jitter=$jitter down=$down up=$up")
                Log.d("Speedtest", "JSON operator=$operator")

                // 7) Wysyłka na serwer (APIM endpoint dla speedtestu).
                AzureClient.scheduleDataUpload(context, json, isSpeedTest = true)
            } catch (e: CancellationException) {
                // Anulowanie jest normalne (np. użytkownik kliknął Stop lub zmienił ekran).
                Log.d("Speedtest", "Test cancelled")
                _instantSpeed.value = null
                _progress.value = SpeedTestProgress.Idle
                throw e
            } catch (e: Exception) {
                // Każdy inny błąd oznacza przerwanie testu i komunikat do UI.
                _progress.value = SpeedTestProgress.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Przerywa test i przywraca UI do stanu początkowego.
     */
    fun cancel() {
        testJob?.cancel()
        _instantSpeed.value = null
        _progress.value = SpeedTestProgress.Idle
    }
}
