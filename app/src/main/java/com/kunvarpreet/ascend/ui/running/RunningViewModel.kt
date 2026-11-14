package com.kunvarpreet.ascend.ui.running

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kunvarpreet.ascend.core.location.RunState
import com.kunvarpreet.ascend.core.location.RunTrackingService
import com.kunvarpreet.ascend.data.repository.QuestRepository
import com.kunvarpreet.ascend.domain.model.ExerciseTarget
import com.kunvarpreet.ascend.domain.model.QuestRewardEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class RunningUiState(
    val target: ExerciseTarget? = null,
    val isGpsMode: Boolean = false, // false = Indoor Treadmill, true = Outdoor GPS
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSeconds: Int = 0,
    val sessionMeters: Float = 0f,
    val caloriesBurned: Int = 0,
    val paceFormatted: String = "--'--\" /km",
    val hasLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isLoading: Boolean = false,
    val gpsPointsCount: Int = 0
)

class RunningViewModel(application: Application) : AndroidViewModel(application) {
    private val questRepo = QuestRepository(application)

    private val _uiState = MutableStateFlow(RunningUiState(isLoading = true))
    val uiState: StateFlow<RunningUiState> = _uiState.asStateFlow()

    val rewardEvent: StateFlow<QuestRewardEvent?> = questRepo.rewardEvent

    fun dismissRewardEvent() {
        questRepo.dismissRewardEvent()
    }

    private var indoorTimerJob: Job? = null

    init {
        // Collect and sync live state from Foreground Service in Outdoor GPS mode
        viewModelScope.launch {
            RunTrackingService.sessionState.collect { gpsSession ->
                if (_uiState.value.isGpsMode) {
                    when (gpsSession.state) {
                        RunState.RUNNING -> {
                            _uiState.update { current ->
                                current.copy(
                                    isRunning = true,
                                    isPaused = false,
                                    elapsedSeconds = gpsSession.elapsedSeconds,
                                    sessionMeters = gpsSession.distanceMeters,
                                    caloriesBurned = gpsSession.caloriesBurned,
                                    paceFormatted = gpsSession.paceFormatted,
                                    gpsPointsCount = gpsSession.waypoints.size
                                )
                            }
                        }
                        RunState.PAUSED -> {
                            _uiState.update { current ->
                                current.copy(
                                    isRunning = false,
                                    isPaused = true,
                                    elapsedSeconds = gpsSession.elapsedSeconds,
                                    sessionMeters = gpsSession.distanceMeters,
                                    caloriesBurned = gpsSession.caloriesBurned,
                                    paceFormatted = gpsSession.paceFormatted,
                                    gpsPointsCount = gpsSession.waypoints.size
                                )
                            }
                        }
                        RunState.IDLE -> {
                            if (_uiState.value.isRunning || _uiState.value.isPaused) {
                                _uiState.update { current ->
                                    current.copy(
                                        isRunning = false,
                                        isPaused = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun loadTarget(targetId: String) {
        viewModelScope.launch {
            questRepo.todayQuest.collect { quest ->
                val target = quest?.targets?.find { it.id == targetId }
                _uiState.update { it.copy(target = target, isLoading = false) }
            }
        }
    }

    fun setMode(isGpsMode: Boolean) {
        if (_uiState.value.isRunning) return // Prevent mode change during active run
        _uiState.update { it.copy(isGpsMode = isGpsMode) }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
    }

    fun setNotificationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasNotificationPermission = granted) }
    }

    fun startRun() {
        val current = _uiState.value
        if (current.isGpsMode) {
            val target = current.target
            val targetDistance = target?.targetQuantity ?: 3000f
            val targetId = target?.id ?: ""
            if (current.isPaused) {
                RunTrackingService.resumeService(getApplication())
            } else {
                RunTrackingService.startService(getApplication(), targetId, targetDistance)
            }
            _uiState.update { it.copy(isRunning = true, isPaused = false) }
        } else {
            // Indoor Treadmill mode
            if (current.isRunning) return
            _uiState.update { it.copy(isRunning = true, isPaused = false) }
            startIndoorTimer()
        }
    }

    fun pauseRun() {
        val current = _uiState.value
        if (current.isGpsMode) {
            RunTrackingService.pauseService(getApplication())
            _uiState.update { it.copy(isRunning = false, isPaused = true) }
        } else {
            _uiState.update { it.copy(isRunning = false, isPaused = true) }
            indoorTimerJob?.cancel()
            indoorTimerJob = null
        }
    }

    private fun startIndoorTimer() {
        indoorTimerJob?.cancel()
        indoorTimerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRunning) {
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

    fun addIndoorDistance(meters: Float) {
        if (_uiState.value.isGpsMode) return
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

    fun finishRun(onFinished: () -> Unit) {
        val current = _uiState.value
        if (current.isGpsMode) {
            RunTrackingService.stopAndSaveService(getApplication())
            _uiState.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    sessionMeters = 0f,
                    elapsedSeconds = 0
                )
            }
            onFinished()
        } else {
            indoorTimerJob?.cancel()
            indoorTimerJob = null
            val target = current.target
            val meters = current.sessionMeters
            val elapsed = current.elapsedSeconds

            if (target != null && meters > 0f) {
                val avgPaceSec = if (meters > 0) elapsed / (meters / 1000f) else 0f
                val calories = (meters * 0.065f).toInt()
                questRepo.logRepetitionProgress(target.id, meters)
                questRepo.logRunGps(
                    targetId = target.id,
                    distanceMeters = meters,
                    durationSeconds = elapsed,
                    avgPaceSecPerKm = avgPaceSec,
                    caloriesBurned = calories,
                    gpsPathGeoJson = "[]"
                )
            }
            _uiState.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    sessionMeters = 0f,
                    elapsedSeconds = 0
                )
            }
            onFinished()
        }
    }

    private fun calculatePaceString(meters: Float, seconds: Int): String {
        if (meters < 50f || seconds < 5) return "--'--\" /km"
        val km = meters / 1000f
        val secPerKm = (seconds / km).toInt()
        val paceMin = secPerKm / 60
        val paceSec = secPerKm % 60
        return if (paceMin in 2..29) {
            String.format(Locale.US, "%d'%02d\" /km", paceMin, paceSec)
        } else {
            "--'--\" /km"
        }
    }

    override fun onCleared() {
        super.onCleared()
        indoorTimerJob?.cancel()
    }
}
