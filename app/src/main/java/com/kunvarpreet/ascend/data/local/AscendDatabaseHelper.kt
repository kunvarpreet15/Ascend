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

    private val _statsState = MutableStateFlow<AttributeStats?>(null)
    val statsState: StateFlow<AttributeStats?> = _statsState.asStateFlow()

    private val _todayQuestState = MutableStateFlow<DailyQuest?>(null)
    val todayQuestState: StateFlow<DailyQuest?> = _todayQuestState.asStateFlow()

    companion object {
        const val DATABASE_NAME = "ascend_rpg.db"
        const val DATABASE_VERSION = 2

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
                is_onboarded INTEGER NOT NULL DEFAULT 0,
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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attribute_stats_hunter ON attribute_stats(hunter_id);")

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
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_quests_date ON daily_quests(hunter_id, quest_date);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_quests_status ON daily_quests(status);")

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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_exercise_targets_quest ON exercise_targets(quest_id);")

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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_logs_target ON workout_session_logs(target_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_logs_created ON workout_session_logs(created_at);")

        // Table: run_gps_logs
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS run_gps_logs (
                id TEXT PRIMARY KEY NOT NULL,
                workout_session_id TEXT NOT NULL,
                distance_meters REAL NOT NULL,
                avg_pace_sec_per_km REAL NOT NULL,
                elevation_gain_meters REAL DEFAULT 0.0,
                calories_burned INTEGER NOT NULL DEFAULT 0,
                gps_path_geojson TEXT NOT NULL DEFAULT '[]',
                created_at INTEGER NOT NULL,
                FOREIGN KEY (workout_session_id) REFERENCES workout_session_logs(id) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_run_gps_session ON run_gps_logs(workout_session_id);")

        // Table: penalty_logs
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS penalty_logs (
                id TEXT PRIMARY KEY NOT NULL,
                hunter_id TEXT NOT NULL,
                triggered_date TEXT NOT NULL,
                penalty_task_description TEXT NOT NULL,
                is_cleared INTEGER NOT NULL DEFAULT 0,
                cleared_at INTEGER,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (hunter_id) REFERENCES hunter_profiles(id) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_penalty_logs_hunter ON penalty_logs(hunter_id, triggered_date);")

        // Table: inventory_items
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS inventory_items (
                id TEXT PRIMARY KEY NOT NULL,
                hunter_id TEXT NOT NULL,
                item_code TEXT NOT NULL
                    CHECK (item_code IN ('STREAK_SHIELD','STAMINA_POTION','TITLE_SHADOW_NOVICE','TITLE_ASCENDED_WARRIOR')),
                item_name TEXT NOT NULL,
                quantity INTEGER NOT NULL DEFAULT 1,
                acquired_at INTEGER NOT NULL,
                FOREIGN KEY (hunter_id) REFERENCES hunter_profiles(id) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_hunter ON inventory_items(hunter_id);")

        // Table: body_evolution_logs
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS body_evolution_logs (
                id TEXT PRIMARY KEY NOT NULL,
                hunter_id TEXT NOT NULL,
                log_date TEXT NOT NULL,
                weight_kg REAL NOT NULL,
                photo_file_path TEXT,
                notes TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (hunter_id) REFERENCES hunter_profiles(id) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_body_evolution_hunter ON body_evolution_logs(hunter_id, log_date);")

        // Seed initial data
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        val check = db.rawQuery("SELECT COUNT(*) FROM hunter_profiles", null)
        val hasHunter = check.moveToFirst() && check.getInt(0) > 0
        check.close()
        if (hasHunter) return

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
            put("is_onboarded", 0)
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
        val db = writableDatabase
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // 1. Query or initialize Hunter
        var hunterId = ""
        val hunterCursor: Cursor = db.rawQuery("SELECT * FROM hunter_profiles LIMIT 1", null)
        if (hunterCursor.moveToFirst()) {
            hunterId = hunterCursor.getString(hunterCursor.getColumnIndexOrThrow("id"))
            val profile = HunterProfile(
                id = hunterId,
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

        if (hunterId.isEmpty()) return

        // 2. Query Attribute Stats
        val statsCursor = db.rawQuery("SELECT * FROM attribute_stats WHERE hunter_id = ? LIMIT 1", arrayOf(hunterId))
        if (statsCursor.moveToFirst()) {
            val stats = AttributeStats(
                hunterId = hunterId,
                strength = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("strength")),
                agility = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("agility")),
                sense = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("sense")),
                vitality = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("vitality")),
                intelligence = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("intelligence"))
            )
            _statsState.value = stats
        }
        statsCursor.close()

        // 3. Query Today's Quest, or auto-generate for a new day
        var questCursor = db.rawQuery("SELECT * FROM daily_quests WHERE quest_date = ? LIMIT 1", arrayOf(todayIso))
        if (!questCursor.moveToFirst()) {
            questCursor.close()
            // Auto-generate today's quest deck for new calendar day
            ensureTodayQuestExists(db, hunterId, todayIso)
            questCursor = db.rawQuery("SELECT * FROM daily_quests WHERE quest_date = ? LIMIT 1", arrayOf(todayIso))
        }

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
                hunterId = hunterId,
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

    private fun ensureTodayQuestExists(db: SQLiteDatabase, hunterId: String, todayIso: String) {
        val now = System.currentTimeMillis()
        val questId = UUID.randomUUID().toString()

        db.beginTransaction()
        try {
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
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun allocateStatPoint(statKey: String): Boolean {
        val column = when (statKey.lowercase()) {
            "strength", "str" -> "strength"
            "agility", "agi" -> "agility"
            "sense", "sen" -> "sense"
            "vitality", "vit" -> "vitality"
            "intelligence", "int" -> "intelligence"
            else -> return false
        }

        val db = writableDatabase
        var success = false

        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT id, available_stat_points FROM hunter_profiles LIMIT 1", null)
            if (cursor.moveToFirst()) {
                val hunterId = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                val availablePoints = cursor.getInt(cursor.getColumnIndexOrThrow("available_stat_points"))
                if (availablePoints > 0) {
                    val hunterValues = ContentValues().apply {
                        put("available_stat_points", availablePoints - 1)
                        put("updated_at", System.currentTimeMillis())
                    }
                    db.update("hunter_profiles", hunterValues, "id = ?", arrayOf(hunterId))
                    db.execSQL("UPDATE attribute_stats SET $column = $column + 1 WHERE hunter_id = ?", arrayOf(hunterId))
                    db.setTransactionSuccessful()
                    success = true
                }
            }
            cursor.close()
        } finally {
            db.endTransaction()
        }

        if (success) {
            refreshCache()
        }
        return success
    }

    fun addProgressToTarget(targetId: String, increment: Float): Boolean {
        if (increment <= 0f) return false

        val db = writableDatabase
        var questCompletedJustNow = false
        var xpToAward = 0L
        var goldToAward = 0L

        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT completed_quantity, target_quantity, quest_id, exercise_type FROM exercise_targets WHERE id = ?", arrayOf(targetId))
            if (cursor.moveToFirst()) {
                val current = cursor.getFloat(cursor.getColumnIndexOrThrow("completed_quantity"))
                val target = cursor.getFloat(cursor.getColumnIndexOrThrow("target_quantity"))
                val questId = cursor.getString(cursor.getColumnIndexOrThrow("quest_id"))
                val exType = cursor.getString(cursor.getColumnIndexOrThrow("exercise_type"))
                cursor.close()

                val newCompleted = current + increment
                val isCompleted = if (newCompleted >= target) 1 else 0

                val values = ContentValues().apply {
                    put("completed_quantity", newCompleted)
                    put("is_completed", isCompleted)
                }
                db.update("exercise_targets", values, "id = ?", arrayOf(targetId))

                // Insert into workout_session_logs
                val now = System.currentTimeMillis()
                val logValues = ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("target_id", targetId)
                    put("exercise_type", exType)
                    put("start_time", now - 60000)
                    put("end_time", now)
                    put("total_reps_or_meters", increment)
                    put("duration_seconds", 60)
                    put("sets_data_json", "[{\"reps\":$increment,\"timestamp\":$now}]")
                    put("created_at", now)
                }
                db.insert("workout_session_logs", null, logValues)

                // Check if all targets for this quest are completed and verify quest is not already COMPLETED
                val remainingCursor = db.rawQuery("SELECT COUNT(*) FROM exercise_targets WHERE quest_id = ? AND is_completed = 0", arrayOf(questId))
                if (remainingCursor.moveToFirst() && remainingCursor.getInt(0) == 0) {
                    val questCheckCursor = db.rawQuery("SELECT status, xp_reward, gold_reward FROM daily_quests WHERE id = ?", arrayOf(questId))
                    if (questCheckCursor.moveToFirst()) {
                        val currentStatus = questCheckCursor.getString(questCheckCursor.getColumnIndexOrThrow("status"))
                        if (currentStatus != "COMPLETED") {
                            val questValues = ContentValues().apply {
                                put("status", "COMPLETED")
                                put("completed_at", now)
                            }
                            db.update("daily_quests", questValues, "id = ?", arrayOf(questId))

                            xpToAward = questCheckCursor.getLong(questCheckCursor.getColumnIndexOrThrow("xp_reward"))
                            goldToAward = questCheckCursor.getLong(questCheckCursor.getColumnIndexOrThrow("gold_reward"))
                            questCompletedJustNow = true
                        }
                    }
                    questCheckCursor.close()
                }
                remainingCursor.close()

                db.setTransactionSuccessful()
            } else {
                cursor.close()
            }
        } finally {
            db.endTransaction()
        }

        // Award rewards outside transaction
        if (questCompletedJustNow && (xpToAward > 0 || goldToAward > 0)) {
            awardXpAndGold(xpToAward, goldToAward)
        } else {
            refreshCache()
        }
        return true
    }

    fun awardXpAndGold(xp: Long, gold: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT id, level, current_xp, xp_to_next_level, available_stat_points, gold_balance, rank_tier FROM hunter_profiles LIMIT 1", null)
            if (cursor.moveToFirst()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                var level = cursor.getInt(cursor.getColumnIndexOrThrow("level"))

                // Intelligence scaling: multiply XP earned by 1.0 + (intelligence - 10) * 0.02
                var intelligence = 10
                val statsCursor = db.rawQuery("SELECT intelligence FROM attribute_stats WHERE hunter_id = ? LIMIT 1", arrayOf(id))
                if (statsCursor.moveToFirst()) {
                    intelligence = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("intelligence"))
                }
                statsCursor.close()

                val multiplier = 1.0 + (intelligence - 10) * 0.02
                val scaledXp = (xp * multiplier).toLong()

                var currentXp = cursor.getLong(cursor.getColumnIndexOrThrow("current_xp")) + scaledXp
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

                // Update rank tier based on new level
                val newRankTier = when {
                    level >= 150 -> "SSS"
                    level >= 100 -> "S"
                    level >= 70 -> "A"
                    level >= 45 -> "B"
                    level >= 25 -> "C"
                    level >= 10 -> "D"
                    else -> "E"
                }

                val values = ContentValues().apply {
                    put("level", level)
                    put("current_xp", currentXp)
                    put("xp_to_next_level", xpToNext)
                    put("available_stat_points", statPoints)
                    put("gold_balance", currentGold)
                    put("rank_tier", newRankTier)
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

    fun saveOnboardingProfile(alias: String, heightCm: Float, weightKg: Float, difficultyMultiplier: Float): Boolean {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        var success = false

        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT id FROM hunter_profiles LIMIT 1", null)
            if (cursor.moveToFirst()) {
                val hunterId = cursor.getString(0)
                val values = ContentValues().apply {
                    put("alias", alias.ifBlank { "SHADOW_HUNTER" })
                    put("height_cm", heightCm)
                    put("weight_kg", weightKg)
                    put("is_onboarded", 1)
                    put("updated_at", now)
                }
                db.update("hunter_profiles", values, "id = ?", arrayOf(hunterId))

                // Scale today's quest targets if multiplier != 1.0f
                if (difficultyMultiplier > 0f && difficultyMultiplier != 1.0f) {
                    val targetsCursor = db.rawQuery(
                        "SELECT id, exercise_type FROM exercise_targets WHERE quest_id IN (SELECT id FROM daily_quests WHERE hunter_id = ?)",
                        arrayOf(hunterId)
                    )
                    while (targetsCursor.moveToNext()) {
                        val targetId = targetsCursor.getString(0)
                        val exType = targetsCursor.getString(1)
                        val baseQty = when (exType) {
                            "PUSH_UPS", "SIT_UPS", "SQUATS" -> 100f
                            "RUNNING" -> 3000f
                            else -> 100f
                        }
                        val newTarget = baseQty * difficultyMultiplier
                        val targetValues = ContentValues().apply {
                            put("target_quantity", newTarget)
                        }
                        db.update("exercise_targets", targetValues, "id = ?", arrayOf(targetId))
                    }
                    targetsCursor.close()
                }
                db.setTransactionSuccessful()
                success = true
            }
            cursor.close()
        } finally {
            db.endTransaction()
        }

        if (success) {
            refreshCache()
        }
        return success
    }

    fun logRunGps(
        targetId: String,
        distanceMeters: Float,
        durationSeconds: Int,
        avgPaceSecPerKm: Float,
        caloriesBurned: Int,
        gpsPathGeoJson: String = "[]"
    ): Boolean {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        var linkedSessionId = UUID.randomUUID().toString()
        val cursor = db.rawQuery(
            "SELECT id FROM workout_session_logs WHERE target_id = ? ORDER BY created_at DESC LIMIT 1",
            arrayOf(targetId)
        )
        if (cursor.moveToFirst()) {
            linkedSessionId = cursor.getString(0)
        }
        cursor.close()

        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("workout_session_id", linkedSessionId)
            put("distance_meters", distanceMeters)
            put("avg_pace_sec_per_km", avgPaceSecPerKm)
            put("elevation_gain_meters", 0.0)
            put("calories_burned", caloriesBurned)
            put("gps_path_geojson", gpsPathGeoJson)
            put("created_at", now)
        }
        return db.insert("run_gps_logs", null, values) > 0
    }
}
