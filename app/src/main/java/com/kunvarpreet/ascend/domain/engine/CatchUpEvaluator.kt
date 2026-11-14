package com.kunvarpreet.ascend.domain.engine

import com.kunvarpreet.ascend.domain.model.QuestStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Result of evaluating hunter quest continuity across calendar days.
 */
sealed interface CatchUpResult {
    /**
     * Evaluation on the same calendar day (or first day onboarding): no streak changes.
     */
    data object NoOpSameDay : CatchUpResult

    /**
     * Previous quest was completed on the preceding calendar day: streak continues.
     */
    data object StreakContinued : CatchUpResult

    /**
     * One or more days were missed, but protected by consuming available streak shields.
     */
    data class ShieldConsumed(val shieldsRemaining: Int) : CatchUpResult

    /**
     * Quests were missed without sufficient streak shields: penalty zone triggered.
     */
    data class PenaltyTriggered(
        val missedDaysCount: Int,
        val penaltyTaskDescription: String
    ) : CatchUpResult
}

object CatchUpEvaluator {

    /**
     * Evaluates daily quest continuity between [lastQuestDate] and [todayDate].
     *
     * @param lastQuestDate ISO-8601 date string (YYYY-MM-DD) of the most recent recorded quest, or null if first run.
     * @param todayDate ISO-8601 date string (YYYY-MM-DD) of today.
     * @param lastQuestStatus Status of the most recent quest (PENDING, COMPLETED, FAILED, PENALTY_CLEARED).
     * @param currentStreak Current streak count.
     * @param availableStreakShields Number of streak shields in hunter's possession.
     */
    fun evaluateCatchUp(
        lastQuestDate: String?,
        todayDate: String,
        lastQuestStatus: QuestStatus,
        currentStreak: Int,
        availableStreakShields: Int
    ): CatchUpResult {
        if (lastQuestDate.isNullOrBlank()) {
            return CatchUpResult.NoOpSameDay
        }

        val lastDate = try {
            LocalDate.parse(lastQuestDate)
        } catch (_: Exception) {
            return CatchUpResult.NoOpSameDay
        }

        val today = try {
            LocalDate.parse(todayDate)
        } catch (_: Exception) {
            return CatchUpResult.NoOpSameDay
        }

        val daysBetween = ChronoUnit.DAYS.between(lastDate, today).toInt()
        if (daysBetween <= 0) {
            return CatchUpResult.NoOpSameDay
        }

        val isLastQuestDone = lastQuestStatus == QuestStatus.COMPLETED ||
            lastQuestStatus == QuestStatus.PENALTY_CLEARED

        if (daysBetween == 1) {
            return if (isLastQuestDone) {
                CatchUpResult.StreakContinued
            } else {
                if (availableStreakShields > 0) {
                    CatchUpResult.ShieldConsumed(shieldsRemaining = availableStreakShields - 1)
                } else {
                    CatchUpResult.PenaltyTriggered(
                        missedDaysCount = 1,
                        penaltyTaskDescription = buildPenaltyDescription(1)
                    )
                }
            }
        }

        // Multi-day gap (daysBetween > 1)
        val missedDays = if (isLastQuestDone) daysBetween - 1 else daysBetween
        val safeShields = maxOf(0, availableStreakShields)

        return if (safeShields >= missedDays) {
            CatchUpResult.ShieldConsumed(shieldsRemaining = safeShields - missedDays)
        } else {
            CatchUpResult.PenaltyTriggered(
                missedDaysCount = missedDays,
                penaltyTaskDescription = buildPenaltyDescription(missedDays)
            )
        }
    }

    private fun buildPenaltyDescription(missedDaysCount: Int): String {
        val pushups = minOf(missedDaysCount * 50, 200)
        val runKm = minOf(missedDaysCount * 2, 10)
        return "Survival Trial: $missedDaysCount missed quest deck(s). Clear $pushups Push-ups and ${runKm}km Survival Run before midnight to purge Penalty Zone."
    }
}
