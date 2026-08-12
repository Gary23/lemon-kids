package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.Task
import com.lemonkids.shared.repository.TaskRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseTaskRepository @Inject constructor(
    private val supabase: SupabaseClient
) : TaskRepository {

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)
    private val auth get() = supabase.pluginManager.getPlugin(Auth)

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
            while (true) { delay(60_000); fetch() }
        }

    override fun observeTasksForDate(childId: String, date: String): Flow<List<Task>> =
        observeTodayTasks(childId, date)

    override fun observeChildTasks(childId: String): Flow<List<Task>> =
        callbackFlow {
            suspend fun fetch() {
                try {
                    if (!isSessionValid()) return
                    val tasks = postgrest.from("tasks").select {
                        filter { eq("child_id", childId) }
                    }.decodeList<Task>()
                    trySend(tasks.filter { it.deletedAt == null })
                } catch (_: Exception) {}
            }
            fetch()
            while (true) { delay(60_000); fetch() }
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
        }

    override suspend fun undoCompleteTask(taskId: String, childId: String, rewardPoints: Int): Result<Unit> =
        runCatching {
            // 1) 任务状态恢复为 pending
            postgrest.from("tasks").update(mapOf(
                "status" to "pending",
                "completed_at" to null
            )) { filter { eq("id", taskId) } }
            // 2) 积分扣回
            // 先读取当前积分
            val user = postgrest.from("users").select {
                filter { eq("uid", childId) }
            }.decodeSingle<com.lemonkids.shared.model.User>()
            val newPoints = (user.totalPoints - rewardPoints).coerceAtLeast(0)
            postgrest.from("users").update(mapOf("total_points" to newPoints)) {
                filter { eq("uid", childId) }
            }
            // 3) 删除该任务产生的 task_complete 积分记录
            val records = postgrest.from("point_records").select {
                filter { eq("child_id", childId); eq("type", "task_complete"); eq("related_task_id", taskId) }
                order("timestamp", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(1)
            }.decodeList<com.lemonkids.shared.model.PointRecord>()
            records.firstOrNull()?.let { record ->
                postgrest.from("point_records").delete { filter { eq("id", record.id) } }
            }
        }

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
