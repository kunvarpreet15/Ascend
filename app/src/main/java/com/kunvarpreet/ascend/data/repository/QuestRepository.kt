package com.kunvarpreet.ascend.data.repository

import android.content.Context
import com.kunvarpreet.ascend.data.local.AscendDatabaseHelper
import com.kunvarpreet.ascend.domain.model.DailyQuest
import com.kunvarpreet.ascend.domain.model.QuestRewardEvent
import com.kunvarpreet.ascend.domain.model.RunCheckpoint
import kotlinx.coroutines.flow.StateFlow

class QuestRepository(context: Context) {
    private val dbHelper = AscendDatabaseHelper.getInstance(context)

    val todayQuest: StateFlow<DailyQuest?> = dbHelper.todayQuestState
    val rewardEvent: StateFlow<QuestRewardEvent?> = dbHelper.rewardEvent

    fun dismissRewardEvent() {
        dbHelper.dismissRewardEvent()
    }

    fun logRepetitionProgress(targetId: String, increment: Float): Boolean {
        return dbHelper.addProgressToTarget(targetId, increment)
    }

    fun logRunGps(
        targetId: String,
        distanceMeters: Float,
        durationSeconds: Int,
        avgPaceSecPerKm: Float,
        caloriesBurned: Int,
        gpsPathGeoJson: String = "[]"
    ): Boolean {
        return dbHelper.logRunGps(targetId, distanceMeters, durationSeconds, avgPaceSecPerKm, caloriesBurned, gpsPathGeoJson)
    }

    fun checkpointRunGps(
        sessionId: String,
        targetId: String,
        distanceMeters: Float,
        durationSeconds: Int,
        avgPaceSecPerKm: Float,
        caloriesBurned: Int,
        gpsPathGeoJson: String = "[]"
    ): Boolean {
        return dbHelper.checkpointRunGps(sessionId, targetId, distanceMeters, durationSeconds, avgPaceSecPerKm, caloriesBurned, gpsPathGeoJson)
    }

    fun finalizeRunGps(
        sessionId: String,
        targetId: String,
        distanceMeters: Float,
        durationSeconds: Int,
        avgPaceSecPerKm: Float,
        caloriesBurned: Int,
        gpsPathGeoJson: String = "[]"
    ): Boolean {
        return dbHelper.finalizeRunGps(sessionId, targetId, distanceMeters, durationSeconds, avgPaceSecPerKm, caloriesBurned, gpsPathGeoJson)
    }

    fun getActiveInProgressRun(targetId: String): RunCheckpoint? {
        return dbHelper.getActiveInProgressRun(targetId)
    }

    fun finalizeOrDiscardRunCheckpoint(
        sessionId: String,
        finalizeAndSave: Boolean,
        targetId: String = ""
    ): Boolean {
        return dbHelper.finalizeOrDiscardRunCheckpoint(sessionId, finalizeAndSave, targetId)
    }

    fun refresh() {
        dbHelper.refreshCache()
    }
}
