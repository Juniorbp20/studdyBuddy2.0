package com.example.studybuddy.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.studybuddy.R
import com.example.studybuddy.data.ServiceLocator
import com.example.studybuddy.model.RepeatInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val EXTRA_REPEAT_INTERVAL = "repeat_interval"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarma activada")

        val taskTitle = intent.getStringExtra("task_title")
            ?: context.getString(R.string.notification_task)
        val taskDescription = intent.getStringExtra("task_description") ?: ""
        val taskId = intent.getIntExtra("task_id", -1)
        val repeatInterval = intent.getIntExtra(EXTRA_REPEAT_INTERVAL, RepeatInterval.NONE)

        val notificationHelper = NotificationHelper(context)
        notificationHelper.showTaskReminderNotification(
            context.getString(R.string.notification_reminder_title),
            context.getString(
                R.string.notification_reminder_body, taskTitle,
                if (taskDescription.isBlank()) "" else "\n$taskDescription"
            ),
            taskId
        )

        if (repeatInterval > RepeatInterval.NONE) {
            reschedule(context, taskId, taskTitle, taskDescription, repeatInterval)
        }
    }

    private fun reschedule(
        context: Context,
        taskId: Int,
        taskTitle: String,
        taskDescription: String,
        repeatInterval: Int
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = ServiceLocator.getRepository(context)
                val task = repository.allTasks.first().firstOrNull { it.id == taskId }
                if (task != null && !task.isCompleted) {
                    val nextDueDate = task.dueDate + repeatInterval * 24L * 60 * 60 * 1000
                    repository.update(task.copy(dueDate = nextDueDate))
                    val helper = AlarmManagerHelper(context)
                    helper.setAlarm(
                        taskId,
                        taskTitle,
                        taskDescription,
                        nextDueDate,
                        repeatInterval
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reprogramando alarma recurrente: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}