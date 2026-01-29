package com.example.datasender.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.datasender.viewmodel.MainViewModel

@Composable
fun MapScreen(mainViewModel: MainViewModel) {

    // ShortCode (jeśli istnieje) jest pobierany z Flow w ViewModelu.
    // null/blank oznacza brak identyfikatora użytkownika.
    val codeState by mainViewModel.shortCodeFlow.collectAsState(initial = null)
    val code: String? = codeState

    // Bazowy adres strony z mapą.
    val baseUrl = "https://signalmap.com.pl"

    // Jeśli kod istnieje, dopinamy go jako parametr URL (ułatwia filtrowanie mapy po pomiarach użytkownika).
    val url = if (!code.isNullOrBlank()) {
        "$baseUrl/?code=$code"
    } else {
        baseUrl
    }

    Box(Modifier.fillMaxSize()) {
        // WebView wypełnia cały ekran i renderuje mapę jako stronę WWW.
        SignalMapWebView(
            url = url,
            modifier = Modifier.fillMaxSize()
        )
    }
}
