package com.kunvarpreet.ascend.core.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kunvarpreet.ascend.MainActivity
import com.kunvarpreet.ascend.R
import com.kunvarpreet.ascend.data.local.AscendDatabaseHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyDeadlineReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val dbHelper = AscendDatabaseHelper.getInstance(context)
        dbHelper.refreshCache()

        val todayQuest = dbHelper.todayQuestState.value
        if (todayQuest != null && !todayQuest.isAllCompleted) {
            postReminderNotification(context)
        }

        // Schedule next reminder for tomorrow 21:00
        schedule(context)

        return Result.success()
    }

    private fun postReminderNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

        val channel = NotificationChannel(
            CHANNEL_ID_DEADLINE,
            "Daily Deadline Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Evening reminders to complete daily training quests before midnight"
        }
        notificationManager.createNotificationChannel(channel)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_DEADLINE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("DAILY QUEST DEADLINE")
            .setContentText("Complete your daily training before midnight to protect your streak.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Training deadline approaching! Complete all assigned quests before 23:59:59 to avoid system penalty activation.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_DEADLINE, notification)
    }

    companion object {
        const val WORK_NAME = "ascend_daily_deadline_reminder_work"
        const val CHANNEL_ID_DEADLINE = "ascend_deadline_reminders"
        const val NOTIFICATION_ID_DEADLINE = 9002

        fun calculateDelayToNext2100(currentTimeMillis: Long = System.currentTimeMillis()): Long {
            val now = Calendar.getInstance().apply {
                timeInMillis = currentTimeMillis
            }
            val target = Calendar.getInstance().apply {
                timeInMillis = currentTimeMillis
                set(Calendar.HOUR_OF_DAY, 21)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            return target.timeInMillis - now.timeInMillis
        }

        fun schedule(context: Context) {
            val delayMillis = calculateDelayToNext2100()
            val workRequest = OneTimeWorkRequestBuilder<DailyDeadlineReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
