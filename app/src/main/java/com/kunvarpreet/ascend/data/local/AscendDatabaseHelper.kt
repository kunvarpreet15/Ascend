package com.kunvarpreet.ascend.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.kunvarpreet.ascend.domain.model.AttributeStats
import com.kunvarpreet.ascend.domain.model.DailyQuest
import com.kunvarpreet.ascend.domain.model.ExerciseTarget
import com.kunvarpreet.ascend.domain.model.ExerciseType
import com.kunvarpreet.ascend.domain.model.ExerciseUnit
import com.kunvarpreet.ascend.domain.model.HunterProfile
import com.kunvarpreet.ascend.domain.model.QuestStatus
import com.kunvarpreet.ascend.domain.model.RankTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AscendDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val _hunterState = MutableStateFlow<HunterProfile?>(null)
    val hunterState: StateFlow<HunterProfile?> = _hunterState.asStateFlow()

    private val _todayQuestState = MutableStateFlow<DailyQuest?>(null)
    val todayQuestState: StateFlow<DailyQuest?> = _todayQuestState.asStateFlow()

    companion object {
        const val DATABASE_NAME = "ascend_rpg.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var instance: AscendDatabaseHelper? = null

        fun getInstance(context: Context): AscendDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: AscendDatabaseHelper(context.applicationContext).also {
                    instance = it
                    it.refreshCache()
                }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Table: hunter_profiles
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS hunter_profiles (
                id TEXT PRIMARY KEY NOT NULL,
                alias TEXT NOT NULL,
                avatar_id TEXT NOT NULL DEFAULT 'default_hunter',
                level INTEGER NOT NULL DEFAULT 1,
                current_xp INTEGER NOT NULL DEFAULT 0,
                xp_to_next_level INTEGER NOT NULL DEFAULT 100,
                rank_tier TEXT NOT NULL DEFAULT 'E',
                available_stat_points INTEGER NOT NULL DEFAULT 0,
                current_streak INTEGER NOT NULL DEFAULT 1,
                longest_streak INTEGER NOT NULL DEFAULT 1,
                gold_balance INTEGER NOT NULL DEFAULT 100,
                height_cm REAL NOT NULL DEFAULT 175.0,
                weight_kg REAL NOT NULL DEFAULT 70.0,
                is_onboarded INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
        """.trimIndent())

        // Table: attribute_stats
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS attribute_stats (
                hunter_id TEXT PRIMARY KEY NOT NULL,
                strength INTEGER NOT NULL DEFAULT 10,
                agility INTEGER NOT NULL DEFAULT 10,
                sense INTEGER NOT NULL DEFAULT 10,
                vitality INTEGER NOT NULL DEFAULT 10,
                intelligence INTEGER NOT NULL DEFAULT 10,
                FOREIGN KEY (hunter_id) REFERENCES hunter_profiles(id) ON DELETE CASCADE
            );
        """.trimIndent())

        // Table: daily_quests
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS daily_quests (
                id TEXT PRIMARY KEY NOT NULL,
                hunter_id TEXT NOT NULL,
                quest_date TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                xp_reward INTEGER NOT NULL DEFAULT 100,
                gold_reward INTEGER NOT NULL DEFAULT 50,
                completed_at INTEGER,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (hunter_id) REFERENCES hunter_profiles(id) ON DELETE CASCADE
            );
        """.trimIndent())

        // Table: exercise_targets
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS exercise_targets (
                id TEXT PRIMARY KEY NOT NULL,
                quest_id TEXT NOT NULL,
                exercise_type TEXT NOT NULL,
                target_quantity REAL NOT NULL,
                completed_quantity REAL NOT NULL DEFAULT 0.0,
                unit TEXT NOT NULL DEFAULT 'REPS',
                is_completed INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (quest_id) REFERENCES daily_quests(id) ON DELETE CASCADE
            );
        """.trimIndent())

        // Table: workout_session_logs
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS workout_session_logs (
                id TEXT PRIMARY KEY NOT NULL,
                target_id TEXT NOT NULL,
                exercise_type TEXT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER NOT NULL,
                total_reps_or_meters REAL NOT NULL,
                duration_seconds INTEGER NOT NULL,
                sets_data_json TEXT NOT NULL DEFAULT '[]',
                created_at INTEGER NOT NULL,
                FOREIGN KEY (target_id) REFERENCES exercise_targets(id) ON DELETE CASCADE
            );
        """.trimIndent())

        // Seed initial data
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration logic for future schema increments
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val hunterId = UUID.randomUUID().toString()
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))

        // Create Hunter
        val hunterValues = ContentValues().apply {
            put("id", hunterId)
            put("alias", "SHADOW_HUNTER")
            put("avatar_id", "default_hunter")
            put("level", 1)
            put("current_xp", 0)
            put("xp_to_next_level", 100)
            put("rank_tier", "E")
            put("available_stat_points", 0)
            put("current_streak", 1)
            put("longest_streak", 1)
            put("gold_balance", 100)
            put("height_cm", 175.0)
            put("weight_kg", 70.0)
            put("is_onboarded", 1)
            put("created_at", now)
            put("updated_at", now)
        }
        db.insert("hunter_profiles", null, hunterValues)

        // Create Attribute Stats
        val statValues = ContentValues().apply {
            put("hunter_id", hunterId)
            put("strength", 10)
            put("agility", 10)
            put("sense", 10)
            put("vitality", 10)
            put("intelligence", 10)
        }
        db.insert("attribute_stats", null, statValues)

        // Create Today's Quest Deck
        val questId = UUID.randomUUID().toString()
        val questValues = ContentValues().apply {
            put("id", questId)
            put("hunter_id", hunterId)
            put("quest_date", todayIso)
            put("status", "PENDING")
            put("xp_reward", 100)
            put("gold_reward", 50)
            put("created_at", now)
        }
        db.insert("daily_quests", null, questValues)

        // Create 4 standard targets: Push-ups (100), Sit-ups (100), Squats (100), Run (3.0 km)
        val targets = listOf(
            Triple(ExerciseType.PUSH_UPS, 100f, ExerciseUnit.REPS),
            Triple(ExerciseType.SIT_UPS, 100f, ExerciseUnit.REPS),
            Triple(ExerciseType.SQUATS, 100f, ExerciseUnit.REPS),
            Triple(ExerciseType.RUNNING, 3000f, ExerciseUnit.METERS)
        )

        for ((type, quantity, unit) in targets) {
            val targetValues = ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("quest_id", questId)
                put("exercise_type", type.name)
                put("target_quantity", quantity)
                put("completed_quantity", 0f)
                put("unit", unit.name)
                put("is_completed", 0)
            }
            db.insert("exercise_targets", null, targetValues)
        }
    }

    fun refreshCache() {
        val db = readableDatabase
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Query Hunter
        val hunterCursor: Cursor = db.rawQuery("SELECT * FROM hunter_profiles LIMIT 1", null)
        if (hunterCursor.moveToFirst()) {
            val profile = HunterProfile(
                id = hunterCursor.getString(hunterCursor.getColumnIndexOrThrow("id")),
                alias = hunterCursor.getString(hunterCursor.getColumnIndexOrThrow("alias")),
                avatarId = hunterCursor.getString(hunterCursor.getColumnIndexOrThrow("avatar_id")),
                level = hunterCursor.getInt(hunterCursor.getColumnIndexOrThrow("level")),
                currentXp = hunterCursor.getLong(hunterCursor.getColumnIndexOrThrow("current_xp")),
                xpToNextLevel = hunterCursor.getLong(hunterCursor.getColumnIndexOrThrow("xp_to_next_level")),
                rankTier = try {
                    RankTier.valueOf(hunterCursor.getString(hunterCursor.getColumnIndexOrThrow("rank_tier")))
                } catch (e: Exception) { RankTier.E },
                availableStatPoints = hunterCursor.getInt(hunterCursor.getColumnIndexOrThrow("available_stat_points")),
                currentStreak = hunterCursor.getInt(hunterCursor.getColumnIndexOrThrow("current_streak")),
                longestStreak = hunterCursor.getInt(hunterCursor.getColumnIndexOrThrow("longest_streak")),
                goldBalance = hunterCursor.getLong(hunterCursor.getColumnIndexOrThrow("gold_balance")),
                heightCm = hunterCursor.getFloat(hunterCursor.getColumnIndexOrThrow("height_cm")),
                weightKg = hunterCursor.getFloat(hunterCursor.getColumnIndexOrThrow("weight_kg")),
                isOnboarded = hunterCursor.getInt(hunterCursor.getColumnIndexOrThrow("is_onboarded")) == 1
            )
            _hunterState.value = profile
        }
        hunterCursor.close()

        // Query Today's Quest
        val questCursor = db.rawQuery("SELECT * FROM daily_quests WHERE quest_date = ? LIMIT 1", arrayOf(todayIso))
        if (questCursor.moveToFirst()) {
            val questId = questCursor.getString(questCursor.getColumnIndexOrThrow("id"))
            val statusStr = questCursor.getString(questCursor.getColumnIndexOrThrow("status"))
            val xpReward = questCursor.getInt(questCursor.getColumnIndexOrThrow("xp_reward"))
            val goldReward = questCursor.getInt(questCursor.getColumnIndexOrThrow("gold_reward"))
            val completedAt = if (questCursor.isNull(questCursor.getColumnIndexOrThrow("completed_at"))) null
                              else questCursor.getLong(questCursor.getColumnIndexOrThrow("completed_at"))

            // Query Targets
            val targetsList = mutableListOf<ExerciseTarget>()
            val targetCursor = db.rawQuery("SELECT * FROM exercise_targets WHERE quest_id = ?", arrayOf(questId))
            while (targetCursor.moveToNext()) {
                val targetId = targetCursor.getString(targetCursor.getColumnIndexOrThrow("id"))
                val exTypeStr = targetCursor.getString(targetCursor.getColumnIndexOrThrow("exercise_type"))
                val targetQty = targetCursor.getFloat(targetCursor.getColumnIndexOrThrow("target_quantity"))
                val compQty = targetCursor.getFloat(targetCursor.getColumnIndexOrThrow("completed_quantity"))
                val unitStr = targetCursor.getString(targetCursor.getColumnIndexOrThrow("unit"))
                val isDone = targetCursor.getInt(targetCursor.getColumnIndexOrThrow("is_completed")) == 1

                targetsList.add(
                    ExerciseTarget(
                        id = targetId,
                        questId = questId,
                        exerciseType = try { ExerciseType.valueOf(exTypeStr) } catch (e: Exception) { ExerciseType.CUSTOM },
                        targetQuantity = targetQty,
                        completedQuantity = compQty,
                        unit = try { ExerciseUnit.valueOf(unitStr) } catch (e: Exception) { ExerciseUnit.REPS },
                        isCompleted = isDone
                    )
                )
            }
            targetCursor.close()

            val quest = DailyQuest(
                id = questId,
                hunterId = _hunterState.value?.id ?: "",
                questDate = todayIso,
                status = try { QuestStatus.valueOf(statusStr) } catch (e: Exception) { QuestStatus.PENDING },
                xpReward = xpReward,
                goldReward = goldReward,
                completedAt = completedAt,
                targets = targetsList
            )
            _todayQuestState.value = quest
        }
        questCursor.close()
    }

    fun addProgressToTarget(targetId: String, increment: Float): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT completed_quantity, target_quantity, quest_id FROM exercise_targets WHERE id = ?", arrayOf(targetId))
            if (cursor.moveToFirst()) {
                val current = cursor.getFloat(cursor.getColumnIndexOrThrow("completed_quantity"))
                val target = cursor.getFloat(cursor.getColumnIndexOrThrow("target_quantity"))
                val questId = cursor.getString(cursor.getColumnIndexOrThrow("quest_id"))
                cursor.close()

                val newCompleted = current + increment
                val isCompleted = if (newCompleted >= target) 1 else 0

                val values = ContentValues().apply {
                    put("completed_quantity", newCompleted)
                    put("is_completed", isCompleted)
                }
                db.update("exercise_targets", values, "id = ?", arrayOf(targetId))

                // Check if all targets for this quest are completed
                val remainingCursor = db.rawQuery("SELECT COUNT(*) FROM exercise_targets WHERE quest_id = ? AND is_completed = 0", arrayOf(questId))
                if (remainingCursor.moveToFirst() && remainingCursor.getInt(0) == 0) {
                    val questValues = ContentValues().apply {
                        put("status", "COMPLETED")
                        put("completed_at", System.currentTimeMillis())
                    }
                    db.update("daily_quests", questValues, "id = ?", arrayOf(questId))
                }
                remainingCursor.close()

                db.setTransactionSuccessful()
            } else {
                cursor.close()
            }
        } finally {
            db.endTransaction()
        }
        refreshCache()
        return true
    }

    fun awardXpAndGold(xp: Long, gold: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT id, level, current_xp, xp_to_next_level, available_stat_points, gold_balance FROM hunter_profiles LIMIT 1", null)
            if (cursor.moveToFirst()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                var level = cursor.getInt(cursor.getColumnIndexOrThrow("level"))
                var currentXp = cursor.getLong(cursor.getColumnIndexOrThrow("current_xp")) + xp
                var xpToNext = cursor.getLong(cursor.getColumnIndexOrThrow("xp_to_next_level"))
                var statPoints = cursor.getInt(cursor.getColumnIndexOrThrow("available_stat_points"))
                val currentGold = cursor.getLong(cursor.getColumnIndexOrThrow("gold_balance")) + gold

                // Check level up threshold
                while (currentXp >= xpToNext) {
                    currentXp -= xpToNext
                    level += 1
                    statPoints += 5
                    xpToNext = (100 * Math.pow(level.toDouble(), 1.5)).toLong()
                }

                val values = ContentValues().apply {
                    put("level", level)
                    put("current_xp", currentXp)
                    put("xp_to_next_level", xpToNext)
                    put("available_stat_points", statPoints)
                    put("gold_balance", currentGold)
                    put("updated_at", System.currentTimeMillis())
                }
                db.update("hunter_profiles", values, "id = ?", arrayOf(id))
                db.setTransactionSuccessful()
            }
            cursor.close()
        } finally {
            db.endTransaction()
        }
        refreshCache()
    }
}
