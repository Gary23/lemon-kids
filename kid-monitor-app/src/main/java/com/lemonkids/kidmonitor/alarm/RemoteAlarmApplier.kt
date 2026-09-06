package com.lemonkids.kidmonitor.alarm

import javax.inject.Inject
import javax.inject.Singleton

/** 云端同步层交给 Pad 的不可变快照。删除以 enabled=false 的新版本表达。 */
data class RemoteAlarmSnapshot(
    val alarmId: String,
    val revision: Long,
    val triggerAtMillis: Long,
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

        val local = DeviceAlarmEntity(
            alarmId = snapshot.alarmId,
            revision = snapshot.revision,
            triggerAtMillis = snapshot.triggerAtMillis,
            title = snapshot.title,
            message = snapshot.message,
            enabled = snapshot.enabled,
            requiresConfirmation = snapshot.requiresConfirmation
        )
        alarmDao.upsert(local)
        if (!snapshot.enabled) {
            alarmScheduler.cancel(snapshot.alarmId)
            return null
        }
        return alarmScheduler.schedule(
            AlarmTrigger(snapshot.alarmId, snapshot.revision, snapshot.triggerAtMillis)
        )
    }
}
