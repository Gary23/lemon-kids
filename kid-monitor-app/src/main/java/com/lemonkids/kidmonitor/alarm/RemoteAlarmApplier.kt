package com.lemonkids.kidmonitor.alarm

import javax.inject.Inject
import javax.inject.Singleton

/** 云端同步层交给 Pad 的不可变快照。关闭和删除都以下发 disabled 新版本撤销。 */
data class RemoteAlarmSnapshot(
    val alarmId: String,
    val revision: Long,
    val triggerAtMillis: Long,
    val endAtMillis: Long,
    val timezone: String,
    val title: String,
    val message: String,
    val enabled: Boolean,
    val requiresConfirmation: Boolean
)

/**
 * 远程下发的唯一落点。先落库再登记 OS 闹钟；同一或更旧 revision 重放不会改变本地状态。
 * 后续 Supabase Realtime、推送唤醒和周期对账都必须调用此类，不能各自直接操作 AlarmManager。
 */
@Singleton
class RemoteAlarmApplier @Inject constructor(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler
) {
    suspend fun apply(snapshot: RemoteAlarmSnapshot): AlarmScheduleResult? {
        val current = alarmDao.get(snapshot.alarmId)
        if (current != null && current.revision >= snapshot.revision) return null

        val nextTriggerAtMillis = AlarmOccurrence.nextInRange(
            snapshot.triggerAtMillis, snapshot.endAtMillis, snapshot.timezone
        )
        val local = DeviceAlarmEntity(
            alarmId = snapshot.alarmId,
            revision = snapshot.revision,
            triggerAtMillis = nextTriggerAtMillis ?: snapshot.triggerAtMillis,
            endAtMillis = snapshot.endAtMillis,
            timezone = snapshot.timezone,
            title = snapshot.title,
            message = snapshot.message,
            enabled = snapshot.enabled && nextTriggerAtMillis != null,
            requiresConfirmation = snapshot.requiresConfirmation
        )
        alarmDao.upsert(local)
        if (!local.enabled) {
            alarmScheduler.cancel(snapshot.alarmId)
            return null
        }
        return alarmScheduler.schedule(
            AlarmTrigger(snapshot.alarmId, snapshot.revision, local.triggerAtMillis)
        )
    }

    /** 远端仍保留启用态但目标时间已过时的本地收敛：不得在恢复网络后补响。 */
    suspend fun markMissed(snapshot: RemoteAlarmSnapshot) {
        val current = alarmDao.get(snapshot.alarmId)
        if (current != null && current.revision > snapshot.revision) return
        alarmDao.upsert(
            DeviceAlarmEntity(
                alarmId = snapshot.alarmId, revision = snapshot.revision,
                triggerAtMillis = snapshot.triggerAtMillis, endAtMillis = snapshot.endAtMillis, timezone = snapshot.timezone,
                title = snapshot.title, message = snapshot.message,
                enabled = false, requiresConfirmation = snapshot.requiresConfirmation, state = DeviceAlarmEntity.STATE_DISMISSED
            )
        )
        alarmScheduler.cancel(snapshot.alarmId)
    }

    /** 当前这一次响铃结束后，把同一日期范围内的下一天交给系统闹钟。 */
    suspend fun scheduleNextOccurrence(alarmId: String, revision: Long): AlarmScheduleResult? {
        val current = alarmDao.get(alarmId) ?: return null
        if (!current.enabled || current.revision != revision) return null
        val nextTriggerAtMillis = AlarmOccurrence.nextAfter(
            current.triggerAtMillis, current.endAtMillis, current.timezone
        ) ?: run {
            alarmDao.upsert(current.copy(enabled = false, state = DeviceAlarmEntity.STATE_DISMISSED))
            alarmScheduler.cancel(alarmId)
            return null
        }
        val next = current.copy(
            triggerAtMillis = nextTriggerAtMillis,
            state = DeviceAlarmEntity.STATE_SCHEDULED,
            updatedAtMillis = System.currentTimeMillis()
        )
        alarmDao.upsert(next)
        return alarmScheduler.schedule(AlarmTrigger(alarmId, revision, nextTriggerAtMillis))
    }
}
