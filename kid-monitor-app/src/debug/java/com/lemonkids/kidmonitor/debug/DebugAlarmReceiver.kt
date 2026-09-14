package com.lemonkids.kidmonitor.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.lemonkids.kidmonitor.alarm.AlarmPresentation
import com.lemonkids.kidmonitor.alarm.AlarmRingService

/**
 * Debug APK 专用：从 adb 广播立即进入闹钟展示链路。测试会话不会读写 Room、
 * AlarmManager 或 Supabase；release 变体没有此类和 Manifest 条目。
 */
class DebugAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: "debug-local-${SystemClock.elapsedRealtime()}"
        val revision = intent.getLongExtra(EXTRA_REVISION, System.currentTimeMillis())
        if (intent.action == ACTION_STOP) {
            Log.i(TAG, "停止本地调试闹钟 alarmId=$alarmId revision=$revision")
            context.startService(AlarmRingService.stopIntent(context, alarmId, revision))
            return
        }
        if (intent.action != ACTION_TRIGGER) return
        Log.i(TAG, "触发本地调试闹钟 alarmId=$alarmId revision=$revision")
        ContextCompat.startForegroundService(
            context,
            AlarmRingService.debugStartIntent(
                context = context,
                alarmId = alarmId,
                revision = revision,
                title = intent.getStringExtra(EXTRA_TITLE) ?: AlarmPresentation.DEFAULT_TITLE,
                message = intent.getStringExtra(EXTRA_MESSAGE) ?: AlarmPresentation.DEFAULT_MESSAGE,
                requiresConfirmation = intent.getBooleanExtra(EXTRA_REQUIRES_CONFIRMATION, true),
                backgroundMusicId = intent.getStringExtra(EXTRA_BACKGROUND_MUSIC_ID) ?: "gentle_bell_v1",
                voiceEnabled = intent.getBooleanExtra(EXTRA_VOICE_ENABLED, true),
                voiceText = intent.getStringExtra(EXTRA_VOICE_TEXT)
            )
        )
    }

    companion object {
        const val ACTION_TRIGGER = "com.lemonkids.kidmonitor.debug.TRIGGER_ALARM"
        const val ACTION_STOP = "com.lemonkids.kidmonitor.debug.STOP_ALARM"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_REVISION = "revision"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_REQUIRES_CONFIRMATION = "requires_confirmation"
        const val EXTRA_BACKGROUND_MUSIC_ID = "background_music_id"
        const val EXTRA_VOICE_ENABLED = "voice_enabled"
        const val EXTRA_VOICE_TEXT = "voice_text"
        private const val TAG = "DebugAlarmReceiver"
    }
}
