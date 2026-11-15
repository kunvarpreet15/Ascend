package com.kunvarpreet.ascend.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kunvarpreet.ascend.core.sound.SoundFeedbackHelper
import com.kunvarpreet.ascend.core.worker.DailyDeadlineReminderWorker
import com.kunvarpreet.ascend.data.repository.HunterRepository
import com.kunvarpreet.ascend.domain.model.RestDayOfWeek
import com.kunvarpreet.ascend.domain.model.UnitSystem
import com.kunvarpreet.ascend.domain.model.UserPreferences
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val hunterRepo = HunterRepository(application)

    val preferences: StateFlow<UserPreferences> = hunterRepo.preferencesState

    fun setUnitSystem(unitSystem: UnitSystem): Boolean {
        val current = preferences.value
        val updated = current.copy(unitSystem = unitSystem)
        return hunterRepo.saveUserPreferences(updated)
    }

    fun setScheduledRestDay(restDay: RestDayOfWeek): Boolean {
        val current = preferences.value
        val updated = current.copy(scheduledRestDay = restDay)
        return hunterRepo.saveUserPreferences(updated)
    }

    fun setDailyReminder(enabled: Boolean, hour: Int = preferences.value.dailyReminderHour): Boolean {
        val current = preferences.value
        val updated = current.copy(
            dailyReminderEnabled = enabled,
            dailyReminderHour = hour.coerceIn(0, 23)
        )
        val success = hunterRepo.saveUserPreferences(updated)
        if (success) {
            DailyDeadlineReminderWorker.schedule(
                context = getApplication(),
                targetHour = updated.dailyReminderHour,
                enabled = updated.dailyReminderEnabled
            )
        }
        return success
    }

    fun setDailyReminderHour(hour: Int): Boolean {
        return setDailyReminder(enabled = preferences.value.dailyReminderEnabled, hour = hour)
    }

    fun setSoundEffects(enabled: Boolean): Boolean {
        val current = preferences.value
        val updated = current.copy(soundEffectsEnabled = enabled)
        val success = hunterRepo.saveUserPreferences(updated)
        if (success) {
            SoundFeedbackHelper.isSoundEnabled = enabled
        }
        return success
    }
}
