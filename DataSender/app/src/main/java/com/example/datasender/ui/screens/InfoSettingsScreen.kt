package com.example.datasender.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.datasender.ui.components.ShortCodeBadge
import com.example.datasender.viewmodel.MainViewModel

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun InfoSettingsScreen(mainViewModel: MainViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // ShortCode pobierany z ViewModelu (Flow). Null / blank oznacza brak kodu.
    val codeState by mainViewModel.shortCodeFlow.collectAsState(initial = null)
    val code = codeState?.takeIf { it.isNotBlank() }

    // Kolory pomocnicze do kart i przycisków.
    val cardBg = Color.White.copy(alpha = 0.08f)
    val accentA = Color(0xFF1D6CFF)

    // Otwiera systemowe ustawienia tej aplikacji (uprawnienia, powiadomienia itd.).
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // Otwiera klienta poczty z uzupełnionym tematem wiadomości.
    fun sendEmail(subject: String) {
        val uri = Uri.parse(
            "mailto:kl.zdrojewski@gmail.com?subject=${Uri.encode(subject)}"
        )
        val intent = Intent(Intent.ACTION_SENDTO, uri)
        context.startActivity(Intent.createChooser(intent, "Wybierz aplikację e-mail"))
    }

    // Gotowe tematy e-maili.
    val bugSubject = "Błąd w aplikacji mobilnej - SignalMap"
    val deleteSubject = "Prośba o usunięcie danych z serwera - (${code ?: "-"}) - SignalMap"

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Dekoracyjne tło z kropkami.
        DotsBg()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {



            // Informacja o celu projektu.
            InfoCardSpeedDark(
                title = "O projekcie",
                cardBg = cardBg
            ) {
                BodyDark(
                    "To projekt inżynierski, którego celem jest crowdsourcing - zbieranie anonimowych pomiarów od wielu użytkowników, aby tworzyć mapę jakości sieci komórkowej."
                )
                Spacer(Modifier.height(6.dp))
                BodyDark(
                    "Dane pomagają lepiej zrozumieć zasięg, stabilność i\u00A0parametry sieci w różnych miejscach."
                )
            }

            // Mapa projektu.
            InfoCardSpeedDark(
                title = "Mapa projektu",
                cardBg = Color.White.copy(alpha = 0.10f)
            ) {
                Text(
                    text = "Wersja mapy dostępna na komputerze",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "signalmap.com.pl",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Na stronie możesz przeglądać mapę pomiarów i\u00A0analizować dane na większym ekranie.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // LEGENDA JAKOŚCI SYGNAŁU
            InfoCardSpeedDark(
                title = "Legenda jakości sygnału",
                cardBg = cardBg
            ) {
                BodyDark("Zakresy parametrów sieci określające ocenę jakości w aplikacji:")

                Spacer(Modifier.height(8.dp))

                // Nagłówki
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Parametr", Modifier.weight(1.2f), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Text("Świetny", Modifier.weight(1f), color = Color(0xFF4CAF50), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    Text("Dobry", Modifier.weight(1f), color = Color(0xFFFFC107), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    Text("Średni", Modifier.weight(1f), color = Color(0xFFFF9800), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    Text("Słaby", Modifier.weight(1f), color = Color(0xFFF44336), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(0.1f), thickness = 0.5.dp)

                // RSRP row
                QualityRow("RSRP [dBm]", "≥-70", "≥-85", "≥-100", "<-100")
                // RSRQ row
                QualityRow("RSRQ [dB]", "≥-10", "≥-15", "≥-20", "<-20")
                // SINR row
                QualityRow("SINR [dB]", "≥20", "≥13", "≥0", "<0")
            }

            // ShortCode.
            InfoCardSpeedDark(
                title = "Twój identyfikator pomiarów (ShortCode)",
                cardBg = cardBg
            ) {
                BodyDark(
                    "Ten kod pozwala filtrować mapę po Twoich pomiarach. Po wpisaniu ShortCode w filtrze zobaczysz tylko pomiary z Twojego telefonu."
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (code != null) {
                        ShortCodeBadge(code = code)
                    } else {
                        Text(
                            text = "Kod: -",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Prywatność.
            InfoCardSpeedDark(
                title = "Prywatność",
                cardBg = cardBg
            ) {
                BodyDark(
                    "Pomiary są anonimowe - nie zbieramy danych identyfikujących użytkownika. ShortCode służy wyłącznie do filtrowania pomiarów w aplikacji/mapie."
                )
                Spacer(Modifier.height(6.dp))
                BodyDark(
                    "Jeśli chcesz usunąć swoje dane, napisz e-mail na: kl.zdrojewski@gmail.com (podaj ShortCode)."
                )
            }

            // Uprawnienia.
            InfoCardSpeedDark(
                title = "Uprawnienia",
                cardBg = cardBg
            ) {
                BodyDark(
                    "Uprawnienia (lokalizacja, telefon, powiadomienia) możesz zmienić w ustawieniach aplikacji."
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { openAppSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentA.copy(alpha = 0.88f),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("Otwórz ustawienia aplikacji")
                }
            }

            // Kontakt.
            InfoCardSpeedDark(
                title = "Kontakt",
                cardBg = cardBg
            ) {
                BodyDark(
                    "Błędy w aplikacji i sugestie możesz zgłaszać mailowo. W sprawie usunięcia danych również napisz na ten sam adres."
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { sendEmail(bugSubject) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentA.copy(alpha = 0.88f),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Zgłoś błąd")
                    }
                    OutlinedButton(
                        onClick = { sendEmail(deleteSubject) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Usuń dane")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Wiersz tabeli legendy jakości.
 */
@Composable
private fun QualityRow(label: String, v1: String, v2: String, v3: String, v4: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1.3f), color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
        Text(v1, Modifier.weight(1f), color = Color.White.copy(0.9f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        Text(v2, Modifier.weight(1f), color = Color.White.copy(0.9f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        Text(v3, Modifier.weight(1f), color = Color.White.copy(0.9f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        Text(v4, Modifier.weight(1f), color = Color.White.copy(0.9f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoCardSpeedDark(
    title: String,
    cardBg: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.95f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun BodyDark(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.78f),
        style = MaterialTheme.typography.bodyMedium
    )
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