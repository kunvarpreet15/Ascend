package com.kunvarpreet.ascend.ui.workout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kunvarpreet.ascend.data.repository.QuestRepository
import com.kunvarpreet.ascend.domain.model.ExerciseTarget
import com.kunvarpreet.ascend.domain.model.QuestRewardEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkoutUiState(
    val target: ExerciseTarget? = null,
    val isLoading: Boolean = false,
    val isCompletedJustNow: Boolean = false
)

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val questRepo = QuestRepository(application)

    private val _uiState = MutableStateFlow(WorkoutUiState(isLoading = true))
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    val rewardEvent: StateFlow<QuestRewardEvent?> = questRepo.rewardEvent

    fun dismissRewardEvent() {
        questRepo.dismissRewardEvent()
    }

    fun loadTarget(targetId: String) {
        viewModelScope.launch {
            val quest = questRepo.todayQuest.value
            val target = quest?.targets?.find { it.id == targetId }
            _uiState.value = WorkoutUiState(
                target = target,
                isLoading = false,
                isCompletedJustNow = target?.isCompleted == true
            )
        }
    }

    fun logReps(targetId: String, reps: Float) {
        viewModelScope.launch {
            val success = questRepo.logRepetitionProgress(targetId, reps)
            if (success) {
                val updatedQuest = questRepo.todayQuest.value
                val updatedTarget = updatedQuest?.targets?.find { it.id == targetId }
                _uiState.value = _uiState.value.copy(
                    target = updatedTarget,
                    isCompletedJustNow = updatedTarget?.isCompleted == true
                )
            }
        }
    }
}
