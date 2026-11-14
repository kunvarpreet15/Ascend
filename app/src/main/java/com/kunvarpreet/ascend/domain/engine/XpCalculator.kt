package com.kunvarpreet.ascend.domain.engine

import kotlin.math.roundToLong

/**
 * Outcome returned after applying XP gain.
 *
 * @property newLevel The hunter's level after applying XP.
 * @property remainingXp The hunter's current XP progress into [newLevel].
 * @property xpToNextLevel Total XP required to advance from [newLevel] to [newLevel] + 1.
 * @property levelsGained Number of level-ups achieved during this XP gain event.
 * @property statPointsEarned Available stat points earned (5 AP per level gained).
 */
data class LevelUpOutcome(
    val newLevel: Int,
    val remainingXp: Long,
    val xpToNextLevel: Long,
    val levelsGained: Int,
    val statPointsEarned: Int
)

object XpCalculator {

    /**
     * Calculates the XP required to advance from [level] to [level] + 1.
     * Formula: 450L + 42L * (level - 1)
     */
    fun xpRequiredForLevel(level: Int): Long {
        val safeLevel = maxOf(1, level)
        return 450L + 42L * (safeLevel - 1)
    }

    /**
     * Calculates daily quest XP reward factoring in streak days, difficulty multiplier,
     * and intelligence stat bonus.
     *
     * Formula: (baseXp + minOf(streakDays * 5, 80)) * difficultyMultiplier * (1.0 + (intelligence - 10) * 0.015)
     */
    fun calculateDailyQuestXp(
        baseXp: Int,
        streakDays: Int,
        difficultyMultiplier: Float,
        intelligence: Int
    ): Long {
        val streakBonus = minOf(maxOf(0, streakDays) * 5, 80)
        val baseWithStreak = (maxOf(0, baseXp) + streakBonus).toDouble()
        val effectiveMultiplier = if (difficultyMultiplier > 0f) difficultyMultiplier.toDouble() else 1.0
        val intBonus = 1.0 + (maxOf(0, intelligence) - 10) * 0.015
        val earnedXp = baseWithStreak * effectiveMultiplier * intBonus
        return maxOf(0L, earnedXp.roundToLong())
    }

    /**
     * Calculates daily quest Gold reward factoring in streak days and vitality stat bonus.
     *
     * Formula: (baseGold + minOf(streakDays * 2, 40)) * (1.0 + (vitality - 10) * 0.01)
     */
    fun calculateDailyQuestGold(
        baseGold: Int,
        streakDays: Int,
        vitality: Int
    ): Long {
        val streakBonus = minOf(maxOf(0, streakDays) * 2, 40)
        val baseWithStreak = (maxOf(0, baseGold) + streakBonus).toDouble()
        val vitBonus = 1.0 + (maxOf(0, vitality) - 10) * 0.01
        val earnedGold = baseWithStreak * vitBonus
        return maxOf(0L, earnedGold.roundToLong())
    }

    /**
     * Applies gained XP to current level and XP, evaluating multi-level-up jumps
     * and awarding 5 stat points per level gained.
     */
    fun applyXpGain(
        currentLevel: Int,
        currentXp: Long,
        xpGained: Long
    ): LevelUpOutcome {
        var level = maxOf(1, currentLevel)
        var xp = maxOf(0L, currentXp) + maxOf(0L, xpGained)
        var xpToNext = xpRequiredForLevel(level)
        var levelsGained = 0

        while (xp >= xpToNext) {
            xp -= xpToNext
            level++
            levelsGained++
            xpToNext = xpRequiredForLevel(level)
        }

        return LevelUpOutcome(
            newLevel = level,
            remainingXp = xp,
            xpToNextLevel = xpToNext,
            levelsGained = levelsGained,
            statPointsEarned = levelsGained * 5
        )
    }
}
