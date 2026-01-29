package com.example.datasender.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
/**
 * WebView do wyświetlania mapy (strona WWW) wewnątrz aplikacji.
 *
 * Dodatkowo po załadowaniu strony wstrzykuje JS/CSS, żeby naprawić problemy WebView
 * z 100vh/100vw, overlayami i panelami na urządzeniach mobilnych.
 */
@Composable
fun SignalMapWebView(
    url: String,
    modifier: Modifier = Modifier
) {
    // Stan kontrolujący widoczność, aby uniknąć białego błysku przed wyrenderowaniem mapy.
    var isLoaded by remember { mutableStateOf(false) }

    // Box z tłem dopasowanym do motywu mapy (np. ciemny szary),
    // który będzie widoczny zamiast białego ekranu.
    Box(modifier = modifier.background(androidx.compose.ui.graphics.Color(0xFF121212))) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                // Ukrywamy WebView (alfa 0), dopóki onPageFinished nie potwierdzi gotowości.
                .graphicsLayer(alpha = if (isLoaded) 1f else 0f),

            // factory tworzy WebView tylko raz (przy pierwszej kompozycji).
            factory = { context ->
                WebView(context).apply {

                    // Kluczowe: ustawienie przezroczystości tła samego komponentu WebView.
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    // Wymagane przez mapę/web-app (JS + local storage).
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    // WebViewClient przechwytuje zdarzenia ładowania strony.
                    // onPageFinished = moment, kiedy DOM jest gotowy do wstrzyknięcia poprawek.
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)

                            // Wstrzyknięcie JS:
                            // - ustawia zmienne CSS --vh/--vw na podstawie realnego viewportu WebView,
                            // - dokleja style naprawiające layout (100vh, modale, dropdowny, panele szczegółów).
                            view?.evaluateJavascript(VIEWPORT_FIX_JS, null)

                            // Ustawienie flagi na true powoduje płynne pokazanie załadowanego WebView.
                            isLoaded = true
                        }
                    }

                    // Pierwsze załadowanie strony.
                    loadUrl(url)
                }
            },

            // update wywołuje się przy recomposition; jeśli url się zmienił, ładujemy nowy.
            update = { webView ->
                if (webView.url != url) {
                    isLoaded = false
                    webView.loadUrl(url)
                }
            }
        )
    }
}

/**
 * Skrypt naprawiający problemy z viewportem w Android WebView.
 *
 * Dlaczego to istnieje:
 * - na mobile 100vh/100vw często nie odpowiada realnemu obszarowi (pasek adresu / system UI),
 * - overlaye i panele w web-app mogą się „ucinać” lub mieć złą wysokość,
 * - ten skrypt ustawia zmienne CSS i dokleja style dostosowujące layout.
 *
 * Uwaga: skrypt jest wstrzykiwany po onPageFinished.
 */
private const val VIEWPORT_FIX_JS = """
(function(){
  try {
    // Oblicza realne vh/vw i zapisuje jako zmienne CSS (--vh, --vw).
    function updateViewportVars() {
      var vh = window.innerHeight * 0.01;
      var vw = window.innerWidth * 0.01;
      document.documentElement.style.setProperty('--vh', vh + 'px');
      document.documentElement.style.setProperty('--vw', vw + 'px');
    }

    // Inicjalizacja po wejściu na stronę.
    updateViewportVars();

    // Doklejenie CSS wymuszającego poprawne wysokości i zachowanie overlay/paneli.
    var style = document.createElement('style');
    style.textContent = `
      html, body {
        height: 100% !important;
        width: 100% !important;
      }
      .app {
        height: calc(var(--vh, 1vh) * 100) !important;
        width: 100% !important;
      }
      .app-content {
        height: calc(var(--vh, 1vh) * 100 - 64px) !important;
      }
      .map-container {
        height: calc(var(--vh, 1vh) * 100 - 64px) !important;
      }
      .sidebar {
        max-height: calc(var(--vh, 1vh) * 100 - 64px) !important;
        overflow-y: auto !important;
      }

      /* Modale / overlaye */
      .modal-content {
        max-height: calc(var(--vh, 1vh) * 80) !important;
        min-width: 280px !important;
        width: auto !important;
      }

      .dropdown-menu {
        max-height: calc(var(--vh, 1vh) * 70 - 60px) !important;
        min-width: 280px !important;
        max-width: 90vw !important;
      }

      .dropdown-content {
        max-height: calc(var(--vh, 1vh) * 70 - 60px) !important;
        overflow-y: auto !important;
      }

      /* Panel szczegółów na mobile */
      .map-view {
        position: relative !important;
        width: 100% !important;
        height: 100% !important;
      }

      .details-panel {
        position: fixed !important;
        bottom: 0 !important;
        left: 0 !important;
        right: 0 !important;
        width: 100% !important;
        max-height: calc(var(--vh, 1vh) * 60) !important;
        top: auto !important;
        border-radius: 12px 12px 0 0 !important;
        overflow-y: auto !important;
        z-index: 10 !important;
        padding: var(--spacing-lg) !important;
        box-sizing: border-box !important;
      }

      .bts-details {
        position: fixed !important;
        bottom: 0 !important;
        left: 0 !important;
        right: 0 !important;
        width: 100% !important;
        max-height: calc(var(--vh, 1vh) * 60) !important;
        z-index: 11 !important;
        border-radius: 12px 12px 0 0 !important;
        overflow-y: auto !important;
        padding: var(--spacing-lg) !important;
        box-sizing: border-box !important;
      }

      .mobile-handle {
        display: block !important;
        width: 40px !important;
        height: 4px !important;
        background: var(--border-color) !important;
        border-radius: 2px !important;
        margin: 0 auto var(--spacing-sm) !important;
        opacity: 0.5 !important;
      }

      .details-panel h3 {
        margin: 0 0 var(--spacing-lg) 0 !important;
        font-size: 18px !important;
      }

      .close-details {
        position: absolute !important;
        top: var(--spacing-md) !important;
        right: var(--spacing-md) !important;
        z-index: 100 !important;
      }

      .modal-overlay {
        position: fixed !important;
        top: 0 !important;
        left: 0 !important;
        width: 100% !important;
        height: 100% !important;
        z-index: 1000 !important;
      }

      @media (max-width: 768px) {
        body, html { height: 100vh; width: 100vw; }
      }
    `;
    document.head.appendChild(style);

    // Aktualizacja przy zmianie rozmiaru / orientacji.
    window.addEventListener('resize', updateViewportVars);
    window.addEventListener('orientationchange', function(){
      setTimeout(updateViewportVars, 100);
    });

  } catch(e) {
    console.log('Viewport fix error:', e.message);
  }
})();
"""
