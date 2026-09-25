package com.example.studybuddy.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.studybuddy.FocusActivity
import com.example.studybuddy.MainActivity
import com.example.studybuddy.R
import com.example.studybuddy.util.StudyGoalManager
import java.util.Locale

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_REMINDERS = "StudyBuddy_Channel"
        const val CHANNEL_OVERDUE = "StudyBuddy_Overdue_Channel"
        const val CHANNEL_FOCUS = "StudyBuddy_Focus_Channel"
        const val CHANNEL_GOALS = "StudyBuddy_Goal_Channel"
        const val NOTIFICATION_ID = 100
        const val NOTIFICATION_FOCUS_ID = 200
        const val NOTIFICATION_FOCUS_ONGOING_ID = 201
        const val NOTIFICATION_GOAL_ID = 300
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    context.getString(R.string.notification_channel_reminders),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_reminders_desc)
                    enableLights(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_OVERDUE,
                    context.getString(R.string.notification_channel_overdue),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_overdue_desc)
                }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_FOCUS,
                    context.getString(R.string.notification_channel_focus),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_focus_desc)
                    enableLights(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 800, 300, 800)
                }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_GOALS,
                    context.getString(R.string.notification_channel_goals),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_goals_desc)
                    enableLights(true)
                    enableVibration(true)
                }
            )
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun showTaskReminderNotification(title: String, message: String, notificationId: Int = NOTIFICATION_ID) {
        if (!canPostNotifications()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun showOverdueSummaryNotification(count: Int) {
        if (!canPostNotifications() || count <= 0) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_OVERDUE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_overdue_title, count))
            .setContentText(context.getString(R.string.notification_overdue_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + 1, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun showFocusCompletedNotification(breakMinutes: Int, isLongBreak: Boolean) {
        if (!canPostNotifications()) return

        val intent = Intent(context, FocusActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_focus_done_title))
            .setContentText(context.getString(R.string.notification_focus_done_body, breakMinutes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_FOCUS_ID, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun showBreakCompletedNotification() {
        if (!canPostNotifications()) return

        val intent = Intent(context, FocusActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_break_done_title))
            .setContentText(context.getString(R.string.notification_break_done_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_FOCUS_ID, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun showFocusOngoingNotification(timeText: String, isFocus: Boolean, sessionProgress: String) {
        if (!canPostNotifications()) return

        val intent = Intent(context, FocusActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeTitle = if (isFocus) {
            context.getString(R.string.focus_mode)
        } else {
            context.getString(R.string.break_mode)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${context.getString(R.string.notification_focus_running_title)}: $modeTitle")
            .setContentText(context.getString(R.string.notification_focus_running_body, timeText, sessionProgress))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_FOCUS_ONGOING_ID, builder.build())
    }

    fun cancelFocusOngoingNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_FOCUS_ONGOING_ID)
    }

    fun cancelFocusCompletedNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_FOCUS_ID)
    }

    @SuppressLint("MissingPermission")
    fun showDailyGoalReminderNotification(
        completedMinutes: Int,
        targetMinutes: Int,
        remainingMinutes: Int
    ) {
        if (!canPostNotifications()) return

        val targetHours = targetMinutes / 60.0f
        val targetHoursStr = String.format(Locale.getDefault(), "%.1f", targetHours)
        val remainingStr = StudyGoalManager.formatDuration(remainingMinutes)
        val percent = if (targetMinutes > 0) minOf(100, (completedMinutes * 100) / targetMinutes) else 0

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_GOAL_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val focusIntent = Intent(context, FocusActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(FocusActivity.EXTRA_START_IMMEDIATELY, true)
        }
        val pendingFocusIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_GOAL_ID + 1,
            focusIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notification_goal_title)
        val message = if (completedMinutes == 0) {
            context.getString(R.string.notification_goal_body_not_started, targetHoursStr)
        } else {
            context.getString(
                R.string.notification_goal_body_progress,
                completedMinutes,
                targetHoursStr,
                percent,
                remainingStr
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_GOALS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setProgress(targetMinutes, completedMinutes, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_play_arrow,
                context.getString(R.string.notification_goal_action_start),
                pendingFocusIntent
            )

        NotificationManagerCompat.from(context).notify(NOTIFICATION_GOAL_ID, builder.build())
    }

    fun cancelDailyGoalReminderNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_GOAL_ID)
    }
}