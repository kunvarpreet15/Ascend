package com.kunvarpreet.ascend.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kunvarpreet.ascend.data.repository.HunterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class OnboardingStep {
    ASSESSMENT,
    AWAKENING_CEREMONY
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.ASSESSMENT,
    val alias: String = "SHADOW_HUNTER",
    val heightCm: Float = 175.0f,
    val weightKg: Float = 70.0f,
    val difficultyMultiplier: Float = 1.0f,
    val isSaving: Boolean = false
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val hunterRepo = HunterRepository(application)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun updateAlias(alias: String) {
        _uiState.update { it.copy(alias = alias.take(20)) }
    }

    fun updateHeight(height: Float) {
        _uiState.update { it.copy(heightCm = height) }
    }

    fun updateWeight(weight: Float) {
        _uiState.update { it.copy(weightKg = weight) }
    }

    fun updateDifficulty(multiplier: Float) {
        _uiState.update { it.copy(difficultyMultiplier = multiplier) }
    }

    fun proceedToAwakening() {
        _uiState.update { it.copy(step = OnboardingStep.AWAKENING_CEREMONY) }
    }

    fun completeAwakening(onComplete: () -> Unit) {
        val state = _uiState.value
        hunterRepo.saveOnboardingProfile(
            alias = state.alias.ifBlank { "SHADOW_HUNTER" },
            heightCm = state.heightCm,
            weightKg = state.weightKg,
            difficultyMultiplier = state.difficultyMultiplier
        )
        onComplete()
    }
}
