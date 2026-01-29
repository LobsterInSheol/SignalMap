package com.example.datasender.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * Jedna instancja DataStore na Context.
 * Przechowuje proste ustawienia aplikacji (Preferences DataStore).
 */
val Context.appDataStore by preferencesDataStore(name = "app_prefs")

/**
 * Warstwa dostępu do ustawień zapisanych w DataStore.
 * Trzyma m.in. ShortCode (identyfikator pomiarów) oraz flagę stanu zbierania.
 */
class SettingsDataStore(private val context: Context) {

    // Klucz: identyfikator pomiarów użytkownika (ShortCode).
    private val keyShortCode = stringPreferencesKey("short_code")

    // Klucz: informacja, czy aplikacja jest w trybie zbierania danych.
    private val keyIsCollecting = booleanPreferencesKey("is_collecting")

    /**
     * Strumień aktualnej wartości ShortCode.
     * Zwraca null, jeśli kod nie został jeszcze zapisany.
     */
    val shortCodeFlow = context.appDataStore.data.map { prefs ->
        prefs[keyShortCode]
    }

    /**
     * Strumień informujący, czy zbieranie jest aktywne.
     * Jeśli brak wartości w DataStore, domyślnie false.
     */
    val isCollectingFlow = context.appDataStore.data.map { prefs ->
        prefs[keyIsCollecting] ?: false
    }

    /**
     * Zapisuje ShortCode do DataStore (nadpisuje poprzednią wartość).
     */
    suspend fun saveShortCode(shortCode: String) {
        context.appDataStore.edit { prefs ->
            prefs[keyShortCode] = shortCode
        }
    }

    /**
     * Ustawia flagę zbierania danych (np. start/stop pomiarów).
     */
    suspend fun setCollecting(value: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[keyIsCollecting] = value
        }
    }

    /**
     * Jednorazowy odczyt ShortCode (pobiera pierwszą dostępną wartość ze strumienia).
     */
    suspend fun getShortCode(): String? = shortCodeFlow.firstOrNull()
}
