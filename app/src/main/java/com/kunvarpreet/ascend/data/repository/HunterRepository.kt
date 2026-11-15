package com.kunvarpreet.ascend.data.repository

import android.content.Context
import com.kunvarpreet.ascend.data.local.AscendDatabaseHelper
import com.kunvarpreet.ascend.domain.model.AttributeStats
import com.kunvarpreet.ascend.domain.model.HunterProfile
import com.kunvarpreet.ascend.domain.model.InventoryItem
import com.kunvarpreet.ascend.domain.model.QuestRewardEvent
import kotlinx.coroutines.flow.StateFlow

class HunterRepository(
    context: Context,
    private val dbHelper: AscendDatabaseHelper = AscendDatabaseHelper.getInstance(context)
) {

    val hunterProfile: StateFlow<HunterProfile?> = dbHelper.hunterState
    val statsState: StateFlow<AttributeStats?> = dbHelper.statsState
    val rewardEvent: StateFlow<QuestRewardEvent?> = dbHelper.rewardEvent
    val inventoryState: StateFlow<List<InventoryItem>> = dbHelper.inventoryState

    fun purchaseShopItem(itemCode: String): AscendDatabaseHelper.PurchaseResult {
        val hunterId = hunterProfile.value?.id ?: return AscendDatabaseHelper.PurchaseResult.Error("No hunter profile")
        return dbHelper.purchaseShopItem(hunterId, itemCode)
    }

    fun promoteRank(): Boolean {
        val hunterId = hunterProfile.value?.id
        return dbHelper.promoteRank(hunterId)
    }

    fun dismissRewardEvent() {
        dbHelper.dismissRewardEvent()
    }

    fun allocateStatPoint(statKey: String): Boolean {
        return dbHelper.allocateStatPoint(statKey)
    }

    fun saveOnboardingProfile(alias: String, heightCm: Float, weightKg: Float, difficultyMultiplier: Float): Boolean {
        return dbHelper.saveOnboardingProfile(alias, heightCm, weightKg, difficultyMultiplier)
    }

    fun addXpAndGold(xp: Long, gold: Long) {
        dbHelper.awardXpAndGold(xp, gold)
    }

    fun refresh() {
        dbHelper.refreshCache()
    }
}
