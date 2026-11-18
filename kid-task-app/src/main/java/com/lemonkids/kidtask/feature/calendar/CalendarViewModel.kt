package com.lemonkids.kidtask.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.RewardRepository
import com.lemonkids.shared.repository.TaskRepository
import com.lemonkids.kidtask.feature.home.TaskActionRunner
import com.lemonkids.kidtask.feature.home.taskActionFailureMessage
import com.lemonkids.kidtask.feature.pomodoro.PomodoroManager
import com.lemonkids.kidtask.ui.components.TaskUiItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue,
    val selectedDate: String = LocalDate.now().toString(),
    val tasksByDate: Map<String, List<TaskUiItem>> = emptyMap(),
    /** 只包含已经取得权威数据或本机待同步记录的日期；缺失表示未知，不能当作 0。 */
    val pomodoroCountsByDate: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val confirmDialogTaskId: String? = null,
    val undoDialogTaskId: String? = null,
    val syncingTaskIds: Set<String> = emptySet(),
    val actionError: String? = null
) {
    val selectedSummary: DayTaskSummary get() = dayTaskSummary(tasksByDate[selectedDate].orEmpty())
}

data class DayTaskSummary(val total: Int, val completed: Int, val earnedStars: Int = 0) {
    val status: DayTaskStatus get() = when {
        total == 0 -> DayTaskStatus.EMPTY
        completed == total -> DayTaskStatus.COMPLETE
        else -> DayTaskStatus.PENDING
    }
}

enum class DayTaskStatus { EMPTY, COMPLETE, PENDING }

fun dayTaskSummary(tasks: List<TaskUiItem>): DayTaskSummary {
    val completed = tasks.filter { it.status == "DONE" || it.status == "VERIFIED" }
    return DayTaskSummary(tasks.size, completed.size, completed.sumOf { it.rewardPoints })
}

fun dayDisplayStatus(date: LocalDate, today: LocalDate, summary: DayTaskSummary): DayTaskStatus =
    when {
        summary.status == DayTaskStatus.COMPLETE -> DayTaskStatus.COMPLETE
        date.isAfter(today) || summary.status == DayTaskStatus.EMPTY -> DayTaskStatus.EMPTY
        else -> DayTaskStatus.PENDING
    }

fun sortCalendarTasks(tasks: List<TaskUiItem>): List<TaskUiItem> =
    tasks.sortedWith(compareBy<TaskUiItem> { it.dueTime.isNullOrBlank() }
        .thenBy { it.dueTime ?: "" })

fun selectedDateForMonth(target: YearMonth, today: LocalDate): LocalDate =
    if (YearMonth.from(today) == target) today else target.atDay(1)

fun CalendarUiState.withSelectedDate(date: LocalDate): CalendarUiState = copy(
    year = date.year, month = date.monthValue, selectedDate = date.toString()
)

/** 无后端来源的看板视觉示例，所有使用处须标明“演示”。 */
data class CalendarDemoUiState(
    val checkInDays: String = "12天",
    val stars: String = "38颗",
    val perfectDays: String = "8天",
    val completionRate: String = "86%",
    val encouragement: String = "每完成一件小事，都是向前迈出的一步。继续加油！",
    val footprints: List<String> = listOf("认真完成任务", "坚持阅读", "帮助家人")
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val authRepository: AuthRepository,
    private val rewardRepository: RewardRepository,
    private val pomodoroManager: PomodoroManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState(isLoading = true))
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()
    private val taskActions = TaskActionRunner()
    private var taskChildId: String? = null

    init {
        loadTasks()
        observePomodoro()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            taskChildId = userId
            taskRepository.observeChildTasks(userId).collect { tasks ->
                if (!isCurrentTaskUser(userId)) return@collect
                val grouped = tasks
                    .filter { !it.dueDate.isNullOrEmpty() }
                    .groupBy { it.dueDate }
                    .mapValues { (_, list) ->
                        sortCalendarTasks(list.map { it.toUiItem() })
                    }
                _uiState.value = _uiState.value.copy(
                    tasksByDate = grouped,
                    isLoading = false
                )
            }
        }
    }

    private fun observePomodoro() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            pomodoroManager.bind(userId)
            pomodoroManager.state.collect { state ->
                _uiState.value = _uiState.value.copy(pomodoroCountsByDate = state.countsByDate)
            }
        }
    }

    private fun refreshPomodoroMonth() {
        val month = YearMonth.of(_uiState.value.year, _uiState.value.month)
        pomodoroManager.refreshCounts(month.atDay(1).minusDays(7), month.atEndOfMonth().plusDays(7))
    }

    fun selectDate(date: String) {
        val selected = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
        _uiState.value = _uiState.value.withSelectedDate(selected)
    }

    fun shiftMonth(delta: Int) {
        val current = _uiState.value
        val target = YearMonth.of(current.year, current.month).plusMonths(delta.toLong())
        val selected = selectedDateForMonth(target, LocalDate.now())
        _uiState.value = current.withSelectedDate(selected)
        refreshPomodoroMonth()
    }

    fun goToToday() {
        selectDate(LocalDate.now().toString())
        refreshPomodoroMonth()
    }

    // ==================== 完成任务 ====================

    fun markTaskDone(taskId: String) {
        if (taskId in _uiState.value.syncingTaskIds) return
        _uiState.value = _uiState.value.copy(confirmDialogTaskId = taskId)
    }

    fun confirmTaskDone(taskId: String) = performTaskAction(taskId, completing = true)

    fun markTaskUndo(taskId: String) {
        if (taskId in _uiState.value.syncingTaskIds) return
        _uiState.value = _uiState.value.copy(undoDialogTaskId = taskId)
    }

    fun confirmTaskUndo(taskId: String) = performTaskAction(taskId, completing = false)

    private fun performTaskAction(taskId: String, completing: Boolean) {
        val task = findTaskById(taskId) ?: return
        if (taskId in _uiState.value.syncingTaskIds) return
        if (completing == (task.status == "DONE" || task.status == "VERIFIED")) return
        if (!taskActions.tryStart(taskId)) return
        _uiState.value = _uiState.value.copy(
            confirmDialogTaskId = null,
            undoDialogTaskId = null,
            actionError = null,
            syncingTaskIds = _uiState.value.syncingTaskIds + taskId
        )
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var operationUserId: String? = null
            taskActions.execute(
                taskId = taskId,
                request = {
                    val userId = authRepository.currentUserId
                        ?: authRepository.observeCurrentUser().first()?.uid
                        ?: throw IllegalStateException("No current task user")
                    check(taskChildId == null || taskChildId == userId) { "Task account changed" }
                    operationUserId = userId
                    if (completing) taskRepository.completeTask(taskId, userId)
                    else taskRepository.undoCompleteTask(taskId, userId, task.rewardPoints)
                },
                onSuccess = {
                    if (operationUserId?.let { isCurrentTaskUser(it) } == true) {
                        _uiState.value = _uiState.value.copy(
                            tasksByDate = _uiState.value.tasksByDate.mapValues { (_, tasks) ->
                                tasks.map { item ->
                                    if (item.id == taskId) item.copy(status = if (completing) "VERIFIED" else "PENDING")
                                    else item
                                }
                            }
                        )
                        rewardRepository.requestPointsRefresh()
                    }
                },
                onFailure = {
                    if (operationUserId == null || operationUserId?.let { isCurrentTaskUser(it) } == true) {
                        _uiState.value = _uiState.value.copy(actionError = taskActionFailureMessage(completing))
                        rewardRepository.requestPointsRefresh()
                        // 响应不确定时只读核验，不盲目重试写操作。
                        launch {
                            try { taskRepository.refreshTasks() }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { /* 保留失败提示，等待下次刷新。 */ }
                        }
                    }
                },
                onFinished = {
                    _uiState.value = _uiState.value.copy(syncingTaskIds = _uiState.value.syncingTaskIds - taskId)
                }
            )
        }
    }

    private suspend fun isCurrentTaskUser(userId: String): Boolean =
        (authRepository.currentUserId ?: authRepository.observeCurrentUser().first()?.uid) == userId

    fun dismissConfirmDialog() { _uiState.value = _uiState.value.copy(confirmDialogTaskId = null) }
    fun dismissUndoDialog() { _uiState.value = _uiState.value.copy(undoDialogTaskId = null) }
    fun dismissActionError() { _uiState.value = _uiState.value.copy(actionError = null) }

    /** 在所有日期任务中查找指定 ID 的任务 */
    private fun findTaskById(taskId: String): TaskUiItem? =
        _uiState.value.tasksByDate.values.flatten().find { it.id == taskId }

    private fun Task.toUiItem() = TaskUiItem(
        id = id, title = title, description = description,
        status = status.name, category = category,
        dueDate = dueDate, dueTime = dueTime, rewardPoints = rewardPoints, penaltyPoints = penaltyPoints,
        sourceCategoryId = sourceCategoryId
    )
}
