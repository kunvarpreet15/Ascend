package com.kunvarpreet.ascend.ui.promotion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kunvarpreet.ascend.data.repository.HunterRepository
import com.kunvarpreet.ascend.domain.engine.RankPolicy
import com.kunvarpreet.ascend.domain.model.HunterProfile
import com.kunvarpreet.ascend.domain.model.RankTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RankTrialConfig(
    val currentRank: RankTier,
    val targetRank: RankTier,
    val trialName: String,
    val targetReps: Int,
    val targetDistanceKm: Float,
    val description: String
)

class PromotionViewModel(application: Application) : AndroidViewModel(application) {
    private val hunterRepo = HunterRepository(application)

    val hunterProfile: StateFlow<HunterProfile?> = hunterRepo.hunterProfile

    private val _repsLogged = MutableStateFlow(0)
    val repsLogged: StateFlow<Int> = _repsLogged.asStateFlow()

    private val _distanceKmLogged = MutableStateFlow(0f)
    val distanceKmLogged: StateFlow<Float> = _distanceKmLogged.asStateFlow()

    private val _isPromoted = MutableStateFlow(false)
    val isPromoted: StateFlow<Boolean> = _isPromoted.asStateFlow()

    private val _promotedRank = MutableStateFlow<RankTier?>(null)
    val promotedRank: StateFlow<RankTier?> = _promotedRank.asStateFlow()

    fun getTrialConfig(rank: RankTier): RankTrialConfig? {
        val next = RankPolicy.nextRank(rank) ?: return null
        return when (rank) {
            RankTier.E -> RankTrialConfig(
                currentRank = RankTier.E,
                targetRank = RankTier.D,
                trialName = "STRIKER AWAKENING TRIAL",
                targetReps = 120,
                targetDistanceKm = 3.5f,
                description = "Single-session trial gate: Complete 120 Repetitions and a 3.5 km Run to break through into D-Rank Striker standing."
            )
            RankTier.D -> RankTrialConfig(
                currentRank = RankTier.D,
                targetRank = RankTier.C,
                trialName = "ADEPT GATE TRIAL",
                targetReps = 150,
                targetDistanceKm = 5.0f,
                description = "Complete 150 Repetitions and a 5.0 km Run to ascend to C-Rank Adept."
            )
            RankTier.C -> RankTrialConfig(
                currentRank = RankTier.C,
                targetRank = RankTier.B,
                trialName = "ELITE CRUCIBLE TRIAL",
                targetReps = 200,
                targetDistanceKm = 7.5f,
                description = "Complete 200 Repetitions and a 7.5 km Run to enter B-Rank Elite standing."
            )
            RankTier.B -> RankTrialConfig(
                currentRank = RankTier.B,
                targetRank = RankTier.A,
                trialName = "CHAMPION GAUNTLET TRIAL",
                targetReps = 250,
                targetDistanceKm = 10.0f,
                description = "Complete 250 Repetitions and a 10.0 km Run to claim A-Rank Champion title."
            )
            RankTier.A -> RankTrialConfig(
                currentRank = RankTier.A,
                targetRank = RankTier.S,
                trialName = "MONARCH ASCENDANCE TRIAL",
                targetReps = 300,
                targetDistanceKm = 15.0f,
                description = "Complete 300 Repetitions and a 15.0 km Run to ascend into S-Rank Monarch majesty."
            )
            RankTier.S -> RankTrialConfig(
                currentRank = RankTier.S,
                targetRank = RankTier.SSS,
                trialName = "SOVEREIGN PINNACLE TRIAL",
                targetReps = 400,
                targetDistanceKm = 20.0f,
                description = "Complete 400 Repetitions and a 20.0 km Run to attain Sovereign Pinnacle standing."
            )
            RankTier.SSS -> null
        }
    }

    fun addReps(reps: Int) {
        _repsLogged.value += reps
    }

    fun addDistance(km: Float) {
        _distanceKmLogged.value += km
    }

    fun canPromote(config: RankTrialConfig): Boolean {
        return _repsLogged.value >= config.targetReps && _distanceKmLogged.value >= config.targetDistanceKm
    }

    fun completePromotion(): Boolean {
        val current = hunterProfile.value ?: return false
        val config = getTrialConfig(current.rankTier) ?: return false
        if (!canPromote(config)) return false

        val success = hunterRepo.promoteRank()
        if (success) {
            _promotedRank.value = config.targetRank
            _isPromoted.value = true
            hunterRepo.refresh()
        }
        return success
    }
}
