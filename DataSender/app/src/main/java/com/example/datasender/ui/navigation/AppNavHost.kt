package com.example.datasender.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.datasender.ui.screens.InfoSettingsScreen
import com.example.datasender.ui.screens.MapScreen
import com.example.datasender.ui.screens.SignalScreen
import com.example.datasender.ui.screens.SpeedTestScreen
import com.example.datasender.viewmodel.MainViewModel
import com.example.datasender.viewmodel.SpeedTestViewModel

/**
 * Główny host nawigacji aplikacji.
 *
 * Definiuje:
 * - dostępne ekrany (destinations),
 * - trasy (routes),
 * - powiązanie tras z konkretnymi composable screenami.
 *
 * ViewModel-e są przekazywane jawnie, bez użycia Hilt/Koin,
 * co upraszcza przepływ danych i ułatwia kontrolę cyklu życia.
 */
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun AppNavHost(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    speedTestViewModel: SpeedTestViewModel
) {

    // Kontener nawigacji Compose.
    // startDestination określa ekran startowy po uruchomieniu aplikacji.
    NavHost(
        navController = navController,
        startDestination = NavItem.SpeedTest.route
    ) {

        // Ekran testu prędkości sieci.
        composable(NavItem.SpeedTest.route) {
            SpeedTestScreen(
                mainVm = mainViewModel,
                vm = speedTestViewModel
            )
        }

        // Ekran informacji o sygnale i sieci.
        composable(NavItem.Signal.route) {
            SignalScreen(viewModel = mainViewModel)
        }

        // Ekran mapy z aktualną lokalizacją.
        composable(NavItem.Map.route) {
            MapScreen(mainViewModel = mainViewModel)
        }

        // Ekran informacji i ustawień aplikacji.
        composable(NavItem.Info.route) {
            InfoSettingsScreen(mainViewModel = mainViewModel)
        }
    }
}
