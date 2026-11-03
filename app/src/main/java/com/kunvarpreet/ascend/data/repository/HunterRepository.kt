package com.kunvarpreet.ascend.data.repository

import android.content.Context
import com.kunvarpreet.ascend.data.local.AscendDatabaseHelper
import com.kunvarpreet.ascend.domain.model.HunterProfile
import kotlinx.coroutines.flow.StateFlow

class HunterRepository(context: Context) {
    private val dbHelper = AscendDatabaseHelper.getInstance(context)

    val hunterProfile: StateFlow<HunterProfile?> = dbHelper.hunterState

    fun addXpAndGold(xp: Long, gold: Long) {
        dbHelper.awardXpAndGold(xp, gold)
    }

    fun refresh() {
        dbHelper.refreshCache()
    }
}
