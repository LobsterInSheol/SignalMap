package com.example.datasender.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.NetworkCell
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Info

/**
 * Definicja elementów nawigacji aplikacji.
 *
 * Każdy NavItem opisuje:
 * - trasę nawigacyjną (route),
 * - etykietę tekstową widoczną w UI,
 * - ikonę używaną w dolnym pasku nawigacji.
 *
 * sealed class zapewnia zamknięty zbiór elementów nawigacji,
 * co ułatwia obsługę i eliminuje przypadkowe wartości.
 */
sealed class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {

    // Ekran testu prędkości sieci
    data object SpeedTest :
        NavItem("speedtest", "Test", Icons.Outlined.Speed)

    // Ekran informacji o sygnale i parametrach sieci
    data object Signal :
        NavItem("signal", "Sygnał", Icons.Outlined.NetworkCell)

    // Ekran mapy z aktualną lokalizacją
    data object Map :
        NavItem("map", "Mapa", Icons.Outlined.Map)

    // Ekran informacji i ustawień aplikacji
    data object Info :
        NavItem("info", "Info", Icons.Outlined.Info)

    companion object {
        // Lista wszystkich elementów nawigacji, używana np. w BottomBar.
        val items = listOf(SpeedTest, Signal, Map, Info)
    }
}
