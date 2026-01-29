package com.example.datasender.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.datasender.ui.navigation.NavItem

/**
 * Dolny pasek nawigacyjny aplikacji (Bottom Navigation).
 *
 * Odpowiada wyłącznie za:
 * - renderowanie przycisków,
 * - reagowanie na zmianę aktualnej trasy,
 * - wywoływanie nawigacji po kliknięciu.
 *
 * Logika ekranów i stanu jest poza tym komponentem.
 */
@Composable
fun BottomBar(navController: NavController) {


    NavigationBar(
        containerColor = Color.Transparent,
        tonalElevation = 0.dp
    ) {

        // Obserwujemy aktualny wpis w back stacku nawigacji.
        // Dzięki temu komponent się recomposuje, gdy zmieni się ekran.
        val backStackEntry by navController.currentBackStackEntryAsState()

        // Aktualna trasa (route) – używana do zaznaczenia aktywnej zakładki.
        val currentRoute = backStackEntry?.destination?.route

        // Iterujemy po zdefiniowanych elementach nawigacji (ikona + trasa + etykieta).
        NavItem.items.forEach { item ->

            NavigationBarItem(
                // Element jest zaznaczony, jeśli jego trasa = aktualna trasa.
                selected = currentRoute == item.route,

                // Po kliknięciu nawigujemy do docelowej trasy.
                // launchSingleTop zapobiega tworzeniu wielu instancji tego samego ekranu.
                onClick = {
                    navController.navigate(item.route) {
                        launchSingleTop = true
                    }
                },

                // Ikona elementu nawigacji.
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = item.label
                    )
                },

                // Tekst pod ikoną.
                label = {
                    Text(item.label)
                },

                // Etykieta widoczna tylko dla zaznaczonego elementu.
                alwaysShowLabel = false,


                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.White.copy(alpha = 0.10f),
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color.White.copy(alpha = 0.70f),
                    unselectedTextColor = Color.White.copy(alpha = 0.70f)
                )
            )
        }
    }
}
