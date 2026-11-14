package com.kunvarpreet.ascend.domain.engine

import com.kunvarpreet.ascend.domain.model.ExerciseTarget
import com.kunvarpreet.ascend.domain.model.ExerciseType
import com.kunvarpreet.ascend.domain.model.ExerciseUnit
import java.util.UUID

/**
 * Pure Kotlin generator for daily quest exercise targets.
 * Scales base workout volume (100 push-ups, 100 sit-ups, 100 squats, 3000m run)
 * by the hunter's regimen difficulty multiplier.
 */
object QuestGenerator {

    const val BASE_PUSH_UPS = 100f
    const val BASE_SIT_UPS = 100f
    const val BASE_SQUATS = 100f
    const val BASE_RUN_METERS = 3000f

    /**
     * Generates standard daily quest exercise targets scaled by [difficultyMultiplier].
     *
     * @param questId Foreign key ID of the parent daily quest.
     * @param difficultyMultiplier Regimen scaling factor (e.g. 0.5x, 1.0x, 1.5x).
     * @param strength Hunter strength attribute (reserved for workout difficulty scaling).
     * @param agility Hunter agility attribute (reserved for run distance/pace scaling).
     */
    fun generateDailyTargets(
        questId: String,
        difficultyMultiplier: Float = 1.0f,
        strength: Int = 10,
        agility: Int = 10
    ): List<ExerciseTarget> {
        val multiplier = if (difficultyMultiplier > 0f) difficultyMultiplier else 1.0f

        return listOf(
            ExerciseTarget(
                id = UUID.randomUUID().toString(),
                questId = questId,
                exerciseType = ExerciseType.PUSH_UPS,
                targetQuantity = BASE_PUSH_UPS * multiplier,
                completedQuantity = 0f,
                unit = ExerciseUnit.REPS,
                isCompleted = false
            ),
            ExerciseTarget(
                id = UUID.randomUUID().toString(),
                questId = questId,
                exerciseType = ExerciseType.SIT_UPS,
                targetQuantity = BASE_SIT_UPS * multiplier,
                completedQuantity = 0f,
                unit = ExerciseUnit.REPS,
                isCompleted = false
            ),
            ExerciseTarget(
                id = UUID.randomUUID().toString(),
                questId = questId,
                exerciseType = ExerciseType.SQUATS,
                targetQuantity = BASE_SQUATS * multiplier,
                completedQuantity = 0f,
                unit = ExerciseUnit.REPS,
                isCompleted = false
            ),
            ExerciseTarget(
                id = UUID.randomUUID().toString(),
                questId = questId,
                exerciseType = ExerciseType.RUNNING,
                targetQuantity = BASE_RUN_METERS * multiplier,
                completedQuantity = 0f,
                unit = ExerciseUnit.METERS,
                isCompleted = false
            )
        )
    }
}
