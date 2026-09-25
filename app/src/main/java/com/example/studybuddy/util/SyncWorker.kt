package com.example.studybuddy.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val syncManager = SyncManager.getInstance(applicationContext)
            val success = syncManager.performSync(applicationContext)
            if (success) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME_ONE_TIME = "studybuddy_sync_work_one_time"
        private const val UNIQUE_WORK_NAME_PERIODIC = "studybuddy_sync_work_periodic"

        fun scheduleOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME_PERIODIC,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
