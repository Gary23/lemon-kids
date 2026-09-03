package com.lemonkids.kidtask.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lemonkids.kidtask.R
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.model.TaskStatus
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.CategoryRepository
import com.lemonkids.shared.repository.TaskRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** 今日任务小部件：按家长分类分组，点按任务可直接完成或撤销，不会打开任务端 App。 */
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE_TASK) {
            super.onReceive(context, intent)
            return
        }

        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val status = intent.getStringExtra(EXTRA_TASK_STATUS) ?: return
        val rewardPoints = intent.getIntExtra(EXTRA_TASK_POINTS, 0)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    TaskWidgetEntryPoint::class.java
                )
                val childId = entryPoint.authRepository().currentUserId
                    ?: entryPoint.authRepository().observeCurrentUser().first()?.uid
                if (childId != null) {
                    val repository = entryPoint.taskRepository()
                    if (status == TaskStatus.PENDING.name) {
                        repository.completeTask(taskId, childId)
                    } else if (status == TaskStatus.DONE.name || status == TaskStatus.VERIFIED.name) {
                        repository.undoCompleteTask(taskId, childId, rewardPoints)
                    }
                }
            } finally {
                updateAll(context)
                pendingResult.finish()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        refreshAsync(context, appWidgetManager, appWidgetIds) { pendingResult.finish() }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        refreshAsync(context, appWidgetManager, intArrayOf(appWidgetId)) { pendingResult.finish() }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAll(context)
    }

    companion object {
        const val ACTION_TOGGLE_TASK = "com.lemonkids.kidtask.widget.TOGGLE_TASK"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_STATUS = "task_status"
        const val EXTRA_TASK_POINTS = "task_points"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TaskWidgetProvider::class.java)
            refreshAsync(context, manager, manager.getAppWidgetIds(component))
        }

        private fun refreshAsync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            onFinished: (() -> Unit)? = null
        ) {
            if (appWidgetIds.isEmpty()) {
                onFinished?.invoke()
                return
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val tasks = TaskWidgetDataLoader.loadTodayTasks(context)
                    appWidgetIds.forEach { appWidgetId ->
                        appWidgetManager.updateAppWidget(
                            appWidgetId,
                            buildRemoteViews(context, appWidgetId, tasks.size)
                        )
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_task_list)
                    }
                } finally {
                    onFinished?.invoke()
                }
            }
        }

        private fun buildRemoteViews(context: Context, appWidgetId: Int, taskCount: Int): RemoteViews {
            val adapterIntent = Intent(context, TaskWidgetRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("lemonkids-widget://tasks/$appWidgetId")
            }
            val clickTemplate = Intent(context, TaskWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_TASK
            }
            val clickPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                clickTemplate,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            return RemoteViews(context.packageName, R.layout.widget_today_tasks).apply {
                setTextViewText(R.id.widget_title, "今日任务 · $taskCount 项")
                setTextViewText(
                    R.id.widget_subtitle,
                    if (taskCount == 0) "今天没有任务，尽情玩耍吧～" else "按分类展示 · 点按完成或撤销"
                )
                setViewVisibility(R.id.widget_empty, if (taskCount == 0) View.VISIBLE else View.GONE)
                setRemoteAdapter(R.id.widget_task_list, adapterIntent)
                setPendingIntentTemplate(R.id.widget_task_list, clickPendingIntent)
            }
        }
    }
}

/** 为小部件列表提供远程视图；系统会在后台线程调用 onDataSetChanged。 */
class TaskWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TaskWidgetRemoteViewsFactory(applicationContext)
}

private class TaskWidgetRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {
    private var items: List<WidgetListItem> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        items = runBlocking { TaskWidgetDataLoader.loadListItems(context) }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val item = items.getOrNull(position) ?: return null
        return when (item) {
            is WidgetListItem.Category -> RemoteViews(context.packageName, R.layout.widget_task_category).apply {
                setTextViewText(R.id.widget_category_name, item.name)
            }
            is WidgetListItem.TaskItem -> RemoteViews(context.packageName, R.layout.widget_task_item).apply {
                val isDone = item.task.status == TaskStatus.DONE || item.task.status == TaskStatus.VERIFIED
                val time = item.task.dueTime?.takeIf { it.isNotBlank() }?.let { "$it  " }.orEmpty()
                setTextViewText(
                    R.id.widget_task_name,
                    if (isDone) "✓  ${time}${item.task.title}\n已完成 · 点按撤销" else "○  ${time}${item.task.title}\n待完成 · 点按完成"
                )
                setInt(
                    R.id.widget_task_name,
                    "setBackgroundResource",
                    if (isDone) R.drawable.task_widget_task_done_background else R.drawable.task_widget_task_pending_background
                )
                setTextColor(
                    R.id.widget_task_name, if (isDone) 0xFF237A58.toInt() else 0xFF2B210B.toInt()
                )
                setOnClickFillInIntent(
                    R.id.widget_task_name,
                    Intent().apply {
                        putExtra(TaskWidgetProvider.EXTRA_TASK_ID, item.task.id)
                        putExtra(TaskWidgetProvider.EXTRA_TASK_STATUS, item.task.status.name)
                        putExtra(TaskWidgetProvider.EXTRA_TASK_POINTS, item.task.rewardPoints)
                    }
                )
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 2
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}

private sealed interface WidgetListItem {
    data class Category(val name: String) : WidgetListItem
    data class TaskItem(val task: Task) : WidgetListItem
}

private object TaskWidgetDataLoader {
    suspend fun loadTodayTasks(context: Context): List<Task> {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TaskWidgetEntryPoint::class.java
        )
        val authRepository = entryPoint.authRepository()
        val userId = authRepository.currentUserId ?: return emptyList()
        return withTimeoutOrNull(6_000) {
            entryPoint.taskRepository().observeTasksForDate(userId, LocalDate.now().toString()).first()
        }.orEmpty().let { tasks ->
            orderByCategory(tasks, loadCategoryNames(entryPoint, authRepository))
        }
    }

    suspend fun loadListItems(context: Context): List<WidgetListItem> {
        val tasks = loadTodayTasks(context)
        if (tasks.isEmpty()) return emptyList()
        return buildList {
            var previousCategory: String? = null
            tasks.forEach { task ->
                val category = task.category.ifBlank { "默认" }
                if (category != previousCategory) {
                    add(WidgetListItem.Category(category))
                    previousCategory = category
                }
                add(WidgetListItem.TaskItem(task))
            }
        }
    }

    private suspend fun loadCategoryNames(
        entryPoint: TaskWidgetEntryPoint,
        authRepository: AuthRepository
    ): List<String> {
        val user = authRepository.observeCurrentUser().first() ?: return emptyList()
        val familyId = user.familyId?.takeIf { it.isNotBlank() } ?: return emptyList()
        return withTimeoutOrNull(6_000) {
            entryPoint.categoryRepository().observeCategories(familyId).first().map { it.name }
        }.orEmpty()
    }

    private fun orderByCategory(tasks: List<Task>, categoryNames: List<String>): List<Task> {
        val categoryOrder = categoryNames.withIndex().associate { (index, name) -> name to index }
        return tasks.sortedWith(
            compareBy<Task> { categoryOrder[it.category] ?: Int.MAX_VALUE }
                .thenBy { it.category.ifBlank { "默认" } }
                .thenBy { if (it.status == TaskStatus.PENDING) 0 else 1 }
                .thenBy { it.dueTime ?: "" }
                .thenBy { it.title }
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TaskWidgetEntryPoint {
    fun authRepository(): AuthRepository
    fun taskRepository(): TaskRepository
    fun categoryRepository(): CategoryRepository
}
