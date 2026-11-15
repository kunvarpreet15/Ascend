package com.kunvarpreet.ascend

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kunvarpreet.ascend.data.local.AscendDatabaseHelper
import com.kunvarpreet.ascend.data.repository.HunterRepository
import com.kunvarpreet.ascend.data.repository.QuestRepository
import com.kunvarpreet.ascend.domain.engine.CatchUpEvaluator
import com.kunvarpreet.ascend.domain.engine.CatchUpResult
import com.kunvarpreet.ascend.domain.engine.FakeAscendClock
import com.kunvarpreet.ascend.domain.engine.RankPolicy
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
class Phase3DisciplineEconomyPromotionTest {

    private lateinit var context: Context
    private lateinit var clock: FakeAscendClock
    private lateinit var dbHelper: AscendDatabaseHelper
    private lateinit var questRepo: QuestRepository
    private lateinit var hunterRepo: HunterRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AscendDatabaseHelper.DATABASE_NAME)
        AscendDatabaseHelper.resetInstanceForTesting()
        clock = FakeAscendClock(
            currentMillis = Instant.parse("2026-11-11T12:00:00Z").toEpochMilli(),
            zoneId = ZoneId.of("UTC")
        )
        dbHelper = AscendDatabaseHelper(context, clock)
        dbHelper.refreshCache()
        questRepo = QuestRepository(context, dbHelper)
        hunterRepo = HunterRepository(context, dbHelper)
    }

    @After
    fun tearDown() {
        dbHelper.close()
        context.deleteDatabase(AscendDatabaseHelper.DATABASE_NAME)
        AscendDatabaseHelper.resetInstanceForTesting()
    }

    // =========================================================================
    // 1. Tiered Penalty Zone (CatchUpEvaluator & AscendDatabaseHelper)
    // =========================================================================

    @Test
    fun `test catchup penalty tasks scale by difficulty multiplier`() {
        // 0.5x Recruit
        val recruitTask = CatchUpEvaluator.getPenaltyTaskDescription(0.5f)
        assertEquals("SURVIVAL TRIAL: 30 BURPEES OR 2-MIN WALL SIT", recruitTask)

        // 1.0x Operative
        val operativeTask = CatchUpEvaluator.getPenaltyTaskDescription(1.0f)
        assertEquals("SURVIVAL TRIAL: 50 BURPEES OR 3-MIN WALL SIT", operativeTask)

        // 1.5x Monarch
        val monarchTask = CatchUpEvaluator.getPenaltyTaskDescription(1.5f)
        assertEquals("SURVIVAL TRIAL: 75 BURPEES OR 5-MIN WALL SIT", monarchTask)

        // evaluateCatchUp with 0.5x
        val recruitResult = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-10",
            todayDate = "2026-11-11",
            lastQuestStatus = QuestStatus.PENDING,
            currentStreak = 5,
            availableStreakShields = 0,
            difficultyMultiplier = 0.5f
        )
        assertTrue(recruitResult is CatchUpResult.PenaltyTriggered)
        val recruitPenalty = recruitResult as CatchUpResult.PenaltyTriggered
        assertTrue(recruitPenalty.penaltyTaskDescription.contains("SURVIVAL TRIAL: 30 BURPEES OR 2-MIN WALL SIT"))

        // evaluateCatchUp with 1.5x
        val monarchResult = CatchUpEvaluator.evaluateCatchUp(
            lastQuestDate = "2026-11-10",
            todayDate = "2026-11-11",
            lastQuestStatus = QuestStatus.PENDING,
            currentStreak = 5,
            availableStreakShields = 0,
            difficultyMultiplier = 1.5f
        )
        assertTrue(monarchResult is CatchUpResult.PenaltyTriggered)
        val monarchPenalty = monarchResult as CatchUpResult.PenaltyTriggered
        assertTrue(monarchPenalty.penaltyTaskDescription.contains("SURVIVAL TRIAL: 75 BURPEES OR 5-MIN WALL SIT"))
    }

    @Test
    fun `test activePenalty StateFlow and clearPenalty purging`() {
        val hunter = hunterRepo.hunterProfile.value
        assertNotNull(hunter)
        val hunterId = hunter!!.id

        // Insert active penalty into database
        val penaltyId = UUID.randomUUID().toString()
        val cv = ContentValues().apply {
            put("id", penaltyId)
            put("hunter_id", hunterId)
            put("triggered_date", "2026-11-11")
            put("penalty_task_description", "SURVIVAL TRIAL: 50 BURPEES OR 3-MIN WALL SIT")
            put("is_cleared", 0)
            put("created_at", clock.nowMillis())
        }
        dbHelper.writableDatabase.insert("penalty_logs", null, cv)
        dbHelper.refreshCache()

        // Verify activePenalty is exposed and populated
        val activePenalty = dbHelper.activePenalty.value
        assertNotNull(activePenalty)
        assertEquals(penaltyId, activePenalty!!.id)
        assertFalse(activePenalty.isCleared)

        // Clear penalty via clearPenalty
        val cleared = dbHelper.clearPenalty(penaltyId)
        assertTrue(cleared)

        // Verify activePenalty StateFlow becomes null
        assertNull(dbHelper.activePenalty.value)

        // Verify row in database is marked as cleared
        val cursor = dbHelper.readableDatabase.rawQuery(
            "SELECT is_cleared FROM penalty_logs WHERE id = ?",
            arrayOf(penaltyId)
        )
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(0))
        cursor.close()
    }

    // =========================================================================
    // 2. Armory & Item Shop Purchases (Gold Balance & Inventory Validation)
    // =========================================================================

    @Test
    fun `test purchase validation rejects transaction when gold balance is insufficient`() {
        val hunter = hunterRepo.hunterProfile.value
        assertNotNull(hunter)
        val hunterId = hunter!!.id

        // Set gold balance to 50
        val cv = ContentValues().apply { put("gold_balance", 50L) }
        dbHelper.writableDatabase.update("hunter_profiles", cv, "id = ?", arrayOf(hunterId))
        dbHelper.refreshCache()

        // Attempt to purchase Streak Shield (Cost: 150)
        val result = dbHelper.purchaseShopItem(hunterId, "STREAK_SHIELD")
        assertTrue(result is AscendDatabaseHelper.PurchaseResult.InsufficientGold)

        // Verify gold balance was not modified
        dbHelper.refreshCache()
        assertEquals(50L, dbHelper.hunterState.value?.goldBalance)
    }

    @Test
    fun `test purchase Streak Shield deducts 150 gold and increments inventory`() {
        val hunter = hunterRepo.hunterProfile.value
        assertNotNull(hunter)
        val hunterId = hunter!!.id

        // Set gold balance to 300
        val cv = ContentValues().apply { put("gold_balance", 300L) }
        dbHelper.writableDatabase.update("hunter_profiles", cv, "id = ?", arrayOf(hunterId))
        dbHelper.refreshCache()

        // Purchase first Streak Shield
        val result1 = dbHelper.purchaseShopItem(hunterId, "STREAK_SHIELD")
        assertTrue(result1 is AscendDatabaseHelper.PurchaseResult.Success)

        assertEquals(150L, dbHelper.hunterState.value?.goldBalance)
        val item1 = dbHelper.inventoryState.value.firstOrNull { it.itemCode == "STREAK_SHIELD" }
        assertNotNull(item1)
        assertEquals(1, item1!!.quantity)

        // Purchase second Streak Shield
        val result2 = dbHelper.purchaseShopItem(hunterId, "STREAK_SHIELD")
        assertTrue(result2 is AscendDatabaseHelper.PurchaseResult.Success)

        assertEquals(0L, dbHelper.hunterState.value?.goldBalance)
        val item2 = dbHelper.inventoryState.value.firstOrNull { it.itemCode == "STREAK_SHIELD" }
        assertNotNull(item2)
        assertEquals(2, item2!!.quantity)
    }

    @Test
    fun `test purchase Stamina Potion deducts 100 gold and instantly grants 75 XP`() {
        val hunter = hunterRepo.hunterProfile.value
        assertNotNull(hunter)
        val hunterId = hunter!!.id

        // Set gold to 200, XP to 0
        val cv = ContentValues().apply {
            put("gold_balance", 200L)
            put("current_xp", 0L)
        }
        dbHelper.writableDatabase.update("hunter_profiles", cv, "id = ?", arrayOf(hunterId))
        dbHelper.refreshCache()

        val result = dbHelper.purchaseShopItem(hunterId, "STAMINA_POTION")
        assertTrue(result is AscendDatabaseHelper.PurchaseResult.Success)

        // Check gold balance reduced by 100
        assertEquals(100L, dbHelper.hunterState.value?.goldBalance)
        // Check current XP increased by 75
        assertEquals(75L, dbHelper.hunterState.value?.currentXp)
    }

    @Test
    fun `test purchase Title Ascended Sovereign deducts 500 gold and equips title badge`() {
        val hunter = hunterRepo.hunterProfile.value
        assertNotNull(hunter)
        val hunterId = hunter!!.id

        // Set gold to 600
        val cv = ContentValues().apply { put("gold_balance", 600L) }
        dbHelper.writableDatabase.update("hunter_profiles", cv, "id = ?", arrayOf(hunterId))
        dbHelper.refreshCache()

        assertNull(dbHelper.hunterState.value?.equippedTitle)

        // Purchase Title
        val result = dbHelper.purchaseShopItem(hunterId, "TITLE_ASCENDED_SOVEREIGN")
        assertTrue(result is AscendDatabaseHelper.PurchaseResult.Success)

        assertEquals(100L, dbHelper.hunterState.value?.goldBalance)
        assertEquals("ASCENDED SOVEREIGN", dbHelper.hunterState.value?.equippedTitle)
    }

    // =========================================================================
    // 3. Rank Promotion Trials (RankPolicy & promoteRank)
    // =========================================================================

    @Test
    fun `test rank promotion eligibility thresholds`() {
        // E -> D requires Level 10
        assertFalse(RankPolicy.isEligibleForRankTrial(9, RankTier.E))
        assertTrue(RankPolicy.isEligibleForRankTrial(10, RankTier.E))

        // D -> C requires Level 25
        assertFalse(RankPolicy.isEligibleForRankTrial(24, RankTier.D))
        assertTrue(RankPolicy.isEligibleForRankTrial(25, RankTier.D))

        // C -> B requires Level 45
        assertFalse(RankPolicy.isEligibleForRankTrial(44, RankTier.C))
        assertTrue(RankPolicy.isEligibleForRankTrial(45, RankTier.C))

        // B -> A requires Level 70
        assertFalse(RankPolicy.isEligibleForRankTrial(69, RankTier.B))
        assertTrue(RankPolicy.isEligibleForRankTrial(70, RankTier.B))

        // A -> S requires Level 100
        assertFalse(RankPolicy.isEligibleForRankTrial(99, RankTier.A))
        assertTrue(RankPolicy.isEligibleForRankTrial(100, RankTier.A))
    }

    @Test
    fun `test promoteRank promotes rank tier in database when eligible`() {
        val hunter = hunterRepo.hunterProfile.value
        assertNotNull(hunter)
        val hunterId = hunter!!.id
        assertEquals(RankTier.E, hunter.rankTier)

        // Not eligible at Level 5 -> promoteRank should fail
        val cv5 = ContentValues().apply { put("level", 5) }
        dbHelper.writableDatabase.update("hunter_profiles", cv5, "id = ?", arrayOf(hunterId))
        dbHelper.refreshCache()

        val failedPromotion = dbHelper.promoteRank(hunterId)
        assertFalse(failedPromotion)
        assertEquals(RankTier.E, dbHelper.hunterState.value?.rankTier)

        // Level 10 reached -> promoteRank should succeed to D-Rank
        val cv10 = ContentValues().apply { put("level", 10) }
        dbHelper.writableDatabase.update("hunter_profiles", cv10, "id = ?", arrayOf(hunterId))
        dbHelper.refreshCache()

        val successfulPromotion = dbHelper.promoteRank(hunterId)
        assertTrue(successfulPromotion)
        assertEquals(RankTier.D, dbHelper.hunterState.value?.rankTier)
    }
}
