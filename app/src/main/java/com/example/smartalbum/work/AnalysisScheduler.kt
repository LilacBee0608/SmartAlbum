package com.example.smartalbum.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object AnalysisScheduler {

    fun scheduleSlowBackground(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<PhotoAnalysisWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PhotoAnalysisWorker.UNIQUE_SLOW_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
    }

    fun enqueueInstantForMediaId(context: Context, mediaId: Long) {
        val data = workDataOf(PhotoAnalysisWorker.KEY_MEDIA_ID to mediaId)
        val base = OneTimeWorkRequestBuilder<PhotoAnalysisWorker>()
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
        val request = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            base.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        } else {
            base.build()
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            "instant_$mediaId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun enqueueOneShotBatch(context: Context) {
        val request = OneTimeWorkRequestBuilder<PhotoAnalysisWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
