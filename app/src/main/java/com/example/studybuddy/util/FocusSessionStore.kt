package com.example.studybuddy.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FocusSessionStore {

    private const val PREFS = "focus_sessions"
    private const val KEY_TOTAL = "total_sessions"
    private const val KEY_DAILY_PREFIX = "day_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordSession(context: Context) {
        val prefs = prefs(context)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        prefs.edit()
            .putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + 1)
            .putInt(KEY_DAILY_PREFIX + today, prefs.getInt(KEY_DAILY_PREFIX + today, 0) + 1)
            .apply()
    }

    fun totalSessions(context: Context): Int =
        prefs(context).getInt(KEY_TOTAL, 0)

    fun sessionsToday(context: Context): Int {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        return prefs(context).getInt(KEY_DAILY_PREFIX + today, 0)
    }
}