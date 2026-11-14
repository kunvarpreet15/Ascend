package com.kunvarpreet.ascend.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.kunvarpreet.ascend.domain.engine.AscendClock
import com.kunvarpreet.ascend.domain.engine.CatchUpEvaluator
import com.kunvarpreet.ascend.domain.engine.CatchUpResult
import com.kunvarpreet.ascend.domain.engine.QuestGenerator
import com.kunvarpreet.ascend.domain.engine.RankPolicy
import com.kunvarpreet.ascend.domain.engine.SystemAscendClock
import com.kunvarpreet.ascend.domain.engine.XpCalculator
import com.kunvarpreet.ascend.domain.model.AttributeStats
import com.kunvarpreet.ascend.domain.model.DailyQuest
import com.kunvarpreet.ascend.domain.model.ExerciseTarget
import com.kunvarpreet.ascend.domain.model.ExerciseType
import com.kunvarpreet.ascend.domain.model.ExerciseUnit
import com.kunvarpreet.ascend.domain.model.HunterProfile
import com.kunvarpreet.ascend.domain.model.QuestRewardEvent
import com.kunvarpreet.ascend.domain.model.QuestStatus
import com.kunvarpreet.ascend.domain.model.RankTier
import com.kunvarpreet.ascend.domain.model.RunCheckpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AscendDatabaseHelper(
    context: Context,
    val clock: AscendClock = SystemAscendClock()
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val _hunterState = MutableStateFlow<HunterProfile?>(null)
    val hunterState: StateFlow<HunterProfile?> = _hunterState.asStateFlow()

    private val _statsState = MutableStateFlow<AttributeStats?>(null)
    val statsState: StateFlow<AttributeStats?> = _statsState.asStateFlow()

    private val _todayQuestState = MutableStateFlow<DailyQuest?>(null)
    val todayQuestState: StateFlow<DailyQuest?> = _todayQuestState.asStateFlow()

    private val _rewardEvent = MutableStateFlow<QuestRewardEvent?>(null)
    val rewardEvent: StateFlow<QuestRewardEvent?> = _rewardEvent.asStateFlow()

    companion object {
        const val DATABASE_NAME = "ascend_rpg.db"
        const val DATABASE_VERSION = 4

        @Volatile
        private var instance: AscendDatabaseHelper? = null

        fun getInstance(context: Context, clock: AscendClock = SystemAscendClock()): AscendDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: AscendDatabaseHelper(context.applicationContext, clock).also {
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
                xp_to_next_level INTEGER NOT NULL DEFAULT 450,
                rank_tier TEXT NOT NULL DEFAULT 'E',
                available_stat_points INTEGER NOT NULL DEFAULT 0,
                current_streak INTEGER NOT NULL DEFAULT 1,
                longest_streak INTEGER NOT NULL DEFAULT 1,
                gold_balance INTEGER NOT NULL DEFAULT 100,
                height_cm REAL NOT NULL DEFAULT 175.0,
                weight_kg REAL NOT NULL DEFAULT 70.0,
                is_onboarded INTEGER NOT NULL DEFAULT 0,
                difficulty_multiplier REAL NOT NULL DEFAULT 1.0,
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
                target_id TEXT,
                distance_meters REAL NOT NULL,
                avg_pace_sec_per_km REAL NOT NULL,
                elevation_gain_meters REAL DEFAULT 0.0,
                calories_burned INTEGER NOT NULL DEFAULT 0,
                gps_path_geojson TEXT NOT NULL DEFAULT '[]',
                status TEXT NOT NULL DEFAULT 'COMPLETED',
                created_at INTEGER NOT NULL,
                FOREIGN KEY (workout_session_id) REFERENCES workout_session_logs(id) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_run_gps_session ON run_gps_logs(workout_session_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_run_gps_target ON run_gps_logs(target_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_run_gps_status ON run_gps_logs(status);")

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
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE hunter_profiles ADD COLUMN difficulty_multiplier REAL NOT NULL DEFAULT 1.0;")
            } catch (_: Exception) {
                // Ignore if column already exists
            }
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE run_gps_logs ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED';")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE run_gps_logs ADD COLUMN target_id TEXT;")
            } catch (_: Exception) {}
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_run_gps_target ON run_gps_logs(target_id);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_run_gps_status ON run_gps_logs(status);")
            } catch (_: Exception) {}
        }
        onCreate(db)
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        val check = db.rawQuery("SELECT COUNT(*) FROM hunter_profiles", null)
        val hasHunter = check.moveToFirst() && check.getInt(0) > 0
        check.close()
        if (hasHunter) return

        val now = clock.nowMillis()
        val hunterId = UUID.randomUUID().toString()
        val todayIso = clock.todayIsoDate()

        // Create Hunter
        val hunterValues = ContentValues().apply {
            put("id", hunterId)
            put("alias", "SHADOW_HUNTER")
            put("avatar_id", "default_hunter")
            put("level", 1)
            put("current_xp", 0)
            put("xp_to_next_level", XpCalculator.xpRequiredForLevel(1))
            put("rank_tier", "E")
            put("available_stat_points", 0)
            put("current_streak", 1)
            put("longest_streak", 1)
            put("gold_balance", 100)
            put("height_cm", 175.0)
            put("weight_kg", 70.0)
            put("is_onboarded", 0)
            put("difficulty_multiplier", 1.0)
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

        // Create Today's Quest Deck using QuestGenerator
        ensureTodayQuestExists(
            db = db,
            hunterId = hunterId,
            todayIso = todayIso,
            difficultyMultiplier = 1.0f,
            strength = 10,
            agility = 10
        )
    }

    fun refreshCache() {
        val db = writableDatabase
        val todayIso = clock.todayIsoDate()
        val now = clock.nowMillis()

        // 1. Query Hunter Profile
        var hunterId = ""
        var currentStreak = 1
        var longestStreak = 1
        var difficultyMultiplier = 1.0f
        val hunterCursor: Cursor = db.rawQuery("SELECT * FROM hunter_profiles LIMIT 1", null)
        if (hunterCursor.moveToFirst()) {
            hunterId = hunterCursor.getString(hunterCursor.getColumnIndexOrThrow("id"))
            currentStreak = hunterCursor.getInt(hunterCursor.getColumnIndexOrThrow("current_streak"))
            longestStreak = hunterCursor.getInt(hunterCursor.getColumnIndexOrThrow("longest_streak"))
            val diffCol = hunterCursor.getColumnIndex("difficulty_multiplier")
            difficultyMultiplier = if (diffCol != -1) hunterCursor.getFloat(diffCol) else 1.0f

            val profile = HunterProfile(
                id = hunterId,
                alias = hunterCursor.getString(hunterCursor.getColumnIndexOrThrow("alias")),
                avatarId = hunterCursor.getString(hunterCursor.getColumnIndexOrThrow("avatar_id")),
                level = hunterCursor.getInt(hunterCursor.getColumnIndexOrThrow("level")),
                currentXp = hunterCursor.getLong(hunterCursor.getColumnIndexOrThrow("current_xp")),
                xpToNextLevel = hunterCursor.getLong(hunterCursor.getColumnIndexOrThrow("xp_to_next_level")),
                rankTier = try {
                    RankTier.valueOf(hunterCursor.getString(hunterCursor.getColumnIndexOrThrow("rank_tier")))
                } catch (_: Exception) { RankTier.E },
                availableStatPoints = hunterCursor.getInt(hunterCursor.getColumnIndexOrThrow("available_stat_points")),
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                goldBalance = hunterCursor.getLong(hunterCursor.getColumnIndexOrThrow("gold_balance")),
                heightCm = hunterCursor.getFloat(hunterCursor.getColumnIndexOrThrow("height_cm")),
                weightKg = hunterCursor.getFloat(hunterCursor.getColumnIndexOrThrow("weight_kg")),
                isOnboarded = hunterCursor.getInt(hunterCursor.getColumnIndexOrThrow("is_onboarded")) == 1,
                difficultyMultiplier = difficultyMultiplier
            )
            _hunterState.value = profile
        }
        hunterCursor.close()

        if (hunterId.isEmpty()) return

        // 2. Query Attribute Stats
        var strength = 10
        var agility = 10
        val statsCursor = db.rawQuery("SELECT * FROM attribute_stats WHERE hunter_id = ? LIMIT 1", arrayOf(hunterId))
        if (statsCursor.moveToFirst()) {
            strength = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("strength"))
            agility = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("agility"))
            val stats = AttributeStats(
                hunterId = hunterId,
                strength = strength,
                agility = agility,
                sense = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("sense")),
                vitality = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("vitality")),
                intelligence = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("intelligence"))
            )
            _statsState.value = stats
        }
        statsCursor.close()

        // 3. Check if today's quest exists; if not, evaluate catch-up and generate today's quest
        var questCursor = db.rawQuery("SELECT * FROM daily_quests WHERE quest_date = ? LIMIT 1", arrayOf(todayIso))
        if (!questCursor.moveToFirst()) {
            questCursor.close()

            // Query the most recent past quest
            val lastQuestCursor = db.rawQuery(
                "SELECT quest_date, status FROM daily_quests WHERE hunter_id = ? ORDER BY quest_date DESC LIMIT 1",
                arrayOf(hunterId)
            )
            var lastQuestDate: String? = null
            var lastQuestStatus = QuestStatus.PENDING
            if (lastQuestCursor.moveToFirst()) {
                lastQuestDate = lastQuestCursor.getString(0)
                lastQuestStatus = try {
                    QuestStatus.valueOf(lastQuestCursor.getString(1))
                } catch (_: Exception) {
                    QuestStatus.PENDING
                }
            }
            lastQuestCursor.close()

            // Check streak shields
            val shieldCursor = db.rawQuery(
                "SELECT quantity FROM inventory_items WHERE hunter_id = ? AND item_code = 'STREAK_SHIELD' LIMIT 1",
                arrayOf(hunterId)
            )
            val shieldsOwned = if (shieldCursor.moveToFirst()) shieldCursor.getInt(0) else 0
            shieldCursor.close()

            // Run CatchUpEvaluator
            val catchUpResult = CatchUpEvaluator.evaluateCatchUp(
                lastQuestDate = lastQuestDate,
                todayDate = todayIso,
                lastQuestStatus = lastQuestStatus,
                currentStreak = currentStreak,
                availableStreakShields = shieldsOwned
            )

            when (catchUpResult) {
                is CatchUpResult.NoOpSameDay -> {
                    // Same day or initial run: no streak penalty
                }
                is CatchUpResult.StreakContinued -> {
                    // Previous day quest completed: streak continues intact
                }
                is CatchUpResult.ShieldConsumed -> {
                    val remaining = catchUpResult.shieldsRemaining
                    if (remaining <= 0) {
                        db.delete("inventory_items", "hunter_id = ? AND item_code = 'STREAK_SHIELD'", arrayOf(hunterId))
                    } else {
                        val cv = ContentValues().apply { put("quantity", remaining) }
                        db.update("inventory_items", cv, "hunter_id = ? AND item_code = 'STREAK_SHIELD'", arrayOf(hunterId))
                    }
                }
                is CatchUpResult.PenaltyTriggered -> {
                    // Reset streak to 0
                    currentStreak = 0
                    val cv = ContentValues().apply {
                        put("current_streak", 0)
                        put("updated_at", now)
                    }
                    db.update("hunter_profiles", cv, "id = ?", arrayOf(hunterId))

                    // Insert penalty_logs row if not already created for today
                    val penaltyCheck = db.rawQuery(
                        "SELECT id FROM penalty_logs WHERE hunter_id = ? AND triggered_date = ? LIMIT 1",
                        arrayOf(hunterId, todayIso)
                    )
                    if (!penaltyCheck.moveToFirst()) {
                        val penaltyCv = ContentValues().apply {
                            put("id", UUID.randomUUID().toString())
                            put("hunter_id", hunterId)
                            put("triggered_date", todayIso)
                            put("penalty_task_description", catchUpResult.penaltyTaskDescription)
                            put("is_cleared", 0)
                            put("created_at", now)
                        }
                        db.insert("penalty_logs", null, penaltyCv)
                    }
                    penaltyCheck.close()
                }
            }

            // Auto-generate today's quest deck for new calendar day
            ensureTodayQuestExists(
                db = db,
                hunterId = hunterId,
                todayIso = todayIso,
                difficultyMultiplier = difficultyMultiplier,
                strength = strength,
                agility = agility
            )
            questCursor = db.rawQuery("SELECT * FROM daily_quests WHERE quest_date = ? LIMIT 1", arrayOf(todayIso))

            // Re-read updated hunter profile in case streak was modified
            val updatedHunterCursor = db.rawQuery("SELECT * FROM hunter_profiles WHERE id = ? LIMIT 1", arrayOf(hunterId))
            if (updatedHunterCursor.moveToFirst()) {
                val diffCol = updatedHunterCursor.getColumnIndex("difficulty_multiplier")
                val diff = if (diffCol != -1) updatedHunterCursor.getFloat(diffCol) else 1.0f
                _hunterState.value = HunterProfile(
                    id = hunterId,
                    alias = updatedHunterCursor.getString(updatedHunterCursor.getColumnIndexOrThrow("alias")),
                    avatarId = updatedHunterCursor.getString(updatedHunterCursor.getColumnIndexOrThrow("avatar_id")),
                    level = updatedHunterCursor.getInt(updatedHunterCursor.getColumnIndexOrThrow("level")),
                    currentXp = updatedHunterCursor.getLong(updatedHunterCursor.getColumnIndexOrThrow("current_xp")),
                    xpToNextLevel = updatedHunterCursor.getLong(updatedHunterCursor.getColumnIndexOrThrow("xp_to_next_level")),
                    rankTier = try {
                        RankTier.valueOf(updatedHunterCursor.getString(updatedHunterCursor.getColumnIndexOrThrow("rank_tier")))
                    } catch (_: Exception) { RankTier.E },
                    availableStatPoints = updatedHunterCursor.getInt(updatedHunterCursor.getColumnIndexOrThrow("available_stat_points")),
                    currentStreak = updatedHunterCursor.getInt(updatedHunterCursor.getColumnIndexOrThrow("current_streak")),
                    longestStreak = updatedHunterCursor.getInt(updatedHunterCursor.getColumnIndexOrThrow("longest_streak")),
                    goldBalance = updatedHunterCursor.getLong(updatedHunterCursor.getColumnIndexOrThrow("gold_balance")),
                    heightCm = updatedHunterCursor.getFloat(updatedHunterCursor.getColumnIndexOrThrow("height_cm")),
                    weightKg = updatedHunterCursor.getFloat(updatedHunterCursor.getColumnIndexOrThrow("weight_kg")),
                    isOnboarded = updatedHunterCursor.getInt(updatedHunterCursor.getColumnIndexOrThrow("is_onboarded")) == 1,
                    difficultyMultiplier = diff
                )
            }
            updatedHunterCursor.close()
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
                        exerciseType = try { ExerciseType.valueOf(exTypeStr) } catch (_: Exception) { ExerciseType.CUSTOM },
                        targetQuantity = targetQty,
                        completedQuantity = compQty,
                        unit = try { ExerciseUnit.valueOf(unitStr) } catch (_: Exception) { ExerciseUnit.REPS },
                        isCompleted = isDone
                    )
                )
            }
            targetCursor.close()

            val quest = DailyQuest(
                id = questId,
                hunterId = hunterId,
                questDate = todayIso,
                status = try { QuestStatus.valueOf(statusStr) } catch (_: Exception) { QuestStatus.PENDING },
                xpReward = xpReward,
                goldReward = goldReward,
                completedAt = completedAt,
                targets = targetsList
            )
            _todayQuestState.value = quest
        }
        questCursor.close()
    }

    private fun ensureTodayQuestExists(
        db: SQLiteDatabase,
        hunterId: String,
        todayIso: String,
        difficultyMultiplier: Float = 1.0f,
        strength: Int = 10,
        agility: Int = 10
    ) {
        val now = clock.nowMillis()
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

            val targets = QuestGenerator.generateDailyTargets(
                questId = questId,
                difficultyMultiplier = difficultyMultiplier,
                strength = strength,
                agility = agility
            )

            for (target in targets) {
                val targetValues = ContentValues().apply {
                    put("id", target.id)
                    put("quest_id", questId)
                    put("exercise_type", target.exerciseType.name)
                    put("target_quantity", target.targetQuantity)
                    put("completed_quantity", 0f)
                    put("unit", target.unit.name)
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
                        put("updated_at", clock.nowMillis())
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
            val cursor = db.rawQuery(
                "SELECT completed_quantity, target_quantity, quest_id, exercise_type FROM exercise_targets WHERE id = ?",
                arrayOf(targetId)
            )
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
                val now = clock.nowMillis()
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

                // INFINITE REWARD GUARD: First verify parent quest is not already 'COMPLETED'
                val questCheckCursor = db.rawQuery(
                    "SELECT status, xp_reward, gold_reward, hunter_id FROM daily_quests WHERE id = ?",
                    arrayOf(questId)
                )
                if (questCheckCursor.moveToFirst()) {
                    val currentStatus = questCheckCursor.getString(questCheckCursor.getColumnIndexOrThrow("status"))
                    val hunterId = questCheckCursor.getString(questCheckCursor.getColumnIndexOrThrow("hunter_id"))

                    // Only proceed if quest is currently PENDING (never award twice if COMPLETED)
                    if (currentStatus != "COMPLETED") {
                        val remainingCursor = db.rawQuery(
                            "SELECT COUNT(*) FROM exercise_targets WHERE quest_id = ? AND is_completed = 0",
                            arrayOf(questId)
                        )
                        if (remainingCursor.moveToFirst() && remainingCursor.getInt(0) == 0) {
                            val questValues = ContentValues().apply {
                                put("status", "COMPLETED")
                                put("completed_at", now)
                            }
                            db.update("daily_quests", questValues, "id = ?", arrayOf(questId))

                            xpToAward = questCheckCursor.getLong(questCheckCursor.getColumnIndexOrThrow("xp_reward"))
                            goldToAward = questCheckCursor.getLong(questCheckCursor.getColumnIndexOrThrow("gold_reward"))
                            questCompletedJustNow = true

                            // Advance hunter streak upon successful completion
                            val streakCursor = db.rawQuery(
                                "SELECT current_streak, longest_streak FROM hunter_profiles WHERE id = ?",
                                arrayOf(hunterId)
                            )
                            if (streakCursor.moveToFirst()) {
                                val curStreak = streakCursor.getInt(0)
                                val longStreak = streakCursor.getInt(1)
                                val nextStreak = curStreak + 1
                                val nextLongest = maxOf(longStreak, nextStreak)
                                val streakCv = ContentValues().apply {
                                    put("current_streak", nextStreak)
                                    put("longest_streak", nextLongest)
                                    put("updated_at", now)
                                }
                                db.update("hunter_profiles", streakCv, "id = ?", arrayOf(hunterId))
                            }
                            streakCursor.close()
                        }
                        remainingCursor.close()
                    }
                }
                questCheckCursor.close()

                db.setTransactionSuccessful()
            } else {
                cursor.close()
            }
        } finally {
            db.endTransaction()
        }

        // Award rewards outside transaction if quest transitioned to COMPLETED
        if (questCompletedJustNow && (xpToAward > 0 || goldToAward > 0)) {
            awardXpAndGold(xpToAward, goldToAward)
        } else {
            refreshCache()
        }
        return true
    }

    fun awardXpAndGold(xp: Long, gold: Long) {
        val db = writableDatabase
        var rewardEventToEmit: QuestRewardEvent? = null

        db.beginTransaction()
        try {
            val cursor = db.rawQuery(
                "SELECT id, level, current_xp, xp_to_next_level, available_stat_points, gold_balance, rank_tier, current_streak, difficulty_multiplier FROM hunter_profiles LIMIT 1",
                null
            )
            if (cursor.moveToFirst()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                val oldLevel = cursor.getInt(cursor.getColumnIndexOrThrow("level"))
                val oldCurrentXp = cursor.getLong(cursor.getColumnIndexOrThrow("current_xp"))
                val oldStatPoints = cursor.getInt(cursor.getColumnIndexOrThrow("available_stat_points"))
                val currentGoldBalance = cursor.getLong(cursor.getColumnIndexOrThrow("gold_balance"))
                val rankTierStr = cursor.getString(cursor.getColumnIndexOrThrow("rank_tier"))
                val currentRank = try { RankTier.valueOf(rankTierStr) } catch (_: Exception) { RankTier.E }
                val streakDays = cursor.getInt(cursor.getColumnIndexOrThrow("current_streak"))
                val diffMultiplier = try {
                    cursor.getFloat(cursor.getColumnIndexOrThrow("difficulty_multiplier"))
                } catch (_: Exception) { 1.0f }

                // Fetch intelligence and vitality for stat scaling
                var intelligence = 10
                var vitality = 10
                val statsCursor = db.rawQuery(
                    "SELECT intelligence, vitality FROM attribute_stats WHERE hunter_id = ? LIMIT 1",
                    arrayOf(id)
                )
                if (statsCursor.moveToFirst()) {
                    intelligence = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("intelligence"))
                    vitality = statsCursor.getInt(statsCursor.getColumnIndexOrThrow("vitality"))
                }
                statsCursor.close()

                // Calculate scaled quest XP and Gold using calibrated formulas
                val scaledXp = XpCalculator.calculateDailyQuestXp(
                    baseXp = xp.toInt(),
                    streakDays = streakDays,
                    difficultyMultiplier = diffMultiplier,
                    intelligence = intelligence
                )
                val scaledGold = XpCalculator.calculateDailyQuestGold(
                    baseGold = gold.toInt(),
                    streakDays = streakDays,
                    vitality = vitality
                )

                // Apply XP gain through pure domain engine
                val outcome = XpCalculator.applyXpGain(
                    currentLevel = oldLevel,
                    currentXp = oldCurrentXp,
                    xpGained = scaledXp
                )

                val didLevelUp = outcome.levelsGained > 0
                val rankTrialUnlocked = !RankPolicy.isEligibleForRankTrial(oldLevel, currentRank) &&
                    RankPolicy.isEligibleForRankTrial(outcome.newLevel, currentRank)

                val newTotalStatPoints = oldStatPoints + outcome.statPointsEarned
                val newGoldBalance = currentGoldBalance + scaledGold

                val values = ContentValues().apply {
                    put("level", outcome.newLevel)
                    put("current_xp", outcome.remainingXp)
                    put("xp_to_next_level", outcome.xpToNextLevel)
                    put("available_stat_points", newTotalStatPoints)
                    put("gold_balance", newGoldBalance)
                    put("updated_at", clock.nowMillis())
                }
                db.update("hunter_profiles", values, "id = ?", arrayOf(id))
                db.setTransactionSuccessful()

                rewardEventToEmit = QuestRewardEvent(
                    xpEarned = scaledXp,
                    goldEarned = scaledGold,
                    didLevelUp = didLevelUp,
                    oldLevel = oldLevel,
                    newLevel = outcome.newLevel,
                    statPointsGained = outcome.statPointsEarned,
                    rankTrialUnlocked = rankTrialUnlocked
                )
            }
            cursor.close()
        } finally {
            db.endTransaction()
        }

        if (rewardEventToEmit != null) {
            _rewardEvent.value = rewardEventToEmit
        }
        refreshCache()
    }

    fun dismissRewardEvent() {
        _rewardEvent.value = null
    }

    fun addInventoryItem(hunterId: String, itemCode: String, itemName: String, quantity: Int = 1): Boolean {
        val db = writableDatabase
        val now = clock.nowMillis()
        val cursor = db.rawQuery(
            "SELECT id, quantity FROM inventory_items WHERE hunter_id = ? AND item_code = ? LIMIT 1",
            arrayOf(hunterId, itemCode)
        )
        val success = if (cursor.moveToFirst()) {
            val id = cursor.getString(0)
            val currentQty = cursor.getInt(1)
            val cv = ContentValues().apply {
                put("quantity", currentQty + quantity)
            }
            db.update("inventory_items", cv, "id = ?", arrayOf(id)) > 0
        } else {
            val cv = ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("hunter_id", hunterId)
                put("item_code", itemCode)
                put("item_name", itemName)
                put("quantity", quantity)
                put("acquired_at", now)
            }
            db.insert("inventory_items", null, cv) > 0
        }
        cursor.close()
        return success
    }

    fun saveOnboardingProfile(alias: String, heightCm: Float, weightKg: Float, difficultyMultiplier: Float): Boolean {
        val db = writableDatabase
        val now = clock.nowMillis()
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
                    put("difficulty_multiplier", difficultyMultiplier)
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
        val now = clock.nowMillis()
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
            put("target_id", targetId)
            put("distance_meters", distanceMeters)
            put("avg_pace_sec_per_km", avgPaceSecPerKm)
            put("elevation_gain_meters", 0.0)
            put("calories_burned", caloriesBurned)
            put("gps_path_geojson", gpsPathGeoJson)
            put("status", "COMPLETED")
            put("created_at", now)
        }
        return db.insert("run_gps_logs", null, values) > 0
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
        val db = writableDatabase
        val now = clock.nowMillis()
        var success = false

        db.beginTransaction()
        try {
            // Ensure workout_session_logs row exists or update it
            val sessionCursor = db.rawQuery("SELECT id FROM workout_session_logs WHERE id = ?", arrayOf(sessionId))
            val sessionExists = sessionCursor.moveToFirst()
            sessionCursor.close()

            if (!sessionExists) {
                val sessionValues = ContentValues().apply {
                    put("id", sessionId)
                    put("target_id", targetId)
                    put("exercise_type", ExerciseType.RUNNING.name)
                    put("start_time", now - durationSeconds * 1000L)
                    put("end_time", now)
                    put("total_reps_or_meters", distanceMeters)
                    put("duration_seconds", durationSeconds)
                    put("sets_data_json", gpsPathGeoJson)
                    put("created_at", now)
                }
                db.insert("workout_session_logs", null, sessionValues)
            } else {
                val sessionValues = ContentValues().apply {
                    put("end_time", now)
                    put("total_reps_or_meters", distanceMeters)
                    put("duration_seconds", durationSeconds)
                    put("sets_data_json", gpsPathGeoJson)
                }
                db.update("workout_session_logs", sessionValues, "id = ?", arrayOf(sessionId))
            }

            // Insert or update in-progress run_gps_logs row
            val gpsCursor = db.rawQuery("SELECT id FROM run_gps_logs WHERE workout_session_id = ?", arrayOf(sessionId))
            val gpsExists = gpsCursor.moveToFirst()
            gpsCursor.close()

            if (!gpsExists) {
                val gpsValues = ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("workout_session_id", sessionId)
                    put("target_id", targetId)
                    put("distance_meters", distanceMeters)
                    put("avg_pace_sec_per_km", avgPaceSecPerKm)
                    put("elevation_gain_meters", 0.0)
                    put("calories_burned", caloriesBurned)
                    put("gps_path_geojson", gpsPathGeoJson)
                    put("status", "IN_PROGRESS")
                    put("created_at", now)
                }
                db.insert("run_gps_logs", null, gpsValues)
            } else {
                val gpsValues = ContentValues().apply {
                    put("distance_meters", distanceMeters)
                    put("avg_pace_sec_per_km", avgPaceSecPerKm)
                    put("calories_burned", caloriesBurned)
                    put("gps_path_geojson", gpsPathGeoJson)
                    put("status", "IN_PROGRESS")
                }
                db.update("run_gps_logs", gpsValues, "workout_session_id = ?", arrayOf(sessionId))
            }

            db.setTransactionSuccessful()
            success = true
        } finally {
            db.endTransaction()
        }
        return success
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
        checkpointRunGps(
            sessionId = sessionId,
            targetId = targetId,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            avgPaceSecPerKm = avgPaceSecPerKm,
            caloriesBurned = caloriesBurned,
            gpsPathGeoJson = gpsPathGeoJson
        )

        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", "COMPLETED")
        }
        db.update("run_gps_logs", values, "workout_session_id = ?", arrayOf(sessionId))

        if (distanceMeters > 0f && targetId.isNotBlank()) {
            addProgressToTarget(targetId, distanceMeters)
        }
        return true
    }

    fun getActiveInProgressRun(targetId: String): RunCheckpoint? {
        val db = readableDatabase
        val query = """
            SELECT g.workout_session_id, g.target_id, g.distance_meters, 
                   w.duration_seconds, g.avg_pace_sec_per_km, g.calories_burned, 
                   g.gps_path_geojson, g.created_at
            FROM run_gps_logs g
            LEFT JOIN workout_session_logs w ON g.workout_session_id = w.id
            WHERE g.target_id = ? AND g.status = 'IN_PROGRESS'
            ORDER BY g.created_at DESC
            LIMIT 1
        """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(targetId))
        var checkpoint: RunCheckpoint? = null
        if (cursor.moveToFirst()) {
            val sessionId = cursor.getString(0) ?: ""
            val tid = cursor.getString(1) ?: targetId
            val distance = cursor.getFloat(2)
            val duration = if (!cursor.isNull(3)) cursor.getInt(3) else 0
            val pace = cursor.getFloat(4)
            val calories = cursor.getInt(5)
            val geoJson = cursor.getString(6) ?: "[]"
            val createdAt = cursor.getString(7) ?: ""
            checkpoint = RunCheckpoint(
                sessionId = sessionId,
                targetId = tid,
                distanceMeters = distance,
                durationSeconds = duration,
                avgPaceSecPerKm = pace,
                caloriesBurned = calories,
                gpsPathGeoJson = geoJson,
                createdAt = createdAt
            )
        }
        cursor.close()
        return checkpoint
    }

    fun finalizeOrDiscardRunCheckpoint(
        sessionId: String,
        finalizeAndSave: Boolean,
        targetId: String = ""
    ): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            if (finalizeAndSave) {
                val cursor = db.rawQuery(
                    "SELECT distance_meters, target_id FROM run_gps_logs WHERE workout_session_id = ?",
                    arrayOf(sessionId)
                )
                var distance = 0f
                var actualTargetId = targetId
                if (cursor.moveToFirst()) {
                    distance = cursor.getFloat(0)
                    if (actualTargetId.isBlank()) {
                        actualTargetId = cursor.getString(1) ?: ""
                    }
                }
                cursor.close()

                val values = ContentValues().apply {
                    put("status", "COMPLETED")
                }
                db.update("run_gps_logs", values, "workout_session_id = ?", arrayOf(sessionId))

                if (distance > 0f && actualTargetId.isNotBlank()) {
                    addProgressToTarget(actualTargetId, distance)
                }
            } else {
                db.delete("run_gps_logs", "workout_session_id = ?", arrayOf(sessionId))
                db.delete("workout_session_logs", "id = ?", arrayOf(sessionId))
            }
            db.setTransactionSuccessful()
            true
        } catch (_: Exception) {
            false
        } finally {
            db.endTransaction()
        }
    }
}
