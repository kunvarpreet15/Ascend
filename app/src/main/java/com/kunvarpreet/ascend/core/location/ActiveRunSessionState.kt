package com.kunvarpreet.ascend.core.location

import java.util.Locale

enum class RunState {
    IDLE,
    RUNNING,
    PAUSED
}

data class LatLng(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class ActiveRunSessionState(
    val state: RunState = RunState.IDLE,
    val targetId: String = "",
    val sessionId: String = "",
    val elapsedSeconds: Int = 0,
    val distanceMeters: Float = 0f,
    val currentPaceSecPerKm: Float = 0f,
    val caloriesBurned: Int = 0,
    val waypoints: List<LatLng> = emptyList(),
    val targetDistanceMeters: Float = 3000f
) {
    val isRunning: Boolean get() = state == RunState.RUNNING
    val isPaused: Boolean get() = state == RunState.PAUSED
    val isIdle: Boolean get() = state == RunState.IDLE

    val progressFraction: Float
        get() = if (targetDistanceMeters > 0f) {
            (distanceMeters / targetDistanceMeters).coerceIn(0f, 1f)
        } else {
            0f
        }

    val paceFormatted: String
        get() {
            if (distanceMeters < 50f || elapsedSeconds < 5 || currentPaceSecPerKm <= 0f) {
                return "--'--\" /km"
            }
            val totalSec = currentPaceSecPerKm.toInt()
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format(Locale.US, "%d'%02d\" /km", min, sec)
        }

    val durationFormatted: String
        get() {
            val min = elapsedSeconds / 60
            val sec = elapsedSeconds % 60
            return String.format(Locale.US, "%02d:%02d", min, sec)
        }

    val distanceKmFormatted: String
        get() = String.format(Locale.US, "%.2f / %.2f km", distanceMeters / 1000f, targetDistanceMeters / 1000f)
}
