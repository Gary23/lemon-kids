package com.lemonkids.kidtask.feature.pomodoro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.lemonkids.kidtask.MainActivity
import com.lemonkids.kidtask.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 番茄钟前台服务：只负责保持进程和展示进行中通知，让后台计时/响铃尽量自然继续。
 *
 * 计时事实由 [PomodoroManager] 的内存快照驱动；App 被关闭后不会恢复。
 * 通知上的“停止响铃和震动”与应用内按钮走同一个幂等停止动作，不会启动下一轮。
 */
@AndroidEntryPoint
class PomodoroTimerService : Service() {

    @Inject lateinit var manager: PomodoroManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastSignature: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val initial = buildNotification("番茄钟准备中")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            initial,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
        scope.launch {
            manager.state.collect { state ->
                if (state.phase == PomodoroPhase.SETUP) {
                    stopSelf()
                    return@collect
                }
                val text = when (state.phase) {
                    PomodoroPhase.FOCUS -> "${if (state.isPaused) "专注已暂停" else "专注中"} · ${PomodoroRules.formatRemaining(state.remainingMillis)}"
                    PomodoroPhase.READY_FOR_BREAK -> "专注完成，等待手动开始休息"
                    PomodoroPhase.BREAK -> "${if (state.isPaused) "休息已暂停" else "休息中"} · ${PomodoroRules.formatRemaining(state.remainingMillis)}"
                    PomodoroPhase.WAITING -> "休息完成，等待开始下一轮"
                    PomodoroPhase.SETUP -> "番茄钟准备中"
                }
                val alertText = when (state.alert) {
                    PomodoroAlert.FOCUS_END -> "专注完成，准备好后开始休息"
                    PomodoroAlert.BREAK_END -> "休息结束，准备好再出发"
                    PomodoroAlert.NONE -> null
                }
                val title = alertText ?: "番茄钟"
                val signature = "$title|$text|${state.alert}"
                if (signature != lastSignature) {
                    lastSignature = signature
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(text, title, state.alert != PomodoroAlert.NONE))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALERT) {
            manager.stopAlert()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 用户从最近任务划掉 App：运行时随任务结束；不涉及任何持久记录。 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        manager.unbind()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(
        text: String,
        title: String = "番茄钟",
        showStopAction: Boolean = false
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .apply {
            if (showStopAction) {
                addAction(
                    0,
                    "停止响铃和震动",
                    PendingIntent.getService(
                        this@PomodoroTimerService,
                        1,
                        Intent(this@PomodoroTimerService, PomodoroTimerService::class.java)
                            .setAction(ACTION_STOP_ALERT),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }
        .build()

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "番茄钟", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示正在进行的专注或休息"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "pomodoro_timer"
        const val NOTIFICATION_ID = 20261111
        const val ACTION_STOP_ALERT = "com.lemonkids.kidtask.pomodoro.STOP_ALERT"
    }
}
