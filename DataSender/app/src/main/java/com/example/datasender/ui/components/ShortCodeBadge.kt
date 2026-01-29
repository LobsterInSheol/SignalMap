package com.example.datasender.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * Mała „odznaka” wyświetlająca kod rejestracyjny użytkownika.
 *
 * Komponent:
 * - pokazuje kod w czytelnej formie,
 * - pozwala skopiować go do schowka jednym kliknięciem,
 * - nie przechowuje żadnego stanu aplikacji.
 */
@Composable
fun ShortCodeBadge(
    code: String,
    modifier: Modifier = Modifier
) {
    // Dostęp do systemowego schowka.
    val clipboard = LocalClipboardManager.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),   // zaokrąglone rogi „badge”
        color = Color.Transparent,           // przezroczyste tło
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.35f) // subtelna, półprzezroczysta ramka
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tekst z kodem przekazywanym z zewnątrz.
            Text(
                text = "Twój kod: $code",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )

            Spacer(Modifier.width(10.dp))

            // Przycisk kopiowania kodu do schowka.
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Kopiuj kod",
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}
