package com.lemonkids.kidtask.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.model.TaskStatus
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.RewardRepository
import com.lemonkids.shared.repository.TaskRepository
import com.lemonkids.kidtask.ui.components.TaskUiItem
import com.lemonkids.kidtask.reminder.TaskReminderScheduler
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
    private val rewardRepository: RewardRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var celebrateShownToday = false
    private var lastCelebrateDate: String = ""
    /** 首次加载是否完成（至少收到一次非空数据或超时确认） */
    private var firstLoadDone = false

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            val today = LocalDate.now()

            launch {
                // 观察所有任务（非删除），在内存中分类过滤
                taskRepository.observeChildTasks(userId).collect { tasks ->
                    TaskReminderScheduler.schedule(appContext, tasks)
                    val todayStr = today.toString()

                    val todayTasks = tasks
                        .filter { it.dueDate == todayStr }
                        .map { it.toUiItem() }
                        .sortedWith(taskSort)

                    val overdueTasks = tasks
                        .filter { t ->
                            val d = try { LocalDate.parse(t.dueDate) } catch (_: Exception) { null }
                            d != null && d < today && t.status == TaskStatus.PENDING
                        }
                        .map { it.toUiItem() }
                        .sortedByDescending { it.dueDate }

                    val upcomingTasks = tasks
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
        _uiState.value = _uiState.value.copy(confirmDialogTaskId = taskId)
    }

    fun confirmTaskDone(taskId: String) {
        viewModelScope.launch {
            val oldPoints = _uiState.value.points
            val taskPoints = findTaskById(taskId)?.rewardPoints ?: 0
            _uiState.value = _uiState.value.copy(isLoading = true)
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            taskRepository.completeTask(taskId, user.uid).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        points = oldPoints + taskPoints,
                        confirmDialogTaskId = null,
                        previousPoints = oldPoints,
                        earnedPoints = taskPoints,
                        showPointsAnimation = true
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        points = oldPoints, isLoading = false, confirmDialogTaskId = null
                    )
                }
            )
        }
    }

    fun markTaskUndo(taskId: String) {
        _uiState.value = _uiState.value.copy(undoDialogTaskId = taskId)
    }

    fun confirmTaskUndo(taskId: String) {
        viewModelScope.launch {
            val taskPoints = findTaskById(taskId)?.rewardPoints ?: 0
            _uiState.value = _uiState.value.copy(isLoading = true)
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            taskRepository.undoCompleteTask(taskId, user.uid, taskPoints).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        points = (_uiState.value.points - taskPoints).coerceAtLeast(0),
                        undoDialogTaskId = null
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isLoading = false, undoDialogTaskId = null)
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
