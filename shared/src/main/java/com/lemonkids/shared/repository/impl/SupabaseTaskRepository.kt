package com.lemonkids.shared.repository.impl

import android.util.Log
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.repository.TaskRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseTaskRepository @Inject constructor(
    private val supabase: SupabaseClient
) : TaskRepository {

    companion object {
        private const val TAG = "SupabaseTaskRepo"
    }

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)
    private val auth get() = supabase.pluginManager.getPlugin(Auth)
    /** 本进程内任务写入后的即时刷新信号，避免界面只能等待轮询。 */
    private val taskRefreshEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * 检查 auth session 是否有效，RLS 在 session 无效时会返回空列表而非报错，
     * 必须在此拦截以避免空列表覆盖已有数据
     */
    private fun isSessionValid(): Boolean = auth.currentSessionOrNull() != null

    override fun observeTodayTasks(childId: String, date: String): Flow<List<Task>> =
        callbackFlow {
            suspend fun fetch() {
                try {
                    if (!isSessionValid()) return
                    val tasks = postgrest.from("tasks").select {
                        filter { eq("child_id", childId); eq("due_date", date) }
                    }.decodeList<Task>()
                    trySend(tasks.filter { it.deletedAt == null })
                } catch (_: Exception) {}
            }
            fetch()
            launch { taskRefreshEvents.collect { fetch() } }
            launch { while (true) { delay(60_000); fetch() } }
            awaitClose { }
        }

    override fun observeTasksForDate(childId: String, date: String): Flow<List<Task>> =
        observeTodayTasks(childId, date)

    override fun observeChildTasks(childId: String): Flow<List<Task>> = callbackFlow {
        suspend fun fetch() {
            try {
                if (!isSessionValid()) return
                val tasks = postgrest.from("tasks").select {
                    filter { eq("child_id", childId) }
                }.decodeList<Task>()
                trySend(tasks.filter { it.deletedAt == null })
            } catch (_: Exception) {
                // 保留上一次已展示的数据，下一次轮询或写入信号会重试。
            }
        }
        fetch()
        launch { taskRefreshEvents.collect { fetch() } }
        launch { while (true) { delay(60_000); fetch() } }
        awaitClose { }
    }

    override fun observeDeletedTasks(familyId: String): Flow<List<Task>> =
        callbackFlow {
            suspend fun fetch() {
                try {
                    if (!isSessionValid()) return
                    val tasks = postgrest.from("tasks").select {
                        filter { eq("family_id", familyId) }
                    }.decodeList<Task>()
                    trySend(tasks.filter { it.deletedAt != null })
                } catch (_: Exception) {}
            }
            fetch()
            while (true) { delay(60_000); fetch() }
        }

    override suspend fun getMonthTasks(childId: String, year: Int, month: Int): List<Task> {
        val startDate = "%04d-%02d-01".format(year, month)
        val endDate = "%04d-%02d-31".format(year, month)
        val tasks = postgrest.from("tasks").select {
            filter { eq("child_id", childId); gte("due_date", startDate); lte("due_date", endDate) }
        }.decodeList<Task>()
        return tasks.filter { it.deletedAt == null }
    }

    override suspend fun createTask(task: Task): Result<String> = runCatching {
        postgrest.from("tasks").insert(task) { select() }.decodeSingle<Task>().id
    }.onSuccess { taskId ->
        Log.i(TAG, "任务创建成功 taskId=$taskId childId=${task.childId} dueDate=${task.dueDate}")
    }.onFailure { error ->
        // 不记录标题和描述，避免将家庭内容写入设备日志；保留服务端异常用于排查鉴权、RLS 与字段校验问题。
        Log.e(
            TAG,
            "任务创建失败 familyId=${task.familyId} childId=${task.childId} dueDate=${task.dueDate} " +
                "recurrence=${task.recurrenceType} seriesId=${task.recurrenceSeriesId}",
            error
        )
    }

    override suspend fun updateTask(task: Task): Result<Unit> = runCatching {
        postgrest.from("tasks").update(task) { filter { eq("id", task.id) } }
    }

    override suspend fun updateFutureTasksInSeries(seriesId: String, fromDate: String, task: Task): Result<Unit> = runCatching {
        postgrest.from("tasks").update(
            mapOf(
                "title" to task.title,
                "description" to task.description,
                "category" to task.category,
                "reward_points" to task.rewardPoints,
                "penalty_points" to task.penaltyPoints,
                "due_time" to task.dueTime,
                "recurrence_type" to task.recurrenceType.name.lowercase(),
                "recurrence_weekdays" to task.recurrenceWeekdays,
                "recurrence_end_date" to task.recurrenceEndDate
            )
        ) {
            filter {
                eq("recurrence_series_id", seriesId)
                gte("due_date", fromDate)
                eq("status", "pending")
            }
        }
    }

    override suspend fun deleteTask(taskId: String): Result<Unit> = runCatching {
        postgrest.from("tasks").update(mapOf("deleted_at" to Instant.now().toString())) {
            filter { eq("id", taskId) }
        }
    }

    override suspend fun permanentlyDeleteTask(taskId: String): Result<Unit> = runCatching {
        postgrest.from("tasks").delete { filter { eq("id", taskId) } }
    }

    override suspend fun restoreTask(taskId: String): Result<Unit> = runCatching {
        postgrest.from("tasks").update(mapOf("deleted_at" to null)) {
            filter { eq("id", taskId) }
        }
    }

    override suspend fun emptyRecycleBin(familyId: String): Result<Unit> = runCatching {
        // 先查询所有已删除任务，再逐个彻底删除
        val deleted = postgrest.from("tasks").select {
            filter { eq("family_id", familyId) }
        }.decodeList<Task>().filter { it.deletedAt != null }
        for (task in deleted) {
            postgrest.from("tasks").delete { filter { eq("id", task.id) } }
        }
    }

    override suspend fun completeTask(taskId: String, childId: String): Result<Unit> =
        runCatching {
            postgrest.rpc(
                function = "complete_task",
                parameters = mapOf("p_task_id" to taskId, "p_child_id" to childId)
            )
            Unit
        }.onSuccess { taskRefreshEvents.tryEmit(Unit) }

    override suspend fun undoCompleteTask(taskId: String, childId: String, rewardPoints: Int): Result<Unit> =
        runCatching {
            // 1) 任务状态恢复为 pending
            // 先读取当前积分；其余三个互不依赖的操作并行执行，减少网络往返等待。
            val user = postgrest.from("users").select {
                filter { eq("uid", childId) }
            }.decodeSingle<com.lemonkids.shared.model.User>()
            val newPoints = (user.totalPoints - rewardPoints).coerceAtLeast(0)
            kotlinx.coroutines.coroutineScope {
                launch {
                    postgrest.from("tasks").update(mapOf(
                        "status" to "pending",
                        "completed_at" to null,
                        "verified_at" to null
                    )) { filter { eq("id", taskId) } }
                }
                launch {
                    postgrest.from("users").update(mapOf("total_points" to newPoints)) {
                        filter { eq("uid", childId) }
                    }
                }
                launch {
                    // 一个任务只保留一条完成积分记录，直接按关联任务删除即可，免去一次查询。
                    postgrest.from("point_records").delete {
                        filter { eq("child_id", childId); eq("type", "task_complete"); eq("related_task_id", taskId) }
                    }
                }
            }
            Unit
        }.onSuccess { taskRefreshEvents.tryEmit(Unit) }

    override suspend fun verifyTask(taskId: String): Result<Unit> = runCatching {
        postgrest.from("tasks").update(mapOf("status" to "verified")) {
            filter { eq("id", taskId) }
        }
    }

    override suspend fun rejectTask(taskId: String): Result<Unit> = runCatching {
        postgrest.rpc(function = "reject_task", parameters = mapOf("p_task_id" to taskId))
    }

    override suspend fun getTaskById(taskId: String): Result<Task> = runCatching {
        postgrest.from("tasks").select {
            filter { eq("id", taskId) }
        }.decodeSingle<Task>()
    }
}
