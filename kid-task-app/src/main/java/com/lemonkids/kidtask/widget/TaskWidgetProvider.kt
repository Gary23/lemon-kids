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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** 今日任务小部件：首页当日任务的只读镜像，点按任意区域进入任务端首页。 */
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 华为等桌面会在锁屏后解除 RemoteViewsService 的绑定，解锁时不会自动请求小部件更新。
        // 此处主动重建适配器并通知数据集，避免桌面留下空白列表。
        when (intent.action) {
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val pendingResult = goAsync()
                updateAll(context) { pendingResult.finish() }
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
        fun updateAll(context: Context, onFinished: (() -> Unit)? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TaskWidgetProvider::class.java)
            refreshAsync(context, manager, manager.getAppWidgetIds(component), onFinished)
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
    suspend fun loadTodayTasks(context: Context, preferCachedSnapshot: Boolean = false): List<Task> {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TaskWidgetEntryPoint::class.java
        )
        val authRepository = entryPoint.authRepository()
        // 小部件可能由桌面在应用进程刚创建时直接启动；此时 AuthRepository 的异步会话恢复
        // 尚未完成，不能把暂时为空的 currentUserId 当成“没有任务”。
        val userId = authRepository.currentUserId
            ?: withTimeoutOrNull(6_000) { authRepository.restoreSession().getOrNull()?.uid }
            ?: authRepository.currentUserId
            ?: return TaskWidgetCache.loadToday(context, null)
        if (preferCachedSnapshot) {
            TaskWidgetCache.loadToday(context, userId).takeIf { it.isNotEmpty() }?.let { return it }
        }
        val tasks = withTimeoutOrNull(6_000) {
            // 与首页一致：先读取孩子的全量任务流，再在本地筛选当天任务和排序。
            entryPoint.taskRepository().observeChildTasks(userId).first()
                .filter { it.dueDate == LocalDate.now().toString() }
        } ?: return TaskWidgetCache.loadToday(context, userId)

        return orderByCategory(tasks, loadCategoryNames(entryPoint, authRepository)).also {
            // 小部件进程在后台被系统回收后，先用最近一次成功同步的当天快照恢复列表；
            // 网络请求随后会覆盖该快照，因此不会牺牲数据的新鲜度。
            TaskWidgetCache.saveToday(context, userId, it)
        }
    }

    suspend fun loadListItems(context: Context): List<WidgetListItem> {
        // RemoteViewsService 经常在桌面恢复时先于网络初始化。优先读取快照，
        // 随后的 Provider 刷新会触发 notifyAppWidgetViewDataChanged 并换成最新数据。
        val tasks = loadTodayTasks(context, preferCachedSnapshot = true)
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

/** 小部件专用的短期快照；按日期和当前孩子账号隔离，避免显示过期或错账号的数据。 */
private object TaskWidgetCache {
    private const val PREFERENCES_NAME = "task_widget_cache"
    private const val KEY_DATE = "date"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_TASKS = "tasks"
    private val json = Json { ignoreUnknownKeys = true }

    fun loadToday(context: Context, userId: String?): List<Task> {
        if (userId.isNullOrBlank()) return emptyList()
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getString(KEY_DATE, null) != LocalDate.now().toString() ||
            preferences.getString(KEY_USER_ID, null) != userId
        ) return emptyList()

        return runCatching {
            json.decodeFromString<List<Task>>(preferences.getString(KEY_TASKS, null).orEmpty())
                .filter { it.dueDate == LocalDate.now().toString() && it.deletedAt == null }
        }.getOrElse { emptyList() }
    }

    fun saveToday(context: Context, userId: String, tasks: List<Task>) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DATE, LocalDate.now().toString())
            .putString(KEY_USER_ID, userId)
            .putString(KEY_TASKS, json.encodeToString(tasks))
            .apply()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TaskWidgetEntryPoint {
    fun authRepository(): AuthRepository
    fun taskRepository(): TaskRepository
    fun categoryRepository(): CategoryRepository
}
