package com.example.datasender.service

import android.location.Location

internal class SamplingPolicy {

    enum class MovementMode { STATIC, WALKING, DRIVING }

    data class Decision(
        val mode: MovementMode,
        val sendIntervalMs: Long,
        val shouldSend: Boolean
    )

    // Mechanizm stabilizacji na starcie
    private val policyStartTime: Long = System.currentTimeMillis()
    private val STABILIZATION_MS = 5000L
    private var movementMode: MovementMode = MovementMode.STATIC
    private var lastModeChange: Long = 0L

    private var lastLocationForMovement: Location? = null
    private var lastCellId: Long? = null

    private var lastSentLocation: Location? = null
    private var lastSentTime: Long = 0L
    private var lastSentCellId: Long? = null

    fun decide(
        loc: Location,
        cellId: Long?,
        nowMs: Long = System.currentTimeMillis()
    ): Decision {

        // 1. Blokada startowa (Eliminuje "3 próbki na start")
        if (nowMs - policyStartTime < STABILIZATION_MS) {
            return Decision(MovementMode.STATIC, 1000L, false)
        }

        val distanceSinceLastMeasure = lastLocationForMovement?.distanceTo(loc) ?: Float.MAX_VALUE
        val distanceSinceLastSent = lastSentLocation?.distanceTo(loc) ?: Float.MAX_VALUE
        val speed = loc.speed
        val cellChanged = cellId?.let { lastCellId == null || it != lastCellId } ?: false

        val timeSinceLastSent = if (lastSentTime == 0L) Long.MAX_VALUE else nowMs - lastSentTime
        val cellChangedSinceLastSent = cellId?.let {
            lastSentCellId == null || it != lastSentCellId
        } ?: false

        // Wybór trybu ruchu
        val candidateMode = when {
            speed > 8f || distanceSinceLastMeasure > 300f -> MovementMode.DRIVING
            speed > 1.2f || distanceSinceLastMeasure > 30f || cellChanged -> MovementMode.WALKING
            else -> MovementMode.STATIC
        }

        // Histereza zmiany trybu
        val hysteresisMs = when (candidateMode) {
            MovementMode.DRIVING -> 5_000L
            MovementMode.WALKING -> 10_000L
            MovementMode.STATIC  -> 20_000L
        }

        if (candidateMode != movementMode) {
            if (nowMs - lastModeChange >= hysteresisMs) {
                movementMode = candidateMode
                lastModeChange = nowMs
            }
        } else if (lastModeChange == 0L) {
            lastModeChange = nowMs
        }

        val sendIntervalMs = when (movementMode) {
            MovementMode.STATIC  -> 60_000L
            MovementMode.WALKING -> 15_000L
            MovementMode.DRIVING -> 3_000L
        }

        // 2. FILTR NADMIAROWOŚCI (Naprawa "pływania GPS")
        val shouldSend = if (movementMode == MovementMode.STATIC) {
            val maxIntervalMs = 3 * 60_000L // Max co 3 minuty w bezruchu
            cellChangedSinceLastSent || timeSinceLastSent >= maxIntervalMs
        } else {
            // W ruchu (WALKING/DRIVING) wyślij tylko jeśli:
            // - minął czas interwału ORAZ przemieściliśmy się o min. 10 metrów (odfiltrowanie szumu)
            // - LUB zmieniła się stacja bazowa (ważne dla mapy zasięgu)
            val movedEnough = distanceSinceLastSent > 10f
            cellChangedSinceLastSent || (timeSinceLastSent >= sendIntervalMs && movedEnough)
        }

        // aktualizacja "last measured"
        lastLocationForMovement = loc
        lastCellId = cellId

        return Decision(movementMode, sendIntervalMs, shouldSend)
    }

    fun markSent(loc: Location, cellId: Long?, nowMs: Long = System.currentTimeMillis()) {
        lastSentLocation = loc
        lastSentCellId = cellId
        lastSentTime = nowMs
    }
}
