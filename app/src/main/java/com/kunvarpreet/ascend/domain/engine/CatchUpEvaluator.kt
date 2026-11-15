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

    /**
     * Missed day coincided with hunter's scheduled weekly rest day: streak preserved without penalty.
     */
    data object RestDayExempt : CatchUpResult
}

object CatchUpEvaluator {

    /**
     * Evaluates daily quest continuity between [lastQuestDate] and [todayDate].
     *
     * @param lastQuestDate ISO-8601 date string (YYYY-MM-DD) of the most recent recorded quest, or null if first run.
     * @param todayDate ISO-8601 date string (YYYY-MM-DD) of today.
     * @param lastQuestStatus Status of the most recent quest (PENDING, COMPLETED, FAILED, PENALTY_CLEARED, REST_DAY_EXEMPT).
     * @param currentStreak Current streak count.
     * @param availableStreakShields Number of streak shields in hunter's possession.
     * @param difficultyMultiplier Training regimen multiplier (0.5x, 1.0x, 1.5x).
     * @param scheduledRestDay Hunter's chosen weekly rest day (e.g., SUNDAY), or null if none.
     */
    fun evaluateCatchUp(
        lastQuestDate: String?,
        todayDate: String,
        lastQuestStatus: QuestStatus,
        currentStreak: Int,
        availableStreakShields: Int,
        difficultyMultiplier: Float = 1.0f,
        scheduledRestDay: java.time.DayOfWeek? = null
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
            lastQuestStatus == QuestStatus.PENALTY_CLEARED ||
            lastQuestStatus == QuestStatus.REST_DAY_EXEMPT

        if (daysBetween == 1) {
            return if (isLastQuestDone) {
                CatchUpResult.StreakContinued
            } else {
                if (scheduledRestDay != null && lastDate.dayOfWeek == scheduledRestDay) {
                    CatchUpResult.RestDayExempt
                } else if (availableStreakShields > 0) {
                    CatchUpResult.ShieldConsumed(shieldsRemaining = availableStreakShields - 1)
                } else {
                    CatchUpResult.PenaltyTriggered(
                        missedDaysCount = 1,
                        penaltyTaskDescription = buildPenaltyDescription(1, difficultyMultiplier)
                    )
                }
            }
        }

        // Multi-day gap (daysBetween > 1)
        val startDate = if (isLastQuestDone) lastDate.plusDays(1) else lastDate
        val endDate = today.minusDays(1)
        val missedDates = mutableListOf<LocalDate>()
        var d = startDate
        while (!d.isAfter(endDate)) {
            missedDates.add(d)
            d = d.plusDays(1)
        }

        var exemptCount = 0
        if (scheduledRestDay != null) {
            // Count exempt rest days (up to 1 per missed calendar week)
            val restDaysInGap = missedDates.count { it.dayOfWeek == scheduledRestDay }
            exemptCount = minOf(restDaysInGap, (missedDates.size + 6) / 7)
        }

        val chargeableMissedDays = maxOf(0, missedDates.size - exemptCount)
        if (chargeableMissedDays == 0) {
            return CatchUpResult.RestDayExempt
        }

        val safeShields = maxOf(0, availableStreakShields)

        return if (safeShields >= chargeableMissedDays) {
            CatchUpResult.ShieldConsumed(shieldsRemaining = safeShields - chargeableMissedDays)
        } else {
            CatchUpResult.PenaltyTriggered(
                missedDaysCount = chargeableMissedDays,
                penaltyTaskDescription = buildPenaltyDescription(chargeableMissedDays, difficultyMultiplier)
            )
        }
    }

    fun getPenaltyTaskDescription(difficultyMultiplier: Float): String = when {
        difficultyMultiplier <= 0.5f -> "SURVIVAL TRIAL: 30 BURPEES OR 2-MIN WALL SIT"
        difficultyMultiplier >= 1.5f -> "SURVIVAL TRIAL: 75 BURPEES OR 5-MIN WALL SIT"
        else -> "SURVIVAL TRIAL: 50 BURPEES OR 3-MIN WALL SIT"
    }

    fun buildPenaltyDescription(missedDaysCount: Int, difficultyMultiplier: Float = 1.0f): String {
        val task = getPenaltyTaskDescription(difficultyMultiplier)
        return "Survival Trial: $task ($missedDaysCount missed quest deck(s))"
    }
}
