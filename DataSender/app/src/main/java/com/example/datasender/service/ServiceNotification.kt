package com.example.datasender.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.datasender.MainActivity
import com.example.datasender.R

internal class ServiceNotification(
    private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "location_channel" // unikalny identyfikator kanału powiadomień
        const val NOTIFICATION_ID = 1 // stały numer identyfikujący to konkretne powiadomienie
    }

    fun createChannel() {
        // sprawdzenie czy system to Android 8.0 lub nowszy, bo tam kanały są wymagane
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Śledzenie lokalizacji",
                NotificationManager.IMPORTANCE_LOW // niski priorytet, żeby powiadomienie nie hałasowało przy zmianach
            )
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }

    fun startForegroundNotification(): NotificationCompat.Builder {
        // przygotowanie wstępnego wyglądu powiadomienia dla usługi działającej w tle
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("DataSender działa")
            .setContentText("Zbieranie danych o lokalizacji i sygnale")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true) // flaga sprawiająca, że użytkownik nie może usunąć powiadomienia palcem
    }

    private fun canPostNotifications(): Boolean {
        // weryfikacja uprawnień do powiadomień, które są wymagane od Androida 13 wzwyż
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun update(
        operator: String?,
        signal: Int,
        networkType: String?,
        currentNrMode: String?,
        hasNr: Boolean,
        rsrq: Int?,
        sinr: Int?,
        enb: Int?,
        displayTime: String,
        latitude: Double,
        longitude: Double
    ) {
        if (!canPostNotifications()) return // przerwanie funkcji, jeśli aplikacja nie ma zgody na powiadomienia

        // przygotowanie intencji, która zatrzyma usługę po kliknięciu przycisku Stop
        val stopIntent = Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // określanie czy telefon korzysta obecnie z technologii LTE czy 5G (NSA/NR)
        val isLte = networkType?.contains("LTE", true) == true
        val isNsa = (currentNrMode == "NSA") || (hasNr && isLte)

        val techShort = when {
            isNsa -> "5G (LTE + NSA)"
            hasNr -> "5G (NR)"
            isLte -> "4G (LTE)"
            else  -> networkType ?: "—"
        }

        // składanie tekstów, które pojawią się w nagłówku i treści powiadomienia
        val opName = operator ?: "Nieznany operator"
        val title = "$opName $techShort | $signal dBm"
        val extraLine =
            "RSRQ: ${rsrq?.let { "$it dB" } ?: "—"} | SINR: ${sinr?.let { "$it dB" } ?: "—"} | eNB: ${enb ?: "—"}"

        val timeLine = "%s | %.5f, %.5f".format(displayTime, latitude, longitude)

        // konfiguracja powrotu do aplikacji po dotknięciu powiadomienia
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("nav_target", "signal")
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            1,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // budowa finalnego obiektu powiadomienia z listą parametrów i przyciskiem akcji
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(extraLine)
            .setContentIntent(tapPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // zapobieganie powtarzaniu sygnału dźwiękowego przy każdej aktualizacji
            .setStyle(
                NotificationCompat.InboxStyle() // użycie stylu listy dla lepszej czytelności wielu linii danych
                    .addLine(extraLine)
                    .addLine(timeLine)
            )
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()

        // wysłanie zaktualizowanego powiadomienia do systemu Android
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}