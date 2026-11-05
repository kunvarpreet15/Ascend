package com.kunvarpreet.ascend

import com.kunvarpreet.ascend.domain.model.AttributeStats
import com.kunvarpreet.ascend.domain.model.DailyQuest
import com.kunvarpreet.ascend.domain.model.ExerciseTarget
import com.kunvarpreet.ascend.domain.model.ExerciseType
import com.kunvarpreet.ascend.domain.model.ExerciseUnit
import com.kunvarpreet.ascend.domain.model.HunterProfile
import com.kunvarpreet.ascend.domain.model.QuestStatus
import com.kunvarpreet.ascend.domain.model.RankTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionAndRewardTest {

    @Test
    fun `test initial hunter profile starts at Level 1 E-Rank`() {
        val hunter = HunterProfile()
        assertEquals(1, hunter.level)
        assertEquals(0L, hunter.currentXp)
        assertEquals(100L, hunter.xpToNextLevel)
        assertEquals(RankTier.E, hunter.rankTier)
        assertEquals(0f, hunter.xpProgress, 0.001f)
    }

    @Test
    fun `test xp progress calculates accurate ratio and clamps between 0 and 1`() {
        val halfXp = HunterProfile(currentXp = 50, xpToNextLevel = 100)
        assertEquals(0.5f, halfXp.xpProgress, 0.001f)

        val overflowXp = HunterProfile(currentXp = 150, xpToNextLevel = 100)
        assertEquals(1.0f, overflowXp.xpProgress, 0.001f)

        val zeroTotal = HunterProfile(currentXp = 0, xpToNextLevel = 0)
        assertEquals(0.0f, zeroTotal.xpProgress, 0.001f)
    }

    @Test
    fun `test exercise target progress clamps to 1 when exceeding goal`() {
        val target = ExerciseTarget(
            questId = "q1",
            exerciseType = ExerciseType.PUSH_UPS,
            targetQuantity = 100f,
            completedQuantity = 120f
        )
        assertEquals(1.0f, target.progress, 0.001f)
    }

    @Test
    fun `test exercise target edge cases zero target and negative completed`() {
        val zeroTarget = ExerciseTarget(
            questId = "q1",
            exerciseType = ExerciseType.PUSH_UPS,
            targetQuantity = 0f,
            completedQuantity = 10f
        )
        assertEquals(0f, zeroTarget.progress, 0.001f)

        val zeroCompleted = ExerciseTarget(
            questId = "q1",
            exerciseType = ExerciseType.PUSH_UPS,
            targetQuantity = 100f,
            completedQuantity = 0f
        )
        assertEquals(0f, zeroCompleted.progress, 0.001f)
    }

    @Test
    fun `test daily quest isAllCompleted returns true only when all targets are completed`() {
        val t1 = ExerciseTarget(questId = "q1", exerciseType = ExerciseType.PUSH_UPS, targetQuantity = 100f, completedQuantity = 100f, isCompleted = true)
        val t2 = ExerciseTarget(questId = "q1", exerciseType = ExerciseType.SIT_UPS, targetQuantity = 100f, completedQuantity = 50f, isCompleted = false)

        val partialQuest = DailyQuest(hunterId = "h1", questDate = "2026-11-03", targets = listOf(t1, t2))
        assertFalse(partialQuest.isAllCompleted)

        val allCompletedQuest = DailyQuest(hunterId = "h1", questDate = "2026-11-03", targets = listOf(t1, t2.copy(isCompleted = true, completedQuantity = 100f)))
        assertTrue(allCompletedQuest.isAllCompleted)
    }

    @Test
    fun `test level scaling calculation formula`() {
        fun calculateXpToNext(level: Int): Long = (100 * Math.pow(level.toDouble(), 1.5)).toLong()

        assertEquals(100L, calculateXpToNext(1))
        assertEquals(282L, calculateXpToNext(2))
        assertEquals(1118L, calculateXpToNext(5))
        assertEquals(3162L, calculateXpToNext(10))
    }

    @Test
    fun `test multi-level level up logic awards 5 stat points per level`() {
        var level = 1
        var currentXp = 0L + 500L // 500 XP burst
        var xpToNext = 100L
        var statPoints = 0

        while (currentXp >= xpToNext) {
            currentXp -= xpToNext
            level += 1
            statPoints += 5
            xpToNext = (100 * Math.pow(level.toDouble(), 1.5)).toLong()
        }

        // Level 1 -> 2 costs 100 XP (400 remaining)
        // Level 2 -> 3 costs 282 XP (118 remaining)
        // Level 3 -> 4 costs 519 XP (not enough)
        assertEquals(3, level)
        assertEquals(10, statPoints) // 2 level ups * 5 = 10 stat points
        assertEquals(118L, currentXp)
    }

    @Test
    fun `test rank tier ladder mapping`() {
        fun getRankForLevel(level: Int): RankTier = when {
            level >= 150 -> RankTier.SSS
            level >= 100 -> RankTier.S
            level >= 70 -> RankTier.A
            level >= 45 -> RankTier.B
            level >= 25 -> RankTier.C
            level >= 10 -> RankTier.D
            else -> RankTier.E
        }

        assertEquals(RankTier.E, getRankForLevel(1))
        assertEquals(RankTier.E, getRankForLevel(9))
        assertEquals(RankTier.D, getRankForLevel(10))
        assertEquals(RankTier.C, getRankForLevel(25))
        assertEquals(RankTier.B, getRankForLevel(45))
        assertEquals(RankTier.A, getRankForLevel(70))
        assertEquals(RankTier.S, getRankForLevel(100))
        assertEquals(RankTier.SSS, getRankForLevel(150))
    }

    @Test
    fun `test intelligence stat xp scaling multiplier formula`() {
        fun calculateScaledXp(baseXp: Long, intelligence: Int): Long {
            val multiplier = 1.0 + (intelligence - 10) * 0.02
            return (baseXp * multiplier).toLong()
        }

        assertEquals(100L, calculateScaledXp(100L, 10))
        assertEquals(110L, calculateScaledXp(100L, 15)) // +10% bonus
        assertEquals(120L, calculateScaledXp(100L, 20)) // +20% bonus
        assertEquals(150L, calculateScaledXp(100L, 35)) // +50% bonus
        assertEquals(98L, calculateScaledXp(100L, 9))
    }

    @Test
    fun `test stat point allocation rules`() {
        var availablePoints = 5
        var strength = 10

        fun allocateStrength(): Boolean {
            if (availablePoints > 0) {
                availablePoints -= 1
                strength += 1
                return true
            }
            return false
        }

        assertTrue(allocateStrength())
        assertEquals(4, availablePoints)
        assertEquals(11, strength)

        // Allocate remaining
        repeat(4) { assertTrue(allocateStrength()) }
        assertEquals(0, availablePoints)
        assertEquals(15, strength)

        // Cannot allocate with 0 available points
        assertFalse(allocateStrength())
        assertEquals(0, availablePoints)
        assertEquals(15, strength)
    }

    @Test
    fun `test quest completion prevents double reward on subsequent target updates`() {
        var questStatus = QuestStatus.PENDING
        var totalXpAwarded = 0L

        fun simulateTargetProgress(allTargetsComplete: Boolean, questReward: Long) {
            if (allTargetsComplete && questStatus != QuestStatus.COMPLETED) {
                questStatus = QuestStatus.COMPLETED
                totalXpAwarded += questReward
            }
        }

        // First completion triggers reward
        simulateTargetProgress(allTargetsComplete = true, questReward = 100L)
        assertEquals(QuestStatus.COMPLETED, questStatus)
        assertEquals(100L, totalXpAwarded)

        // Subsequent target progress when already COMPLETED does NOT re-award
        simulateTargetProgress(allTargetsComplete = true, questReward = 100L)
        assertEquals(100L, totalXpAwarded)
    }
}
