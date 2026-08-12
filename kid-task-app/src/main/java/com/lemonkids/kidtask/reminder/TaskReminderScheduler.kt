package com.lemonkids.kidtask.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.model.TaskStatus
import java.time.LocalDateTime
import java.time.ZoneId

/** 根据任务截止时间注册本地提醒；无时间的任务不打扰孩子。 */
object TaskReminderScheduler {
    fun schedule(context: Context, tasks: List<Task>) {
        val manager = context.getSystemService(AlarmManager::class.java)
        tasks.filter { it.status == TaskStatus.PENDING && !it.dueTime.isNullOrBlank() }.forEach { task ->
            val dueAt = runCatching {
                LocalDateTime.parse("${task.dueDate}T${task.dueTime}")
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull() ?: return@forEach
            if (dueAt <= System.currentTimeMillis()) return@forEach
            val requestCode = task.id.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, TaskReminderReceiver::class.java)
                    .putExtra(TaskReminderReceiver.EXTRA_TITLE, task.title)
                    .putExtra(TaskReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pendingIntent)
        }
    }
}
