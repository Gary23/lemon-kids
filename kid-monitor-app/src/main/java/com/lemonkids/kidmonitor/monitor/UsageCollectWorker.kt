package com.lemonkids.kidmonitor.monitor

import android.app.usage.UsageStatsManager
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
import com.lemonkids.shared.model.AppUsageRecord
import com.lemonkids.shared.repository.AppUsageRepository
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.util.Constants
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class UsageCollectWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        val appUsageRepository: AppUsageRepository
        val authRepository: AuthRepository
        val supabaseClient: SupabaseClient
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, WorkerEntryPoint::class.java
        )
        val auth = entryPoint.authRepository
        val supabase = entryPoint.supabaseClient
        val postgrest = supabase.pluginManager.getPlugin(Postgrest)

        val userId = auth.currentUserId ?: run {
            Log.w(TAG, "currentUserId is null, user not logged in yet")
            return Result.retry()
        }
        val user = auth.observeCurrentUser().first() ?: run {
            Log.w(TAG, "observeCurrentUser returned null")
            return Result.retry()
        }
        val familyId = user.familyId ?: run {
            Log.w(TAG, "familyId is null")
            return Result.retry()
        }

        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "UsageStats permission not granted, will retry later")
            return Result.retry()
        }

        Log.d(TAG, "Collecting usage data for user=$userId date=${LocalDate.now()}")
        val usageStatsManager = applicationContext.getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager

        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTime = System.currentTimeMillis()

        val statsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startOfDay, endTime
        )

        Log.d(TAG, "Found ${statsList.size} apps with foreground time")

        val appNameMap = resolveAppNames(statsList.map { it.packageName }.toSet())

        val records = statsList
            .filter { it.totalTimeInForeground > 0 }
            .map { stats ->
                AppUsageRecord(
                    familyId = familyId,
                    childId = userId,
                    packageName = stats.packageName,
                    appName = appNameMap[stats.packageName] ?: stats.packageName,
                    durationSeconds = stats.totalTimeInForeground / 1000,
                    date = today.toString()
                )
            }

        runCatching {
            postgrest.from("app_usage").delete {
                filter { eq("child_id", userId); eq("date", today.toString()) }
            }
        }

        if (records.isNotEmpty()) {
            Log.d(TAG, "Uploading ${records.size} usage records")
            entryPoint.appUsageRepository.uploadUsageRecords(records)
        } else {
            Log.d(TAG, "No usage records to upload")
        }

        return Result.success()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = applicationContext.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            applicationContext.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private suspend fun resolveAppNames(packageNames: Set<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            val pm = applicationContext.packageManager
            packageNames.associateWith { pkg ->
                runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
            }
        }

    companion object {
        private const val TAG = "UsageCollectWorker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 立即执行一次，确保首次使用就有数据
            val oneTimeRequest = OneTimeWorkRequestBuilder<UsageCollectWorker>()
                .setConstraints(constraints)
                .setInitialDelay(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(oneTimeRequest)

            // 注册周期任务，每15分钟采集一次
            val periodicRequest = PeriodicWorkRequestBuilder<UsageCollectWorker>(
                Constants.USAGE_COLLECT_INTERVAL_MINUTES,
                java.util.concurrent.TimeUnit.MINUTES
            ).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "usage_collect", ExistingPeriodicWorkPolicy.KEEP, periodicRequest
            )
        }
    }
}
