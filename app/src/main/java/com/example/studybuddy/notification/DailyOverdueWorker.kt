package com.example.studybuddy.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.studybuddy.data.ServiceLocator
import kotlinx.coroutines.flow.first

class DailyOverdueWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = ServiceLocator.getRepository(applicationContext)
            val count = repository.overdueCount(System.currentTimeMillis()).first()
            if (count > 0) {
                NotificationHelper(applicationContext)
                    .showOverdueSummaryNotification(count)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}