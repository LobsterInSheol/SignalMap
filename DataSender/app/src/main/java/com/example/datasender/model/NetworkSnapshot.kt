package com.example.datasender.model

/**
 * Migawka aktualnego stanu sieci i lokalizacji.
 *
 * Obiekt jest niemutowalny (data class) i może być:
 * - przechowywany w ViewModelu,
 * - wysyłany do backendu,
 * - używany do renderowania UI.
 */
data class NetworkSnapshot(

    // Współrzędne geograficzne (jeśli udało się pobrać lokalizację)
    val latitude: Double? = null,
    val longitude: Double? = null,

    // Podstawowe informacje sieciowe
    val signalStrength: Int? = null,   // siła sygnału w dBm (najlepsza z dostępnych komórek)
    val operatorName: String? = null,  // nazwa operatora (np. Play, Orange)
    val networkType: String? = null,   // uproszczony typ sieci: "2G", "3G", "LTE", "5G"
    val locationFetched: Boolean = false, // czy lokalizacja została już pobrana
    val isLoading: Boolean = false,       // czy trwa pobieranie danych

    // Szczegóły radiowe z aktualnie zarejestrowanej komórki
    val rat: String? = null,    // Radio Access Technology (np. "LTE", "NR", "WCDMA", "GSM")
    val rsrp: Int? = null,      // Reference Signal Received Power (LTE/NR)
    val rsrq: Int? = null,      // Reference Signal Received Quality (LTE/NR)
    val sinr: Int? = null,      // Signal to Interference plus Noise Ratio
    val rssi: Int? = null,      // Received Signal Strength Indicator (głównie 2G/3G)
    val pci: Int? = null,       // Physical Cell ID
    val cellId: Long? = null,   // ID komórki (różne znaczenie zależnie od RAT)
    val band: String? = null,   // pasmo radiowe (np. B3, B20, n78)
    val arfcn: Int? = null,     // numer kanału częstotliwości (EARFCN / NRARFCN)

    // Identyfikatory sieciowe
    val tac: Int? = null,      // Tracking Area Code (LTE/NR)
    val lac: Int? = null,      // Location Area Code (2G/3G)
    val enb: Int? = null,      // LTE: eNodeB ID (część ECI)
    val sectorId: Int? = null, // LTE: identyfikator sektora (0..255)
    val eci: Long? = null,     // LTE: E-UTRAN Cell Identifier
    val nci: Long? = null,     // NR: NR Cell Identifier

    // Parametry dodatkowe
    val timingAdvance: Int? = null, // LTE: Timing Advance (odległość UE–stacja bazowa)
    val nrMode: String? = null,     // tryb 5G: "SA", "NSA" albo null jeśli nieustalony

    // Stan aplikacji
    val isCollecting: Boolean = false,     // czy trwa aktywne zbieranie danych
    val lastMeasurementTime: Long? = null  // timestamp ostatniego pomiaru (epoch millis)
)
