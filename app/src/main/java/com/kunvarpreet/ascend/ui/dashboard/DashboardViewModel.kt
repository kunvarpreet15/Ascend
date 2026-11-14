package com.kunvarpreet.ascend.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kunvarpreet.ascend.data.repository.HunterRepository
import com.kunvarpreet.ascend.data.repository.QuestRepository
import com.kunvarpreet.ascend.domain.model.DailyQuest
import com.kunvarpreet.ascend.domain.model.HunterProfile
import com.kunvarpreet.ascend.domain.model.QuestRewardEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class DashboardUiState(
    val hunter: HunterProfile? = null,
    val todayQuest: DailyQuest? = null,
    val timeRemainingUntilMidnight: String = "00:00:00",
    val isLoading: Boolean = false
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val hunterRepo = HunterRepository(application)
    private val questRepo = QuestRepository(application)

    private val _timeRemaining = MutableStateFlow(calculateTimeUntilMidnight())
    val timeRemaining: StateFlow<String> = _timeRemaining

    val rewardEvent: StateFlow<QuestRewardEvent?> = questRepo.rewardEvent

    fun dismissRewardEvent() {
        questRepo.dismissRewardEvent()
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        hunterRepo.hunterProfile,
        questRepo.todayQuest,
        _timeRemaining
    ) { hunter, quest, time ->
        DashboardUiState(
            hunter = hunter,
            todayQuest = quest,
            timeRemainingUntilMidnight = time,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )

    init {
        // Start countdown ticker for midnight quest deadline
        viewModelScope.launch {
            while (true) {
                _timeRemaining.value = calculateTimeUntilMidnight()
                delay(1000)
            }
        }
    }

    private fun calculateTimeUntilMidnight(): String {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val diffMs = (midnight.timeInMillis - now.timeInMillis).coerceAtLeast(0)
        val hours = diffMs / (1000 * 60 * 60)
        val minutes = (diffMs / (1000 * 60)) % 60
        val seconds = (diffMs / 1000) % 60
        return "%02dh %02dm %02ds".format(hours, minutes, seconds)
    }

    fun refresh() {
        hunterRepo.refresh()
        questRepo.refresh()
    }
}
