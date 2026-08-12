package com.lemonkids.parent.feature.tasks

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.lemonkids.parent.R

/** 家长端在应用运行时收到任务列表刷新后，提示新完成的任务。 */
object TaskCompletionNotifier {
    private const val CHANNEL_ID = "task_completion"

    fun notify(context: Context, childName: String, title: String, notificationId: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "任务完成", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.notify(
            notificationId,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("${childName.ifBlank { "孩子" }}完成了任务 🎉")
                .setContentText(title)
                .setAutoCancel(true)
                .build()
        )
    }
}
