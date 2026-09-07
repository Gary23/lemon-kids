package com.lemonkids.kidmonitor.alarm

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.lemonkids.shared.model.AlarmDelivery
import com.lemonkids.shared.model.AlarmDeliveryStatus
import com.lemonkids.shared.model.AlarmEvent
import com.lemonkids.shared.model.AlarmEventType
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.RemoteAlarmRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pad 与云端闹钟的唯一同步入口。WorkManager、应用启动和用户解锁都只调用它；
 * 准点执行不依赖本类，而是依赖已落地的 AlarmManager.setAlarmClock。
 */
@Singleton
class RemoteAlarmSyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val remoteAlarmRepository: RemoteAlarmRepository,
    private val remoteAlarmApplier: RemoteAlarmApplier
) {
    suspend fun sync(): Result<Unit> = runCatching {
        val user = authRepository.observeCurrentUser().first()
            ?: error("监控端尚未完成登录，暂不对账")
        val deviceId = deviceId()
        val alarms = remoteAlarmRepository.getAlarmsForDevice(deviceId).getOrThrow()
        alarms.forEach { alarm ->
            val triggerAt = runCatching { Instant.parse(alarm.triggerAt).toEpochMilli() }
                .getOrElse {
                    Log.e(TAG, "忽略时间格式无效的远程闹钟 alarmId=${alarm.id}", it)
                    report(alarm.id, alarm.revision, AlarmDeliveryStatus.MISSED, AlarmEventType.MISSED, "trigger_at 无效")
                    return@forEach
                }
            val endAt = runCatching { Instant.parse(alarm.endAt).toEpochMilli() }
                .getOrElse {
                    // 兼容没有日期范围的历史单点闹钟。
                    triggerAt
                }
            if (endAt < triggerAt) {
                report(alarm.id, alarm.revision, AlarmDeliveryStatus.MISSED, AlarmEventType.MISSED, "结束日期不能早于开始日期")
                return@forEach
            }
            // 日期范围闹钟只登记范围内下一次尚未来临的每日时刻；不会因前几天未部署而补响。
            if (alarm.enabled && AlarmOccurrence.nextInRange(triggerAt, endAt, alarm.timezone) == null) {
                remoteAlarmApplier.markMissed(
                    RemoteAlarmSnapshot(alarm.id, alarm.revision, triggerAt, endAt, alarm.timezone, alarm.title, alarm.message, false, alarm.requiresConfirmation)
                )
                report(alarm.id, alarm.revision, AlarmDeliveryStatus.MISSED, AlarmEventType.MISSED, "日期范围内已无待执行提醒")
                return@forEach
            }
            val result = remoteAlarmApplier.apply(
                RemoteAlarmSnapshot(
                    alarmId = alarm.id,
                    revision = alarm.revision,
                    triggerAtMillis = triggerAt,
                    endAtMillis = endAt,
                    timezone = alarm.timezone,
                    title = alarm.title,
                    message = alarm.message,
                    enabled = alarm.enabled,
                    requiresConfirmation = alarm.requiresConfirmation
                )
            )
            when (result) {
                AlarmScheduleResult.Scheduled -> reportCapability(alarm.id, alarm.revision)
                AlarmScheduleResult.ExactAlarmPermissionMissing -> report(
                    alarm.id, alarm.revision, AlarmDeliveryStatus.EXACT_ALARM_DENIED,
                    AlarmEventType.PERMISSION_DENIED, "未授予精确闹钟权限"
                )
                null -> if (!alarm.enabled) report(
                    alarm.id, alarm.revision, AlarmDeliveryStatus.DEPLOYED, AlarmEventType.DEPLOYED, "已取消本地闹钟"
                )
            }
        }
        Log.d(TAG, "远程闹钟对账完成 device=$deviceId count=${alarms.size} user=${user.uid}")
    }

    suspend fun reportRinging(alarmId: String, revision: Long) = report(
        alarmId, revision, AlarmDeliveryStatus.RINGING, AlarmEventType.RINGING, "Pad 开始响铃"
    )

    suspend fun reportDismissed(alarmId: String, revision: Long) = report(
        alarmId, revision, AlarmDeliveryStatus.DISMISSED, AlarmEventType.DISMISSED, "已在 Pad 关闭"
    )

    private suspend fun reportCapability(alarmId: String, revision: Long) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                report(alarmId, revision, AlarmDeliveryStatus.NOTIFICATION_DENIED, AlarmEventType.PERMISSION_DENIED, "通知权限未授予")
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() ->
                report(alarmId, revision, AlarmDeliveryStatus.FULL_SCREEN_DENIED, AlarmEventType.PERMISSION_DENIED, "全屏通知资格未开启")
            else -> report(alarmId, revision, AlarmDeliveryStatus.DEPLOYED, AlarmEventType.DEPLOYED, "已登记系统闹钟")
        }
    }

    private suspend fun report(
        alarmId: String,
        revision: Long,
        status: AlarmDeliveryStatus,
        eventType: AlarmEventType,
        detail: String
    ) {
        val deviceId = deviceId()
        remoteAlarmRepository.updateDelivery(
            AlarmDelivery(alarmId = alarmId, deviceId = deviceId, revision = revision, status = status.value,
                errorCode = if (eventType == AlarmEventType.PERMISSION_DENIED) status.value else null,
                ackAt = Instant.now().toString())
        ).onFailure { Log.w(TAG, "闹钟回执更新失败 alarmId=$alarmId", it) }
        remoteAlarmRepository.recordEvent(
            AlarmEvent(alarmId, deviceId, revision, eventType.value, detail)
        ).onFailure { Log.w(TAG, "闹钟事件写入失败 alarmId=$alarmId", it) }
    }

    private fun deviceId(): String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ?: error("无法读取设备标识")

    companion object { private const val TAG = "RemoteAlarmSync" }
}
