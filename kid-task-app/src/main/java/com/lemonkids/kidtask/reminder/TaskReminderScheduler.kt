package com.lemonkids.kidtask.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import com.lemonkids.shared.di.SupabaseEnvironment
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.model.TaskStatus
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

internal fun isCurrentLocalReminder(expected: String?, received: String?): Boolean =
    expected != null && expected == received

/** 根据任务截止时间注册本地提醒；无时间的任务不打扰孩子。 */
object TaskReminderScheduler {
    private const val LOCAL_REMINDERS = "local_task_reminders"
    private const val IDS = "request_codes"
    private const val TOKEN = "environment_token"

    internal fun localEnvironmentToken(context: Context): String? = context
        .getSharedPreferences(LOCAL_REMINDERS, Context.MODE_PRIVATE).getString(TOKEN, null)

    fun cancelLocalEnvironment(context: Context) {
        check(SupabaseEnvironment.isLocal)
        val manager = context.getSystemService(AlarmManager::class.java)
        val preferences = context.getSharedPreferences(LOCAL_REMINDERS, Context.MODE_PRIVATE)
        preferences.getStringSet(IDS, emptySet()).orEmpty().forEach { value ->
            val id = value.toIntOrNull() ?: return@forEach
            val pending = PendingIntent.getBroadcast(context, id,
                Intent(context, TaskReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pending != null) {
                manager.cancel(pending)
                pending.cancel()
            }
        }
        context.getSystemService(NotificationManager::class.java).cancelAll()
        check(preferences.edit().clear().commit()) { "无法清理任务提醒" }
    }

    fun schedule(context: Context, tasks: List<Task>) {
        val manager = context.getSystemService(AlarmManager::class.java)
        tasks.filter { it.status == TaskStatus.PENDING && !it.dueTime.isNullOrBlank() }.forEach { task ->
            val dueAt = runCatching {
                LocalDateTime.parse("${task.dueDate}T${task.dueTime}")
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull() ?: return@forEach
            if (dueAt <= System.currentTimeMillis()) return@forEach
            val requestCode = task.id.hashCode()
            val environmentToken = if (SupabaseEnvironment.isLocal) {
                val preferences = context.getSharedPreferences(LOCAL_REMINDERS, Context.MODE_PRIVATE)
                val ids = preferences.getStringSet(IDS, emptySet()).orEmpty().toMutableSet()
                ids.add(requestCode.toString())
                val token = preferences.getString(TOKEN, null) ?: UUID.randomUUID().toString()
                // Persist before scheduling so an address change can cancel every local alarm.
                if (!preferences.edit().putStringSet(IDS, ids).putString(TOKEN, token).commit()) return@forEach
                token
            } else null
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, TaskReminderReceiver::class.java)
                    .putExtra(TaskReminderReceiver.EXTRA_TITLE, task.title)
                    .putExtra(TaskReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode)
                    .apply {
                        if (SupabaseEnvironment.isLocal) {
                            putExtra(TaskReminderReceiver.EXTRA_ENVIRONMENT, environmentToken)
                        }
                    },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pendingIntent)
        }
    }
}
