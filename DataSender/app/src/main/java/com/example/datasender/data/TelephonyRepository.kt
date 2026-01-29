package com.example.datasender.data

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.*
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TelephonyRepository(private val app: Application) {

    // TelephonyManager pobierany po staremu (zgodne z minSdk 21).
    private val tm: TelephonyManager =
        app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    // Prosty helper do sprawdzania uprawnień.
    private fun has(permission: String) =
        ActivityCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasPhone() = has(Manifest.permission.READ_PHONE_STATE)
    private fun hasFineLoc() = has(Manifest.permission.ACCESS_FINE_LOCATION)

    // TelephonyManager „dla aktywnej karty danych” (jeśli system potrafi wskazać subskrypcję danych).
    // Na starszych API zwraca zwykły tm.
    private fun subTelephonyManager(): TelephonyManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
            if (dataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                tm.createForSubscriptionId(dataSubId) else tm
        } else tm
    }

    // Filtr dla wartości „brak danych”: UNAVAILABLE / Int.MAX_VALUE.
    private fun safeInt(v: Int): Int? =
        if (v == Int.MAX_VALUE || v == CellInfo.UNAVAILABLE) null else v

    // Wywołuje metodę zwracającą Int przez refleksję (część getterów nie jest dostępna na wszystkich API).
    private fun callIntGetterOrNull(target: Any, name: String): Int? {
        return try {
            val m = target.javaClass.getMethod(name)
            val v = m.invoke(target) as? Int
            if (v == null) null else safeInt(v)
        } catch (_: Throwable) {
            null
        }
    }

    // Wywołuje metodę zwracającą Long przez refleksję.
    private fun callLongGetterOrNull(target: Any, name: String): Long? = try {
        val m = target.javaClass.getMethod(name)
        m.invoke(target) as? Long
    } catch (_: Throwable) { null }

    // Wywołuje metodę zwracającą IntArray przez refleksję (np. getBands()).
    private fun callIntArrayGetterOrNull(target: Any, name: String): IntArray? = try {
        val m = target.javaClass.getMethod(name)
        m.invoke(target) as? IntArray
    } catch (_: Throwable) { null }

    // Skleja listę pasm w postaci np. "B3,B7" albo "n78,n1".
    private fun joinBands(bands: IntArray?, prefix: String): String? =
        bands?.joinToString(",") { b -> "$prefix$b" }

    // Odczyt ServiceState potrafi rzucić SecurityException, więc zabezpieczamy.
    @RequiresApi(Build.VERSION_CODES.O)
    private fun safeServiceState(t: TelephonyManager): ServiceState? =
        try { t.serviceState } catch (_: SecurityException) { null }

    // getNrState() jest od API 30, więc wywołujemy ją przez refleksję.
    private fun getNrStateCompat(ss: ServiceState?): Int? {
        if (ss == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val m = ServiceState::class.java.getMethod("getNrState")
            m.invoke(ss) as? Int
        } catch (_: Throwable) { null }
    }

    // Sprawdza czy NR jest CONNECTED, bez bezpośredniego odwołania do stałej (refleksja).
    private fun isNrConnectedCompat(ss: ServiceState?): Boolean {
        val value = getNrStateCompat(ss) ?: return false
        return try {
            val f = ServiceState::class.java.getField("NR_STATE_CONNECTED")
            value == f.getInt(null)
        } catch (_: Throwable) {
            // Awaryjnie: na Android R CONNECTED = 3.
            value == 3
        }
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getOperatorName(): String? {
        if (!hasPhone()) return null

        // Najpierw próbujemy operatora z TM przypisanego do aktywnej karty danych.
        subTelephonyManager().networkOperatorName?.takeIf { it.isNotBlank() }?.let { return it }

        // Fallback: SubscriptionManager (API 22+), żeby trafić w odpowiednią kartę SIM.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val sm = app.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val list = sm?.activeSubscriptionInfoList.orEmpty()
            val dataSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val id = SubscriptionManager.getDefaultDataSubscriptionId()
                if (id != SubscriptionManager.INVALID_SUBSCRIPTION_ID) id else null
            } else null

            list.firstOrNull { it.subscriptionId == dataSubId }?.carrierName?.toString()
                ?.takeIf { it.isNotBlank() }?.let { return it }
            list.firstOrNull()?.carrierName?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }

        // Ostateczny fallback.
        return tm.networkOperatorName?.takeIf { it.isNotBlank() }
    }

    /**
     * Typ sieci:
     * - jeżeli (API 30+) NR faktycznie CONNECTED -> zwracamy "5G"
     * - w przeciwnym razie zwracamy na podstawie (data)NetworkType
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getNetworkType(): String {
        if (!hasPhone()) return "Nieznany"

        val subTm = subTelephonyManager()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val ss = safeServiceState(subTm)
            if (isNrConnectedCompat(ss)) return "5G"
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            subTm.dataNetworkType
        } else {
            @Suppress("DEPRECATION") tm.networkType
        }

        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR   -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE  -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G (EDGE)"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G (GPRS)"
            TelephonyManager.NETWORK_TYPE_GSM  -> "2G (GSM)"
            else -> "Nieznany"
        }
    }

    /**
     * Zwraca „najlepszy” sygnał (dbm) spośród zarejestrowanych komórek.
     * Wymaga READ_PHONE_STATE i ACCESS_FINE_LOCATION, bo allCellInfo jest traktowane jako dane lokalizacyjne.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getSignalStrength(): Int? {
        if (!hasPhone() || !hasFineLoc()) return null
        val cells = try { subTelephonyManager().allCellInfo } catch (_: SecurityException) { null } ?: return null

        var best: Int? = null
        for (cell in cells) {
            if (!cell.isRegistered) continue

            val rawDbm: Int? = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr -> cell.cellSignalStrength.dbm
                cell is CellInfoLte   -> cell.cellSignalStrength.dbm
                cell is CellInfoWcdma -> cell.cellSignalStrength.dbm
                cell is CellInfoGsm   -> cell.cellSignalStrength.dbm
                cell is CellInfoCdma  -> cell.cellSignalStrength.dbm
                else -> null
            }

            val dbm = rawDbm?.let { safeInt(it) }
            if (dbm != null) best = if (best == null) dbm else maxOf(best!!, dbm)
        }
        return best
    }

    // Szczegóły radiowe „wyciągnięte” z CellInfo: parametry sygnału + identyfikatory komórki + pasmo/arfcn.
    data class RadioDetails(
        val rat: String? = null,
        val rsrp: Int? = null,
        val rsrq: Int? = null,
        val sinr: Int? = null,
        val rssi: Int? = null,
        val pci: Int? = null,
        val cellId: Long? = null,
        val band: String? = null,
        val arfcn: Int? = null,
        val timingAdvance: Int? = null,
        val tac: Int? = null,
        val lac: Int? = null,
        val enb: Int? = null,
        val sectorId: Int? = null,
        val eci: Long? = null,
        val nci: Long? = null
    )

    /**
     * Zwraca szczegóły „aktywnie zarejestrowanej” komórki.
     * Preferencja wyboru:
     * - NR -> LTE -> WCDMA -> GSM
     *
     * W NR część pól jest odczytywana refleksją (różnice API/producentów).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @RequiresPermission(allOf = [
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PHONE_STATE
    ])
    fun getRadioDetails(): RadioDetails? {
        if (!hasFineLoc() || !hasPhone()) return null
        val cells = try { subTelephonyManager().allCellInfo } catch (_: SecurityException) { null } ?: return null

        val reg = cells.filter { it.isRegistered }
        val picked = reg.firstOrNull { it is CellInfoNr }
            ?: reg.firstOrNull { it is CellInfoLte }
            ?: reg.firstOrNull { it is CellInfoWcdma }
            ?: reg.firstOrNull { it is CellInfoGsm }
            ?: return null

        return when (picked) {
            is CellInfoNr -> {
                val id = picked.cellIdentity as CellIdentityNr
                val ss = picked.cellSignalStrength as CellSignalStrengthNr

                val rsrp = callIntGetterOrNull(ss, "getCsiRsrp")
                    ?: callIntGetterOrNull(ss, "getSsRsrp")
                    ?: safeInt(ss.dbm)
                val rsrq = callIntGetterOrNull(ss, "getCsiRsrq")
                    ?: callIntGetterOrNull(ss, "getSsRsrq")
                val sinr = callIntGetterOrNull(ss, "getCsiSinr")
                    ?: callIntGetterOrNull(ss, "getSsSinr")

                val nrarfcn = callIntGetterOrNull(id, "getNrarfcn")
                val bandsStr = joinBands(callIntArrayGetterOrNull(id, "getBands"), prefix = "n")
                val tac = callIntGetterOrNull(id, "getTac")
                val nci = callLongGetterOrNull(id, "getNci") ?: id.nci

                RadioDetails(
                    rat = "NR",
                    rsrp = rsrp, rsrq = rsrq, sinr = sinr,
                    pci = id.pci,
                    nci = nci,
                    tac = tac,
                    cellId = nci,
                    band = bandsStr ?: nrarfcn?.let { guessNrBand(it) },
                    arfcn = nrarfcn
                )
            }

            is CellInfoLte -> {
                val id = picked.cellIdentity
                val ss = picked.cellSignalStrength
                val earfcn = callIntGetterOrNull(id, "getEarfcn")
                val bandsStr = joinBands(callIntArrayGetterOrNull(id, "getBands"), prefix = "B")

                val ci = id.ci
                val tac = id.tac
                val enb = if (ci != CellInfo.UNAVAILABLE && ci != Int.MAX_VALUE) ci / 256 else null
                val sector = if (ci != CellInfo.UNAVAILABLE && ci != Int.MAX_VALUE) ci % 256 else null

                RadioDetails(
                    rat = "LTE",
                    rsrp = safeInt(ss.rsrp),
                    rsrq = safeInt(ss.rsrq),
                    sinr = safeInt(ss.rssnr),
                    pci = id.pci,
                    cellId = ci.toLong(),
                    eci = ci.toLong(),
                    tac = tac,
                    enb = enb,
                    sectorId = sector,
                    band = bandsStr ?: earfcn?.let { guessLteBand(it) },
                    arfcn = earfcn,
                    timingAdvance = safeInt(ss.timingAdvance)
                )
            }

            is CellInfoWcdma -> {
                val id = picked.cellIdentity
                val ss = picked.cellSignalStrength
                RadioDetails(
                    rat = "WCDMA",
                    rssi = safeInt(ss.dbm),
                    pci = id.psc,
                    cellId = id.cid?.toLong(),
                    lac = id.lac,
                    arfcn = callIntGetterOrNull(id, "getUarfcn")
                )
            }

            is CellInfoGsm -> {
                val id = picked.cellIdentity
                val ss = picked.cellSignalStrength
                RadioDetails(
                    rat = "GSM",
                    rssi = safeInt(ss.dbm),
                    cellId = id.cid?.toLong(),
                    lac = id.lac,
                    arfcn = callIntGetterOrNull(id, "getArfcn")
                )
            }

            else -> null
        }
    }

    // Mapowanie „najczęstszych” pasm (uprość/rozszerz według potrzeb).
    private fun guessLteBand(earfcn: Int): String? = when (earfcn) {
        in   0..  599 -> "B1"
        in 1200.. 1949 -> "B3"
        in 2750.. 3449 -> "B7"
        in 6150.. 6449 -> "B20"
        in 9210.. 9659 -> "B28"
        else -> null
    }

    private fun guessNrBand(nrarfcn: Int): String? = when (nrarfcn) {
        in 620000..653333 -> "n78"
        in 422000..434000 -> "n7"
        in 361000..376000 -> "n3"
        in 151600..160600 -> "n1"
        else -> null
    }

    /**
     * Timing Advance dla LTE (jeśli dostępny).
     * Zwraca null gdy brak uprawnień, brak LTE lub wartości są „UNAVAILABLE”.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getLteTimingAdvance(): Int? {
        if (!hasFineLoc() || !hasPhone()) return null
        val cells = try { subTelephonyManager().allCellInfo } catch (_: SecurityException) { null } ?: return null
        val lte = cells.filterIsInstance<CellInfoLte>().firstOrNull { it.isRegistered } ?: return null
        val ta = lte.cellSignalStrength.timingAdvance
        return safeInt(ta)
    }

    /**
     * Jednorazowa próba rozróżnienia SA/NSA.
     * - SA: dataNetworkType = NR albo (API 30+) NR connected w ServiceState
     * - NSA: bez listenera DisplayInfo zwykle nie da się tego pewnie stwierdzić
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getNrModeOnce(): String? {
        if (!hasPhone()) return null

        val subTm = subTelephonyManager()

        val isDataNr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            subTm.dataNetworkType == TelephonyManager.NETWORK_TYPE_NR
        else false

        val saByService =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) isNrConnectedCompat(safeServiceState(subTm)) else false

        return when {
            isDataNr || saByService -> "SA"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> null
            else -> null
        }
    }

    /**
     * Obserwuje tryb NR:
     * - API 31+: TelephonyCallback (DisplayInfoListener)
     * - API 30: PhoneStateListener(LISTEN_DISPLAY_INFO_CHANGED)
     * - niżej: kończy flow (brak wsparcia)
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun observeNrMode(): Flow<String> = callbackFlow {
        if (!hasPhone()) { close(); return@callbackFlow }
        val t = subTelephonyManager()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val executor = androidx.core.content.ContextCompat.getMainExecutor(app)
            val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                    val mode = when {
                        info.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "NSA"
                        info.networkType == TelephonyManager.NETWORK_TYPE_NR -> "SA"
                        else -> "Brak NR"
                    }
                    trySend(mode)
                }
            }
            t.registerTelephonyCallback(executor, cb)
            awaitClose { t.unregisterTelephonyCallback(cb) }

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val listener = object : PhoneStateListener() {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                    val mode = when {
                        info.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "NSA"
                        info.networkType == TelephonyManager.NETWORK_TYPE_NR -> "SA"
                        else -> "Brak NR"
                    }
                    trySend(mode)
                }
            }
            t.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
            awaitClose { t.listen(listener, PhoneStateListener.LISTEN_NONE) }

        } else {
            close()
        }
    }
}
