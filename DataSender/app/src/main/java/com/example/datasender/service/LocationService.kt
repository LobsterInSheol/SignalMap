package com.example.datasender.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.datasender.data.AzureClient
import com.example.datasender.data.LocationRepository
import com.example.datasender.data.TelephonyRepository
import com.example.datasender.util.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LocationService : Service() {

    // główny zakres pracy dla zadań w tle (korutyn)
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)

    // obiekty pomocnicze do ustawień, powiadomień i logiki zbierania danych
    private lateinit var store: SettingsDataStore
    private lateinit var notifier: ServiceNotification
    private val sampling = SamplingPolicy()

    private var cachedShortCode: String? = null
    private var currentNrMode: String? = null

    private var loopStarted = false

    override fun onCreate() {
        super.onCreate()

        store = SettingsDataStore(applicationContext)
        notifier = ServiceNotification(this)

        // informowanie reszty aplikacji o rozpoczęciu zbierania danych
        scope.launch { store.setCollecting(true) }

        // przygotowanie kanału i start powiadomienia na pasku stanu
        notifier.createChannel()
        startForeground(
            ServiceNotification.NOTIFICATION_ID,
            notifier.startForegroundNotification().build()
        )

        Log.d(TAG, "onCreate() -> foreground started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")

        // obsługa żądania zatrzymania usługi z zewnątrz
        if (intent?.action == ACTION_STOP) {
            stopFromAction()
            return START_NOT_STICKY
        }

        // uruchomienie głównej pętli pomiarowej tylko raz
        if (!loopStarted) {
            loopStarted = true
            startMainLoop()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        // czyszczenie zasobów i wyłączenie flagi zbierania przy niszczeniu usługi
        scope.launch { store.setCollecting(false) }
        serviceJob.cancel()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved()")
        // całkowite wyłączenie usługi po usunięciu aplikacji z listy zadań
        stopForeground(true)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // główny proces nadzorujący zbieranie danych
    private fun startMainLoop() {
        scope.launch {
            // weryfikacja uprawnień do lokalizacji przed startem
            val haveCoarse = hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION)
            val haveFine = hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!(haveCoarse || haveFine)) {
                Log.w(TAG, "No location permission -> stopSelf()")
                stopSelf()
                return@launch
            }

            val locationRepo = LocationRepository(application)
            val telephonyRepo = TelephonyRepository(application)

            // obserwowanie trybu sieci 5G w czasie rzeczywistym
            if (hasPerm(Manifest.permission.READ_PHONE_STATE)) {
                scope.launch(Dispatchers.Main) {
                    try {
                        telephonyRepo.observeNrMode().collect { mode ->
                            currentNrMode = mode
                        }
                    } catch (se: SecurityException) {
                        Log.w(TAG, "NR mode observe blocked (permission revoked)", se)
                        currentNrMode = null
                    } catch (t: Throwable) {
                        Log.w(TAG, "NR mode observe error", t)
                    }
                }
            }

            // pętla wykonująca pojedyncze pomiary w określonych odstępach
            while (isActive) {
                try {
                    tickOnce(locationRepo, telephonyRepo)
                } catch (t: Throwable) {
                    Log.e(TAG, "Loop error", t)
                    delay(10_000L)
                }
            }
        }
    }

    private suspend fun tickOnce(
        locationRepo: LocationRepository,
        telephonyRepo: TelephonyRepository
    ) {
        // ponowne sprawdzenie uprawnień w trakcie działania pętli
        val stillCoarse = hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION)
        val stillFine = hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasPhone = hasPerm(Manifest.permission.READ_PHONE_STATE)

        if (!(stillCoarse || stillFine)) {
            Log.w(TAG, "Location permission revoked -> stopSelf()")
            stopSelf()
            return
        }

        // pobieranie kodu użytkownika z pamięci stałej
        if (cachedShortCode == null) cachedShortCode = store.getShortCode()
        val shortCode = cachedShortCode

        // próba pobrania aktualnych współrzędnych GPS
        val loc = try {
            locationRepo.getCurrentLocation()
        } catch (_: SecurityException) {
            null
        }

        if (loc == null) {
            Log.w(TAG, "No location -> wait")
            delay(30_000L)
            return
        }

        // gromadzenie danych o sile sygnału, operatorze i typie sieci
        val signal = if (hasPhone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { telephonyRepo.getSignalStrength() } catch (_: SecurityException) { null }
        } else null

        val operator = if (hasPhone) {
            try { telephonyRepo.getOperatorName() } catch (_: SecurityException) { null }
        } else null

        val netType = if (hasPhone) {
            try { telephonyRepo.getNetworkType() } catch (_: SecurityException) { null }
        } else null

        val radio = if (hasPhone && stillFine && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { telephonyRepo.getRadioDetails() } catch (_: SecurityException) { null }
        } else null

        // przygotowanie znaczników czasu dla serwera i powiadomienia
        val sentTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.format(Date())
        }

        val displayTime = SimpleDateFormat("dd.MM.yyyy, HH:mm:ss", Locale.getDefault())
            .format(Date())

        val nowMs = System.currentTimeMillis()

        // sprawdzenie czy na podstawie ruchu i czasu należy wysłać nowy pomiar
        val decision = sampling.decide(
            loc = loc,
            cellId = radio?.cellId,
            nowMs = nowMs
        )
        val idempotencyKey = java.util.UUID.randomUUID().toString()

        // tworzenie obiektu JSON ze wszystkimi zebranymi parametrami sieci i lokalizacji
        val json = JSONObject().apply {
            put("idempotencyKey", idempotencyKey)
            put("shortCode", shortCode ?: JSONObject.NULL)
            put("sentTime", sentTime)

            put("position", JSONObject().apply {
                put("lat", loc.latitude)
                put("lon", loc.longitude)
            })

            put("operator", operator ?: JSONObject.NULL)
            put("networkType", netType ?: JSONObject.NULL)
            put("signal", signal ?: JSONObject.NULL)

            put("rat", netType ?: JSONObject.NULL)
            put("nrMode", currentNrMode ?: JSONObject.NULL)

            put("band", radio?.band ?: JSONObject.NULL)
            put("arfcn", radio?.arfcn ?: JSONObject.NULL)

            put("rsrp", radio?.rsrp ?: JSONObject.NULL)
            put("rsrq", radio?.rsrq ?: JSONObject.NULL)
            put("sinr", radio?.sinr ?: JSONObject.NULL)
            put("rssi", radio?.rssi ?: JSONObject.NULL)

            put("timingAdvance", radio?.timingAdvance ?: JSONObject.NULL)

            put("pci", radio?.pci ?: JSONObject.NULL)
            put("eci", radio?.eci ?: JSONObject.NULL)
            put("nci", radio?.nci ?: JSONObject.NULL)
            put("cellId", radio?.cellId ?: JSONObject.NULL)

            put("enb", radio?.enb ?: JSONObject.NULL)
            put("sectorId", radio?.sectorId ?: JSONObject.NULL)

            put("tac", radio?.tac ?: JSONObject.NULL)
            put("lac", radio?.lac ?: JSONObject.NULL)
        }

        if (decision.shouldSend) {
            Log.d(TAG, "SEND sample mode=${decision.mode} shortCode=$shortCode cellId=${radio?.cellId}")

            // zlecenie wysyłki danych do chmury Azure przez WorkManager
            AzureClient.scheduleDataUpload(applicationContext, json, isSpeedTest = false)

            Log.d(TAG, "Task added to WorkManager queue")

            // prosta logika sprawdzająca czy technologia to 5G
            val hasNr = (currentNrMode == "NSA" || currentNrMode == "SA") ||
                    (netType?.contains("5G", true) == true) ||
                    (netType?.contains("NR", true) == true) ||
                    (radio?.rat == "NR")

            // odświeżenie treści powiadomienia o nowe dane
            notifier.update(
                operator = operator,
                signal = signal ?: -999,
                networkType = netType,
                currentNrMode = currentNrMode,
                hasNr = hasNr,
                rsrq = radio?.rsrq,
                sinr = radio?.sinr,
                enb = radio?.enb,
                displayTime = displayTime,
                latitude = loc.latitude,
                longitude = loc.longitude
            )

            // zapisanie faktu wysyłki w polityce zbierania danych
            sampling.markSent(loc, radio?.cellId, nowMs)
        } else {
            Log.d(TAG, "Skip send (mode=${decision.mode}, nextInterval=${decision.sendIntervalMs}ms)")
        }

        // oczekiwanie przed kolejnym sprawdzeniem (zależne od prędkości ruchu)
        delay(decision.sendIntervalMs)
    }

    // procedura wyłączania usługi i informowania systemu o zakończeniu pracy
    private fun stopFromAction() {
        scope.launch { store.setCollecting(false) }
        sendBroadcast(Intent(ACTION_STOPPED_BROADCAST))
        stopForeground(true)
        stopSelf()
    }

    // pomocnicza funkcja do sprawdzania statusu uprawnień
    private fun hasPerm(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "LocationService"

        const val ACTION_STOP = "com.example.datasender.ACTION_STOP"
        const val ACTION_STOPPED_BROADCAST = "com.example.datasender.ACTION_STOPPED_BROADCAST"
    }
}