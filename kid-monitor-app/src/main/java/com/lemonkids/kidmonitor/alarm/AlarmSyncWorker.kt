package com.lemonkids.kidmonitor.alarm

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/** 仅做下发对账；绝不承担准点触发。 */
class AlarmSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint { val coordinator: RemoteAlarmSyncCoordinator }

    override suspend fun doWork(): Result {
        val coordinator = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java).coordinator
        return coordinator.sync().fold(
            onSuccess = { Result.success() },
            onFailure = { error -> Log.w(TAG, "远程闹钟对账失败", error); Result.retry() }
        )
    }

    companion object {
        private const val TAG = "AlarmSyncWorker"
        private const val PERIODIC_NAME = "remote_alarm_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<AlarmSyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AlarmSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
