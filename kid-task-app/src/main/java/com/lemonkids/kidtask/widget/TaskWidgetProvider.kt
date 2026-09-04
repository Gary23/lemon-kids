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
import com.lemonkids.kidtask.MainActivity
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

/** 今日任务小部件：首页当日任务的只读镜像，点按任意区域进入任务端首页。 */
class TaskWidgetProvider : AppWidgetProvider() {

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
            appWidgetIds.forEach { appWidgetId ->
                appWidgetManager.updateAppWidget(
                    appWidgetId,
                    buildRemoteViews(context, appWidgetId, emptyList(), isLoading = true)
                )
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val tasks = TaskWidgetDataLoader.loadTodayTasks(context)
                    appWidgetIds.forEach { appWidgetId ->
                        appWidgetManager.updateAppWidget(
                            appWidgetId,
                            buildRemoteViews(context, appWidgetId, tasks)
                        )
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_task_list)
                    }
                } finally {
                    onFinished?.invoke()
                }
            }
        }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            tasks: List<Task>,
            isLoading: Boolean = false
        ): RemoteViews {
            val adapterIntent = Intent(context, TaskWidgetRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("lemonkids-widget://tasks/$appWidgetId")
            }
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val pendingCount = tasks.count { it.status == TaskStatus.PENDING }
            val allDone = tasks.isNotEmpty() && pendingCount == 0 && tasks.all {
                it.status == TaskStatus.DONE || it.status == TaskStatus.VERIFIED
            }
            return RemoteViews(context.packageName, R.layout.widget_today_tasks).apply {
                setTextViewText(
                    R.id.widget_subtitle,
                    when {
                        isLoading -> "正在同步今天的任务…"
                        tasks.isEmpty() -> "等妈妈给你布置任务吧"
                        allDone -> "太棒了！今天所有任务都完成啦～"
                        else -> "还有 $pendingCount 个待完成"
                    }
                )
                setViewVisibility(R.id.widget_loading, if (isLoading) View.VISIBLE else View.GONE)
                setViewVisibility(
                    R.id.widget_empty,
                    if (!isLoading && tasks.isEmpty()) View.VISIBLE else View.GONE
                )
                setViewVisibility(
                    R.id.widget_task_list,
                    if (!isLoading && tasks.isNotEmpty()) View.VISIBLE else View.GONE
                )
                setRemoteAdapter(R.id.widget_task_list, adapterIntent)
                setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
                setPendingIntentTemplate(R.id.widget_task_list, openAppPendingIntent)
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
                setTextViewText(R.id.widget_category_name, "${CATEGORY_EMOJIS[item.index % CATEGORY_EMOJIS.size]}  ${item.name}")
                setOnClickFillInIntent(R.id.widget_category_name, Intent())
            }
            is WidgetListItem.TaskItem -> RemoteViews(context.packageName, R.layout.widget_task_item).apply {
                val isDone = item.task.status == TaskStatus.DONE || item.task.status == TaskStatus.VERIFIED
                val time = item.task.dueTime?.takeIf { it.isNotBlank() }?.let { "$it  " }.orEmpty()
                setTextViewText(
                    R.id.widget_task_name,
                    when {
                        isDone -> "✓  ${time}${item.task.title}\n✅ 已完成 · 得到 ${item.task.rewardPoints} 颗星星"
                        item.task.status == TaskStatus.EXPIRED || item.task.status == TaskStatus.REJECTED ->
                            "◷  ${time}${item.task.title}\n⏰ 已错过"
                        else -> "○  ${time}${item.task.title}\n⭐ ${item.task.rewardPoints} 积分 · 待完成"
                    }
                )
                setInt(
                    R.id.widget_task_name,
                    "setBackgroundResource",
                    when {
                        isDone -> R.drawable.task_widget_task_done_background
                        item.task.status == TaskStatus.EXPIRED || item.task.status == TaskStatus.REJECTED ->
                            R.drawable.task_widget_task_expired_background
                        else -> R.drawable.task_widget_task_pending_background
                    }
                )
                setTextColor(
                    R.id.widget_task_name,
                    if (item.task.status == TaskStatus.EXPIRED || item.task.status == TaskStatus.REJECTED) {
                        0xFFA3A3A3.toInt()
                    } else {
                        0xFF6B4B4B.toInt()
                    }
                )
                setOnClickFillInIntent(R.id.widget_task_name, Intent())
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 2
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}

private sealed interface WidgetListItem {
    data class Category(val name: String, val index: Int) : WidgetListItem
    data class TaskItem(val task: Task) : WidgetListItem
}

private val CATEGORY_EMOJIS = listOf("🌸", "💜", "🍊", "🌿", "⭐")

private object TaskWidgetDataLoader {
    suspend fun loadTodayTasks(context: Context): List<Task> {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TaskWidgetEntryPoint::class.java
        )
        val authRepository = entryPoint.authRepository()
        // 小部件可能由桌面在应用进程刚创建时直接启动；此时 AuthRepository 的异步会话恢复
        // 尚未完成，不能把暂时为空的 currentUserId 当成“没有任务”。
        val userId = authRepository.currentUserId
            ?: authRepository.restoreSession().getOrNull()?.uid
            ?: authRepository.currentUserId
            ?: return emptyList()
        return withTimeoutOrNull(6_000) {
            // 与首页一致：先读取孩子的全量任务流，再在本地筛选当天任务和排序。
            entryPoint.taskRepository().observeChildTasks(userId).first()
                .filter { it.dueDate == LocalDate.now().toString() }
        }.orEmpty().let { tasks ->
            orderByCategory(tasks, loadCategoryNames(entryPoint, authRepository))
        }
    }

    suspend fun loadListItems(context: Context): List<WidgetListItem> {
        val tasks = loadTodayTasks(context)
        if (tasks.isEmpty()) return emptyList()
        return buildList {
            var previousCategory: String? = null
            var categoryIndex = 0
            tasks.forEach { task ->
                val category = task.category.ifBlank { "默认" }
                if (category != previousCategory) {
                    add(WidgetListItem.Category(category, categoryIndex++))
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
                .thenBy {
                    when (it.status) {
                        TaskStatus.PENDING -> 0
                        TaskStatus.DONE, TaskStatus.VERIFIED -> 1
                        else -> 2
                    }
                }
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
