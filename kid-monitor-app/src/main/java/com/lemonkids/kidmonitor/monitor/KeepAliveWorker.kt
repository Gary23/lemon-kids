package com.lemonkids.kidmonitor.monitor

import android.app.ActivityManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.lemonkids.shared.model.DeviceStatusEventType
import java.util.concurrent.TimeUnit

class KeepAliveWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        if (!isServiceRunning()) {
            LimitEnforcementService.start(applicationContext)
            DeviceStatusWorker.reportNow(applicationContext, DeviceStatusEventType.SERVICE_RECOVERED)
        }
        return Result.success()
    }

    private fun isServiceRunning(): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = manager.getRunningServices(Int.MAX_VALUE)
        val serviceClassName = LimitEnforcementService::class.java.name
        return runningServices.any { it.service.className == serviceClassName }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "keep_alive", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
