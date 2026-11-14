package com.kunvarpreet.ascend

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kunvarpreet.ascend.data.local.AscendDatabaseHelper
import com.kunvarpreet.ascend.domain.engine.FakeAscendClock
import com.kunvarpreet.ascend.domain.model.QuestStatus
import com.kunvarpreet.ascend.domain.model.RankTier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AscendDatabaseIntegrationTest {

    private lateinit var context: Context
    private lateinit var clock: FakeAscendClock
    private lateinit var dbHelper: AscendDatabaseHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AscendDatabaseHelper.DATABASE_NAME)
        clock = FakeAscendClock(
            currentMillis = Instant.parse("2026-11-11T12:00:00Z").toEpochMilli(),
            zoneId = ZoneId.of("UTC")
        )
        dbHelper = AscendDatabaseHelper(context, clock)
        dbHelper.refreshCache()
    }

    @After
    fun tearDown() {
        dbHelper.close()
        context.deleteDatabase(AscendDatabaseHelper.DATABASE_NAME)
    }

    @Test
    fun testFreshInstallSeedsAllTablesAndDefaultProfile() {
        val db = dbHelper.readableDatabase

        // 1. Verify all 9 tables exist in sqlite_master
        val expectedTables = listOf(
            "hunter_profiles",
            "attribute_stats",
            "daily_quests",
            "exercise_targets",
            "workout_session_logs",
            "run_gps_logs",
            "penalty_logs",
            "inventory_items",
            "body_evolution_logs"
        )
        val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
        val actualTables = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            actualTables.add(cursor.getString(0))
        }
        cursor.close()

        for (table in expectedTables) {
            assertTrue("Expected table $table missing from SQLite schema", actualTables.contains(table))
        }

        // 2. Verify Hunter Profile seeded defaults
        val hunter = dbHelper.hunterState.value
        assertNotNull(hunter)
        assertFalse(hunter!!.isOnboarded)
        assertEquals(1, hunter.level)
        assertEquals(RankTier.E, hunter.rankTier)
        assertEquals(0, hunter.availableStatPoints)
        assertEquals(1, hunter.currentStreak)
        assertEquals(100L, hunter.goldBalance)

        // 3. Verify Attribute Stats seeded defaults
        val stats = dbHelper.statsState.value
        assertNotNull(stats)
        assertEquals(10, stats!!.strength)
        assertEquals(10, stats.agility)
        assertEquals(10, stats.sense)
        assertEquals(10, stats.vitality)
        assertEquals(10, stats.intelligence)

        // 4. Verify Today's Quest Deck seeded with 4 targets
        val quest = dbHelper.todayQuestState.value
        assertNotNull(quest)
        assertEquals(QuestStatus.PENDING, quest!!.status)
        assertEquals(4, quest.targets.size)
        assertEquals(100f, quest.targets[0].targetQuantity, 0.01f) // Pushups
        assertEquals(3000f, quest.targets[3].targetQuantity, 0.01f) // Running
    }

    @Test
    fun testSaveOnboardingProfileUpdatesHunterAndScalesQuestTargets() {
        val success = dbHelper.saveOnboardingProfile(
            alias = "JIN_WOO",
            heightCm = 182f,
            weightKg = 76f,
            difficultyMultiplier = 1.5f
        )
        assertTrue(success)
        dbHelper.refreshCache()

        val hunter = dbHelper.hunterState.value
        assertNotNull(hunter)
        assertTrue(hunter!!.isOnboarded)
        assertEquals("JIN_WOO", hunter.alias)
        assertEquals(182f, hunter.heightCm, 0.01f)
        assertEquals(76f, hunter.weightKg, 0.01f)
        assertEquals(1.5f, hunter.difficultyMultiplier, 0.01f)

        // Verify all 4 targets scaled by 1.5x
        val quest = dbHelper.todayQuestState.value
        assertNotNull(quest)
        assertEquals(150f, quest!!.targets[0].targetQuantity, 0.01f) // 100 * 1.5 = 150
        assertEquals(150f, quest.targets[1].targetQuantity, 0.01f) // 100 * 1.5 = 150
        assertEquals(150f, quest.targets[2].targetQuantity, 0.01f) // 100 * 1.5 = 150
        assertEquals(4500f, quest.targets[3].targetQuantity, 0.01f) // 3000 * 1.5 = 4500
    }

    @Test
    fun testAllocateStatPointAtomicallyModifiesDatabaseAndRejectsWhenZero() {
        // Zero available stat points initially
        val initialReject = dbHelper.allocateStatPoint("strength")
        assertFalse(initialReject)
        assertEquals(10, dbHelper.statsState.value?.strength)

        // Award 5 stat points directly in SQLite
        dbHelper.writableDatabase.execSQL(
            "UPDATE hunter_profiles SET available_stat_points = 5"
        )
        dbHelper.refreshCache()
        assertEquals(5, dbHelper.hunterState.value?.availableStatPoints)

        // Allocate Strength (+1)
        val allocStr = dbHelper.allocateStatPoint("strength")
        assertTrue(allocStr)
        assertEquals(11, dbHelper.statsState.value?.strength)
        assertEquals(4, dbHelper.hunterState.value?.availableStatPoints)

        // Allocate Intelligence (+1)
        val allocInt = dbHelper.allocateStatPoint("intelligence")
        assertTrue(allocInt)
        assertEquals(11, dbHelper.statsState.value?.intelligence)
        assertEquals(3, dbHelper.hunterState.value?.availableStatPoints)
    }

    @Test
    fun testQuestCompletionAwardsRewardsOnceAndGuardPreventsDoubleRewarding() {
        val quest = dbHelper.todayQuestState.value!!
        val initialXp = dbHelper.hunterState.value!!.currentXp
        val initialGold = dbHelper.hunterState.value!!.goldBalance

        // Complete all 4 targets
        for (target in quest.targets) {
            val res = dbHelper.addProgressToTarget(target.id, target.targetQuantity)
            assertTrue(res)
        }

        dbHelper.refreshCache()

        // 1. Quest marked COMPLETED and reward event emitted
        val completedQuest = dbHelper.todayQuestState.value!!
        assertEquals(QuestStatus.COMPLETED, completedQuest.status)

        val rewardEvent = dbHelper.rewardEvent.value
        assertNotNull(rewardEvent)
        assertTrue(rewardEvent!!.xpEarned > 0)
        assertTrue(rewardEvent.goldEarned > 0)

        val postXp = dbHelper.hunterState.value!!.currentXp
        val postGold = dbHelper.hunterState.value!!.goldBalance
        assertTrue(postXp > initialXp)
        assertTrue(postGold > initialGold)

        // 2. INFINITE REWARD GUARD: Subsequent target progress must NOT re-award XP/Gold
        val duplicateAttempt = dbHelper.addProgressToTarget(quest.targets[0].id, 10f)
        assertTrue(duplicateAttempt)
        dbHelper.refreshCache()

        assertEquals(postXp, dbHelper.hunterState.value!!.currentXp)
        assertEquals(postGold, dbHelper.hunterState.value!!.goldBalance)
    }

    @Test
    fun testCatchUpEvaluatorShieldConsumptionAcrossMissedDay() {
        val hunterId = dbHelper.hunterState.value!!.id

        // Complete today's quest so streak becomes 2
        for (t in dbHelper.todayQuestState.value!!.targets) {
            dbHelper.addProgressToTarget(t.id, t.targetQuantity)
        }
        dbHelper.refreshCache()
        assertEquals(2, dbHelper.hunterState.value!!.currentStreak)

        // Insert 2 STREAK_SHIELD items into inventory
        val shieldCv = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("hunter_id", hunterId)
            put("item_code", "STREAK_SHIELD")
            put("item_name", "Streak Preservation Shield")
            put("quantity", 2)
            put("acquired_at", clock.nowMillis())
        }
        dbHelper.writableDatabase.insert("inventory_items", null, shieldCv)

        // Advance clock by 2 calendar days (2026-11-11 -> 2026-11-13; missed 2026-11-12)
        clock.setIsoDate("2026-11-13")
        dbHelper.refreshCache()

        // Streak is preserved! Shield decremented from 2 to 1
        assertEquals(2, dbHelper.hunterState.value!!.currentStreak)

        val cursor = dbHelper.readableDatabase.rawQuery(
            "SELECT quantity FROM inventory_items WHERE hunter_id = ? AND item_code = 'STREAK_SHIELD'",
            arrayOf(hunterId)
        )
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(0))
        cursor.close()

        // Verify NO penalty_logs row was created
        val penaltyCheck = dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM penalty_logs WHERE hunter_id = ?",
            arrayOf(hunterId)
        )
        assertTrue(penaltyCheck.moveToFirst())
        assertEquals(0, penaltyCheck.getInt(0))
        penaltyCheck.close()
    }

    @Test
    fun testCatchUpEvaluatorPenaltyTriggerWhenNoShieldsAvailable() {
        val hunterId = dbHelper.hunterState.value!!.id

        // Complete today's quest so streak = 2
        for (t in dbHelper.todayQuestState.value!!.targets) {
            dbHelper.addProgressToTarget(t.id, t.targetQuantity)
        }
        dbHelper.refreshCache()
        assertEquals(2, dbHelper.hunterState.value!!.currentStreak)

        // Ensure 0 shields in inventory
        dbHelper.writableDatabase.delete("inventory_items", null, null)

        // Advance clock by 2 calendar days without shields (missed day)
        clock.setIsoDate("2026-11-13")
        dbHelper.refreshCache()

        // Streak reset to 0
        assertEquals(0, dbHelper.hunterState.value!!.currentStreak)

        // Penalty logs row created
        val penaltyCursor = dbHelper.readableDatabase.rawQuery(
            "SELECT triggered_date, is_cleared FROM penalty_logs WHERE hunter_id = ?",
            arrayOf(hunterId)
        )
        assertTrue(penaltyCursor.moveToFirst())
        assertEquals("2026-11-13", penaltyCursor.getString(0))
        assertEquals(0, penaltyCursor.getInt(1))
        penaltyCursor.close()
    }

    @Test
    fun testRunGpsCheckpointsAndRecoveryLifecycle() {
        val runTargetId = dbHelper.todayQuestState.value!!.targets[3].id
        val sessionId = UUID.randomUUID().toString()

        // 1. Write an in-progress 100m checkpoint
        val written = dbHelper.checkpointRunGps(
            sessionId = sessionId,
            targetId = runTargetId,
            distanceMeters = 850f,
            durationSeconds = 240,
            avgPaceSecPerKm = 282.3f,
            caloriesBurned = 55,
            gpsPathGeoJson = "[{\"lat\":37.422,\"lng\":-122.084}]"
        )
        assertTrue(written)

        // 2. Query in-progress checkpoint
        val checkpoint = dbHelper.getActiveInProgressRun(runTargetId)
        assertNotNull(checkpoint)
        assertEquals(sessionId, checkpoint!!.sessionId)
        assertEquals(850f, checkpoint.distanceMeters, 0.01f)
        assertEquals(240, checkpoint.durationSeconds)

        // 3. Finalize checkpoint (SAVE)
        val finalized = dbHelper.finalizeOrDiscardRunCheckpoint(sessionId, finalizeAndSave = true, targetId = runTargetId)
        assertTrue(finalized)

        // Now getActiveInProgressRun must return null because status is 'COMPLETED'
        assertNull(dbHelper.getActiveInProgressRun(runTargetId))

        // Distance progress must be credited to exercise target
        dbHelper.refreshCache()
        val runTarget = dbHelper.todayQuestState.value!!.targets[3]
        assertEquals(850f, runTarget.completedQuantity, 0.01f)

        // 4. Test DISCARD on an abandoned checkpoint
        val discardSessionId = UUID.randomUUID().toString()
        dbHelper.checkpointRunGps(
            sessionId = discardSessionId,
            targetId = runTargetId,
            distanceMeters = 200f,
            durationSeconds = 60,
            avgPaceSecPerKm = 300f,
            caloriesBurned = 13,
            gpsPathGeoJson = "[]"
        )
        assertNotNull(dbHelper.getActiveInProgressRun(runTargetId))

        val discarded = dbHelper.finalizeOrDiscardRunCheckpoint(discardSessionId, finalizeAndSave = false)
        assertTrue(discarded)
        assertNull(dbHelper.getActiveInProgressRun(runTargetId))
    }
}
