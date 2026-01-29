package com.example.datasender.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.datasender.service.LocationService
import com.example.datasender.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ekran pomiarów sygnału.
 *
 * Odpowiedzialności:
 * - renderuje aktualne dane z uiState (NetworkSnapshot / stan zbierania),
 * - uruchamia i zatrzymuje pomiar (ViewModel + foreground service),
 * - pokazuje karty: lokalizacja, sieć, sygnał, identyfikatory komórki,
 * - posiada tryb startowy (intro) gdy pomiar nie działa.
 */
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun SignalScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    // Stan UI trzymany w ViewModelu (Flow/StateFlow).
    val ui by viewModel.uiState.collectAsState()

    val context = LocalContext.current

    // Scroll jest aktywny tylko w trybie zbierania (gdy kart jest dużo).
    val scrollState = rememberScrollState()

    // Formatowanie timestampu ostatniego pomiaru do czytelnego tekstu.
    val lastMeasurementText = ui.lastMeasurementTime?.let { ts ->
        val df = SimpleDateFormat("dd.MM.yyyy • HH:mm:ss", Locale.getDefault())
        df.format(Date(ts))
    } ?: "-"

    // Tło i kolory w stylu pozostałych ekranów.
    val bg = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFF050A1A),
                Color(0xFF081436),
                Color(0xFF060B1E)
            )
        )
    }
    val cardBg = Color.White.copy(alpha = 0.08f)
    val accentA = Color(0xFF1D6CFF)
    val accentB = Color(0xFF00B2FF)

    /**
     * Auto-refresh:
     * - jeśli pomiar trwa,
     * - nie jesteśmy w trakcie ładowania,
     * - a lokalizacja nie została jeszcze pobrana,
     * to wymuszamy jednorazowe odświeżenie.
     *
     * autoRefreshed pilnuje, żeby nie robić tego w pętli.
     */
    var autoRefreshed by remember { mutableStateOf(false) }
    LaunchedEffect(ui.isCollecting, ui.locationFetched, ui.isLoading) {
        if (ui.isCollecting && !ui.isLoading && !ui.locationFetched && !autoRefreshed) {
            autoRefreshed = true
            viewModel.refresh(force = true)
        }
        if (!ui.isCollecting) autoRefreshed = false
    }

    // Start: uruchamiamy logikę w ViewModel + foreground service (pomiary w tle).
    fun startCollecting() {
        viewModel.start()
        startLocationService(context)
    }

    // Stop: zatrzymujemy logikę w ViewModel + service.
    fun stopCollecting() {
        viewModel.stop()
        stopLocationService(context)
    }

    /**
     * Rozpoznanie „rodziny” typu sieci na podstawie stringa z uiState.
     * Jest to używane tylko do:
     * - dobrania etykiety ARFCN,
     * - doboru pól identyfikatorów komórki (NR/LTE/3G/2G).
     */
    val netType = ui.networkType ?: ""
    val isNr = netType.contains("5G", ignoreCase = true) || netType.contains("NR", ignoreCase = true)
    val isLte = netType.contains("LTE", ignoreCase = true)
    val is3g = netType.contains("3G", ignoreCase = true) || netType.contains("WCDMA", ignoreCase = true)
    val is2g = netType.contains("2G", ignoreCase = true) || netType.contains("GSM", ignoreCase = true)

    // Etykieta kanału zależna od technologii radiowej.
    val arfcnLabel = when {
        isNr  -> "NR-ARFCN"
        isLte -> "EARFCN"
        is3g  -> "UARFCN"
        is2g  -> "ARFCN"
        else  -> "ARFCN"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Dekoracyjne kropki w tle (tylko wygląd).
        DotsBg()

        // Scroll tylko podczas zbierania (żeby intro było wycentrowane).
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .let { if (ui.isCollecting) it.verticalScroll(scrollState) else it }

        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Informacja o czasie ostatniego pomiaru (tylko gdy zbieramy i mamy timestamp).
            if (ui.isCollecting && ui.lastMeasurementTime != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ostatni pomiar",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = lastMeasurementText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Tryb startowy: jeśli nie zbieramy, pokazujemy intro + przycisk START.
            if (!ui.isCollecting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    IntroSignalStart(
                        onStartClick = { startCollecting() },
                        accentA = accentA,
                        accentB = accentB
                    )
                }
            } else {

                // KARTA: Lokalizacja
                InfoCardSpeed(
                    title = "Lokalizacja",
                    icon = Icons.Default.LocationOn,
                    cardBg = cardBg
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SpeedLine("Szerokość geograficzna", ui.latitude?.toString() ?: "⏳")
                        SpeedLine("Długość geograficzna", ui.longitude?.toString() ?: "⏳")
                    }
                }

                // KARTA: Sieć
                InfoCardSpeed(
                    title = "Sieć",
                    icon = Icons.Default.NetworkCell,
                    cardBg = cardBg
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SpeedLine("Operator", ui.operatorName ?: "⏳")
                        SpeedLine("Typ sieci (RAT)", ui.networkType ?: ui.rat ?: "⏳")
                        SpeedLine("Tryb 5G", ui.nrMode ?: if (isNr) "nieznany" else "brak")

                        val bandText = ui.band ?: "-"
                        val arfcnText = ui.arfcn?.toString() ?: "-"
                        SpeedLine("Pasmo", bandText)
                        SpeedLine("Kanał ($arfcnLabel)", arfcnText)
                    }
                }

                // --- KARTA: Sygnał ---
                InfoCardSpeed(
                    title = "Sygnał",
                    icon = Icons.Default.BarChart,
                    cardBg = cardBg
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

                        // Dla LTE/NR pokazujemy metryki jakości (RSRP/RSRQ/SINR),
                        // a dla 2G/3G proste RSSI.
                        if (isLte || isNr) {
                            SignalMetricRowSpeed(
                                name = "RSRP",
                                valueText = ui.rsrp?.let { "$it dBm" } ?: "-",
                                quality = rsrpQuality(ui.rsrp)
                            )
                            SignalMetricRowSpeed(
                                name = "RSRQ",
                                valueText = ui.rsrq?.let { "$it dB" } ?: "-",
                                quality = rsrqQuality(ui.rsrq)
                            )
                            SignalMetricRowSpeed(
                                name = "SINR",
                                valueText = ui.sinr?.let { "$it dB" } ?: "-",
                                quality = sinrQuality(ui.sinr)
                            )
                        } else {
                            SpeedLine("RSSI", ui.rssi?.let { "$it dBm" } ?: "-")
                        }

                        // Timing Advance ma sens głównie w LTE.
                        SpeedLine("TA (LTE)", ui.timingAdvance?.toString() ?: "-")
                    }
                }

                // --- KARTA: Komórka (identyfikatory) ---
                InfoCardSpeed(
                    title = "Komórka",
                    icon = Icons.Default.NetworkCell,
                    cardBg = cardBg
                ) {
                    when {
                        isNr -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SpeedLine("PCI", ui.pci?.toString() ?: "-")
                                SpeedLine(
                                    "NCI (Cell ID)",
                                    ui.nci?.toString() ?: ui.cellId?.toString() ?: "-"
                                )
                                SpeedLine("TAC", ui.tac?.toString() ?: "-")
                            }
                        }

                        isLte -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SpeedLine("PCI", ui.pci?.toString() ?: "-")
                                SpeedLine(
                                    "ECI (Cell ID)",
                                    ui.eci?.toString() ?: ui.cellId?.toString() ?: "-"
                                )
                                SpeedLine("eNB", ui.enb?.toString() ?: "-")
                                SpeedLine("sektor", ui.sectorId?.toString() ?: "-")
                                SpeedLine("TAC", ui.tac?.toString() ?: "-")
                            }
                        }

                        is3g || is2g -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SpeedLine("CID (Cell ID)", ui.cellId?.toString() ?: "-")
                                SpeedLine("LAC", ui.lac?.toString() ?: "-")
                            }
                        }

                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SpeedLine(
                                    "Cell ID",
                                    ui.cellId?.toString()
                                        ?: ui.eci?.toString()
                                        ?: ui.nci?.toString()
                                        ?: "-"
                                )
                                SpeedLine("TAC/LAC", ui.tac?.toString() ?: ui.lac?.toString() ?: "-")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Przyciski akcji: ręczne odświeżenie i zatrzymanie pomiaru.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    PillButtonBlue(
                        text = "Odśwież",
                        onClick = { viewModel.refresh(force = true) },
                        accent = accentA
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedPill(
                        text = "Zatrzymaj",
                        onClick = { stopCollecting() }
                    )
                }

                // Spinner ładowania, gdy ViewModel zbiera/odświeża dane.
                if (ui.isLoading) {
                    Spacer(Modifier.height(6.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = accentB
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntroSignalStart(
    onStartClick: () -> Unit,
    accentA: Color,
    accentB: Color
) {
    // Steruje widocznością dolnego sheetu "Jak to działa?"
    var showHowItWorks by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pomiary sygnału",
            color = Color.White.copy(alpha = 0.95f),
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            letterSpacing = 0.3.sp
        )
        Text(
            text = "Zbieraj dane o jakości sieci w Twojej okolicy. Możesz zatrzymać pomiar w każdej chwili.",
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniChip("Moc sygnału", Modifier.weight(1f))
            MiniChip("Operator", Modifier.weight(1f))
            MiniChip("Lokalizacja", Modifier.weight(1f))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Przykład danych",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.titleSmall
                )
                SpeedLine("Operator", "Play")
                SpeedLine("Typ sieci", "LTE/5G")
                SpeedLine("RSRP", "-92 dBm")
            }
        }

        Button(
            onClick = onStartClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentA.copy(alpha = 0.90f))
        ) {
            Text("START POMIARU", color = Color.White)
        }

        OutlinedButton(
            onClick = { showHowItWorks = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Jak to działa?")
        }
    }

    // BottomSheet z opisem: co zbieramy, jak często, jak wysyłamy, jak zatrzymać.
    if (showHowItWorks) {
        ModalBottomSheet(
            onDismissRequest = { showHowItWorks = false },
            containerColor = Color(0xFF081436)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("📡 Co zbieramy?", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    "• Operator i typ sieci (np. LTE, 5G), pasmo oraz identyfikatory komórki (Cell ID, PCI, TAC/LAC)",
                    color = Color.White.copy(alpha = 0.75f)
                )
                Text(
                    "• Parametry jakości sygnału: RSRP, RSRQ, SINR (lub RSSI w sieciach 2G/3G)",
                    color = Color.White.copy(alpha = 0.75f)
                )
                Text(
                    "• Lokalizacja GPS (jeśli wyrazisz zgodę) oraz czas wykonania pomiaru",
                    color = Color.White.copy(alpha = 0.75f)
                )

                Spacer(Modifier.height(10.dp))

                Text("🚗🚶‍♂️🧍 Tryby ruchu", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("• 🧍 Brak ruchu – pomiary wykonywane są rzadziej (około co 60 s)", color = Color.White.copy(alpha = 0.75f))
                Text("• 🚶‍♂️ Chodzenie – pomiary wykonywane są częściej (około co 15 s)", color = Color.White.copy(alpha = 0.75f))
                Text("• 🚗 Jazda samochodem – pomiary wykonywane są bardzo często (około co 3 s)", color = Color.White.copy(alpha = 0.75f))

                Spacer(Modifier.height(10.dp))

                Text("☁️ Wysyłka na serwer", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("• 🧍 Przy braku ruchu dane są wysyłane tylko przy zmianie komórki lub co kilka minut", color = Color.White.copy(alpha = 0.75f))
                Text("• 🚶‍♂️ / 🚗 Podczas ruchu każda zebrana próbka jest wysyłana na serwer", color = Color.White.copy(alpha = 0.75f))

                Spacer(Modifier.height(10.dp))

                Text("⛔ Zatrzymanie pomiarów", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    "• Pomiary możesz zatrzymać w dowolnym momencie - w aplikacji lub bezpośrednio z powiadomienia systemowego",
                    color = Color.White.copy(alpha = 0.75f)
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { showHowItWorks = false },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentB.copy(alpha = 0.9f))
                ) { Text("OK", color = Color.White) }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MiniChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun InfoCardSpeed(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cardBg: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White.copy(alpha = 0.9f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.titleSmall
            )
        }

        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SpeedLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.70f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PillButtonBlue(
    text: String,
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.85f),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(text)
    }
}

@Composable
private fun OutlinedPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(text)
    }
}

/**
 * Wiersz metryki sygnału:
 * - lewa strona: nazwa i wartość,
 * - prawa strona: kropka jakości + opis jakości.
 */
@Composable
private fun SignalMetricRowSpeed(
    name: String,
    valueText: String,
    quality: Quality
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$name: $valueText",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(quality.color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = quality.label,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DotsBg() {
    Canvas(Modifier.fillMaxSize()) {
        val step = 28.dp.toPx()
        val r = 1.4f
        val c = Color.White.copy(alpha = 0.06f)
        var y = 0f
        while (y <= size.height) {
            var x = 0f
            while (x <= size.width) {
                drawCircle(c, r, Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

/* ---------------- Foreground service (pomiary w tle) ---------------- */

private fun startLocationService(context: Context) {
    val intent = Intent(context, LocationService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
    else context.startService(intent)
}

private fun stopLocationService(context: Context) {
    val intent = Intent(context, LocationService::class.java)
    context.stopService(intent)
}

/* ---------------- Ocena jakości (mapowanie wartości -> etykieta + kolor) ---------------- */

private data class Quality(val label: String, val color: Color)

private fun rsrpQuality(rsrp: Int?): Quality {
    if (rsrp == null) return Quality("brak danych", Color.White)

    return when {
        rsrp >= -70  -> Quality("świetny", Color(0xFF4CAF50))
        rsrp >= -85  -> Quality("dobry",   Color(0xFFFFC107))
        rsrp >= -100 -> Quality("średni",  Color(0xFFFF9800))
        else         -> Quality("słaby",   Color(0xFFF44336))
    }
}

private fun rsrqQuality(rsrq: Int?): Quality {
    if (rsrq == null) return Quality("brak danych", Color.White)

    return when {
        rsrq >= -10  -> Quality("świetny", Color(0xFF4CAF50))
        rsrq >= -15  -> Quality("dobry",   Color(0xFFFFC107))
        rsrq >= -20  -> Quality("średni",  Color(0xFFFF9800))
        else         -> Quality("słaby",   Color(0xFFF44336))
    }
}

private fun sinrQuality(sinr: Int?): Quality {
    if (sinr == null) return Quality("brak danych", Color.White)

    return when {
        sinr >= 20 -> Quality("świetny", Color(0xFF4CAF50))
        sinr >= 13 -> Quality("dobry",   Color(0xFFFFC107))
        sinr >= 0  -> Quality("średni",  Color(0xFFFF9800))
        else       -> Quality("słaby",   Color(0xFFF44336))
    }
}
