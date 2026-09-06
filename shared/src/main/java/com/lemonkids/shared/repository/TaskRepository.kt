package com.lemonkids.shared.repository

import com.lemonkids.shared.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTodayTasks(childId: String, date: String): Flow<List<Task>>
    fun observeTasksForDate(childId: String, date: String): Flow<List<Task>>
    fun observeChildTasks(childId: String): Flow<List<Task>>
    fun observeDeletedTasks(familyId: String): Flow<List<Task>>
    /** 应用回到前台后刷新鉴权会话并立即重新拉取任务。 */
    suspend fun refreshTasks()
    suspend fun getMonthTasks(childId: String, year: Int, month: Int): List<Task>
    suspend fun createTask(task: Task): Result<String>
    /**
     * 从一个分类任务包或一个任务模板创建日程。两种来源必须二选一；服务端会原子生成
     * 全部日期任务，并按“孩子 + 日期 + 模板”去重。
     */
    suspend fun createTasksFromSelection(
        childId: String,
        categoryId: String?,
        templateId: String?,
        dueDate: String,
        endDate: String,
        recurrenceType: com.lemonkids.shared.model.TaskRecurrenceType,
        recurrenceWeekdays: List<Int>
    ): Result<List<Task>>
    suspend fun updateTask(task: Task): Result<Unit>
    /** 更新同一重复系列中尚未完成的未来任务。 */
    suspend fun updateFutureTasksInSeries(seriesId: String, fromDate: String, task: Task): Result<Unit>
    /** 取消今日及未来的待完成任务：标记 deleted_at，历史任务不会被修改。 */
    suspend fun deleteTask(taskId: String): Result<Unit>
    /** 仅彻底删除未完成、未产生积分且尚未发生的已取消任务。 */
    suspend fun permanentlyDeleteTask(taskId: String): Result<Unit>
    /** 从回收站还原 */
    suspend fun restoreTask(taskId: String): Result<Unit>
    /** 清空回收站 */
    suspend fun emptyRecycleBin(familyId: String): Result<Unit>
    suspend fun completeTask(taskId: String, childId: String): Result<Unit>
    suspend fun undoCompleteTask(taskId: String, childId: String, rewardPoints: Int): Result<Unit>
    suspend fun verifyTask(taskId: String): Result<Unit>
    suspend fun getTaskById(taskId: String): Result<Task>
}
