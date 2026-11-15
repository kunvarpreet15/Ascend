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

class MidnightEvaluationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val dbHelper = AscendDatabaseHelper.getInstance(context)

        // 1. Evaluate yesterday's quest & catch-up logic
        dbHelper.refreshCache()

        // 2. Check if a penalty was triggered
        val activePenalty = dbHelper.activePenalty.value ?: dbHelper.getActivePenalty()
        if (activePenalty != null) {
            postPenaltyAlertNotification(context, activePenalty.penaltyTaskDescription)
        }

        // 3. Schedule next daily evaluation targeting 00:01:00
        schedule(context)

        return Result.success()
    }

    private fun postPenaltyAlertNotification(context: Context, taskDescription: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

        val channel = NotificationChannel(
            CHANNEL_ID_CRIMSON_ALERT,
            "Crimson System Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High-priority system notifications when penalties activate"
            enableLights(true)
            lightColor = android.graphics.Color.RED
            enableVibration(true)
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CRIMSON_ALERT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SYSTEM ALERT")
            .setContentText("PENALTY ZONE ACTIVATED — SURVIVE TO RESTORE STREAK")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("PENALTY ZONE ACTIVATED — SURVIVE TO RESTORE STREAK\n\n$taskDescription")
            )
            .setColor(CRIMSON_COLOR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PENALTY, notification)
    }

    companion object {
        const val WORK_NAME = "ascend_midnight_evaluation_work"
        const val CHANNEL_ID_CRIMSON_ALERT = "ascend_crimson_system_alert"
        const val NOTIFICATION_ID_PENALTY = 9001
        const val CRIMSON_COLOR = 0xFFFF1744.toInt()

        fun calculateDelayToNext0001(currentTimeMillis: Long = System.currentTimeMillis()): Long {
            val now = Calendar.getInstance().apply {
                timeInMillis = currentTimeMillis
            }
            val target = Calendar.getInstance().apply {
                timeInMillis = currentTimeMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            return target.timeInMillis - now.timeInMillis
        }

        fun schedule(context: Context) {
            val delayMillis = calculateDelayToNext0001()
            val workRequest = OneTimeWorkRequestBuilder<MidnightEvaluationWorker>()
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
