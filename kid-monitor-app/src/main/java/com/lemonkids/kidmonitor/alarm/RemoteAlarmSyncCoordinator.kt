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
        // WorkManager 可以在应用进程刚创建时运行；此时 StateFlow 的初始值仍为 null，
        // 但 Auth SDK 可能已经在私有存储中持有有效会话。主动恢复一次再判定，避免
        // 一次启动时序竞争让本轮远程闹钟完全漏同步。
        val user = authRepository.observeCurrentUser().first()
            ?: authRepository.restoreSession().getOrElse {
                throw IllegalStateException("监控端会话恢复失败，暂不对账", it)
            }
            ?: error("监控端尚未完成登录，暂不对账")
        val deviceId = deviceId()
        val alarms = remoteAlarmRepository.getAlarmsForDevice(deviceId).getOrThrow()
        alarms.forEach { alarm ->
            // 已删除的云端记录仍会投递给目标 Pad，确保离线期间已登记的系统闹钟能被撤销。
            val shouldRemove = !alarm.enabled || alarm.deletedAt != null
            val triggerAt = runCatching { parseServerInstant(alarm.triggerAt).toEpochMilli() }
                .getOrElse {
                    Log.e(TAG, "忽略时间格式无效的远程闹钟 alarmId=${alarm.id}", it)
                    report(alarm.id, alarm.revision, AlarmDeliveryStatus.MISSED, AlarmEventType.MISSED, "trigger_at 无效")
                    return@forEach
                }
            val endAt = runCatching { parseServerInstant(alarm.endAt).toEpochMilli() }
                .getOrElse {
                    // 兼容没有日期范围的历史单点闹钟。
                    triggerAt
                }
            if (endAt < triggerAt) {
                report(alarm.id, alarm.revision, AlarmDeliveryStatus.MISSED, AlarmEventType.MISSED, "结束日期不能早于开始日期")
                return@forEach
            }
            // 日期范围闹钟只登记范围内下一次尚未来临的每日时刻；不会因前几天未部署而补响。
            if (!shouldRemove && AlarmOccurrence.nextInRange(triggerAt, endAt, alarm.timezone) == null) {
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
                    enabled = !shouldRemove,
                    requiresConfirmation = alarm.requiresConfirmation
                )
            )
            when (result) {
                AlarmScheduleResult.Scheduled -> reportCapability(alarm.id, alarm.revision)
                AlarmScheduleResult.ExactAlarmPermissionMissing -> report(
                    alarm.id, alarm.revision, AlarmDeliveryStatus.EXACT_ALARM_DENIED,
                    AlarmEventType.PERMISSION_DENIED, "未授予精确闹钟权限"
                )
                null -> if (shouldRemove) report(
                    alarm.id, alarm.revision, AlarmDeliveryStatus.REMOVED, AlarmEventType.REMOVED, "已从本机移除闹钟"
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

    /**
     * PostgREST 在当前项目会将 TIMESTAMPTZ 序列化为 `+00:00` 结尾。
     * 部分旧版 Android 的 desugared `Instant.parse` 只接受 `Z`，会把有效的
     * UTC 时间误判为无效，导致闹钟完全不下发。
     */
    private fun parseServerInstant(value: String): Instant = Instant.parse(
        if (value.endsWith("+00:00")) value.dropLast("+00:00".length) + "Z" else value
    )

    companion object { private const val TAG = "RemoteAlarmSync" }
}
