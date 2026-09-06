package com.lemonkids.kidmonitor.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/** 静态 Receiver 可在进程已被系统回收时由 AlarmManager 冷启动。 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        val revision = intent.getLongExtra(EXTRA_REVISION, -1L)
        val scheduled = AlarmBootStore(context).find(alarmId)
        if (scheduled == null || scheduled.revision != revision) {
            Log.w(TAG, "忽略已取消或过期的闹钟 alarmId=$alarmId revision=$revision")
            return
        }
        ContextCompat.startForegroundService(context, AlarmRingService.startIntent(context, alarmId, revision))
    }

    companion object {
        const val ACTION_FIRE = "com.lemonkids.kidmonitor.alarm.FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_REVISION = "revision"
        private const val TAG = "AlarmReceiver"
    }
}
