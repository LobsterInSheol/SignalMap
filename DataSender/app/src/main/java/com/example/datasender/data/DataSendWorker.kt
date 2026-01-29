package com.example.datasender.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import org.json.JSONObject

class DataSendWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Pobranie danych z wejścia
        val jsonString = inputData.getString("payload") ?: return Result.failure()
        val type = inputData.getString("type") ?: "telemetry"
        val json = JSONObject(jsonString)

        return try {
            // Próba wysyłki przez AzureClient
            val response = if (type == "speedtest") {
                AzureClient.sendSpeedTest(json)
            } else {
                AzureClient.sendToAzure(json)
            }

            // Sprawdzamy, czy w odpowiedzi nie ma błędów
            if (response.contains("Błąd") || response.contains("Wyjątek")) {
                // Jeśli serwer odrzucił dane (np. 5xx) lub nie ma sieci, zwróć retry
                Result.retry()
            } else {
                // Sukces - zadanie usuwane z kolejki
                Result.success()
            }
        } catch (e: Exception) {
            // Błąd krytyczny sieci - ponów za jakiś czas
            Result.retry()
        }
    }
}