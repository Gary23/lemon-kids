package com.lemonkids.shared.repository

import com.lemonkids.shared.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTodayTasks(childId: String, date: String): Flow<List<Task>>
    fun observeTasksForDate(childId: String, date: String): Flow<List<Task>>
    fun observeChildTasks(childId: String): Flow<List<Task>>
    fun observeDeletedTasks(familyId: String): Flow<List<Task>>
    suspend fun getMonthTasks(childId: String, year: Int, month: Int): List<Task>
    suspend fun createTask(task: Task): Result<String>
    suspend fun updateTask(task: Task): Result<Unit>
    /** 更新同一重复系列中尚未完成的未来任务。 */
    suspend fun updateFutureTasksInSeries(seriesId: String, fromDate: String, task: Task): Result<Unit>
    /** 软删除：标记 deleted_at */
    suspend fun deleteTask(taskId: String): Result<Unit>
    /** 彻底删除 */
    suspend fun permanentlyDeleteTask(taskId: String): Result<Unit>
    /** 从回收站还原 */
    suspend fun restoreTask(taskId: String): Result<Unit>
    /** 清空回收站 */
    suspend fun emptyRecycleBin(familyId: String): Result<Unit>
    suspend fun completeTask(taskId: String, childId: String): Result<Unit>
    suspend fun undoCompleteTask(taskId: String, childId: String, rewardPoints: Int): Result<Unit>
    suspend fun verifyTask(taskId: String): Result<Unit>
    suspend fun rejectTask(taskId: String): Result<Unit>
    suspend fun getTaskById(taskId: String): Result<Task>
}
