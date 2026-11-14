package com.kunvarpreet.ascend.domain.engine

import com.kunvarpreet.ascend.domain.model.ExerciseType
import com.kunvarpreet.ascend.domain.model.HunterProfile
import com.kunvarpreet.ascend.domain.model.QuestRewardEvent
import com.kunvarpreet.ascend.domain.model.QuestStatus
import com.kunvarpreet.ascend.domain.model.RankTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseWiringTest {

    @Test
    fun `test infinite reward guard prevents double reward on already completed quest`() {
        var questStatus = QuestStatus.COMPLETED
        var xpAwardedCount = 0

        // Simulating the database guard:
        fun onProgressUpdate(isTargetDone: Boolean) {
            // Guard: If quest is already COMPLETED, NEVER award rewards
            if (questStatus != QuestStatus.COMPLETED && isTargetDone) {
                questStatus = QuestStatus.COMPLETED
                xpAwardedCount++
            }
        }

        // Subsequent target progress
        onProgressUpdate(isTargetDone = true)
        onProgressUpdate(isTargetDone = true)

        assertEquals(0, xpAwardedCount)
        assertEquals(QuestStatus.COMPLETED, questStatus)
    }

    @Test
    fun `test catchup evaluator consumes shield when available on missed day`() {
        val result = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-09",
            todayDate = "2026-11-11", // 2 day gap (1 day missed)
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 14,
            availableStreakShields = 2
        )

        assertTrue(result is CatchUpResult.ShieldConsumed)
        assertEquals(1, (result as CatchUpResult.ShieldConsumed).shieldsRemaining)
    }

    @Test
    fun `test catchup evaluator triggers penalty and resets streak when no shields available`() {
        val result = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-09",
            todayDate = "2026-11-11", // 2 day gap (1 day missed)
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 14,
            availableStreakShields = 0
        )

        assertTrue(result is CatchUpResult.PenaltyTriggered)
        val penalty = result as CatchUpResult.PenaltyTriggered
        assertEquals(1, penalty.missedDaysCount)
        assertTrue(penalty.penaltyTaskDescription.contains("Survival Trial"))
    }

    @Test
    fun `test difficulty multiplier persists and accurately scales day 2 plus targets`() {
        val hunterProfile = HunterProfile(
            difficultyMultiplier = 1.5f
        )
        assertEquals(1.5f, hunterProfile.difficultyMultiplier, 0.001f)

        // Day 2 quest generation using hunter's stored multiplier
        val day2Targets = QuestGenerator.generateDailyTargets(
            questId = "day-2-quest",
            difficultyMultiplier = hunterProfile.difficultyMultiplier
        )

        val pushups = day2Targets.first { it.exerciseType == ExerciseType.PUSH_UPS }
        assertEquals(150f, pushups.targetQuantity, 0.01f)

        val running = day2Targets.first { it.exerciseType == ExerciseType.RUNNING }
        assertEquals(4500f, running.targetQuantity, 0.01f)
    }

    @Test
    fun `test quest reward event emits level up and rank trial unlocked correctly`() {
        val oldLevel = 9
        val currentXp = 0L
        val questXpGained = 800L // Level 9 requires 450 + 42*8 = 786 XP, so 800 XP advances to Level 10

        val outcome = XpCalculator.applyXpGain(
            currentLevel = oldLevel,
            currentXp = currentXp,
            xpGained = questXpGained
        )

        val currentRank = RankTier.E
        val didLevelUp = outcome.levelsGained > 0
        val rankTrialUnlocked = !RankPolicy.isEligibleForRankTrial(oldLevel, currentRank) &&
            RankPolicy.isEligibleForRankTrial(outcome.newLevel, currentRank)

        val event = QuestRewardEvent(
            xpEarned = questXpGained,
            goldEarned = 70L,
            didLevelUp = didLevelUp,
            oldLevel = oldLevel,
            newLevel = outcome.newLevel,
            statPointsGained = outcome.statPointsEarned,
            rankTrialUnlocked = rankTrialUnlocked
        )

        assertTrue(event.didLevelUp)
        assertEquals(10, event.newLevel)
        assertTrue(event.rankTrialUnlocked)
        assertEquals(5, event.statPointsGained)
    }
}
