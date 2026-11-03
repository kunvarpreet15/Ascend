package com.kunvarpreet.ascend.domain.model

import java.util.UUID

enum class QuestStatus {
    PENDING,
    COMPLETED,
    FAILED,
    PENALTY_CLEARED
}

enum class ExerciseType(val displayName: String, val defaultTarget: Float, val unit: ExerciseUnit) {
    PUSH_UPS("Push-ups", 100f, ExerciseUnit.REPS),
    SIT_UPS("Sit-ups", 100f, ExerciseUnit.REPS),
    SQUATS("Squats", 100f, ExerciseUnit.REPS),
    RUNNING("Distance Run", 3000f, ExerciseUnit.METERS),
    CUSTOM("Custom Task", 50f, ExerciseUnit.REPS)
}

enum class ExerciseUnit(val displaySuffix: String) {
    REPS("Reps"),
    METERS("m"),
    SECONDS("s")
}

data class ExerciseTarget(
    val id: String = UUID.randomUUID().toString(),
    val questId: String,
    val exerciseType: ExerciseType,
    val targetQuantity: Float,
    val completedQuantity: Float = 0f,
    val unit: ExerciseUnit = ExerciseUnit.REPS,
    val isCompleted: Boolean = false
) {
    val progress: Float
        get() = if (targetQuantity > 0) (completedQuantity / targetQuantity).coerceIn(0f, 1f) else 0f

    val formattedTarget: String
        get() = when (unit) {
            ExerciseUnit.METERS -> {
                val km = targetQuantity / 1000f
                "%.1f km".format(km)
            }
            ExerciseUnit.REPS -> "%.0f Reps".format(targetQuantity)
            ExerciseUnit.SECONDS -> "%.0fs".format(targetQuantity)
        }

    val formattedCompleted: String
        get() = when (unit) {
            ExerciseUnit.METERS -> {
                val km = completedQuantity / 1000f
                "%.1f km".format(km)
            }
            ExerciseUnit.REPS -> "%.0f".format(completedQuantity)
            ExerciseUnit.SECONDS -> "%.0fs".format(completedQuantity)
        }
}

data class DailyQuest(
    val id: String = UUID.randomUUID().toString(),
    val hunterId: String,
    val questDate: String, // ISO YYYY-MM-DD
    val status: QuestStatus = QuestStatus.PENDING,
    val xpReward: Int = 100,
    val goldReward: Int = 50,
    val completedAt: Long? = null,
    val targets: List<ExerciseTarget> = emptyList()
) {
    val totalProgress: Float
        get() = if (targets.isNotEmpty()) targets.map { it.progress }.average().toFloat() else 0f

    val isAllCompleted: Boolean
        get() = targets.isNotEmpty() && targets.all { it.isCompleted }
}
