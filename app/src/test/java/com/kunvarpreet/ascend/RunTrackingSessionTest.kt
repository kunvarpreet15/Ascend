package com.kunvarpreet.ascend

import com.kunvarpreet.ascend.core.location.ActiveRunSessionState
import com.kunvarpreet.ascend.core.location.LatLng
import com.kunvarpreet.ascend.core.location.RunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTrackingSessionTest {

    @Test
    fun testDefaultStateIsIdle() {
        val state = ActiveRunSessionState()
        assertTrue(state.isIdle)
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals(0f, state.distanceMeters, 0.001f)
        assertEquals(0, state.elapsedSeconds)
        assertEquals(3000f, state.targetDistanceMeters, 0.001f)
        assertEquals(0f, state.progressFraction, 0.001f)
        assertEquals("--'--\" /km", state.paceFormatted)
        assertEquals("00:00", state.durationFormatted)
        assertEquals("0.00 / 3.00 km", state.distanceKmFormatted)
    }

    @Test
    fun testDurationFormatting() {
        val state1 = ActiveRunSessionState(elapsedSeconds = 65)
        assertEquals("01:05", state1.durationFormatted)

        val state2 = ActiveRunSessionState(elapsedSeconds = 3600)
        assertEquals("60:00", state2.durationFormatted)

        val state3 = ActiveRunSessionState(elapsedSeconds = 448)
        assertEquals("07:28", state3.durationFormatted)
    }

    @Test
    fun testPaceFormatting() {
        // Less than 50m: returns placeholder
        val stateShort = ActiveRunSessionState(
            distanceMeters = 30f,
            elapsedSeconds = 10,
            currentPaceSecPerKm = 333f
        )
        assertEquals("--'--\" /km", stateShort.paceFormatted)

        // Valid pace: 5 minutes 15 seconds (315 seconds) per km
        val statePace = ActiveRunSessionState(
            distanceMeters = 1000f,
            elapsedSeconds = 315,
            currentPaceSecPerKm = 315f
        )
        assertEquals("5'15\" /km", statePace.paceFormatted)

        // Valid pace: 4 minutes 02 seconds (242 seconds) per km
        val stateFast = ActiveRunSessionState(
            distanceMeters = 1500f,
            elapsedSeconds = 363,
            currentPaceSecPerKm = 242f
        )
        assertEquals("4'02\" /km", stateFast.paceFormatted)
    }

    @Test
    fun testDistanceFormattingAndProgress() {
        val state = ActiveRunSessionState(
            distanceMeters = 1420f,
            targetDistanceMeters = 3000f
        )
        assertEquals("1.42 / 3.00 km", state.distanceKmFormatted)
        assertEquals(1420f / 3000f, state.progressFraction, 0.001f)

        val completedState = ActiveRunSessionState(
            distanceMeters = 3500f,
            targetDistanceMeters = 3000f
        )
        assertEquals("3.50 / 3.00 km", completedState.distanceKmFormatted)
        assertEquals(1.0f, completedState.progressFraction, 0.001f)
    }

    @Test
    fun testStateBooleans() {
        val running = ActiveRunSessionState(state = RunState.RUNNING)
        assertTrue(running.isRunning)
        assertFalse(running.isPaused)
        assertFalse(running.isIdle)

        val paused = ActiveRunSessionState(state = RunState.PAUSED)
        assertFalse(paused.isRunning)
        assertTrue(paused.isPaused)
        assertFalse(paused.isIdle)

        val idle = ActiveRunSessionState(state = RunState.IDLE)
        assertFalse(idle.isRunning)
        assertFalse(idle.isPaused)
        assertTrue(idle.isIdle)
    }

    @Test
    fun testWaypointsAccumulation() {
        val pt1 = LatLng(37.4220, -122.0841, 1000L)
        val pt2 = LatLng(37.4225, -122.0845, 2000L)
        val state = ActiveRunSessionState(
            waypoints = listOf(pt1, pt2)
        )
        assertEquals(2, state.waypoints.size)
        assertEquals(37.4220, state.waypoints[0].latitude, 0.0001)
        assertEquals(-122.0845, state.waypoints[1].longitude, 0.0001)
    }
}
