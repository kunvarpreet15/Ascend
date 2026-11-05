package com.kunvarpreet.ascend.ui.status

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kunvarpreet.ascend.data.repository.HunterRepository
import com.kunvarpreet.ascend.domain.model.AttributeStats
import com.kunvarpreet.ascend.domain.model.HunterProfile
import kotlinx.coroutines.flow.StateFlow

class StatusViewModel(application: Application) : AndroidViewModel(application) {
    private val hunterRepo = HunterRepository(application)

    val hunterProfile: StateFlow<HunterProfile?> = hunterRepo.hunterProfile
    val statsState: StateFlow<AttributeStats?> = hunterRepo.statsState

    fun allocateStat(statKey: String): Boolean {
        return hunterRepo.allocateStatPoint(statKey)
    }

    fun refresh() {
        hunterRepo.refresh()
    }
}
