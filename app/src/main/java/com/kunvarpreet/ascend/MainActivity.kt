package com.kunvarpreet.ascend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kunvarpreet.ascend.core.worker.DailyDeadlineReminderWorker
import com.kunvarpreet.ascend.core.worker.MidnightEvaluationWorker
import com.kunvarpreet.ascend.ui.navigation.AscendApp
import com.kunvarpreet.ascend.ui.theme.AscendTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule reliable background midnight evaluation and deadline reminders
        MidnightEvaluationWorker.schedule(applicationContext)
        DailyDeadlineReminderWorker.schedule(applicationContext)

        setContent {
            AscendTheme {
                AscendApp()
            }
        }
    }
}
