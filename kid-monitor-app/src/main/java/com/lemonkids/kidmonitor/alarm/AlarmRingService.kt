package com.lemonkids.kidmonitor.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 收到精确闹钟后立即成为媒体播放前台服务：保留 CPU 唤醒锁、音频焦点和振动。
 * 全屏通知是锁屏展示的主要入口；没有全屏权限时，通知仍保留可点击的提醒入口。
 */
class AlarmRingService : Service() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AlarmEntryPoint {
        val alarmDao: AlarmDao
        val remoteAlarmSyncCoordinator: RemoteAlarmSyncCoordinator
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentAlarmId: String? = null
    private var currentRevision: Long = -1L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getStringExtra(EXTRA_ALARM_ID) ?: return START_NOT_STICKY
        val revision = intent.getLongExtra(EXTRA_REVISION, -1L)
        if (intent.action == ACTION_STOP) {
            stopAlarm(alarmId, revision)
            return START_NOT_STICKY
        }
        currentAlarmId = alarmId
        currentRevision = revision
        startForeground(NOTIFICATION_ID, createNotification(alarmId, "闹钟时间到了", "请查看提醒"))
        acquireWakeLock()
        startAlerting()
        scope.launch {
            val dao = EntryPointAccessors.fromApplication(applicationContext, AlarmEntryPoint::class.java).alarmDao
            val alarm = runCatching { dao.get(alarmId) }.getOrNull()
            // Direct Boot 时凭据保护的 Room 可能还不可读。此时 boot store 已验证过版本，
            // 不能因为查库失败把本应响铃的闹钟静默停止。
            if (alarm?.enabled == false) {
                stopAlarm(alarmId, revision)
                return@launch
            }
            if (alarm != null) dao.updateState(alarmId, DeviceAlarmEntity.STATE_RINGING)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, createNotification(alarmId, alarm?.title ?: "闹钟时间到了", alarm?.message ?: "请查看提醒"))
            if (revision > 0) {
                EntryPointAccessors.fromApplication(applicationContext, AlarmEntryPoint::class.java)
                    .remoteAlarmSyncCoordinator.reportRinging(alarmId, revision)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopAlerting()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAlerting() {
        val audioManager = getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        mediaPlayer = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(this@AlarmRingService, sound)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        val vibrator = getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 450), 0))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(longArrayOf(0, 700, 450), 0)
        }
    }

    private fun stopAlarm(alarmId: String, revision: Long = currentRevision) {
        scope.launch {
            runCatching {
                val entryPoint = EntryPointAccessors.fromApplication(applicationContext, AlarmEntryPoint::class.java)
                entryPoint.alarmDao.updateState(alarmId, DeviceAlarmEntity.STATE_DISMISSED)
                if (revision > 0) entryPoint.remoteAlarmSyncCoordinator.reportDismissed(alarmId, revision)
            }
        }
        stopAlerting()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopAlerting() {
        mediaPlayer?.let { player -> runCatching { player.stop() } }
        mediaPlayer?.release()
        mediaPlayer = null
        getSystemService(Vibrator::class.java).cancel()
        val audioManager = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION") audioManager.abandonAudioFocus(null)
        }
        audioFocusRequest = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:alarm").apply {
            acquire(MAX_RING_MILLIS)
        }
    }

    private fun createNotification(alarmId: String, title: String, message: String): Notification {
        createChannel()
        val fullScreen = PendingIntent.getActivity(
            this,
            alarmId.hashCode(),
            Intent(this, AlarmActivity::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_REVISION, currentRevision)
                putExtra(AlarmActivity.EXTRA_TITLE, title)
                putExtra(AlarmActivity.EXTRA_MESSAGE, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            alarmId.hashCode() xor 0x4A17,
            stopIntent(this, alarmId, currentRevision),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "关闭闹钟", stop)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "闹钟提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "家长设置的到点闹钟提醒"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "lemon_alarm_ringing"
        private const val NOTIFICATION_ID = 3107
        private const val MAX_RING_MILLIS = 10 * 60 * 1000L
        private const val ACTION_STOP = "com.lemonkids.kidmonitor.alarm.STOP"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_REVISION = "revision"

        fun startIntent(context: Context, alarmId: String, revision: Long) = Intent(context, AlarmRingService::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_REVISION, revision)
        }

        fun stopIntent(context: Context, alarmId: String, revision: Long = -1L) = Intent(context, AlarmRingService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_REVISION, revision)
        }
    }
}
