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
import com.lemonkids.shared.model.AlarmBackgroundMusic
import com.lemonkids.shared.model.AlarmVoiceText
import com.lemonkids.shared.repository.AlarmBackgroundMusicRepository
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
    private val remoteAlarmApplier: RemoteAlarmApplier,
    private val musicRepository: AlarmBackgroundMusicRepository,
    private val musicCache: AlarmBackgroundMusicCache,
    private val alarmDao: AlarmDao
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
        // 目录不可用不能阻塞精确闹钟下发；旧缓存和内置铃声仍是到点兜底。
        val musicById = musicRepository.getPublishedMusic().getOrElse {
            Log.w(TAG, "曲目目录读取失败，本轮只同步闹钟", it)
            emptyList()
        }.associateBy { it.id }
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
                    RemoteAlarmSnapshot(
                        alarmId = alarm.id, revision = alarm.revision, triggerAtMillis = triggerAt, endAtMillis = endAt,
                        timezone = alarm.timezone, title = alarm.title, message = alarm.message,
                        backgroundMusicId = AlarmBackgroundMusic.normalized(alarm.backgroundMusicId),
                        backgroundMusic = musicById[alarm.backgroundMusicId], voiceEnabled = alarm.voiceEnabled,
                        voiceText = alarm.voiceText.ifBlank { AlarmVoiceText.limited(alarm.title, alarm.message) },
                        enabled = false, requiresConfirmation = alarm.requiresConfirmation
                    )
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
                    backgroundMusicId = AlarmBackgroundMusic.normalized(alarm.backgroundMusicId),
                    backgroundMusic = musicById[alarm.backgroundMusicId],
                    voiceEnabled = alarm.voiceEnabled,
                    voiceText = alarm.voiceText.ifBlank { AlarmVoiceText.limited(alarm.title, alarm.message) },
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
            // 不等待下一次闹钟；网络仅发生在同步期，失败绝不撤销刚完成的部署。
            musicById[alarm.backgroundMusicId]?.let { music ->
                syncMusicCache(alarm.id, alarm.revision, deviceId, music)
            }
        }
        musicCache.cleanUnreferenced(alarmDao.getBackgroundMusicCacheFiles().toSet())
        Log.d(TAG, "远程闹钟对账完成 device=$deviceId count=${alarms.size} user=${user.uid}")
    }

    private suspend fun syncMusicCache(
        alarmId: String, revision: Long, deviceId: String, music: com.lemonkids.shared.model.AlarmBackgroundMusicAsset
    ) {
        val current = alarmDao.get(alarmId) ?: return
        if (current.revision != revision || !current.enabled) return
        if (current.backgroundMusicSha256 == music.sha256 &&
            musicCache.cachedFile(current.backgroundMusicCacheFile) != null
        ) {
            if (current.backgroundMusicCacheState != DeviceAlarmEntity.MUSIC_CACHE_READY) {
                alarmDao.upsert(current.copy(backgroundMusicCacheState = DeviceAlarmEntity.MUSIC_CACHE_READY))
            }
            reportMusicCache(alarmId, revision, deviceId, DeviceAlarmEntity.MUSIC_CACHE_READY)
            return
        }
        musicCache.cache(music).fold(
            onSuccess = { cached ->
                val latest = alarmDao.get(alarmId)
                if (latest?.revision == revision && latest.backgroundMusicSha256 == music.sha256) {
                    alarmDao.upsert(latest.copy(backgroundMusicCacheFile = cached.fileName, backgroundMusicCacheState = DeviceAlarmEntity.MUSIC_CACHE_READY))
                    reportMusicCache(alarmId, revision, deviceId, DeviceAlarmEntity.MUSIC_CACHE_READY)
                }
            },
            onFailure = { error ->
                val latest = alarmDao.get(alarmId)
                if (latest?.revision == revision && latest.backgroundMusicSha256 == music.sha256) {
                    alarmDao.upsert(latest.copy(backgroundMusicCacheState = DeviceAlarmEntity.MUSIC_CACHE_FAILED))
                }
                Log.w(TAG, "背景音乐缓存失败 alarmId=$alarmId", error)
                reportMusicCache(alarmId, revision, deviceId, DeviceAlarmEntity.MUSIC_CACHE_FAILED, "download_or_verify_failed")
            }
        )
    }

    private suspend fun reportMusicCache(alarmId: String, revision: Long, deviceId: String, state: String, errorCode: String? = null) {
        remoteAlarmRepository.updateBackgroundMusicCacheStatus(alarmId, deviceId, revision, state, errorCode)
            .onFailure { Log.w(TAG, "背景音乐缓存状态回执失败 alarmId=$alarmId", it) }
        remoteAlarmRepository.recordEvent(AlarmEvent(
            alarmId, deviceId, revision,
            if (state == DeviceAlarmEntity.MUSIC_CACHE_READY) AlarmEventType.MUSIC_CACHE_READY.value else AlarmEventType.MUSIC_CACHE_FAILED.value,
            if (state == DeviceAlarmEntity.MUSIC_CACHE_READY) "背景音乐已缓存" else "背景音乐缓存失败"
        )).onFailure { Log.w(TAG, "背景音乐缓存事件写入失败 alarmId=$alarmId", it) }
    }

    suspend fun reportRinging(alarmId: String, revision: Long) = report(
        alarmId, revision, AlarmDeliveryStatus.RINGING, AlarmEventType.RINGING, "Pad 开始响铃"
    )

    suspend fun reportDismissed(alarmId: String, revision: Long) = report(
        alarmId, revision, AlarmDeliveryStatus.DISMISSED, AlarmEventType.DISMISSED, "已在 Pad 关闭"
    )

    /** 状态仍为 deployed/ringing；error_code 仅标记可选增强展示不可用，不能误报下发失败。 */
    suspend fun reportOverlayUnavailable(alarmId: String, revision: Long, reason: String) = report(
        alarmId, revision, AlarmDeliveryStatus.RINGING, AlarmEventType.OVERLAY_UNAVAILABLE,
        "闹钟悬浮增强展示不可用：$reason", errorCode = reason
    )

    private suspend fun reportCapability(alarmId: String, revision: Long) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                report(alarmId, revision, AlarmDeliveryStatus.NOTIFICATION_DENIED, AlarmEventType.PERMISSION_DENIED, "通知权限未授予")
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() ->
                report(alarmId, revision, AlarmDeliveryStatus.FULL_SCREEN_DENIED, AlarmEventType.PERMISSION_DENIED, "全屏通知资格未开启")
            else -> {
                val overlayError = if (Settings.canDrawOverlays(context)) null else "overlay_permission_missing"
                report(alarmId, revision, AlarmDeliveryStatus.DEPLOYED, AlarmEventType.DEPLOYED,
                    if (overlayError == null) "已登记系统闹钟" else "已登记系统闹钟；未开启悬浮窗增强展示",
                    errorCode = overlayError)
            }
        }
    }

    private suspend fun report(
        alarmId: String,
        revision: Long,
        status: AlarmDeliveryStatus,
        eventType: AlarmEventType,
        detail: String,
        errorCode: String? = if (eventType == AlarmEventType.PERMISSION_DENIED) status.value else null
    ) {
        val deviceId = deviceId()
        remoteAlarmRepository.updateDelivery(
            AlarmDelivery(alarmId = alarmId, deviceId = deviceId, revision = revision, status = status.value,
                errorCode = errorCode,
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
