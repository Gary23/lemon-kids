package com.lemonkids.kidmonitor.alarm

import android.content.Context

/**
 * 设备保护存储只保存重启前恢复 AlarmManager 所需的最小字段；不放家长文案等隐私数据。
 * 即使 Pad 尚未首次解锁，也能重新登记本地精确闹钟。
 */
class AlarmBootStore(context: Context) {
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(trigger: AlarmTrigger) {
        val entries = read()
            .filterNot { it.alarmId == trigger.alarmId }
            .map(::encode)
            .toMutableSet()
        entries += encode(trigger)
        preferences.edit().putStringSet(KEY_TRIGGERS, entries).apply()
    }

    fun remove(alarmId: String) {
        preferences.edit().putStringSet(
            KEY_TRIGGERS,
            read().filterNot { it.alarmId == alarmId }.map(::encode).toSet()
        ).apply()
    }

    fun find(alarmId: String): AlarmTrigger? = read().firstOrNull { it.alarmId == alarmId }

    fun read(): List<AlarmTrigger> = preferences.getStringSet(KEY_TRIGGERS, emptySet())
        .orEmpty()
        .mapNotNull(::decode)
        .filter { it.triggerAtMillis > System.currentTimeMillis() - STALE_GRACE_MILLIS }

    private fun encode(trigger: AlarmTrigger) =
        "${trigger.alarmId}|${trigger.revision}|${trigger.triggerAtMillis}"

    private fun decode(value: String): AlarmTrigger? = value.split('|').let { fields ->
        if (fields.size != 3) return@let null
        val revision = fields[1].toLongOrNull() ?: return@let null
        val triggerAt = fields[2].toLongOrNull() ?: return@let null
        AlarmTrigger(fields[0], revision, triggerAt)
    }

    companion object {
        private const val PREFERENCES = "lemon_alarm_boot"
        private const val KEY_TRIGGERS = "triggers"
        private const val STALE_GRACE_MILLIS = 10 * 60 * 1000L
    }
}
