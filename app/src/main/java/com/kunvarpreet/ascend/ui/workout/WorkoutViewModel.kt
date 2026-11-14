package com.kunvarpreet.ascend.ui.workout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.kunvarpreet.ascend.core.sound.SoundFeedbackHelper
import com.kunvarpreet.ascend.data.repository.QuestRepository
import com.kunvarpreet.ascend.domain.engine.AscendClock
import com.kunvarpreet.ascend.domain.engine.SystemAscendClock
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

data class WorkoutUiState(
    val target: ExerciseTarget? = null,
    val isLoading: Boolean = false,
    val isCompletedJustNow: Boolean = false,
    val restEndTimeMillis: Long? = null,
    val selectedRestDurationSec: Int = 60,
    val remainingSeconds: Int = 0,
    val isTimerRunning: Boolean = false
) {
    val formattedRestTime: String
        get() {
            val min = remainingSeconds / 60
            val sec = remainingSeconds % 60
            return String.format(Locale.US, "%02d:%02d", min, sec)
        }
}

class WorkoutViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val clock: AscendClock = SystemAscendClock()
) : AndroidViewModel(application) {

    private val questRepo = QuestRepository(application)
    private val soundHelper = SoundFeedbackHelper(application)

    private val _uiState = MutableStateFlow(WorkoutUiState(isLoading = true))
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    val rewardEvent: StateFlow<QuestRewardEvent?> = questRepo.rewardEvent

    private var timerJob: Job? = null

    init {
        // Restore active rest timer from SavedStateHandle across process death or recreation
        val savedEndTime = savedStateHandle.get<Long>(KEY_REST_END_TIME)
        val savedDuration = savedStateHandle.get<Int>(KEY_SELECTED_REST_DURATION) ?: 60
        if (savedEndTime != null) {
            val now = clock.nowMillis()
            val millisRemaining = savedEndTime - now
            if (millisRemaining > 0) {
                val remainingSec = ((millisRemaining + 999) / 1000).toInt()
                _uiState.update {
                    it.copy(
                        restEndTimeMillis = savedEndTime,
                        selectedRestDurationSec = savedDuration,
                        remainingSeconds = remainingSec,
                        isTimerRunning = true
                    )
                }
                launchWallClockTicker()
            } else {
                savedStateHandle.remove<Long>(KEY_REST_END_TIME)
            }
        }
    }

    fun dismissRewardEvent() {
        questRepo.dismissRewardEvent()
    }

    fun loadTarget(targetId: String) {
        viewModelScope.launch {
            val quest = questRepo.todayQuest.value
            val target = quest?.targets?.find { it.id == targetId }
            _uiState.update {
                it.copy(
                    target = target,
                    isLoading = false,
                    isCompletedJustNow = target?.isCompleted == true
                )
            }
        }
    }

    fun logReps(targetId: String, reps: Float) {
        viewModelScope.launch {
            val success = questRepo.logRepetitionProgress(targetId, reps)
            if (success) {
                val updatedQuest = questRepo.todayQuest.value
                val updatedTarget = updatedQuest?.targets?.find { it.id == targetId }
                _uiState.update {
                    it.copy(
                        target = updatedTarget,
                        isCompletedJustNow = updatedTarget?.isCompleted == true
                    )
                }
            }
        }
    }

    fun startRestTimer(durationSeconds: Int) {
        val now = clock.nowMillis()
        val endTime = now + (durationSeconds * 1000L)
        savedStateHandle[KEY_REST_END_TIME] = endTime
        savedStateHandle[KEY_SELECTED_REST_DURATION] = durationSeconds

        _uiState.update {
            it.copy(
                restEndTimeMillis = endTime,
                selectedRestDurationSec = durationSeconds,
                remainingSeconds = durationSeconds,
                isTimerRunning = true
            )
        }
        launchWallClockTicker()
    }

    fun stopRestTimer() {
        timerJob?.cancel()
        timerJob = null
        savedStateHandle.remove<Long>(KEY_REST_END_TIME)
        _uiState.update {
            it.copy(
                restEndTimeMillis = null,
                remainingSeconds = 0,
                isTimerRunning = false
            )
        }
    }

    private fun launchWallClockTicker() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val endTime = _uiState.value.restEndTimeMillis ?: break
                val now = clock.nowMillis()
                val millisRemaining = endTime - now
                if (millisRemaining <= 0) {
                    _uiState.update {
                        it.copy(
                            restEndTimeMillis = null,
                            remainingSeconds = 0,
                            isTimerRunning = false
                        )
                    }
                    savedStateHandle.remove<Long>(KEY_REST_END_TIME)
                    soundHelper.playCompletionFeedback()
                    break
                } else {
                    val remainingSec = ((millisRemaining + 999) / 1000).toInt()
                    _uiState.update {
                        it.copy(
                            remainingSeconds = remainingSec,
                            isTimerRunning = true
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    companion object {
        const val KEY_REST_END_TIME = "workout_rest_end_time"
        const val KEY_SELECTED_REST_DURATION = "workout_selected_rest_duration"
    }
}
