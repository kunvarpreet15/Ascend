package com.kunvarpreet.ascend

import com.kunvarpreet.ascend.domain.engine.RankPolicy
import com.kunvarpreet.ascend.domain.engine.XpCalculator
import com.kunvarpreet.ascend.domain.model.RankTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XpAndEconomyTest {

    // =========================================================================
    // 1. Exact XP Requirements from Level 1 to 100
    // =========================================================================

    @Test
    fun `test exact xp requirements follow 450 + 42 times level minus 1 across Levels 1 to 100`() {
        for (level in 1..100) {
            val expectedXp = 450L + 42L * (level - 1)
            assertEquals("XP mismatch at level $level", expectedXp, XpCalculator.xpRequiredForLevel(level))
        }

        // Spot check milestone ranks
        assertEquals(450L, XpCalculator.xpRequiredForLevel(1))    // E-Rank start
        assertEquals(828L, XpCalculator.xpRequiredForLevel(10))   // D-Rank gate
        assertEquals(1458L, XpCalculator.xpRequiredForLevel(25))  // C-Rank gate
        assertEquals(2298L, XpCalculator.xpRequiredForLevel(45))  // B-Rank gate
        assertEquals(3348L, XpCalculator.xpRequiredForLevel(70))  // A-Rank gate
        assertEquals(4608L, XpCalculator.xpRequiredForLevel(100)) // S-Rank gate
    }

    // =========================================================================
    // 2. Progression Simulation: D-Rank in 21-28 days, A-Rank in 350-380 days
    // =========================================================================

    @Test
    fun `test simulation confirms D-Rank reached in 21 to 28 days and A-Rank in 350 to 380 days`() {
        // Standard Operative Regimen with consistent habit formation:
        // Base deck reward 125 XP, 1.1x difficulty multiplier, and allocating 1 AP per level to INT
        val baseXp = 125
        val difficultyMultiplier = 1.1f
        var level = 1
        var currentXp = 0L
        var intelligence = 10
        var streak = 0

        var dayReachedD = -1
        var dayReachedA = -1

        for (day in 1..400) {
            streak++
            val xpGain = XpCalculator.calculateDailyQuestXp(
                baseXp = baseXp,
                streakDays = streak,
                difficultyMultiplier = difficultyMultiplier,
                intelligence = intelligence
            )

            val outcome = XpCalculator.applyXpGain(
                currentLevel = level,
                currentXp = currentXp,
                xpGained = xpGain
            )

            if (outcome.levelsGained > 0) {
                // Hunter invests 1 AP per level into INT
                intelligence += outcome.levelsGained * 1
                level = outcome.newLevel
            }
            currentXp = outcome.remainingXp

            if (level >= 10 && dayReachedD == -1) {
                dayReachedD = day
            }
            if (level >= 70 && dayReachedA == -1) {
                dayReachedA = day
            }
        }

        // Assert D-Rank (Level 10) is achieved within the habit anchor window (21-28 days)
        assertTrue(
            "D-Rank should be reached within 21–28 days, but was day $dayReachedD",
            dayReachedD in 21..28
        )

        // Assert A-Rank (Level 70) is achieved within the ~1 year transformation window (350–380 days)
        assertTrue(
            "A-Rank should be reached within 350–380 days, but was day $dayReachedA",
            dayReachedA in 350..380
        )
    }

    // =========================================================================
    // 3. Stat Scaling Multipliers & Multi-Level Overflow XP
    // =========================================================================

    @Test
    fun `test INT stat scaling multiplier applies accurate progression bonus`() {
        val base = 100
        val streak = 0
        val diff = 1.0f

        // Base INT 10 -> 0% bonus (100 XP)
        assertEquals(100L, XpCalculator.calculateDailyQuestXp(base, streak, diff, 10))

        // INT 20 -> +15% bonus (115 XP)
        assertEquals(115L, XpCalculator.calculateDailyQuestXp(base, streak, diff, 20))

        // INT 50 -> +60% bonus (160 XP)
        assertEquals(160L, XpCalculator.calculateDailyQuestXp(base, streak, diff, 50))

        // INT 70 -> +90% bonus (190 XP)
        assertEquals(190L, XpCalculator.calculateDailyQuestXp(base, streak, diff, 70))
    }

    @Test
    fun `test VIT stat scaling multiplier applies accurate gold economy bonus`() {
        val baseGold = 50
        val streak = 0

        // Base VIT 10 -> 0% bonus (50 Gold)
        assertEquals(50L, XpCalculator.calculateDailyQuestGold(baseGold, streak, 10))

        // VIT 20 -> +10% bonus (55 Gold)
        assertEquals(55L, XpCalculator.calculateDailyQuestGold(baseGold, streak, 20))

        // VIT 50 -> +40% bonus (70 Gold)
        assertEquals(70L, XpCalculator.calculateDailyQuestGold(baseGold, streak, 50))

        // VIT 100 -> +90% bonus (95 Gold)
        assertEquals(95L, XpCalculator.calculateDailyQuestGold(baseGold, streak, 100))
    }

    @Test
    fun `test multi-level overflow XP carry-over calculates exact level leap and stat points`() {
        // Level 1 starts with 0 XP.
        // Gaining 2,500 XP burst:
        // Level 1 -> 2 costs 450 XP (remaining 2,050)
        // Level 2 -> 3 costs 492 XP (remaining 1,558)
        // Level 3 -> 4 costs 534 XP (remaining 1,024)
        // Level 4 -> 5 costs 576 XP (remaining 448)
        // Level 5 requires 618 XP. 448 < 618, so hunter stops at Level 5 with 448 remaining XP.
        val outcome = XpCalculator.applyXpGain(
            currentLevel = 1,
            currentXp = 0L,
            xpGained = 2500L
        )

        assertEquals(5, outcome.newLevel)
        assertEquals(448L, outcome.remainingXp)
        assertEquals(618L, outcome.xpToNextLevel)
        assertEquals(4, outcome.levelsGained)
        assertEquals(20, outcome.statPointsEarned) // 4 levels * 5 AP = 20 AP
    }

    // =========================================================================
    // 4. RankPolicy Trial Eligibility Thresholds
    // =========================================================================

    @Test
    fun `test RankPolicy trial eligibility gates at exact level boundaries`() {
        // E-Rank -> D-Rank gate (Threshold: Level 10)
        assertFalse(RankPolicy.isEligibleForRankTrial(level = 9, currentRank = RankTier.E))
        assertTrue(RankPolicy.isEligibleForRankTrial(level = 10, currentRank = RankTier.E))

        // D-Rank -> C-Rank gate (Threshold: Level 25)
        assertFalse(RankPolicy.isEligibleForRankTrial(level = 24, currentRank = RankTier.D))
        assertTrue(RankPolicy.isEligibleForRankTrial(level = 25, currentRank = RankTier.D))

        // B-Rank -> A-Rank gate (Threshold: Level 70)
        assertFalse(RankPolicy.isEligibleForRankTrial(level = 69, currentRank = RankTier.B))
        assertTrue(RankPolicy.isEligibleForRankTrial(level = 70, currentRank = RankTier.B))

        // C-Rank -> B-Rank gate (Threshold: Level 45)
        assertFalse(RankPolicy.isEligibleForRankTrial(level = 44, currentRank = RankTier.C))
        assertTrue(RankPolicy.isEligibleForRankTrial(level = 45, currentRank = RankTier.C))

        // S-Rank -> SSS-Rank gate (Threshold: Level 150)
        assertFalse(RankPolicy.isEligibleForRankTrial(level = 149, currentRank = RankTier.S))
        assertTrue(RankPolicy.isEligibleForRankTrial(level = 150, currentRank = RankTier.S))

        // SSS-Rank (Max Tier) has no next rank
        assertFalse(RankPolicy.isEligibleForRankTrial(level = 200, currentRank = RankTier.SSS))
    }
}
