package com.lemonkids.kidmonitor.alarm

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 单一前台服务维护所有正在响铃的会话，避免旧 revision 的关闭回调影响新会话。 */
class AlarmRingService : Service() {
    @EntryPoint @InstallIn(SingletonComponent::class)
    interface AlarmEntryPoint {
        val alarmDao: AlarmDao
        val remoteAlarmApplier: RemoteAlarmApplier
        val remoteAlarmSyncCoordinator: RemoteAlarmSyncCoordinator
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = LinkedHashMap<AlarmSession, AlarmPresentation>()
    /** 仅由 src/debug 的本地 Receiver 创建；不访问 Room、排程或云端回执。 */
    private val debugSessions = mutableSetOf<AlarmSession>()
    private lateinit var presentationCoordinator: AlarmPresentationCoordinator
    private var receiverRegistered = false
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var audioController: AlarmAudioController

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mainHandler.post {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> presentationCoordinator.onUserPresent()
                Intent.ACTION_SCREEN_OFF -> presentationCoordinator.onScreenOff()
                Intent.ACTION_SCREEN_ON -> presentationCoordinator.onScreenOn()
            }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioController = AlarmAudioController(applicationContext)
        presentationCoordinator = AlarmPresentationCoordinator(
            isKeyguardLocked = { getSystemService(KeyguardManager::class.java).isKeyguardLocked },
            overlay = AlarmOverlayController(applicationContext),
            onOverlayUnavailable = { presentation, reason ->
                Log.w(TAG, "闹钟增强展示不可用 reason=$reason alarmId=${presentation.alarmId}")
                reportOverlayState(presentation, reason)
            },
            onDismissRequested = { presentation -> startService(stopIntent(this, presentation.alarmId, presentation.revision)) }
        )
        AlarmPresentationRegistry.attach(presentationCoordinator)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getStringExtra(EXTRA_ALARM_ID) ?: return START_NOT_STICKY
        val revision = intent.getLongExtra(EXTRA_REVISION, -1L)
        // Release APK 没有任何可调用此分支的导出组件，服务本身也是非导出。
        val debugSession = intent.getBooleanExtra(EXTRA_DEBUG_SESSION, false)
        Log.i(TAG, "收到闹钟服务请求 alarmId=$alarmId revision=$revision debug=$debugSession action=${intent.action}")
        if (intent.action == ACTION_STOP) {
            mainHandler.post { stopAlarm(AlarmSession(alarmId, revision)) }
        } else if (revision > 0L) {
            val presentation = AlarmPresentation(
                alarmId = alarmId,
                revision = revision,
                title = intent.getStringExtra(EXTRA_DEBUG_TITLE) ?: AlarmPresentation.DEFAULT_TITLE,
                message = intent.getStringExtra(EXTRA_DEBUG_MESSAGE) ?: AlarmPresentation.DEFAULT_MESSAGE,
                requiresConfirmation = intent.getBooleanExtra(EXTRA_DEBUG_REQUIRES_CONFIRMATION, true)
            )
            val debugAudioConfig = if (debugSession) {
                AlarmAudioController.Config(
                    backgroundMusicId = intent.getStringExtra(EXTRA_DEBUG_BACKGROUND_MUSIC_ID) ?: "gentle_bell_v1",
                    voiceEnabled = intent.getBooleanExtra(EXTRA_DEBUG_VOICE_ENABLED, true),
                    voiceText = intent.getStringExtra(EXTRA_DEBUG_VOICE_TEXT)
                        ?: "${presentation.title}。${presentation.message}"
                )
            } else null
            mainHandler.post { startAlarm(presentation, debugSession, debugAudioConfig) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        receiverRegistered = false
        presentationCoordinator.dispose()
        AlarmPresentationRegistry.detach(presentationCoordinator)
        stopAlerting()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAlarm(
        initial: AlarmPresentation,
        debugSession: Boolean,
        debugAudioConfig: AlarmAudioController.Config? = null
    ) {
        active.keys.filter { it.alarmId == initial.alarmId && it.revision < initial.revision }
            .toList().forEach { stopAlarm(it, reportDismissal = false) }
        val wasEmpty = active.isEmpty()
        active[initial.session] = initial
        if (debugSession) debugSessions += initial.session
        if (wasEmpty) {
            startForeground(NOTIFICATION_ID, createSummaryNotification())
            acquireWakeLock()
            startAlerting(debugAudioConfig)
            registerScreenReceiver()
        }
        presentationCoordinator.onAlarmStarted(initial)
        refreshNotifications()
        if (debugSession) {
            scope.launch {
                delay(MAX_RING_MILLIS)
                mainHandler.post { stopAlarm(initial.session) }
            }
            return
        }
        scope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(applicationContext, AlarmEntryPoint::class.java)
            val alarm = runCatching { entryPoint.alarmDao.get(initial.alarmId) }.getOrNull()
            if (alarm?.enabled == false || alarm?.revision != initial.revision) {
                mainHandler.post { stopAlarm(initial.session, reportDismissal = false) }
                return@launch
            }
            val presentation = initial.copy(
                title = alarm?.title ?: initial.title,
                message = alarm?.message ?: initial.message,
                requiresConfirmation = alarm?.requiresConfirmation ?: initial.requiresConfirmation
            )
            if (alarm != null) entryPoint.alarmDao.updateState(initial.alarmId, DeviceAlarmEntity.STATE_RINGING)
            mainHandler.post {
                if (active.containsKey(initial.session)) {
                    active[initial.session] = presentation
                    audioController.updateAndSpeak(
                        AlarmAudioController.Config(alarm?.backgroundMusicId ?: "gentle_bell_v1", alarm?.voiceEnabled ?: true,
                            alarm?.voiceText?.ifBlank { "${presentation.title}。${presentation.message}" } ?: "${presentation.title}。${presentation.message}")
                    )
                    presentationCoordinator.onAlarmUpdated(presentation)
                    refreshNotifications()
                }
            }
            entryPoint.remoteAlarmSyncCoordinator.reportRinging(initial.alarmId, initial.revision)
            delay(MAX_RING_MILLIS)
            mainHandler.post { stopAlarm(initial.session) }
        }
    }

    private fun stopAlarm(session: AlarmSession, reportDismissal: Boolean = true) {
        if (active.remove(session) == null) return
        val debugSession = debugSessions.remove(session)
        presentationCoordinator.onAlarmStopped(session)
        getSystemService(NotificationManager::class.java).cancel(notificationId(session))
        if (reportDismissal && !debugSession) scope.launch {
            runCatching {
                val entryPoint = EntryPointAccessors.fromApplication(applicationContext, AlarmEntryPoint::class.java)
                entryPoint.alarmDao.updateState(session.alarmId, DeviceAlarmEntity.STATE_DISMISSED)
                entryPoint.remoteAlarmApplier.scheduleNextOccurrence(session.alarmId, session.revision)
                entryPoint.remoteAlarmSyncCoordinator.reportDismissed(session.alarmId, session.revision)
            }.onFailure { Log.w(TAG, "关闭闹钟后的状态同步失败 alarmId=${session.alarmId}", it) }
        }
        if (active.isEmpty()) {
            stopAlerting()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else refreshNotifications()
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter)
        receiverRegistered = true
    }

    private fun refreshNotifications() {
        val manager = getSystemService(NotificationManager::class.java)
        // 前台服务通知已承载当前主闹钟；锁屏不能再发布每个会话的副本，
        // 否则单个闹钟也会显示两条内容完全相同的通知。
        manager.notify(NOTIFICATION_ID, createSummaryNotification())
    }

    private fun startAlerting(initialAudioConfig: AlarmAudioController.Config? = null) {
        audioController.start(initialAudioConfig ?: AlarmAudioController.Config(voiceEnabled = false))
        val vibrator = getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 450), 0))
        else @Suppress("DEPRECATION") vibrator.vibrate(longArrayOf(0, 700, 450), 0)
    }

    private fun stopAlerting() {
        audioController.stop()
        getSystemService(Vibrator::class.java).cancel()
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }

    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:alarm").apply { acquire(MAX_RING_MILLIS) }
    }

    private fun createSummaryNotification(): Notification {
        createChannel()
        val primary = active.values.lastOrNull()
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (active.size > 1) "${active.size} 个闹钟正在响铃" else primary?.title ?: AlarmPresentation.DEFAULT_TITLE)
            .setContentText(primary?.message ?: AlarmPresentation.DEFAULT_MESSAGE).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX).setOngoing(true).setAutoCancel(false).apply {
                primary?.let { setContentIntent(activityIntent(it)); setFullScreenIntent(activityIntent(it), true); addAction(0, AlarmPresentationUi.DISMISS_LABEL, stopPendingIntent(it)) }
            }.build()
    }

    private fun activityIntent(presentation: AlarmPresentation): PendingIntent = PendingIntent.getActivity(this, notificationId(presentation.session), Intent(this, AlarmActivity::class.java).apply {
        putExtra(EXTRA_ALARM_ID, presentation.alarmId); putExtra(EXTRA_REVISION, presentation.revision)
        putExtra(AlarmActivity.EXTRA_TITLE, presentation.title); putExtra(AlarmActivity.EXTRA_MESSAGE, presentation.message)
        putExtra(AlarmActivity.EXTRA_REQUIRES_CONFIRMATION, presentation.requiresConfirmation)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun stopPendingIntent(presentation: AlarmPresentation): PendingIntent = PendingIntent.getService(this, notificationId(presentation.session) xor STOP_REQUEST_XOR,
        stopIntent(this, presentation.alarmId, presentation.revision), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun reportOverlayState(presentation: AlarmPresentation, reason: String) = scope.launch {
        if (debugSessions.contains(presentation.session)) return@launch
        EntryPointAccessors.fromApplication(applicationContext, AlarmEntryPoint::class.java).remoteAlarmSyncCoordinator
            .reportOverlayUnavailable(presentation.alarmId, presentation.revision, reason)
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "闹钟提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "家长设置的到点闹钟提醒"; setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
    }

    companion object {
        private const val TAG = "AlarmRingService"
        private const val CHANNEL_ID = "lemon_alarm_ringing"
        private const val NOTIFICATION_ID = 3107
        private const val STOP_REQUEST_XOR = 0x4A17
        private const val MAX_RING_MILLIS = 60 * 60 * 1000L
        private const val ACTION_STOP = "com.lemonkids.kidmonitor.alarm.STOP"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_REVISION = "revision"
        const val EXTRA_DEBUG_SESSION = "debug_session"
        const val EXTRA_DEBUG_TITLE = "debug_title"
        const val EXTRA_DEBUG_MESSAGE = "debug_message"
        const val EXTRA_DEBUG_REQUIRES_CONFIRMATION = "debug_requires_confirmation"
        const val EXTRA_DEBUG_BACKGROUND_MUSIC_ID = "debug_background_music_id"
        const val EXTRA_DEBUG_VOICE_ENABLED = "debug_voice_enabled"
        const val EXTRA_DEBUG_VOICE_TEXT = "debug_voice_text"
        fun startIntent(context: Context, alarmId: String, revision: Long) = Intent(context, AlarmRingService::class.java).apply { putExtra(EXTRA_ALARM_ID, alarmId); putExtra(EXTRA_REVISION, revision) }
        fun stopIntent(context: Context, alarmId: String, revision: Long) = Intent(context, AlarmRingService::class.java).apply { action = ACTION_STOP; putExtra(EXTRA_ALARM_ID, alarmId); putExtra(EXTRA_REVISION, revision) }
        fun debugStartIntent(
            context: Context,
            alarmId: String,
            revision: Long,
            title: String,
            message: String,
            requiresConfirmation: Boolean,
            backgroundMusicId: String,
            voiceEnabled: Boolean,
            voiceText: String?
        ) =
            Intent(context, AlarmRingService::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId); putExtra(EXTRA_REVISION, revision)
                putExtra(EXTRA_DEBUG_SESSION, true); putExtra(EXTRA_DEBUG_TITLE, title); putExtra(EXTRA_DEBUG_MESSAGE, message)
                putExtra(EXTRA_DEBUG_REQUIRES_CONFIRMATION, requiresConfirmation)
                putExtra(EXTRA_DEBUG_BACKGROUND_MUSIC_ID, backgroundMusicId)
                putExtra(EXTRA_DEBUG_VOICE_ENABLED, voiceEnabled)
                voiceText?.let { putExtra(EXTRA_DEBUG_VOICE_TEXT, it) }
            }
        private fun notificationId(session: AlarmSession): Int = 40_000 + ((session.alarmId.hashCode() * 31 + session.revision.hashCode()) and 0x3fffffff) % 1_000_000
    }
}
