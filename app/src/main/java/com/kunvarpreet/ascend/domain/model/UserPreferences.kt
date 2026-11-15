package com.kunvarpreet.ascend.domain.model

enum class UnitSystem {
    METRIC,
    IMPERIAL
}

enum class RestDayOfWeek {
    NONE,
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}

data class UserPreferences(
    val hunterId: String = "",
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val scheduledRestDay: RestDayOfWeek = RestDayOfWeek.NONE,
    val dailyReminderEnabled: Boolean = true,
    val dailyReminderHour: Int = 22,
    val soundEffectsEnabled: Boolean = true
)
