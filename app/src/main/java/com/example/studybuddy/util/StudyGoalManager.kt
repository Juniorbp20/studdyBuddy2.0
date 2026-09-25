package com.example.studybuddy.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object StudyGoalManager {

    private const val PREFS_NAME = "study_goal_prefs"
    private const val KEY_TARGET_HOURS = "daily_target_hours"
    private const val KEY_REMINDERS_ENABLED = "goal_reminders_enabled"
    private const val KEY_LAST_REMINDER_DATE = "last_reminder_date"
    private const val KEY_LAST_REMINDER_MILLIS = "last_reminder_millis"

    const val DEFAULT_TARGET_HOURS = 2.0f

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTargetHours(context: Context): Float {
        return prefs(context).getFloat(KEY_TARGET_HOURS, DEFAULT_TARGET_HOURS)
    }

    fun setTargetHours(context: Context, hours: Float) {
        prefs(context).edit().putFloat(KEY_TARGET_HOURS, hours).apply()
    }

    fun getTargetMinutes(context: Context): Int {
        val hours = getTargetHours(context)
        return (hours * 60).roundToInt()
    }

    fun getCompletedMinutesToday(context: Context): Int {
        return FocusSessionStore.focusMinutesToday(context)
    }

    fun getRemainingMinutesToday(context: Context): Int {
        val target = getTargetMinutes(context)
        val completed = getCompletedMinutesToday(context)
        return maxOf(0, target - completed)
    }

    fun isGoalMetToday(context: Context): Boolean {
        val target = getTargetMinutes(context)
        if (target <= 0) return true
        return getCompletedMinutesToday(context) >= target
    }

    fun getGoalProgressPercent(context: Context): Int {
        val target = getTargetMinutes(context)
        if (target <= 0) return 100
        val completed = getCompletedMinutesToday(context)
        return minOf(100, (completed * 100) / target)
    }

    fun isReminderEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_REMINDERS_ENABLED, true)
    }

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply()
    }

    fun shouldRemindOnAppOpen(context: Context): Boolean {
        if (!isReminderEnabled(context)) return false
        if (isGoalMetToday(context)) return false

        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val lastDate = prefs(context).getString(KEY_LAST_REMINDER_DATE, null)
        val lastMillis = prefs(context).getLong(KEY_LAST_REMINDER_MILLIS, 0L)
        val now = System.currentTimeMillis()

        // Remind on app open if:
        // 1) First time today
        // 2) Or at least 2 hours have elapsed since the last notification today and goal is still unmet
        if (lastDate != todayStr) return true
        return (now - lastMillis) >= 2 * 60 * 60 * 1000L
    }

    fun recordReminderSent(context: Context) {
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        prefs(context).edit()
            .putString(KEY_LAST_REMINDER_DATE, todayStr)
            .putLong(KEY_LAST_REMINDER_MILLIS, System.currentTimeMillis())
            .apply()
    }

    fun formatDuration(minutes: Int): String {
        return if (minutes < 60) {
            "$minutes min"
        } else {
            val hours = minutes / 60
            val remMinutes = minutes % 60
            if (remMinutes == 0) {
                "${hours}h"
            } else {
                "${hours}h ${remMinutes}m"
            }
        }
    }
}
