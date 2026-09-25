package com.example.studybuddy.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.studybuddy.util.FocusSessionStore
import com.example.studybuddy.util.PomodoroSoundPlayer

class FocusAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FOCUS_COMPLETE = "com.example.studybuddy.ACTION_FOCUS_COMPLETE"
        const val EXTRA_MODE = "mode"
        const val EXTRA_BREAK_MINUTES = "break_minutes"
        const val EXTRA_IS_LONG_BREAK = "is_long_break"

        const val MODE_FOCUS = 0
        const val MODE_SHORT_BREAK = 1
        const val MODE_LONG_BREAK = 2

        const val PREFS_NAME = "focus_prefs"
        const val KEY_IS_RUNNING = "is_running"
        const val KEY_REMAINING_MILLIS = "remaining_millis"
        const val KEY_TARGET_END_MILLIS = "target_end_millis"
        const val KEY_CURRENT_MODE = "current_mode"
        const val KEY_COMPLETED_CYCLES = "completed_cycles"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val mode = intent.getIntExtra(EXTRA_MODE, MODE_FOCUS)
        val breakMinutes = intent.getIntExtra(EXTRA_BREAK_MINUTES, 5)
        val isLongBreak = intent.getBooleanExtra(EXTRA_IS_LONG_BREAK, false)

        val helper = NotificationHelper(context)
        helper.cancelFocusOngoingNotification()

        vibrate(context)
        PomodoroSoundPlayer.playSessionAlert(context, isFocusEnd = (mode == MODE_FOCUS))

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (mode == MODE_FOCUS) {
            FocusSessionStore.recordSession(context)
            if (com.example.studybuddy.util.StudyGoalManager.isGoalMetToday(context)) {
                helper.cancelDailyGoalReminderNotification()
            }
            val currentCycles = prefs.getInt(KEY_COMPLETED_CYCLES, 0) + 1
            val nextMode = if (isLongBreak) MODE_LONG_BREAK else MODE_SHORT_BREAK

            prefs.edit()
                .putBoolean(KEY_IS_RUNNING, false)
                .putInt(KEY_CURRENT_MODE, nextMode)
                .putInt(KEY_COMPLETED_CYCLES, currentCycles)
                .putLong(KEY_REMAINING_MILLIS, 0L)
                .putLong(KEY_TARGET_END_MILLIS, 0L)
                .apply()

            helper.showFocusCompletedNotification(breakMinutes, isLongBreak)
        } else {
            // Break finished -> switch back to focus
            prefs.edit()
                .putBoolean(KEY_IS_RUNNING, false)
                .putInt(KEY_CURRENT_MODE, MODE_FOCUS)
                .putLong(KEY_REMAINING_MILLIS, 0L)
                .putLong(KEY_TARGET_END_MILLIS, 0L)
                .apply()

            helper.showBreakCompletedNotification()
        }
    }

    private fun vibrate(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 200, 500, 200, 500),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            }
        } catch (_: Exception) {
        }
    }
}
