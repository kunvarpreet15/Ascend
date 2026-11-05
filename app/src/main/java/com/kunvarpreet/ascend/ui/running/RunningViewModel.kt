package com.kunvarpreet.ascend.ui.running

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kunvarpreet.ascend.data.repository.QuestRepository
import com.kunvarpreet.ascend.domain.model.ExerciseTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class RunningUiState(
    val target: ExerciseTarget? = null,
    val isGpsMode: Boolean = false, // false = Indoor Treadmill, true = Outdoor GPS
    val isRunning: Boolean = false,
    val elapsedSeconds: Int = 0,
    val sessionMeters: Float = 0f,
    val caloriesBurned: Int = 0,
    val paceFormatted: String = "--:--",
    val hasLocationPermission: Boolean = false,
    val isLoading: Boolean = false,
    val gpsPointsCount: Int = 0
)

class RunningViewModel(application: Application) : AndroidViewModel(application) {
    private val questRepo = QuestRepository(application)

    private val _uiState = MutableStateFlow(RunningUiState(isLoading = true))
    val uiState: StateFlow<RunningUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private val pathPoints = mutableListOf<Pair<Double, Double>>()

    fun loadTarget(targetId: String) {
        viewModelScope.launch {
            questRepo.todayQuest.collect { quest ->
                val target = quest?.targets?.find { it.id == targetId }
                _uiState.update { it.copy(target = target, isLoading = false) }
            }
        }
    }

    fun setMode(isGpsMode: Boolean) {
        _uiState.update { it.copy(isGpsMode = isGpsMode) }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
    }

    fun startRun() {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(isRunning = true) }
        timerJob = viewModelScope.launch {
            while (_uiState.value.isRunning) {
                delay(1000L)
                val newElapsed = _uiState.value.elapsedSeconds + 1
                val meters = _uiState.value.sessionMeters
                val paceStr = calculatePaceString(meters, newElapsed)
                val calories = (meters * 0.065f).toInt()
                _uiState.update {
                    it.copy(
                        elapsedSeconds = newElapsed,
                        paceFormatted = paceStr,
                        caloriesBurned = calories
                    )
                }
            }
        }
    }

    fun pauseRun() {
        _uiState.update { it.copy(isRunning = false) }
        timerJob?.cancel()
        timerJob = null
    }

    fun addIndoorDistance(meters: Float) {
        val newMeters = _uiState.value.sessionMeters + meters
        val elapsed = _uiState.value.elapsedSeconds
        val paceStr = calculatePaceString(newMeters, elapsed)
        val calories = (newMeters * 0.065f).toInt()
        _uiState.update {
            it.copy(
                sessionMeters = newMeters,
                paceFormatted = paceStr,
                caloriesBurned = calories
            )
        }
    }

    fun onLocationUpdate(lat: Double, lng: Double) {
        if (!_uiState.value.isRunning || !_uiState.value.isGpsMode) return

        if (pathPoints.isNotEmpty()) {
            val last = pathPoints.last()
            val dist = haversineDistanceMeters(last.first, last.second, lat, lng).toFloat()
            // Filter realistic movements (e.g., delta > 1.5m and delta < 60m per second)
            if (dist in 1.5f..60f) {
                pathPoints.add(Pair(lat, lng))
                val newMeters = _uiState.value.sessionMeters + dist
                val elapsed = _uiState.value.elapsedSeconds
                val paceStr = calculatePaceString(newMeters, elapsed)
                val calories = (newMeters * 0.065f).toInt()
                _uiState.update {
                    it.copy(
                        sessionMeters = newMeters,
                        paceFormatted = paceStr,
                        caloriesBurned = calories,
                        gpsPointsCount = pathPoints.size
                    )
                }
            }
        } else {
            pathPoints.add(Pair(lat, lng))
            _uiState.update { it.copy(gpsPointsCount = 1) }
        }
    }

    fun finishRun(onFinished: () -> Unit) {
        pauseRun()
        val currentState = _uiState.value
        val target = currentState.target
        val meters = currentState.sessionMeters
        val elapsed = currentState.elapsedSeconds

        if (target != null && meters > 0f) {
            val avgPaceSec = if (meters > 0) elapsed / (meters / 1000f) else 0f
            val calories = (meters * 0.065f).toInt()
            val geoJson = buildGeoJson(pathPoints)

            questRepo.logRepetitionProgress(target.id, meters)
            questRepo.logRunGps(
                targetId = target.id,
                distanceMeters = meters,
                durationSeconds = elapsed,
                avgPaceSecPerKm = avgPaceSec,
                caloriesBurned = calories,
                gpsPathGeoJson = geoJson
            )
        }
        onFinished()
    }

    private fun calculatePaceString(meters: Float, seconds: Int): String {
        if (meters < 50f || seconds < 5) return "--:--"
        val km = meters / 1000f
        val secPerKm = (seconds / km).toInt()
        val paceMin = secPerKm / 60
        val paceSec = secPerKm % 60
        return if (paceMin in 2..29) String.format(Locale.getDefault(), "%02d:%02d", paceMin, paceSec) else "--:--"
    }

    private fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun buildGeoJson(points: List<Pair<Double, Double>>): String {
        if (points.isEmpty()) return "[]"
        return points.joinToString(separator = ",", prefix = "[", postfix = "]") { (lat, lng) ->
            "{\"lat\":$lat,\"lng\":$lng}"
        }
    }
}
