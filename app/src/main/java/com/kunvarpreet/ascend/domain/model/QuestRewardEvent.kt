package com.kunvarpreet.ascend.domain.model

/**
 * Event emitted upon completing a daily quest deck or claiming milestone progression rewards.
 * Drives Screen S07 (Level-Up & Rewards Celebration Modal).
 */
data class QuestRewardEvent(
    val xpEarned: Long,
    val goldEarned: Long,
    val didLevelUp: Boolean,
    val oldLevel: Int,
    val newLevel: Int,
    val statPointsGained: Int,
    val rankTrialUnlocked: Boolean
)
