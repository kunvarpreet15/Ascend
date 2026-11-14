package com.kunvarpreet.ascend.domain.engine

import com.kunvarpreet.ascend.domain.model.RankTier

/**
 * Pure Kotlin policy governing rank promotion eligibility.
 * Decouples level progression from automatic rank advancement so that
 * Rank Promotion Trials (S10) remain meaningful and gated.
 */
object RankPolicy {

    /**
     * Returns the minimum level required to attempt the promotion trial for the rank above [currentRank].
     * Returns null if [currentRank] is already at the maximum tier (SSS).
     *
     * Thresholds:
     * - E -> 10 (D-Rank)
     * - D -> 25 (C-Rank)
     * - C -> 45 (B-Rank)
     * - B -> 70 (A-Rank)
     * - A -> 100 (S-Rank)
     * - S -> 150 (SSS-Rank)
     * - SSS -> null
     */
    fun requiredLevelForNextRank(currentRank: RankTier): Int? = when (currentRank) {
        RankTier.E -> 10
        RankTier.D -> 25
        RankTier.C -> 45
        RankTier.B -> 70
        RankTier.A -> 100
        RankTier.S -> 150
        RankTier.SSS -> null
    }

    /**
     * Returns the next RankTier above [currentRank], or null if already SSS.
     */
    fun nextRank(currentRank: RankTier): RankTier? = when (currentRank) {
        RankTier.E -> RankTier.D
        RankTier.D -> RankTier.C
        RankTier.C -> RankTier.B
        RankTier.B -> RankTier.A
        RankTier.A -> RankTier.S
        RankTier.S -> RankTier.SSS
        RankTier.SSS -> null
    }

    /**
     * Evaluates whether the hunter at [level] meets the minimum level threshold
     * to challenge the Rank Promotion Trial for the next tier above [currentRank].
     */
    fun isEligibleForRankTrial(level: Int, currentRank: RankTier): Boolean {
        val required = requiredLevelForNextRank(currentRank) ?: return false
        return level >= required
    }
}
