package com.kunvarpreet.ascend.ui.penalty

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kunvarpreet.ascend.data.repository.HunterRepository
import com.kunvarpreet.ascend.data.repository.QuestRepository
import com.kunvarpreet.ascend.domain.engine.CatchUpEvaluator
import com.kunvarpreet.ascend.domain.model.HunterProfile
import com.kunvarpreet.ascend.domain.model.PenaltyLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

enum class PenaltyMode {
    BURPEES,
    WALL_SIT
}

class PenaltyViewModel(application: Application) : AndroidViewModel(application) {
    private val questRepo = QuestRepository(application)
    private val hunterRepo = HunterRepository(application)

    val activePenalty: StateFlow<PenaltyLog?> = questRepo.activePenalty
    val hunterProfile: StateFlow<HunterProfile?> = hunterRepo.hunterProfile

    private val _selectedMode = MutableStateFlow(PenaltyMode.BURPEES)
    val selectedMode: StateFlow<PenaltyMode> = _selectedMode.asStateFlow()

    private val _burpeesDone = MutableStateFlow(0)
    val burpeesDone: StateFlow<Int> = _burpeesDone.asStateFlow()

    private val _timerSecondsRemaining = MutableStateFlow(180)
    val timerSecondsRemaining: StateFlow<Int> = _timerSecondsRemaining.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private val _isSurvivalComplete = MutableStateFlow(false)
    val isSurvivalComplete: StateFlow<Boolean> = _isSurvivalComplete.asStateFlow()

    private var timerJob: Job? = null
    private var isInitialized = false

    init {
        viewModelScope.launch {
            hunterProfile.collect { profile ->
                if (profile != null && !isInitialized) {
                    isInitialized = true
                    val targetSec = getTargetWallSitSeconds(profile.difficultyMultiplier)
                    _timerSecondsRemaining.value = targetSec
                }
            }
        }
    }

    fun setMode(mode: PenaltyMode) {
        _selectedMode.value = mode
    }

    fun getTargetBurpees(multiplier: Float): Int = when {
        multiplier <= 0.5f -> 30
        multiplier >= 1.5f -> 75
        else -> 50
    }

    fun getTargetWallSitSeconds(multiplier: Float): Int = when {
        multiplier <= 0.5f -> 120
        multiplier >= 1.5f -> 300
        else -> 180
    }

    fun getPenaltyTaskLabel(multiplier: Float): String {
        return CatchUpEvaluator.getPenaltyTaskDescription(multiplier)
    }

    fun addBurpees(count: Int) {
        val mult = hunterProfile.value?.difficultyMultiplier ?: 1.0f
        val target = getTargetBurpees(mult)
        val newCount = _burpeesDone.value + count
        _burpeesDone.value = newCount
        if (newCount >= target) {
            _isSurvivalComplete.value = true
        }
    }

    fun toggleWallSitTimer() {
        if (_isTimerRunning.value) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        if (_timerSecondsRemaining.value <= 0) return
        _isTimerRunning.value = true
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_timerSecondsRemaining.value > 0 && _isTimerRunning.value) {
                delay(1000L.milliseconds)
                if (_isTimerRunning.value) {
                    val remaining = _timerSecondsRemaining.value - 1
                    _timerSecondsRemaining.value = remaining
                    if (remaining <= 0) {
                        _isTimerRunning.value = false
                        _isSurvivalComplete.value = true
                    }
                }
            }
        }
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
    }

    fun resetTimer() {
        pauseTimer()
        val mult = hunterProfile.value?.difficultyMultiplier ?: 1.0f
        _timerSecondsRemaining.value = getTargetWallSitSeconds(mult)
    }

    fun surviveAndClearPenalty(): Boolean {
        val penalty = activePenalty.value ?: return false
        val success = questRepo.clearPenalty(penalty.id)
        if (success) {
            _isSurvivalComplete.value = true
            hunterRepo.refresh()
            questRepo.refresh()
        }
        return success
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
