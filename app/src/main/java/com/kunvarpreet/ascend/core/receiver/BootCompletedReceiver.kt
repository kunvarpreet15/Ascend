package com.kunvarpreet.ascend.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kunvarpreet.ascend.core.worker.DailyDeadlineReminderWorker
import com.kunvarpreet.ascend.core.worker.MidnightEvaluationWorker

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            // Re-enqueue MidnightEvaluationWorker targeting 00:01:00
            MidnightEvaluationWorker.schedule(context)

            // Re-enqueue daily deadline reminder notifications
            DailyDeadlineReminderWorker.schedule(context)
        }
    }
}
