package com.example.datasender

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.datasender.OnboardingStep
import com.example.datasender.service.LocationService
import com.example.datasender.ui.components.BottomBar
import com.example.datasender.ui.navigation.AppNavHost
import com.example.datasender.ui.navigation.NavItem
import com.example.datasender.ui.screens.PermissionsOnboardingScreen
import com.example.datasender.ui.theme.DataSenderTheme
import com.example.datasender.viewmodel.MainViewModel
import com.example.datasender.viewmodel.SpeedTestViewModel

class MainActivity : ComponentActivity() {

    // ViewModele (jeden do sygnału/mapy, drugi do speedtestu)
    private val vm: MainViewModel by viewModels()
    private val speedTestVm: SpeedTestViewModel by viewModels()

    // Jeśli Activity została otwarta z notyfikacji, możemy dostać “nav_target” (np. "signal")
    private var navTarget by mutableStateOf<String?>(null)

    private val topBarHeight = 62.dp

    // Aktualny krok onboardingu uprawnień
    private var onboardingStep by mutableStateOf(OnboardingStep.DONE)

    /**
     * Odbiornik broadcastu z serwisu (gdy użytkownik kliknie "Stop" w notyfikacji).
     * Serwis wysyła ACTION_STOPPED_BROADCAST, a my aktualizujemy UI i domykamy service.
     */
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == LocationService.ACTION_STOPPED_BROADCAST) {
                Log.d("MainActivity", "STOP broadcast received -> vm.stop() + stopService()")
                vm.stop()
                stopLocationService()
            }
        }
    }

    /* ---------------- Permissions helpers ---------------- */

    private fun hasPermission(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun hasAnyLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun hasBackgroundLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun hasPhoneStatePermission(): Boolean =
        hasPermission(Manifest.permission.READ_PHONE_STATE)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun hasPostNotificationsPermission(): Boolean =
        hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    /**
     * Wyznacza, jaki ekran onboardingu pokazać:
     * 1) brak lokalizacji -> LOCATION
     * 2) jest lokalizacja, ale brak tła (Android Q+) -> BACKGROUND
     * 3) brak READ_PHONE_STATE -> PHONE
     * 4) wszystko jest -> DONE
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun updateOnboardingStep() {
        val haveLoc = hasAnyLocationPermission()
        val haveBg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasBackgroundLocationPermission()
        } else true
        val havePhone = hasPhoneStatePermission()

        onboardingStep = when {
            !haveLoc -> OnboardingStep.LOCATION
            haveLoc && !haveBg && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> OnboardingStep.BACKGROUND
            !havePhone -> OnboardingStep.PHONE
            else -> OnboardingStep.DONE
        }
    }



    @RequiresApi(Build.VERSION_CODES.Q)
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Nie analizujemy mapy wyników; po prostu sprawdzamy stan po nadaniu/odmowie
        updateOnboardingStep()
    }

    private val backgroundLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Brak lokalizacji w tle – dane będą zbierane tylko przy otwartej aplikacji.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "Lokalizacja w tle przyznana", Toast.LENGTH_SHORT).show()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            updateOnboardingStep()
        }
    }

    /* ---------------- Lifecycle ---------------- */

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        // Rejestrujemy odbiornik STOP z serwisu (klik "Stop" w notyfikacji)
        val filter = IntentFilter(LocationService.ACTION_STOPPED_BROADCAST)
        if (Build.VERSION.SDK_INT >= 34) registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(stopReceiver, filter)

        // Czy przyszliśmy z notyfikacji? (np. nav_target="signal")
        navTarget = intent?.getStringExtra("nav_target")

        // Jeśli to świeży start "normalny" (nie z notyfikacji), to pokazujemy intro (czyli isCollecting=false)
        val fromNotification = navTarget == "signal"
        if (savedInstanceState == null && !fromNotification) {
            vm.stop()
        }

        // Wyznaczamy startowy krok onboardingu
        updateOnboardingStep()

        setContent {
            DataSenderTheme {
                val navController = rememberNavController()

                // Jeśli przyszliśmy z notyfikacji, przejdź na odpowiednią zakładkę
                val target = navTarget
                LaunchedEffect(target) {
                    if (target == "signal") {
                        navController.navigate(NavItem.Signal.route) { launchSingleTop = true }
                        navTarget = null // żeby nie “nadpisywać” późniejszych klików usera
                    }
                }

                // 1) Onboarding uprawnień
                if (onboardingStep != OnboardingStep.DONE) {
                    PermissionsOnboardingScreen(
                        step = onboardingStep,
                        onRequestLocation = {
                            val perms = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            // Android 13+ (opcjonalnie prosimy od razu o notyfikacje)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !hasPostNotificationsPermission()
                            ) {
                                perms += Manifest.permission.POST_NOTIFICATIONS
                            }
                            permissionsLauncher.launch(perms.toTypedArray())
                        },
                        onRequestBackground = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        },
                        onSkipBackground = {
                            // user świadomie pomija lokalizację w tle -> idziemy dalej do PHONE/DONE
                            onboardingStep = if (!hasPhoneStatePermission()) OnboardingStep.PHONE else OnboardingStep.DONE
                        },
                        onRequestPhone = {
                            permissionsLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
                        }
                    )
                } else {
                    // 2) Normalna aplikacja
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(com.example.datasender.ui.theme.SpeedBgBrush)
                    ) {
                        Scaffold(
                            containerColor = Color.Transparent,
                            contentWindowInsets = WindowInsets(0),
                            topBar = {
                                val backStackEntry by navController.currentBackStackEntryAsState()
                                val isMap = backStackEntry?.destination
                                    ?.hierarchy
                                    ?.any { it.route == NavItem.Map.route } == true

                                if (!isMap) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .windowInsetsPadding(WindowInsets.statusBars)
                                            .height(topBarHeight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.signal_7_1),
                                            contentDescription = null,
                                            modifier = Modifier.height(topBarHeight)
                                        )
                                    }
                                }
                            },
                            bottomBar = { BottomBar(navController) }
                        ) { innerPadding ->


                            val backStackEntry by navController.currentBackStackEntryAsState()
                            val isMap = backStackEntry?.destination
                                ?.hierarchy
                                ?.any { it.route == NavItem.Map.route } == true

                            val mapTopPadding =
                                if (isMap) WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                                else 0.dp

                            Box(
                                Modifier
                                    .padding(innerPadding)
                                    .padding(top = mapTopPadding)
                            ) {
                                AppNavHost(
                                    navController = navController,
                                    mainViewModel = vm,
                                    speedTestViewModel = speedTestVm
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Gdy Activity już jest i user kliknie notyfikację jeszcze raz,
     * Android woła onNewIntent() – aktualizujemy navTarget.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navTarget = intent.getStringExtra("nav_target")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: IllegalArgumentException) {
            // już wyrejestrowany
        }
        super.onDestroy()
    }


    // Używane przez stopReceiver (gdy user kliknie Stop w notyfikacji)
    private fun stopLocationService() {
        stopService(Intent(this, LocationService::class.java))
    }
}
