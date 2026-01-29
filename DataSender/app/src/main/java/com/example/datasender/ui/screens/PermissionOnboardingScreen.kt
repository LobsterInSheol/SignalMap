package com.example.datasender.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.datasender.OnboardingStep
import com.example.datasender.R

/**
 * Ekran onboardingu proszący użytkownika o uprawnienia.
 *
 * Zachowanie:
 * - renderuje UI zależnie od aktualnego kroku (step),
 * - wywołuje przekazane callbacki (onRequest...) po kliknięciu przycisków,
 * - nie trzyma stanu – jest komponentem prezentacyjnym.
 */
@Composable
fun PermissionsOnboardingScreen(
    step: OnboardingStep,
    onRequestLocation: () -> Unit,
    onRequestBackground: () -> Unit,
    onSkipBackground: () -> Unit,
    onRequestPhone: () -> Unit
) {

    val bg = Brush.verticalGradient(
        listOf(
            Color(0xFF050A1A),
            Color(0xFF081436),
            Color(0xFF060B1E)
        )
    )

    // Kolory pomocnicze dla kart i przycisków.
    val cardBg = Color.White.copy(alpha = 0.08f)
    val accentA = Color(0xFF1D6CFF)

    // Wysokość górnego paska z logo.
    val TopBarHeight = 62.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Dekoracyjne tło (nie wpływa na logikę).
        DotsBg()

        // Scaffold trzyma topBar i content w jednym układzie.
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                // TopBar z logo – uwzględnia status bar (żeby nie nachodzić pod notch).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(TopBarHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.signal_7_1),
                        contentDescription = null,
                        modifier = Modifier.height(TopBarHeight)
                    )
                }
            }
        ) { innerPadding ->
            // Treść ekranu: jedna karta w środku, z lekkim odsunięciem od góry i dołu.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))

                // Karta pokazuje treść i akcje zależnie od kroku (LOCATION/BACKGROUND/PHONE).
                PermissionCard(
                    step = step,
                    cardBg = cardBg,
                    accent = accentA,
                    onRequestLocation = onRequestLocation,
                    onRequestBackground = onRequestBackground,
                    onSkipBackground = onSkipBackground,
                    onRequestPhone = onRequestPhone
                )

                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Pojedyncza karta onboardingu.
 * Na podstawie `step` wybiera:
 * - tytuł,
 * - opis,
 * - dodatkową informację (hint),
 * - tekst przycisku i akcję.
 */
@Composable
private fun PermissionCard(
    step: OnboardingStep,
    cardBg: Color,
    accent: Color,
    onRequestLocation: () -> Unit,
    onRequestBackground: () -> Unit,
    onSkipBackground: () -> Unit,
    onRequestPhone: () -> Unit
) {
    // Gdy onboarding zakończony – nic nie renderujemy.
    if (step == OnboardingStep.DONE) return

    // Teksty widoczne w UI zależą od kroku onboardingu.
    val title: String
    val body: String
    val hint: String
    val primaryText: String

    // Dobór treści pod konkretny typ uprawnienia.
    when (step) {
        OnboardingStep.LOCATION -> {
            title = "Dostęp do lokalizacji"
            body =
                "Aplikacja mierzy jakość sygnału komórkowego w\u00A0Twojej okolicy. " +
                        "Do tego potrzebujemy przybliżonej lub dokładnej lokalizacji urządzenia. " +
                        "Dane służą wyłącznie do tworzenia mapy jakości sieci."
            hint =
                "Na Androidzie 13+ system może też zapytać o\u00A0zgodę na powiadomienia. " +
                        "Są potrzebne do stałego powiadomienia podczas pomiaru w tle. " +
                        "Możesz je nadać teraz albo później w ustawieniach aplikacji."
            primaryText = "Nadaj dostęp"
        }

        OnboardingStep.BACKGROUND -> {
            title = "Lokalizacja w tle (opcjonalnie)"
            body =
                "Jeśli pozwolisz na lokalizację w tle, aplikacja będzie mogła zbierać pomiary " +
                        "także wtedy, gdy jest zminimalizowana. Dzięki temu mapa będzie dokładniejsza na trasach."
            hint =
                "Możesz to pominąć - pomiary będą działać, ale\u00A0tylko przy otwartej aplikacji."
            primaryText = "Pozwól na lokalizację w tle"
        }

        OnboardingStep.PHONE -> {
            title = "Dostęp do informacji o sieci"
            body =
                "Potrzebujemy dostępu do danych sieci komórkowej, aby pokazywać typ sieci (2G/3G/4G/5G), " +
                        "identyfikatory komórki oraz parametry sygnału. Aplikacja nie ma dostępu do rozmów ani\u00A0SMS."
            hint = "Bez tej zgody pokażemy tylko podstawowe informacje."
            primaryText = "Nadaj dostęp"
        }

        OnboardingStep.DONE -> return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Text(
            text = body,
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = hint,
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(2.dp))

        // Główny przycisk: akcja zależy od kroku.
        Button(
            onClick = {
                when (step) {
                    OnboardingStep.LOCATION -> onRequestLocation()
                    OnboardingStep.BACKGROUND -> onRequestBackground()
                    OnboardingStep.PHONE -> onRequestPhone()
                    else -> Unit
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.90f))
        ) {
            Text(primaryText, color = Color.White)
        }

        // Dodatkowy przycisk tylko w kroku BACKGROUND: pozwala pominąć uprawnienie w tle.
        if (step == OnboardingStep.BACKGROUND) {
            OutlinedButton(
                onClick = onSkipBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Pomiń – tylko przy otwartej aplikacji")
            }
        }
    }
}

/**
 * Dekoracyjne tło z siatką kropek.
 * Ma tylko funkcję wizualną (nie przechowuje stanu, nie reaguje na interakcje).
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
