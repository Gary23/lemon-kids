package com.lemonkids.kidmonitor.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.lemonkids.kidmonitor.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AlarmTrigger(
    val alarmId: String,
    val revision: Long,
    val triggerAtMillis: Long
)

sealed interface AlarmScheduleResult {
    data object Scheduled : AlarmScheduleResult
    data object ExactAlarmPermissionMissing : AlarmScheduleResult
}

/**
 * 只负责 OS 级闹钟登记，不依赖进程或常驻服务。
 * setAlarmClock 属于 Android 的闹钟通道：系统会显示下一次闹钟并在 Doze 下按时唤醒。
 */
@Singleton
class AlarmScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val bootStore = AlarmBootStore(context)

    fun schedule(trigger: AlarmTrigger): AlarmScheduleResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return AlarmScheduleResult.ExactAlarmPermissionMissing
        }
        cancelSystemAlarm(trigger.alarmId)
        bootStore.save(trigger)
        val operation = receiverIntent(trigger)
        val showIntent = PendingIntent.getActivity(
            context,
            trigger.alarmId.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger.triggerAtMillis, showIntent), operation)
        return AlarmScheduleResult.Scheduled
    }

    fun cancel(alarmId: String) {
        cancelSystemAlarm(alarmId)
        bootStore.remove(alarmId)
    }

    fun restoreAfterBoot() {
        bootStore.read().forEach { trigger ->
            if (trigger.triggerAtMillis > System.currentTimeMillis()) schedule(trigger)
        }
    }

    fun exactAlarmSettingsIntent(): Intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
    }

    private fun cancelSystemAlarm(alarmId: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_FIRE
                data = Uri.parse("lemon-alarm://trigger/$alarmId")
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun receiverIntent(trigger: AlarmTrigger): PendingIntent = PendingIntent.getBroadcast(
        context,
        trigger.alarmId.hashCode(),
        Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            data = Uri.parse("lemon-alarm://trigger/${trigger.alarmId}")
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, trigger.alarmId)
            putExtra(AlarmReceiver.EXTRA_REVISION, trigger.revision)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
