package com.example.datasender.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.datasender.R
import com.example.datasender.model.SpeedTestProgress
import com.example.datasender.viewmodel.MainViewModel
import com.example.datasender.viewmodel.SpeedTestViewModel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * Ekran testu prędkości.
 *
 * Źródła danych:
 * - SpeedTestViewModel:
 *   - progress: stan testu (Idle/Running/Done/Error) + faza i procent,
 *   - instantSpeed: chwilowa prędkość (do licznika i wykresu).
 * - MainViewModel:
 *   - shortCodeFlow: identyfikator pomiarów (do wysyłki wyniku),
 *   - getFreshLocationAndOperator(): pobiera świeżą lokalizację i nazwę operatora.
 *
 * Ten plik zawiera tylko UI + zbieranie próbek do wykresu. Logika pomiarów jest w VM/repo.
 */
@Composable
fun SpeedTestScreen(
    mainVm: MainViewModel,
    vm: SpeedTestViewModel
) {
    val context = LocalContext.current

    // Aktualny stan automatu testu (Idle/Running/Done/Error).
    val state by vm.progress.collectAsState()

    // Chwilowa prędkość (np. aktualny Mb/s w trakcie download/upload).
    val instant by vm.instantSpeed.collectAsState()

    // ShortCode użytkownika (jeśli jest zapisany) – przekazywany do wyniku testu.
    val shortCode by mainVm.shortCodeFlow.collectAsState(initial = null)

    // Scope do uruchamiania coroutines z poziomu UI (start testu).
    val scope = rememberCoroutineScope()

    // Gradient tła (zostawiam bez zmian – nawet jeśli IDE pokazuje, że nieużyte w tym pliku).
    val bg = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFF050A1A),
                Color(0xFF081436),
                Color(0xFF060B1E)
            )
        )
    }

    // Kolory elementów wizualnych (ring + wykresy).
    val ringTrack = Color.White.copy(alpha = 0.12f)
    val accentA = Color(0xFF1D6CFF)
    val accentB = Color(0xFF00B2FF)
    val sparkLine = Color(0xFF00B2FF)
    val sparkGlow = Color(0xFF1D6CFF).copy(alpha = 0.18f)

    /**
     * Próbki do wykresów (sparkline) dla download i upload.
     * Uzupełniamy je na bieżąco z instantSpeed (LaunchedEffect).
     */
    val downloadSamples = remember { mutableStateListOf<Float>() }
    val uploadSamples = remember { mutableStateListOf<Float>() }

    // Komunikat błędu „UI” (np. wyjątek przy starcie testu lub problem z danymi wejściowymi).
    var uiError by remember { mutableStateOf<String?>(null) }

    // Flagi pomocnicze do renderowania UI w zależności od stanu.
    val isIdle = state is SpeedTestProgress.Idle
    val isRunning = state is SpeedTestProgress.Running
    val isDone = state is SpeedTestProgress.Done
    val isError = state is SpeedTestProgress.Error

    /**
     * Postęp ringa w zakresie 0..1.
     * - Running: percent / 100
     * - Done: pełne 1.0
     * - pozostałe: 0.0
     */
    val ring01 = when (val s = state) {
        is SpeedTestProgress.Running -> (s.percent / 100f).coerceIn(0f, 1f)
        is SpeedTestProgress.Done -> 1f
        else -> 0f
    }

    /**
     * Tekst fazy wyświetlany w UI.
     * Mapujemy enum (LATENCY/DOWNLOAD/UPLOAD) na polskie nazwy.
     */
    val phaseText: String = when (val s = state) {
        is SpeedTestProgress.Running -> when (s.phase.toString().uppercase()) {
            "DOWNLOAD" -> "Pobieranie"
            "UPLOAD" -> "Wysyłanie"
            "LATENCY" -> "Opóźnienie"
            else -> s.phase.toString()
        }
        is SpeedTestProgress.Done -> "Wynik"
        else -> "Test prędkości"
    }

    // Surowa faza (do decyzji, czy próbki dopisać do download czy do upload).
    val phaseRaw = (state as? SpeedTestProgress.Running)
        ?.phase
        ?.toString()
        ?.uppercase()

    /**
     * Reset UI do stanu startowego:
     * - czyści próbki wykresów,
     * - kasuje błąd UI.
     * Nie zmienia stanu w VM (to robi vm.cancel()).
     */
    fun resetUi() {
        downloadSamples.clear()
        uploadSamples.clear()
        uiError = null
    }

    /**
     * Dopisywanie próbek do wykresów w trakcie testu.
     * Działa tylko w stanie Running i tylko, gdy instantSpeed jest dostępne.
     */
    LaunchedEffect(instant, phaseRaw, isRunning) {
        if (!isRunning) return@LaunchedEffect
        val v = instant?.toFloat() ?: return@LaunchedEffect

        when (phaseRaw) {
            "DOWNLOAD" -> {
                downloadSamples.add(v)
                // Ograniczamy liczbę punktów, żeby wykres był stałej długości.
                if (downloadSamples.size > 60) downloadSamples.removeAt(0)
            }
            "UPLOAD" -> {
                uploadSamples.add(v)
                if (uploadSamples.size > 60) uploadSamples.removeAt(0)
            }
        }
    }

    /**
     * Uruchamia pełny test prędkości.
     * Kroki:
     * 1) reset UI,
     * 2) pobierz świeżą lokalizację i operatora,
     * 3) uruchom test w VM (runFullTest),
     * 4) obsłuż anulowanie oraz błędy (błąd -> komunikat + powrót VM do Idle).
     */
    fun launchSpeedTest() {
        scope.launch {
            resetUi()
            try {
                val (lat, lon, opRaw) = mainVm.getFreshLocationAndOperator()
                vm.runFullTest(
                    includeUpload = true,
                    shortCode = shortCode,
                    lat = lat,
                    lon = lon,
                    operator = opRaw ?: "UNKNOWN",
                    context = context
                )
            } catch (_: CancellationException) {
                // Anulowanie coroutine jest normalne (np. użytkownik przerwie test lub zmieni ekran).
            } catch (t: Throwable) {
                uiError = t.message ?: "Nie udało się uruchomić testu."
                vm.cancel() // Powrót do stanu Idle w VM.
            }
        }
    }

    // Wynik dostępny tylko po zakończeniu (Done).
    val res = (state as? SpeedTestProgress.Done)?.result

    // Informacyjny opis serwera (na sztywno).
    val serverText = "Serwer: Irlandia (Dublin)"

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Dekoracyjne tło (siatka kropek).
        DotsBg()

        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))

            // W stanie Idle „dociskamy” zawartość do środka pionowo.
            if (isIdle) Spacer(Modifier.weight(1f))

            if (isIdle) {
                Text(
                    text = "Speedtest",
                    color = Color.White.copy(alpha = 0.95f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.height(12.dp))
            }

            // Główny element: ring postępu + ikona rakiety + (opcjonalnie) licznik Mb/s.
            RocketWithRing(
                progress01 = ring01,
                isActive = isRunning,
                subtitle = if (isIdle) null else "Trwa: $phaseText",
                centerText = if (isIdle) null else (instant?.let { String.format("%.1f", it) } ?: "-"),
                ringTrack = ringTrack,
                accentA = accentA,
                accentB = accentB,
                ringSize = 240.dp
            )

            Spacer(Modifier.height(10.dp))

            // UI zależnie od stanu testu.
            when {
                isIdle -> {
                    Spacer(Modifier.height(10.dp))

                    PillButtonBlue(text = "TESTUJ", onClick = { launchSpeedTest() })

                    Spacer(Modifier.height(10.dp))

                    Text(
                        serverText,
                        color = Color.White.copy(alpha = 0.52f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "Uwaga: test może zużyć sporo danych (LTE/5G).",
                        color = Color.White.copy(alpha = 0.70f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )

                    // Błąd uruchomienia testu (nie mylić ze stanem SpeedTestProgress.Error).
                    if (uiError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            uiError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                isRunning -> {
                    // Przerwanie testu:
                    // - czyścimy UI (żeby wrócić do widoku startowego),
                    // - anulujemy test w VM (vm.cancel()).
                    OutlinedButton(
                        onClick = {
                            resetUi()
                            vm.cancel()
                        },
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) { Text("Stop") }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        serverText,
                        color = Color.White.copy(alpha = 0.52f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(10.dp))

                    // W trakcie testu pokazujemy wykresy, ale bez finalnych liczb (showValues=false).
                    // Ping/Jitter są placeholderami fakeSpark, bo w tym pliku nie zbierasz próbek ping/jitter.
                    Metrics4(
                        downloadValue = null,
                        uploadValue = null,
                        pingValue = null,
                        jitterValue = null,
                        downloadPoints = if (downloadSamples.size >= 2) downloadSamples.toList() else fakeSpark(80f),
                        uploadPoints = if (uploadSamples.size >= 2) uploadSamples.toList() else fakeSpark(40f),
                        pingPoints = fakeSpark(18f),
                        jitterPoints = fakeSpark(3f),
                        showValues = false,
                        line = sparkLine,
                        glow = sparkGlow
                    )
                }

                isDone -> {
                    PillButtonBlue(text = "TESTUJ PONOWNIE", onClick = { launchSpeedTest() })

                    Spacer(Modifier.height(6.dp))

                    Text(
                        serverText,
                        color = Color.White.copy(alpha = 0.52f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(10.dp))

                    // Finalne wartości po zakończeniu testu.
                    MetricsFinal4(
                        downloadValue = res?.downloadMbps?.toFloat(),
                        uploadValue = res?.uploadMbps?.toFloat(),
                        pingValue = res?.latencyMs?.toFloat(),
                        jitterValue = res?.jitterMs?.toFloat()
                    )
                }

                isError -> {
                    // Błąd zgłoszony przez VM (SpeedTestProgress.Error).
                    val msg = (state as? SpeedTestProgress.Error)?.message ?: "Nieznany błąd."

                    Text(
                        "Błąd: $msg",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(10.dp))

                    PillButtonBlue(
                        text = "WRÓĆ",
                        onClick = {
                            resetUi()
                            vm.cancel()
                        }
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        serverText,
                        color = Color.White.copy(alpha = 0.52f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (isIdle) Spacer(Modifier.weight(1f))

            // Oddech na dole, żeby elementy nie „wpadały” pod BottomBar.
            Spacer(Modifier.height(18.dp))
        }
    }
}

/* ───────────────────────── UI ───────────────────────── */

@Composable
private fun RocketWithRing(
    progress01: Float,
    isActive: Boolean,
    subtitle: String?,
    centerText: String?,
    ringTrack: Color,
    accentA: Color,
    accentB: Color,
    ringSize: androidx.compose.ui.unit.Dp
) {
    // Animacja postępu ringa do aktualnej wartości progress01.
    val animated by animateFloatAsState(
        targetValue = progress01.coerceIn(0f, 1f),
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "ring"
    )

    // Nieskończona animacja obrotu gradientu (wykorzystywana tylko gdy isActive=true).
    val inf = rememberInfiniteTransition(label = "inf")
    val spin by inf.animateFloat(
        0f, 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "spin"
    )

    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val thickness = 10.dp.toPx()
            val inset = thickness / 2f
            val arcSize = Size(size.width - thickness, size.height - thickness)
            val topLeft = Offset(inset, inset)

            // Track: pełny okrąg w tle.
            drawArc(
                color = ringTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(thickness, cap = StrokeCap.Round)
            )

            // Progres: fragment okręgu zależny od animated.
            val sweep = 360f * animated
            if (sweep > 0.5f) {
                val brush = Brush.sweepGradient(listOf(accentA, accentB, accentA), center = center)

                // Obrót gradientu tylko w trakcie działania testu.
                rotate(if (isActive) spin else 0f) {
                    drawArc(
                        brush = brush,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(thickness, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Zawartość środka (rakieta + wartości).
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.rocket_1),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Liczba w środku ringa (tylko poza Idle).
            if (centerText != null) {
                Text(
                    centerText,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Mb/s",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Podpis z aktualną fazą testu.
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PillButtonBlue(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1D6CFF).copy(alpha = 0.85f),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Karty metryk w układzie 2x2 (download/upload/ping/jitter).
 * W trakcie testu showValues=false, więc wartości są zastąpione "--".
 */
@Composable
private fun Metrics4(
    downloadValue: Float?,
    uploadValue: Float?,
    pingValue: Float?,
    jitterValue: Float?,
    downloadPoints: List<Float>,
    uploadPoints: List<Float>,
    pingPoints: List<Float>,
    jitterPoints: List<Float>,
    showValues: Boolean,
    line: Color,
    glow: Color
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Pobieranie", if (showValues) fmt(downloadValue, false) else "--", "Mb/s", downloadPoints, line, glow)
            MetricCard("Wysyłanie", if (showValues) fmt(uploadValue, false) else "--", "Mb/s", uploadPoints, line, glow)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Ping", if (showValues) fmt(pingValue, true) else "--", "ms", pingPoints, line, glow)
            MetricCard("Jitter", if (showValues) fmt(jitterValue, true) else "--", "ms", jitterPoints, line, glow)
        }
    }
}

/**
 * Finalny widok metryk po zakończeniu testu (wartości liczbowe z wyniku).
 */
@Composable
private fun MetricsFinal4(
    downloadValue: Float?,
    uploadValue: Float?,
    pingValue: Float?,
    jitterValue: Float?
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCardFinal("Pobieranie", fmt(downloadValue, isMs = false), "Mb/s")
            MetricCardFinal("Wysyłanie", fmt(uploadValue, isMs = false), "Mb/s")
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCardFinal("Ping", fmt(pingValue, isMs = true), "ms")
            MetricCardFinal("Jitter", fmt(jitterValue, isMs = true), "ms")
        }
    }
}

@Composable
private fun RowScope.MetricCardFinal(
    title: String,
    value: String,
    unit: String
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .height(104.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(14.dp)
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = unit,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RowScope.MetricCard(
    title: String,
    value: String,
    suffix: String,
    points: List<Float>,
    line: Color,
    glow: Color
) {
    Column(
        Modifier
            .weight(1f)
            .height(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(12.dp)
    ) {
        Text(title, color = Color.White.copy(alpha = 0.70f), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text(suffix, color = Color.White.copy(alpha = 0.60f), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Sparkline(points, Modifier.fillMaxWidth().height(30.dp), line, glow)
    }
}

/**
 * Prosty wykres liniowy (sparkline) rysowany na Canvas.
 * Skaluje wartości do wysokości dostępnej przestrzeni.
 */
@Composable
private fun Sparkline(points: List<Float>, modifier: Modifier, line: Color, glow: Color) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas

        val minV = points.minOrNull() ?: 0f
        val maxV = points.maxOrNull() ?: 1f
        val range = (maxV - minV).takeIf { it > 0.0001f } ?: 1f

        fun x(i: Int) = (i.toFloat() / points.lastIndex.toFloat()) * size.width
        fun y(v: Float) = size.height - ((v - minV) / range) * size.height

        val path = Path().apply {
            moveTo(x(0), y(points[0]))
            for (i in 1..points.lastIndex) lineTo(x(i), y(points[i]))
        }

        // Tło (poświata) + linia właściwa (na wierzchu).
        drawPath(path, color = glow, style = Stroke(width = 10f, cap = StrokeCap.Round))
        drawPath(path, color = line, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
    }
}

/**
 * Dekoracyjne tło z drobnymi kropkami (siatka).
 * Nie wpływa na logikę testu – tylko wygląd.
 */
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

/* helpers */

/**
 * Formatowanie wartości do wyświetlenia:
 * - ms: zaokrąglamy do Int,
 * - Mb/s: 1 miejsce po przecinku.
 */
private fun fmt(v: Float?, isMs: Boolean): String {
    if (v == null) return "--"
    return if (isMs) v.roundToInt().toString() else String.format("%.1f", v)
}

/**
 * Placeholder wykresu, gdy nie ma jeszcze prawdziwych próbek.
 * Tworzy „falujący” przebieg w okolicy wartości bazowej.
 */
private fun fakeSpark(base: Float): List<Float> {
    val n = 28
    val out = ArrayList<Float>(n)
    var v = base

    repeat(n) {
        v += listOf(-2f, -1f, 0.5f, 1.2f, -0.8f, 0.9f).random()
        out.add(v.coerceAtLeast(0.1f))
    }

    return out
}
