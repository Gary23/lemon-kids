package com.lemonkids.kidtask.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.model.TaskStatus
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.CategoryRepository
import com.lemonkids.shared.repository.RewardRepository
import com.lemonkids.shared.repository.TaskRepository
import com.lemonkids.kidtask.ui.components.TaskUiItem
import com.lemonkids.kidtask.reminder.TaskReminderScheduler
import com.lemonkids.kidtask.widget.TaskWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    /** 今日任务 */
    val todayTasks: List<TaskUiItem> = emptyList(),
    /** 家长端配置的任务分类，保持配置创建顺序。 */
    val categoryNames: List<String> = emptyList(),
    /** 昨天及以前的未完成任务（PENDING），日期降序 */
    val overdueTasks: List<TaskUiItem> = emptyList(),
    /** 明天及以后的任务，日期升序 */
    val upcomingTasks: List<TaskUiItem> = emptyList(),
    val points: Int = 0,
    val previousPoints: Int = 0,
    val earnedPoints: Int = 0,
    val showPointsAnimation: Boolean = false,
    val streakDays: Int = 0,
    val allTasksDoneToday: Boolean = false,
    val showCelebration: Boolean = false,
    val isLoading: Boolean = false,
    /** 已本地反馈、正在同步到服务端的任务；不再用全屏加载遮挡列表。 */
    val syncingTaskIds: Set<String> = emptySet(),
    val confirmDialogTaskId: String? = null,
    val undoDialogTaskId: String? = null,
    /** 折叠面板展开状态 */
    val todayExpanded: Boolean = true,
    val overdueExpanded: Boolean = true,
    val upcomingExpanded: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
    private val rewardRepository: RewardRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var celebrateShownToday = false
    private var lastCelebrateDate: String = ""
    /** 首次加载是否完成（至少收到一次非空数据或超时确认） */
    private var firstLoadDone = false
    /** 等待仓库返回新快照期间保留本地状态，避免旧的轮询结果把卡片改回去。 */
    private val optimisticTaskStatuses = mutableMapOf<String, TaskStatus>()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            val today = LocalDate.now()

            user.familyId?.takeIf { it.isNotBlank() }?.let { familyId ->
                launch {
                    categoryRepository.observeCategories(familyId).collect { categories ->
                        _uiState.value = _uiState.value.copy(
                            categoryNames = categories.map { it.name }.filter { it.isNotBlank() }
                        )
                    }
                }
            }

            launch {
                // 观察所有任务（非删除），在内存中分类过滤
                taskRepository.observeChildTasks(userId).collect { tasks ->
                    TaskReminderScheduler.schedule(appContext, tasks)
                    val todayStr = today.toString()
                    reconcileOptimisticStatuses(tasks)
                    val displayedTasks = tasks.map { task ->
                        optimisticTaskStatuses[task.id]?.let { task.copy(status = it) } ?: task
                    }

                    val todayTasks = displayedTasks
                        .filter { it.dueDate == todayStr }
                        .map { it.toUiItem() }
                        .sortedWith(taskSort)

                    // 桌面任务卡片与首页使用同一份实时任务源，避免继续展示演示数据。
                    TaskWidgetProvider.updateAll(appContext)

                    val overdueTasks = displayedTasks
                        .filter { t ->
                            val d = try { LocalDate.parse(t.dueDate) } catch (_: Exception) { null }
                            d != null && d < today && t.status == TaskStatus.PENDING
                        }
                        .map { it.toUiItem() }
                        .sortedByDescending { it.dueDate }

                    val upcomingTasks = displayedTasks
                        .filter { t ->
                            val d = try { LocalDate.parse(t.dueDate) } catch (_: Exception) { null }
                            d != null && d > today
                        }
                        .map { it.toUiItem() }
                        .sortedBy { it.dueDate }

                    val allEmpty = todayTasks.isEmpty() && overdueTasks.isEmpty() && upcomingTasks.isEmpty()
                    // 首次加载收到空列表时保持 loading，避免 auth session 未恢复时 RLS 返回空导致误显示"没有任务"
                    if (allEmpty && !firstLoadDone) return@collect

                    firstLoadDone = true

                    val allDone = todayTasks.isNotEmpty() && todayTasks.all {
                        it.status == "DONE" || it.status == "VERIFIED"
                    }
                    if (lastCelebrateDate != todayStr) {
                        celebrateShownToday = false
                        lastCelebrateDate = todayStr
                    }
                    val shouldCelebrate = allDone && !celebrateShownToday && _uiState.value.todayTasks.isNotEmpty()
                    if (shouldCelebrate) celebrateShownToday = true

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        todayTasks = todayTasks,
                        overdueTasks = overdueTasks,
                        upcomingTasks = upcomingTasks,
                        allTasksDoneToday = allDone,
                        showCelebration = if (shouldCelebrate) true else _uiState.value.showCelebration
                    )
                }
            }

            // 超时兜底：首次加载超过 8 秒仍无数据，退出 loading 显示空状态
            launch {
                kotlinx.coroutines.delay(8000)
                if (!firstLoadDone) {
                    firstLoadDone = true
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }

            launch {
                rewardRepository.getCurrentPoints(userId).collect { points ->
                    _uiState.value = _uiState.value.copy(points = points)
                }
            }

            launch {
                rewardRepository.observePointRecords(userId).collect { records ->
                    val streak = calculateStreakDays(records)
                    _uiState.value = _uiState.value.copy(streakDays = streak)
                }
            }
        }
    }

    /** 在所有任务列表中查找指定 ID 的任务 */
    private fun findTaskById(taskId: String): TaskUiItem? =
        _uiState.value.todayTasks.find { it.id == taskId }
            ?: _uiState.value.overdueTasks.find { it.id == taskId }
            ?: _uiState.value.upcomingTasks.find { it.id == taskId }

    fun markTaskDone(taskId: String) {
        if (taskId in _uiState.value.syncingTaskIds) return
        _uiState.value = _uiState.value.copy(confirmDialogTaskId = taskId)
    }

    fun confirmTaskDone(taskId: String) {
        viewModelScope.launch {
            val oldPoints = _uiState.value.points
            val taskPoints = findTaskById(taskId)?.rewardPoints ?: 0
            val userId = authRepository.currentUserId ?: authRepository.observeCurrentUser().first()?.uid
                ?: return@launch
            applyOptimisticStatus(taskId, TaskStatus.VERIFIED)
            _uiState.value = _uiState.value.copy(
                confirmDialogTaskId = null,
                syncingTaskIds = _uiState.value.syncingTaskIds + taskId,
                points = oldPoints + taskPoints,
                previousPoints = oldPoints,
                earnedPoints = taskPoints,
                showPointsAnimation = true
            )
            taskRepository.completeTask(taskId, userId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        syncingTaskIds = _uiState.value.syncingTaskIds - taskId
                    )
                },
                onFailure = {
                    optimisticTaskStatuses.remove(taskId)
                    updateTaskStatusInUi(taskId, TaskStatus.PENDING)
                    _uiState.value = _uiState.value.copy(
                        points = oldPoints,
                        syncingTaskIds = _uiState.value.syncingTaskIds - taskId
                    )
                }
            )
        }
    }

    fun markTaskUndo(taskId: String) {
        if (taskId in _uiState.value.syncingTaskIds) return
        _uiState.value = _uiState.value.copy(undoDialogTaskId = taskId)
    }

    fun confirmTaskUndo(taskId: String) {
        viewModelScope.launch {
            val taskPoints = findTaskById(taskId)?.rewardPoints ?: 0
            val oldPoints = _uiState.value.points
            val userId = authRepository.currentUserId ?: authRepository.observeCurrentUser().first()?.uid
                ?: return@launch
            applyOptimisticStatus(taskId, TaskStatus.PENDING)
            _uiState.value = _uiState.value.copy(
                undoDialogTaskId = null,
                syncingTaskIds = _uiState.value.syncingTaskIds + taskId,
                points = (oldPoints - taskPoints).coerceAtLeast(0)
            )
            taskRepository.undoCompleteTask(taskId, userId, taskPoints).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        syncingTaskIds = _uiState.value.syncingTaskIds - taskId
                    )
                },
                onFailure = {
                    optimisticTaskStatuses.remove(taskId)
                    updateTaskStatusInUi(taskId, TaskStatus.VERIFIED)
                    _uiState.value = _uiState.value.copy(
                        points = oldPoints,
                        syncingTaskIds = _uiState.value.syncingTaskIds - taskId
                    )
                }
            )
        }
    }

    fun dismissConfirmDialog() { _uiState.value = _uiState.value.copy(confirmDialogTaskId = null) }
    fun dismissUndoDialog() { _uiState.value = _uiState.value.copy(undoDialogTaskId = null) }
    fun dismissCelebration() { _uiState.value = _uiState.value.copy(showCelebration = false) }
    fun dismissPointsAnimation() { _uiState.value = _uiState.value.copy(showPointsAnimation = false) }

    fun toggleTodayExpand() { _uiState.value = _uiState.value.copy(todayExpanded = !_uiState.value.todayExpanded) }
    fun toggleOverdueExpand() { _uiState.value = _uiState.value.copy(overdueExpanded = !_uiState.value.overdueExpanded) }
    fun toggleUpcomingExpand() { _uiState.value = _uiState.value.copy(upcomingExpanded = !_uiState.value.upcomingExpanded) }

    private fun reconcileOptimisticStatuses(tasks: List<Task>) {
        val latestStatusById = tasks.associate { it.id to it.status }
        optimisticTaskStatuses.entries.removeAll { (taskId, expectedStatus) ->
            latestStatusById[taskId] == expectedStatus ||
                (expectedStatus == TaskStatus.VERIFIED && latestStatusById[taskId] == TaskStatus.DONE)
        }
    }

    private fun applyOptimisticStatus(taskId: String, status: TaskStatus) {
        optimisticTaskStatuses[taskId] = status
        updateTaskStatusInUi(taskId, status)
    }

    private fun updateTaskStatusInUi(taskId: String, status: TaskStatus) {
        fun List<TaskUiItem>.withUpdatedStatus() = map { task ->
            if (task.id == taskId) task.copy(status = status.name) else task
        }
        _uiState.value = _uiState.value.copy(
            todayTasks = _uiState.value.todayTasks.withUpdatedStatus(),
            overdueTasks = _uiState.value.overdueTasks.withUpdatedStatus(),
            upcomingTasks = _uiState.value.upcomingTasks.withUpdatedStatus()
        )
    }

    private fun calculateStreakDays(records: List<com.lemonkids.shared.model.PointRecord>): Int {
        val dates = records
            .filter { it.type == com.lemonkids.shared.model.PointRecordType.TASK_COMPLETE }
            .mapNotNull { try { LocalDate.parse(it.timestamp.take(10)) } catch (_: Exception) { null } }
            .distinct().sortedDescending()
        if (dates.isEmpty()) return 0
        var streak = 1
        var cur = dates.first()
        for (i in 1 until dates.size) {
            val prev = dates[i]
            if (prev == cur.minusDays(1)) { streak++; cur = prev }
            else if (prev < cur.minusDays(1)) break
        }
        return streak
    }

    private fun Task.toUiItem() = TaskUiItem(
        id = id, title = title, description = description,
        status = status.name, category = category,
        dueDate = dueDate, dueTime = dueTime, rewardPoints = rewardPoints, penaltyPoints = penaltyPoints
    )
}

private val taskSort = compareBy<TaskUiItem> {
    when (it.status) { "PENDING" -> 0; "DONE", "VERIFIED" -> 1; else -> 2 }
}
