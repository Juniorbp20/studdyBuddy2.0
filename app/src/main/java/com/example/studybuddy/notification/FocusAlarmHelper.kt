package com.example.studybuddy.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class FocusAlarmHelper(private val context: Context) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "FocusAlarmHelper"
        private const val REQUEST_CODE = 999
    }

    fun scheduleFocusAlarm(
        triggerAtMillis: Long,
        mode: Int,
        breakMinutes: Int,
        isLongBreak: Boolean
    ) {
        val intent = Intent(context, FocusAlarmReceiver::class.java).apply {
            action = FocusAlarmReceiver.ACTION_FOCUS_COMPLETE
            putExtra(FocusAlarmReceiver.EXTRA_MODE, mode)
            putExtra(FocusAlarmReceiver.EXTRA_BREAK_MINUTES, breakMinutes)
            putExtra(FocusAlarmReceiver.EXTRA_IS_LONG_BREAK, isLongBreak)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                    alarmManager.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        10 * 1000L,
                        pendingIntent
                    )
                    Log.d(TAG, "Alarma Pomodoro en ventana programada para: $triggerAtMillis")
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Alarma Pomodoro exacta programada para: $triggerAtMillis")
                }
                else -> {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "Error de seguridad al programar alarma Pomodoro: ${se.message}")
        }
    }

    fun cancelFocusAlarm() {
        val intent = Intent(context, FocusAlarmReceiver::class.java).apply {
            action = FocusAlarmReceiver.ACTION_FOCUS_COMPLETE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Alarma Pomodoro cancelada")
        }
    }
}
