package com.kunvarpreet.ascend.domain.engine

import com.kunvarpreet.ascend.domain.model.ExerciseType
import com.kunvarpreet.ascend.domain.model.ExerciseUnit
import com.kunvarpreet.ascend.domain.model.QuestStatus
import com.kunvarpreet.ascend.domain.model.RankTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DomainEngineTest {

    // =========================================================================
    // 1. ClockProvider Tests
    // =========================================================================

    @Test
    fun `test FakeAscendClock advances time and updates ISO date across midnight`() {
        val clock = FakeAscendClock()
        clock.setIsoDate("2026-11-11")
        assertEquals("2026-11-11", clock.todayIsoDate())

        // Advance by 12 hours (still 2026-11-11)
        clock.advanceHours(12)
        assertEquals("2026-11-11", clock.todayIsoDate())

        // Advance another 13 hours (crosses midnight into 2026-11-12)
        clock.advanceHours(13)
        assertEquals("2026-11-12", clock.todayIsoDate())

        // Advance by 3 days
        clock.advanceDays(3)
        assertEquals("2026-11-15", clock.todayIsoDate())
    }

    @Test
    fun `test FakeAscendClock handles timezone changes`() {
        val clock = FakeAscendClock()
        // 2026-11-11 23:30:00 UTC
        clock.setIsoDate("2026-11-11")
        clock.advanceHours(23)
        clock.advanceMinutes(30)
        assertEquals("2026-11-11", clock.todayIsoDate())

        // Shift zone to Tokyo (UTC+9) -> next day in Tokyo
        clock.setZoneId(ZoneId.of("Asia/Tokyo"))
        assertEquals("2026-11-12", clock.todayIsoDate())
    }

    @Test
    fun `test SystemAscendClock provides valid ISO date and positive nowMillis`() {
        val clock = SystemAscendClock()
        assertTrue(clock.nowMillis() > 0)
        assertTrue(clock.todayIsoDate().matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals(ZoneId.systemDefault(), clock.zoneId())
    }

    // =========================================================================
    // 2. XpCalculator Tests
    // =========================================================================

    @Test
    fun `test xpRequiredForLevel follows 450 + 42 times level minus 1`() {
        assertEquals(450L, XpCalculator.xpRequiredForLevel(1))
        assertEquals(492L, XpCalculator.xpRequiredForLevel(2))
        assertEquals(534L, XpCalculator.xpRequiredForLevel(3))
        assertEquals(828L, XpCalculator.xpRequiredForLevel(10))
        assertEquals(1458L, XpCalculator.xpRequiredForLevel(25))
        assertEquals(4608L, XpCalculator.xpRequiredForLevel(100))

        // Level <= 1 clamps safely
        assertEquals(450L, XpCalculator.xpRequiredForLevel(0))
        assertEquals(450L, XpCalculator.xpRequiredForLevel(-5))
    }

    @Test
    fun `test calculateDailyQuestXp calculates streak bonus and INT multiplier`() {
        // Base case: streak=0, diff=1.0, INT=10 -> base 100
        val base = XpCalculator.calculateDailyQuestXp(
            baseXp = 100,
            streakDays = 0,
            difficultyMultiplier = 1.0f,
            intelligence = 10
        )
        assertEquals(100L, base)

        // Streak=5 (+25 bonus), INT=10 -> 125
        val streak5 = XpCalculator.calculateDailyQuestXp(
            baseXp = 100,
            streakDays = 5,
            difficultyMultiplier = 1.0f,
            intelligence = 10
        )
        assertEquals(125L, streak5)

        // Streak=20 (caps at +80 bonus), INT=10 -> 180
        val streakCap = XpCalculator.calculateDailyQuestXp(
            baseXp = 100,
            streakDays = 20,
            difficultyMultiplier = 1.0f,
            intelligence = 10
        )
        assertEquals(180L, streakCap)

        // INT=20 (+15% bonus), streak=0 -> 100 * 1.15 = 115
        val int20 = XpCalculator.calculateDailyQuestXp(
            baseXp = 100,
            streakDays = 0,
            difficultyMultiplier = 1.0f,
            intelligence = 20
        )
        assertEquals(115L, int20)

        // Difficulty multiplier 1.5x, streak=10 (+50), INT=10 -> (100 + 50) * 1.5 = 225
        val diff15 = XpCalculator.calculateDailyQuestXp(
            baseXp = 100,
            streakDays = 10,
            difficultyMultiplier = 1.5f,
            intelligence = 10
        )
        assertEquals(225L, diff15)
    }

    @Test
    fun `test calculateDailyQuestGold calculates streak bonus and VIT multiplier`() {
        // Base case: streak=0, VIT=10 -> base 50
        val base = XpCalculator.calculateDailyQuestGold(
            baseGold = 50,
            streakDays = 0,
            vitality = 10
        )
        assertEquals(50L, base)

        // Streak=10 (+20 gold), VIT=10 -> 70
        val streak10 = XpCalculator.calculateDailyQuestGold(
            baseGold = 50,
            streakDays = 10,
            vitality = 10
        )
        assertEquals(70L, streak10)

        // Streak=30 (caps at +40 gold), VIT=10 -> 90
        val streakCap = XpCalculator.calculateDailyQuestGold(
            baseGold = 50,
            streakDays = 30,
            vitality = 10
        )
        assertEquals(90L, streakCap)

        // VIT=30 (+20% bonus), streak=0 -> 50 * 1.20 = 60
        val vit30 = XpCalculator.calculateDailyQuestGold(
            baseGold = 50,
            streakDays = 0,
            vitality = 30
        )
        assertEquals(60L, vit30)
    }

    @Test
    fun `test applyXpGain handles single and multi-level level-ups`() {
        // No level-up
        val outcome1 = XpCalculator.applyXpGain(currentLevel = 1, currentXp = 0, xpGained = 200)
        assertEquals(1, outcome1.newLevel)
        assertEquals(200L, outcome1.remainingXp)
        assertEquals(450L, outcome1.xpToNextLevel)
        assertEquals(0, outcome1.levelsGained)
        assertEquals(0, outcome1.statPointsEarned)

        // Exact level-up from Level 1 (requires 450)
        val outcome2 = XpCalculator.applyXpGain(currentLevel = 1, currentXp = 200, xpGained = 250)
        assertEquals(2, outcome2.newLevel)
        assertEquals(0L, outcome2.remainingXp)
        assertEquals(492L, outcome2.xpToNextLevel)
        assertEquals(1, outcome2.levelsGained)
        assertEquals(5, outcome2.statPointsEarned)

        // Multi-level leap: Level 1 with 0 XP gaining 1,500 XP
        // Level 1: 450 XP -> remaining 1050
        // Level 2: 492 XP -> remaining 558
        // Level 3: 534 XP -> remaining 24
        // Next Level 4 requires 450 + 42*3 = 576 XP
        val outcome3 = XpCalculator.applyXpGain(currentLevel = 1, currentXp = 0, xpGained = 1500)
        assertEquals(4, outcome3.newLevel)
        assertEquals(24L, outcome3.remainingXp)
        assertEquals(576L, outcome3.xpToNextLevel)
        assertEquals(3, outcome3.levelsGained)
        assertEquals(15, outcome3.statPointsEarned)
    }

    // =========================================================================
    // 3. RankPolicy Tests
    // =========================================================================

    @Test
    fun `test requiredLevelForNextRank maps correctly across all tiers`() {
        assertEquals(10, RankPolicy.requiredLevelForNextRank(RankTier.E))
        assertEquals(25, RankPolicy.requiredLevelForNextRank(RankTier.D))
        assertEquals(45, RankPolicy.requiredLevelForNextRank(RankTier.C))
        assertEquals(70, RankPolicy.requiredLevelForNextRank(RankTier.B))
        assertEquals(100, RankPolicy.requiredLevelForNextRank(RankTier.A))
        assertEquals(150, RankPolicy.requiredLevelForNextRank(RankTier.S))
        assertNull(RankPolicy.requiredLevelForNextRank(RankTier.SSS))
    }

    @Test
    fun `test isEligibleForRankTrial gates promotion until level threshold is met`() {
        // E-Rank requires Level 10
        assertFalse(RankPolicy.isEligibleForRankTrial(level = 9, currentRank = RankTier.E))
        assertTrue(RankPolicy.isEligibleForRankTrial(level = 10, currentRank = RankTier.E))
        assertTrue(RankPolicy.isEligibleForRankTrial(level = 15, currentRank = RankTier.E))

        // D-Rank requires Level 25
        assertFalse(RankPolicy.isEligibleForRankTrial(level = 24, currentRank = RankTier.D))
        assertTrue(RankPolicy.isEligibleForRankTrial(level = 25, currentRank = RankTier.D))

        // SSS-Rank has no higher tier
        assertFalse(RankPolicy.isEligibleForRankTrial(level = 200, currentRank = RankTier.SSS))
    }

    // =========================================================================
    // 4. QuestGenerator Tests
    // =========================================================================

    @Test
    fun `test generateDailyTargets scales base targets by difficulty multiplier`() {
        val targets1x = QuestGenerator.generateDailyTargets(
            questId = "q-101",
            difficultyMultiplier = 1.0f
        )
        assertEquals(4, targets1x.size)

        val pushups1x = targets1x.first { it.exerciseType == ExerciseType.PUSH_UPS }
        assertEquals(100f, pushups1x.targetQuantity, 0.01f)
        assertEquals(ExerciseUnit.REPS, pushups1x.unit)

        val run1x = targets1x.first { it.exerciseType == ExerciseType.RUNNING }
        assertEquals(3000f, run1x.targetQuantity, 0.01f)
        assertEquals(ExerciseUnit.METERS, run1x.unit)

        // 0.5x difficulty
        val targetsHalf = QuestGenerator.generateDailyTargets(
            questId = "q-102",
            difficultyMultiplier = 0.5f
        )
        val pushupsHalf = targetsHalf.first { it.exerciseType == ExerciseType.PUSH_UPS }
        val runHalf = targetsHalf.first { it.exerciseType == ExerciseType.RUNNING }
        assertEquals(50f, pushupsHalf.targetQuantity, 0.01f)
        assertEquals(1500f, runHalf.targetQuantity, 0.01f)

        // 1.5x difficulty
        val targets15 = QuestGenerator.generateDailyTargets(
            questId = "q-103",
            difficultyMultiplier = 1.5f
        )
        val pushups15 = targets15.first { it.exerciseType == ExerciseType.PUSH_UPS }
        val run15 = targets15.first { it.exerciseType == ExerciseType.RUNNING }
        assertEquals(150f, pushups15.targetQuantity, 0.01f)
        assertEquals(4500f, run15.targetQuantity, 0.01f)
    }

    // =========================================================================
    // 5. CatchUpEvaluator Tests
    // =========================================================================

    @Test
    fun `test evaluateCatchUp on same day returns NoOpSameDay`() {
        val result = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-11",
            todayDate = "2026-11-11",
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 5,
            availableStreakShields = 2
        )
        assertEquals(CatchUpResult.NoOpSameDay, result)
    }

    @Test
    fun `test evaluateCatchUp on consecutive day with completed quest continues streak`() {
        val result = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-10",
            todayDate = "2026-11-11",
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 5,
            availableStreakShields = 0
        )
        assertEquals(CatchUpResult.StreakContinued, result)
    }

    @Test
    fun `test evaluateCatchUp on consecutive day with incomplete quest consumes shield`() {
        val result = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-10",
            todayDate = "2026-11-11",
            lastQuestStatus = QuestStatus.PENDING,
            currentStreak = 5,
            availableStreakShields = 2
        )
        assertTrue(result is CatchUpResult.ShieldConsumed)
        assertEquals(1, (result as CatchUpResult.ShieldConsumed).shieldsRemaining)
    }

    @Test
    fun `test evaluateCatchUp on consecutive day with incomplete quest and no shields triggers penalty`() {
        val result = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-10",
            todayDate = "2026-11-11",
            lastQuestStatus = QuestStatus.PENDING,
            currentStreak = 5,
            availableStreakShields = 0
        )
        assertTrue(result is CatchUpResult.PenaltyTriggered)
        val penalty = result as CatchUpResult.PenaltyTriggered
        assertEquals(1, penalty.missedDaysCount)
        assertTrue(penalty.penaltyTaskDescription.contains("1 missed quest deck"))
    }

    @Test
    fun `test evaluateCatchUp multi-day gap triggers penalty or consumes multiple shields`() {
        // Gap of 3 days (last completed on 2026-11-08, today is 2026-11-11 -> missed 2 days)
        val resultWithShields = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-08",
            todayDate = "2026-11-11",
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 10,
            availableStreakShields = 3
        )
        assertTrue(resultWithShields is CatchUpResult.ShieldConsumed)
        assertEquals(1, (resultWithShields as CatchUpResult.ShieldConsumed).shieldsRemaining)

        // Gap of 3 days with insufficient shields (1 shield for 2 missed days)
        val resultWithoutShields = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-08",
            todayDate = "2026-11-11",
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 10,
            availableStreakShields = 1
        )
        assertTrue(resultWithoutShields is CatchUpResult.PenaltyTriggered)
        val penalty = resultWithoutShields as CatchUpResult.PenaltyTriggered
        assertEquals(2, penalty.missedDaysCount)
    }
}
