package com.kunvarpreet.ascend

import com.kunvarpreet.ascend.domain.engine.CatchUpEvaluator
import com.kunvarpreet.ascend.domain.engine.CatchUpResult
import com.kunvarpreet.ascend.domain.engine.FakeAscendClock
import com.kunvarpreet.ascend.domain.model.QuestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeAndCatchUpTest {

    // =========================================================================
    // 1. Midnight Rollover (23:59:59 -> 00:00:01)
    // =========================================================================

    @Test
    fun `test midnight rollover flips date and continues streak for yesterday completed quest`() {
        val clock = FakeAscendClock()
        // Set clock to 23:59:59 UTC on 2026-11-11
        clock.setTime(Instant.parse("2026-11-11T23:59:59Z").toEpochMilli())
        assertEquals("2026-11-11", clock.todayIsoDate())

        // Advance 2 seconds into midnight
        clock.advanceMillis(2000L)
        assertEquals("2026-11-12", clock.todayIsoDate())

        val result = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-11",
            todayDate = clock.todayIsoDate(),
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 10,
            availableStreakShields = 0
        )

        assertEquals(CatchUpResult.StreakContinued, result)
    }

    // =========================================================================
    // 2. Daylight Saving Time (DST) Transitions
    // =========================================================================

    @Test
    fun `test DST spring-forward 23-hour day maintains streak without false reset`() {
        val nyZone = ZoneId.of("America/New_York")
        val clock = FakeAscendClock(zoneId = nyZone)

        // Set to noon on March 8, 2026 (day of 23h spring forward in US)
        val march8Noon = ZonedDateTime.of(2026, 3, 8, 12, 0, 0, 0, nyZone)
        clock.setTime(march8Noon.toInstant().toEpochMilli())
        assertEquals("2026-03-08", clock.todayIsoDate())

        // Advance 24 hours
        clock.advanceHours(24)
        assertEquals("2026-03-09", clock.todayIsoDate())

        val result = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-03-08",
            todayDate = clock.todayIsoDate(),
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 15,
            availableStreakShields = 0
        )

        assertEquals(CatchUpResult.StreakContinued, result)
    }

    @Test
    fun `test DST fall-back 25-hour day avoids duplicate quest generation and maintains streak`() {
        val nyZone = ZoneId.of("America/New_York")
        val clock = FakeAscendClock(zoneId = nyZone)

        // Set to noon on November 1, 2026 (day of 25h fall-back in US)
        val nov1Noon = ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, nyZone)
        clock.setTime(nov1Noon.toInstant().toEpochMilli())
        assertEquals("2026-11-01", clock.todayIsoDate())

        // Advance 25 hours across the transition into next day
        clock.advanceHours(25)
        assertEquals("2026-11-02", clock.todayIsoDate())

        val result = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-01",
            todayDate = clock.todayIsoDate(),
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 20,
            availableStreakShields = 0
        )

        assertEquals(CatchUpResult.StreakContinued, result)
    }

    // =========================================================================
    // 3. Timezone Travel Shifts
    // =========================================================================

    @Test
    fun `test timezone shift from Asia Kolkata to America New York prevents false reset`() {
        val kolkataZone = ZoneId.of("Asia/Kolkata")
        val nyZone = ZoneId.of("America/New_York")

        val clock = FakeAscendClock(zoneId = kolkataZone)
        // 20:00 IST on 2026-11-11 (14:30 UTC)
        val eveningKolkata = ZonedDateTime.of(2026, 11, 11, 20, 0, 0, 0, kolkataZone)
        clock.setTime(eveningKolkata.toInstant().toEpochMilli())
        val kolkataQuestDate = clock.todayIsoDate()
        assertEquals("2026-11-11", kolkataQuestDate)

        // Hunter travels westward to New York (UTC-5)
        // At 14:30 UTC, New York local time is 09:30 AM on 2026-11-11
        clock.setZoneId(nyZone)
        assertEquals("2026-11-11", clock.todayIsoDate())

        // Check continuity on same calendar date in NY
        val sameDayResult = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = kolkataQuestDate,
            todayDate = clock.todayIsoDate(),
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 12,
            availableStreakShields = 0
        )
        assertEquals(CatchUpResult.NoOpSameDay, sameDayResult)

        // Next day in New York (advance 24h)
        clock.advanceHours(24)
        assertEquals("2026-11-12", clock.todayIsoDate())

        val nextDayResult = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = kolkataQuestDate,
            todayDate = clock.todayIsoDate(),
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 12,
            availableStreakShields = 0
        )
        assertEquals(CatchUpResult.StreakContinued, nextDayResult)
    }

    // =========================================================================
    // 4. App-Closed Gaps: 1-Day, 3-Day, and 14-Day
    // =========================================================================

    @Test
    fun `test 1-day missed quest gap consumes shield or triggers penalty`() {
        // With Streak Shield
        val protectedResult = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-10",
            todayDate = "2026-11-11",
            lastQuestStatus = QuestStatus.PENDING,
            currentStreak = 5,
            availableStreakShields = 1
        )
        assertTrue(protectedResult is CatchUpResult.ShieldConsumed)
        assertEquals(0, (protectedResult as CatchUpResult.ShieldConsumed).shieldsRemaining)

        // Without Streak Shield
        val penaltyResult = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-10",
            todayDate = "2026-11-11",
            lastQuestStatus = QuestStatus.PENDING,
            currentStreak = 5,
            availableStreakShields = 0
        )
        assertTrue(penaltyResult is CatchUpResult.PenaltyTriggered)
        assertEquals(1, (penaltyResult as CatchUpResult.PenaltyTriggered).missedDaysCount)
    }

    @Test
    fun `test 3-day gap evaluates 2 missed days with sufficient and insufficient shields`() {
        // Last completed on Nov 08, today is Nov 11 -> missed Nov 09 and Nov 10 (2 days)
        // With 3 shields (sufficient)
        val sufficientResult = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-08",
            todayDate = "2026-11-11",
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 25,
            availableStreakShields = 3
        )
        assertTrue(sufficientResult is CatchUpResult.ShieldConsumed)
        assertEquals(1, (sufficientResult as CatchUpResult.ShieldConsumed).shieldsRemaining)

        // With 1 shield (insufficient for 2 missed days -> triggers penalty)
        val insufficientResult = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-08",
            todayDate = "2026-11-11",
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 25,
            availableStreakShields = 1
        )
        assertTrue(insufficientResult is CatchUpResult.PenaltyTriggered)
        val penalty = insufficientResult as CatchUpResult.PenaltyTriggered
        assertEquals(2, penalty.missedDaysCount)
        assertTrue(penalty.penaltyTaskDescription.contains("2 missed quest deck(s)"))
    }

    @Test
    fun `test 14-day extended absence evaluates 13 missed days`() {
        // Last completed on Nov 01, today is Nov 15 -> missed 13 days
        // With 15 shields (ample protection)
        val ampleResult = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-01",
            todayDate = "2026-11-15",
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 40,
            availableStreakShields = 15
        )
        assertTrue(ampleResult is CatchUpResult.ShieldConsumed)
        assertEquals(2, (ampleResult as CatchUpResult.ShieldConsumed).shieldsRemaining)

        // With 0 shields (severe absence)
        val unshieldedResult = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-01",
            todayDate = "2026-11-15",
            lastQuestStatus = QuestStatus.COMPLETED,
            currentStreak = 40,
            availableStreakShields = 0
        )
        assertTrue(unshieldedResult is CatchUpResult.PenaltyTriggered)
        val penalty = unshieldedResult as CatchUpResult.PenaltyTriggered
        assertEquals(13, penalty.missedDaysCount)
        assertTrue(penalty.penaltyTaskDescription.contains("13 missed quest deck(s)"))
    }
}
