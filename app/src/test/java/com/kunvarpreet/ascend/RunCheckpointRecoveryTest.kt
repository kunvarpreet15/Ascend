package com.kunvarpreet.ascend

import com.kunvarpreet.ascend.core.location.LatLng
import com.kunvarpreet.ascend.domain.model.RunCheckpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCheckpointRecoveryTest {

    @Test
    fun testRunCheckpointFormatting() {
        val checkpoint = RunCheckpoint(
            sessionId = "session-test-123",
            targetId = "target-run-456",
            distanceMeters = 1850f,
            durationSeconds = 545,
            avgPaceSecPerKm = 294.6f,
            caloriesBurned = 120,
            gpsPathGeoJson = "[{\"lat\":37.422,\"lng\":-122.084}]",
            createdAt = "2026-11-14T10:30:00Z"
        )

        assertEquals("1.85 km", checkpoint.distanceKmFormatted)
        assertEquals("09:05", checkpoint.durationFormatted)
        assertEquals("session-test-123", checkpoint.sessionId)
        assertEquals(1850f, checkpoint.distanceMeters, 0.001f)
    }

    @Test
    fun testGeoJsonWaypointsParsing() {
        val geoJson = """
            [
                {"lat":37.422000,"lng":-122.084000},
                {"lat":37.422500,"lng":-122.084500},
                {"lat":-33.8688,"lng":151.2093}
            ]
        """.trimIndent()

        val waypoints = parseGeoJsonWaypoints(geoJson)

        assertEquals(3, waypoints.size)
        assertEquals(37.422000, waypoints[0].latitude, 0.00001)
        assertEquals(-122.084000, waypoints[0].longitude, 0.00001)
        assertEquals(37.422500, waypoints[1].latitude, 0.00001)
        assertEquals(-122.084500, waypoints[1].longitude, 0.00001)
        assertEquals(-33.8688, waypoints[2].latitude, 0.00001)
        assertEquals(151.2093, waypoints[2].longitude, 0.00001)
    }

    @Test
    fun testEmptyOrInvalidGeoJsonWaypointsParsing() {
        assertTrue(parseGeoJsonWaypoints("").isEmpty())
        assertTrue(parseGeoJsonWaypoints("[]").isEmpty())
        assertTrue(parseGeoJsonWaypoints("invalid json").isEmpty())
    }

    @Test
    fun testCheckpointRecoveryDecisionLogic() {
        var isSessionFinalized = false
        var isSessionDiscarded = false
        var progressAwardedMeters = 0f

        fun handleCheckpoint(checkpoint: RunCheckpoint, action: String) {
            when (action) {
                "RESUME" -> {
                    // Resumes without finalizing or discarding
                }
                "SAVE" -> {
                    isSessionFinalized = true
                    progressAwardedMeters += checkpoint.distanceMeters
                }
                "DISCARD" -> {
                    isSessionDiscarded = true
                }
            }
        }

        val cp = RunCheckpoint(
            sessionId = "s1",
            targetId = "t1",
            distanceMeters = 1500f,
            durationSeconds = 450,
            avgPaceSecPerKm = 300f,
            caloriesBurned = 95,
            gpsPathGeoJson = "[]",
            createdAt = "2026-11-14T09:00:00Z"
        )

        // Test SAVE action
        handleCheckpoint(cp, "SAVE")
        assertTrue(isSessionFinalized)
        assertEquals(1500f, progressAwardedMeters, 0.001f)

        // Test DISCARD action
        handleCheckpoint(cp, "DISCARD")
        assertTrue(isSessionDiscarded)
    }

    private fun parseGeoJsonWaypoints(geoJson: String): List<LatLng> {
        if (geoJson.isBlank() || geoJson == "[]") return emptyList()
        val waypoints = mutableListOf<LatLng>()
        val regex = """\{"lat":\s*([0-9.-]+)\s*,\s*"lng":\s*([0-9.-]+)\}""".toRegex()
        for (match in regex.findAll(geoJson)) {
            val lat = match.groupValues[1].toDoubleOrNull()
            val lng = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) {
                waypoints.add(LatLng(lat, lng))
            }
        }
        return waypoints
    }
}
