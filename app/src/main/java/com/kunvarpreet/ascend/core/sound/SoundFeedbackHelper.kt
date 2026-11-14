package com.kunvarpreet.ascend.core.sound

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Safe audio & haptic feedback helper that gracefully handles device silent modes,
 * missing audio chips, and emulator environments without throwing RuntimeException.
 */
class SoundFeedbackHelper(private val context: Context) {

    fun playTimerCompletionTone() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 350)
        } catch (e: Exception) {
            // Devices in silent/DND mode, emulators, or restricted audio channels
            Log.w("SoundFeedbackHelper", "Safe tone fallback: ${e.message}")
        }
    }

    fun vibrateCompletion() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(250L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(250L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(250L)
                }
            }
        } catch (e: Exception) {
            Log.w("SoundFeedbackHelper", "Safe vibration fallback: ${e.message}")
        }
    }

    fun playCompletionFeedback() {
        playTimerCompletionTone()
        vibrateCompletion()
    }
}
