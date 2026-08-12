package com.lemonkids.kidmonitor.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lemonkids.shared.model.DeviceStatusEventType
import com.lemonkids.shared.model.DeviceStatusLog
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.DeviceStatusRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class DeviceStatusWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        val authRepository: AuthRepository
        val deviceStatusRepository: DeviceStatusRepository
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )
        val auth = entryPoint.authRepository
        val userId = auth.currentUserId ?: run {
            Log.w(TAG, "currentUserId is null, skip device status upload")
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

        val eventType = inputData.getString(KEY_EVENT_TYPE)
            ?: DeviceStatusEventType.HEARTBEAT.value
        val accessibilityEnabled = AppLimitAccessibilityService.isEnabled(applicationContext)
        val limitServiceRunning = isLimitServiceRunning()
        val finalEventType = when {
            !accessibilityEnabled -> DeviceStatusEventType.ACCESSIBILITY_DISABLED.value
            eventType == DeviceStatusEventType.ACCESSIBILITY_DISABLED.value -> DeviceStatusEventType.HEARTBEAT.value
            else -> eventType
        }

        val log = DeviceStatusLog(
            familyId = familyId,
            childId = userId,
            eventType = finalEventType,
            accessibilityEnabled = accessibilityEnabled,
            limitServiceRunning = limitServiceRunning,
            appProcessAlive = true,
            batteryIgnoringOptimizations = isIgnoringBatteryOptimizations(),
            message = buildMessage(finalEventType, accessibilityEnabled, limitServiceRunning)
        )

        return entryPoint.deviceStatusRepository.uploadStatusLog(log).fold(
            onSuccess = {
                Log.d(TAG, "设备状态已上报 event=$finalEventType")
                Result.success()
            },
            onFailure = {
                Log.e(TAG, "设备状态上报失败", it)
                Result.retry()
            }
        )
    }

    private fun isLimitServiceRunning(): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val serviceClassName = LimitEnforcementService::class.java.name
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == serviceClassName }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = applicationContext.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(applicationContext.packageName)
    }

    private fun buildMessage(
        eventType: String,
        accessibilityEnabled: Boolean,
        limitServiceRunning: Boolean
    ): String {
        return when {
            !accessibilityEnabled -> "无障碍权限未开启，应用限制可能无法完整生效"
            !limitServiceRunning -> "限制服务未运行，等待系统调度恢复"
            eventType == DeviceStatusEventType.APP_START.value -> "孩子端应用启动"
            eventType == DeviceStatusEventType.BOOT.value -> "设备启动后恢复上报"
            eventType == DeviceStatusEventType.USER_PRESENT.value -> "用户解锁后上报"
            eventType == DeviceStatusEventType.SERVICE_RECOVERED.value -> "保活任务恢复限制服务"
            else -> "设备状态正常"
        }
    }

    companion object {
        private const val TAG = "DeviceStatusWorker"
        private const val KEY_EVENT_TYPE = "event_type"
        private const val UNIQUE_PERIODIC_NAME = "device_status_heartbeat"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<DeviceStatusWorker>(
                15,
                TimeUnit.MINUTES
            ).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun reportNow(context: Context, eventType: DeviceStatusEventType) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<DeviceStatusWorker>()
                .setInputData(workDataOf(KEY_EVENT_TYPE to eventType.value))
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
