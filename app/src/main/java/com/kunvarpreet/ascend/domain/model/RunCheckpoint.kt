package com.kunvarpreet.ascend.domain.model

data class RunCheckpoint(
    val sessionId: String,
    val targetId: String,
    val distanceMeters: Float,
    val durationSeconds: Int,
    val avgPaceSecPerKm: Float,
    val caloriesBurned: Int,
    val gpsPathGeoJson: String,
    val createdAt: String
) {
    val distanceKmFormatted: String
        get() = String.format(java.util.Locale.US, "%.2f km", distanceMeters / 1000f)

    val durationFormatted: String
        get() = String.format(java.util.Locale.US, "%02d:%02d", durationSeconds / 60, durationSeconds % 60)
}
