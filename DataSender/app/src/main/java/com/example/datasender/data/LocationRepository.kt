package com.example.datasender.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationRepository(private val app: Application) {

    // Klient Google Fused Location Provider – pobiera lokalizację z GPS/Wi-Fi/Cell, zależnie od dostępności.
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(app)

    // Sprawdza, czy aplikacja ma przyznaną permisję na dokładną lokalizację (FINE).
    private fun hasLocationPerms(): Boolean =
        ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Pobiera jednorazowo aktualną lokalizację.
     * Zwraca null gdy:
     * - brak uprawnień lokalizacji,
     * - usługi lokalizacji zwrócą błąd / nie dadzą wyniku.
     *
     * Uwaga: adnotacja MissingPermission jest świadoma – tu perms są sprawdzane ręcznie.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { cont ->
        if (!hasLocationPerms()) {
            cont.resume(null); return@suspendCancellableCoroutine
        }

        // Żądamy aktualnej, możliwie świeżej lokalizacji (HIGH_ACCURACY, bez akceptowania „starego” wyniku).
        val req = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .build()

        fusedClient.getCurrentLocation(req, null)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }

    /**
     * Strumień (Flow) z aktualizacjami lokalizacji.
     * - jeśli brak uprawnień: emituje null i kończy flow (close)
     * - w przeciwnym razie: emituje ostatnią znaną lokalizację z każdego wyniku (lastLocation)
     *
     * minDistanceMeters: minimalny dystans zmiany pozycji wymagany do aktualizacji
     * intervalMillis: interwał próbkowania/aktualizacji w ms
     */
    @SuppressLint("MissingPermission")
    fun locationFlow(
        minDistanceMeters: Float,
        intervalMillis: Long
    ): Flow<Location?> = callbackFlow {
        if (!hasLocationPerms()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .build()

        // Callback z FusedLocationProvider – na każdą paczkę wyników emitujemy ostatnią lokalizację.
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                trySend(result.lastLocation)
            }
        }

        // Rejestracja aktualizacji na wątku głównym (wymagane przez API FusedLocationProvider).
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        // Gdy flow zostanie anulowane/zamknięte – usuwamy aktualizacje, żeby nie było wycieków i drainu baterii.
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }
}
