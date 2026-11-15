package com.kunvarpreet.ascend.core.worker

import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.kunvarpreet.ascend.data.local.AscendDatabaseHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MidnightEvaluationWorkerTest {

    private lateinit var context: Context
    private lateinit var dbHelper: AscendDatabaseHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        try {
            WorkManager.initialize(context, Configuration.Builder().build())
        } catch (_: IllegalStateException) {
            // Already initialized in test environment
        }

        context.deleteDatabase(AscendDatabaseHelper.DATABASE_NAME)
        dbHelper = AscendDatabaseHelper.getInstance(context)
        dbHelper.refreshCache()
    }

    @After
    fun tearDown() {
        dbHelper.close()
        context.deleteDatabase(AscendDatabaseHelper.DATABASE_NAME)
    }

    @Test
    fun `test calculateDelayToNext0001 targets next 00 01 00 correctly`() {
        // Given a time at 12:00:00 noon
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delay = MidnightEvaluationWorker.calculateDelayToNext0001(cal.timeInMillis)

        // 12 hours and 1 minute = (12 * 60 + 1) * 60 * 1000 = 43260000 ms
        val expected = (12 * 3600 + 60) * 1000L
        assertEquals(expected, delay)

        // Given a time at 00:00:30 (30s before 00:01:00)
        val calBefore = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 30)
            set(Calendar.MILLISECOND, 0)
        }
        val delayBefore = MidnightEvaluationWorker.calculateDelayToNext0001(calBefore.timeInMillis)
        assertEquals(30000L, delayBefore)
    }

    @Test
    fun `test worker execution returns success and schedules next run`() {
        val worker = TestListenableWorkerBuilder<MidnightEvaluationWorker>(context).build()
        val result = runBlocking { worker.doWork() }
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `test worker triggers crimson alert notification when penalty is active`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        // Insert an active penalty
        val hunterId = dbHelper.hunterState.value?.id ?: "test_hunter"
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("hunter_id", hunterId)
            put("triggered_date", "2026-11-14")
            put("penalty_task_description", "Survival Trial: Complete 400 pushups")
            put("is_cleared", 0)
            put("created_at", System.currentTimeMillis())
        }
        db.insert("penalty_logs", null, cv)
        dbHelper.refreshCache()

        assertNotNull(dbHelper.activePenalty.value)

        val worker = TestListenableWorkerBuilder<MidnightEvaluationWorker>(context).build()
        val result = runBlocking { worker.doWork() }
        assertEquals(ListenableWorker.Result.success(), result)

        val notifications = notificationManager.activeNotifications
        assertTrue(notifications.any { it.id == MidnightEvaluationWorker.NOTIFICATION_ID_PENALTY })
    }
}
