package com.example.datasender.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datasender.data.LocationRepository
import com.example.datasender.data.TelephonyRepository
import com.example.datasender.model.NetworkSnapshot
import com.example.datasender.util.SettingsDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * ViewModel odpowiedzialny za ekran pomiarów sygnału oraz za logikę cyklicznego odświeżania danych.
 *
 * Zadania:
 * - utrzymuje stan UI (NetworkSnapshot),
 * - pobiera lokalizację (LocationRepository),
 * - pobiera informacje o sieci komórkowej (TelephonyRepository),
 * - przechowuje ShortCode i flagę "czy zbieramy" w DataStore (SettingsDataStore),
 * - uruchamia/kończy cykliczne odświeżanie i obserwację trybu 5G (NSA/SA).
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Kontekst aplikacji (bezpieczny do trzymania w AndroidViewModel).
    private val app = getApplication<Application>()

    // Repozytoria do pobierania danych z urządzenia.
    private val locationRepo = LocationRepository(app)
    private val telephonyRepo = TelephonyRepository(app)

    // Trwała konfiguracja w DataStore (ShortCode + stan zbierania).
    private val store = SettingsDataStore(app)

    // ShortCode udostępniamy jako Flow dla UI/innych VM.
    val shortCodeFlow = store.shortCodeFlow

    // Stan UI: wszystko, co ekran wyświetla (lokalizacja, parametry sieci, flagi).
    private val _uiState = MutableStateFlow(NetworkSnapshot())
    val uiState = _uiState.asStateFlow()

    init {
        // DataStore jest źródłem prawdy dla flagi "isCollecting".
        // Dzięki temu po restarcie aplikacji UI może odtworzyć stan zbierania.
        viewModelScope.launch {
            store.isCollectingFlow.collect { flag ->
                _uiState.value = _uiState.value.copy(isCollecting = flag)
            }
        }
    }

    // Ostatnia “zaakceptowana” lokalizacja, używana do sprawdzenia czy użytkownik przemieścił się >= 200 m.
    private var lastLocation: Location? = null

    // Joby do kontroli coroutines (żeby można było je anulować przy stop()).
    private var periodicJob: Job? = null
    private var locationJob: Job? = null
    private var nrModeJob: Job? = null

    /**
     * Start trybu zbierania.
     * - zapewnia rejestrację (ShortCode),
     * - uruchamia obserwację trybu 5G (NSA/SA),
     * - wykonuje odświeżenie “na start”,
     * - startuje aktualizacje lokalizacji i cykliczne odświeżanie co 30 s,
     * - zapisuje w DataStore, że zbieranie jest aktywne.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun start() {
        ensureRegistered()
        startNrModeObserver()
        refresh(force = true)
        startLocationUpdates()
        startPeriodicRefresh()

        viewModelScope.launch {
            store.setCollecting(true)
        }
    }

    /**
     * Sprawdza, czy mamy już ShortCode.
     * Jeśli nie – wywołuje rejestrację w Azure i zapisuje ShortCode w DataStore.
     *
     * Uwaga: to jest uruchamiane asynchronicznie w viewModelScope.
     */
    private fun ensureRegistered() {
        viewModelScope.launch {
            Log.d("Register", "ensureRegistered() start")

            val existing = shortCodeFlow.firstOrNull()
            Log.d("Register", "existing shortCode = $existing")

            if (existing.isNullOrBlank()) {
                try {
                    val shortCode = com.example.datasender.data.AzureClient.register(app)
                    store.saveShortCode(shortCode)
                } catch (e: Exception) {
                    Log.e("Register", "failed", e)
                    Toast.makeText(
                        app,
                        "Błąd rejestracji: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Pomocniczy odczyt ShortCode (np. do budowania payloadu wysyłanego na serwer).
     * Zwraca null, jeśli kod nie jest jeszcze dostępny.
     */
    suspend fun requireShortCodeOrNull(): String? = shortCodeFlow.firstOrNull()

    /**
     * Stop trybu zbierania.
     * - anuluje joby (odświeżanie cykliczne, lokalizację, obserwację 5G),
     * - zapisuje w DataStore, że zbieranie zakończone.
     */
    fun stop() {
        periodicJob?.cancel()
        locationJob?.cancel()
        nrModeJob?.cancel()

        viewModelScope.launch {
            store.setCollecting(false)
        }
    }

    /**
     * Cykliczne wymuszanie odświeżenia co 30 sekund.
     * Używane, żeby dane “odświeżały się” nawet jeśli lokalizacja stoi w miejscu.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startPeriodicRefresh() {
        periodicJob?.cancel()
        periodicJob = viewModelScope.launch {
            while (true) {
                delay(30_000L)
                refresh(force = true)
            }
        }
    }

    /**
     * Obserwacja trybu 5G (NSA/SA) jako Flow z TelephonyRepository.
     * Działa tylko jeśli mamy READ_PHONE_STATE.
     */
    private fun startNrModeObserver() {
        nrModeJob?.cancel()
        nrModeJob = viewModelScope.launch {
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                telephonyRepo.observeNrMode().collect { mode ->
                    _uiState.value = _uiState.value.copy(nrMode = mode)
                }
            }
        }
    }

    /**
     * Jednorazowo pobiera świeżą lokalizację i operatora (używane np. przy speedteście).
     * Zwraca Triple(lat, lon, operator).
     */
    suspend fun getFreshLocationAndOperator(): Triple<Double?, Double?, String?> {
        val hasFine = hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION)
        val hasPhone = hasPerm(Manifest.permission.READ_PHONE_STATE)

        val location = if (hasFine || hasCoarse) {
            try { locationRepo.getCurrentLocation() } catch (_: SecurityException) { null }
        } else null

        val operator = if (hasPhone) {
            try { telephonyRepo.getOperatorName() } catch (_: SecurityException) { null }
        } else null

        return Triple(location?.latitude, location?.longitude, operator)
    }

    /**
     * Nasłuch lokalizacji jako Flow.
     * Przy zmianie >= 200 m wywołuje refresh(force=false, newLocation=...).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationRepo.locationFlow(
                minDistanceMeters = 200f,
                intervalMillis = 5_000L
            ).collect { newLoc ->
                val distance = newLoc?.let { lastLocation?.distanceTo(it) } ?: Float.MAX_VALUE
                if (distance >= 200f) {
                    refresh(force = false, newLocation = newLoc)
                }
            }
        }
    }

    /**
     * Odświeża dane do UI.
     *
     * - Jeśli force=true: odświeża niezależnie od dystansu.
     * - Jeśli force=false: odświeża tylko gdy wykryto przemieszczenie >= 200 m.
     *
     * newLocation (opcjonalnie): gdy mamy lokalizację z Flow, podajemy ją tutaj
     * i nie pobieramy drugi raz getCurrentLocation().
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun refresh(force: Boolean, newLocation: Location? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val hasFine = hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)
            val hasCoarse = hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION)
            val hasPhone = hasPerm(Manifest.permission.READ_PHONE_STATE)

            val currentLocation = if (newLocation != null) {
                newLocation
            } else if (hasFine || hasCoarse) {
                try { locationRepo.getCurrentLocation() } catch (_: SecurityException) { null }
            } else null

            val distance = currentLocation?.let { lastLocation?.distanceTo(it) } ?: Float.MAX_VALUE
            val movedEnough = distance >= 200f

            if (force || movedEnough) {
                lastLocation = currentLocation

                val signal = if (hasPhone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try { telephonyRepo.getSignalStrength() } catch (_: SecurityException) { null }
                } else null

                val operator = if (hasPhone) {
                    try { telephonyRepo.getOperatorName() } catch (_: SecurityException) { null }
                } else null

                val netType = if (hasPhone) {
                    try { telephonyRepo.getNetworkType() } catch (_: SecurityException) { null }
                } else null

                // RadioDetails wymaga zarówno danych telefonu, jak i dokładnej lokalizacji.
                val radio = if (hasPhone && hasFine) {
                    try { telephonyRepo.getRadioDetails() } catch (_: SecurityException) { null }
                } else null

                // Timing Advance (LTE) dostępny od API 26 (O) i wymaga uprawnień.
                val ta = if (hasPhone && hasFine && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try { telephonyRepo.getLteTimingAdvance() } catch (_: SecurityException) { null }
                } else null

                // Jednorazowe rozpoznanie trybu 5G (gdy observer nie zadziała / jeszcze nie dostarczył wartości).
                val nrOnce = if (hasPhone) {
                    try { telephonyRepo.getNrModeOnce() } catch (_: SecurityException) { null }
                } else null

                _uiState.value = _uiState.value.copy(
                    latitude = currentLocation?.latitude,
                    longitude = currentLocation?.longitude,
                    signalStrength = signal,
                    operatorName = operator,
                    networkType = netType,
                    locationFetched = true,

                    rat = radio?.rat,
                    rsrp = radio?.rsrp,
                    rsrq = radio?.rsrq,
                    sinr = radio?.sinr,
                    rssi = radio?.rssi,
                    pci = radio?.pci,
                    cellId = radio?.cellId,
                    band = radio?.band,
                    arfcn = radio?.arfcn,

                    tac = radio?.tac,
                    lac = radio?.lac,
                    enb = radio?.enb,
                    sectorId = radio?.sectorId,
                    eci = radio?.eci,
                    nci = radio?.nci,

                    // jeśli radio ma TA to bierzemy je, a jak nie ma – fallback na ta z osobnej funkcji
                    timingAdvance = radio?.timingAdvance ?: ta,

                    // nrOnce jest awaryjne; jeśli null, zostawiamy to co już było w UI
                    nrMode = nrOnce ?: _uiState.value.nrMode,

                    lastMeasurementTime = System.currentTimeMillis()
                )
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * Sprawdzenie pojedynczego uprawnienia runtime.
     */
    private fun hasPerm(perm: String): Boolean =
        ContextCompat.checkSelfPermission(app, perm) == PackageManager.PERMISSION_GRANTED
}
