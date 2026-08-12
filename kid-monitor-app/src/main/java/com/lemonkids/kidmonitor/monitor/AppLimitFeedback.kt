package com.lemonkids.kidmonitor.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lemonkids.kidmonitor.R

object AppLimitFeedback {
    private const val TAG = "AppLimitFeedback"
    private const val CHANNEL_ID = "limit_alert_channel"
    private const val NOTIFICATION_ID = 2003
    private const val HOME_TO_BLOCK_DELAY_MS = 350L

    fun showAfterHome(context: Context, decision: LimitDecision.Blocked) {
        if (AppLimitAccessibilityService.showBlockedFeedbackAfterHome(decision)) {
            Log.d(TAG, "使用无障碍提示层展示阻挡反馈: ${decision.reason}")
            return
        }

        if (AppLimitAccessibilityService.goHome()) {
            Log.d(TAG, "无障碍可返回桌面，使用 Activity/通知兜底展示阻挡反馈: ${decision.reason}")
            Handler(Looper.getMainLooper()).postDelayed(
                { show(context, decision) },
                HOME_TO_BLOCK_DELAY_MS
            )
        } else {
            Log.d(TAG, "无障碍不可用，使用 Activity/通知兜底展示阻挡反馈: ${decision.reason}")
            show(context, decision)
        }
    }

    fun show(context: Context, decision: LimitDecision.Blocked) {
        openBlockScreen(context, decision)
        showNotification(context, decision)
    }

    private fun openBlockScreen(context: Context, decision: LimitDecision.Blocked) {
        runCatching {
            context.startActivity(
                Intent(context, LimitBlockActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(LimitBlockActivity.EXTRA_TITLE, decision.title)
                    putExtra(LimitBlockActivity.EXTRA_MESSAGE, decision.message)
                    putExtra(LimitBlockActivity.EXTRA_NOTIFY_ID, NOTIFICATION_ID)
                }
            )
        }
    }

    private fun showNotification(context: Context, decision: LimitDecision.Blocked) {
        createChannel(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, LimitBlockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(LimitBlockActivity.EXTRA_TITLE, decision.title)
                putExtra(LimitBlockActivity.EXTRA_MESSAGE, decision.message)
                putExtra(LimitBlockActivity.EXTRA_NOTIFY_ID, NOTIFICATION_ID)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(decision.title)
            .setContentText(decision.message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "休息提醒",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
