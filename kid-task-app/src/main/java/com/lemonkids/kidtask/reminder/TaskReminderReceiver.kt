package com.lemonkids.kidtask.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lemonkids.kidtask.R

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (title.isBlank()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "任务提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "在任务约定时间提醒孩子"
            }
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("到时间做任务啦 ✨")
            .setContentText(title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(intent.getIntExtra(EXTRA_NOTIFICATION_ID, title.hashCode()), notification)
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val EXTRA_TITLE = "title"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
