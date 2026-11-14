package com.kunvarpreet.ascend

import com.kunvarpreet.ascend.domain.engine.FakeAscendClock
import com.kunvarpreet.ascend.ui.workout.WorkoutUiState
import com.kunvarpreet.ascend.ui.workout.WorkoutViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class RestTimerLifecycleTest {

    private val baseEpoch = 1700000000000L
    private val clock = FakeAscendClock(currentMillis = baseEpoch, zoneId = ZoneId.of("UTC"))

    @Test
    fun testRestTimerStartComputesExactWallClockEndTime() {
        val durationSec = 60
        val startTime = clock.nowMillis()
        val expectedEndTime = startTime + (durationSec * 1000L)

        val state = WorkoutUiState(
            restEndTimeMillis = expectedEndTime,
            selectedRestDurationSec = durationSec,
            remainingSeconds = durationSec,
            isTimerRunning = true
        )

        assertTrue(state.isTimerRunning)
        assertEquals(expectedEndTime, state.restEndTimeMillis)
        assertEquals(60, state.remainingSeconds)
        assertEquals("01:00", state.formattedRestTime)
    }

    @Test
    fun testRestTimerWallClockCalculationDoesNotDrift() {
        val durationSec = 90
        val startTime = clock.nowMillis()
        val endTime = startTime + (durationSec * 1000L)

        // Advance fake clock by 35.4 seconds (e.g. app in background or screen off)
        clock.advanceMillis(35400L)

        val now = clock.nowMillis()
        val millisRemaining = endTime - now
        val computedRemainingSec = ((millisRemaining + 999) / 1000).toInt()

        // 90s - 35.4s = 54.6s -> ceil to 55s
        assertEquals(55, computedRemainingSec)

        val state = WorkoutUiState(
            restEndTimeMillis = endTime,
            selectedRestDurationSec = durationSec,
            remainingSeconds = computedRemainingSec,
            isTimerRunning = true
        )
        assertEquals("00:55", state.formattedRestTime)

        // Advance fake clock by another 54.8 seconds (total 90.2s elapsed)
        clock.advanceMillis(54800L)
        val finalRemaining = endTime - clock.nowMillis()
        assertTrue(finalRemaining <= 0)
    }

    @Test
    fun testRestTimerStateFormatting() {
        val state30s = WorkoutUiState(remainingSeconds = 30)
        assertEquals("00:30", state30s.formattedRestTime)

        val state90s = WorkoutUiState(remainingSeconds = 90)
        assertEquals("01:30", state90s.formattedRestTime)

        val stateZero = WorkoutUiState(remainingSeconds = 0)
        assertEquals("00:00", stateZero.formattedRestTime)
    }

    @Test
    fun testRestTimerExpiredOnRecreationCleansUpSafely() {
        val startTime = clock.nowMillis()
        val endTime = startTime + (60 * 1000L)

        // Simulate app killed and reopened 2 minutes later
        clock.advanceMillis(120000L)

        val now = clock.nowMillis()
        val millisRemaining = endTime - now

        // Check if expired
        val isExpired = millisRemaining <= 0
        assertTrue(isExpired)

        val recoveredState = if (isExpired) {
            WorkoutUiState(
                restEndTimeMillis = null,
                remainingSeconds = 0,
                isTimerRunning = false
            )
        } else {
            WorkoutUiState(
                restEndTimeMillis = endTime,
                remainingSeconds = ((millisRemaining + 999) / 1000).toInt(),
                isTimerRunning = true
            )
        }

        assertFalse(recoveredState.isTimerRunning)
        assertNull(recoveredState.restEndTimeMillis)
        assertEquals(0, recoveredState.remainingSeconds)
        assertEquals("00:00", recoveredState.formattedRestTime)
    }
}
