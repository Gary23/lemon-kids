package com.lemonkids.kidtask.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.RewardRepository
import com.lemonkids.shared.repository.TaskRepository
import com.lemonkids.kidtask.ui.components.TaskUiItem
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isLoading: Boolean = false,
    val confirmDialogTaskId: String? = null,
    val undoDialogTaskId: String? = null
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
    private val rewardRepository: RewardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState(isLoading = true))
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            taskRepository.observeChildTasks(userId).collect { tasks ->
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

    fun selectDate(date: String) {
        val selected = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
        _uiState.value = _uiState.value.withSelectedDate(selected)
    }

    fun shiftMonth(delta: Int) {
        val current = _uiState.value
        val target = YearMonth.of(current.year, current.month).plusMonths(delta.toLong())
        val selected = selectedDateForMonth(target, LocalDate.now())
        _uiState.value = current.withSelectedDate(selected)
    }

    fun goToToday() = selectDate(LocalDate.now().toString())

    // ==================== 完成任务 ====================

    fun markTaskDone(taskId: String) {
        _uiState.value = _uiState.value.copy(confirmDialogTaskId = taskId)
    }

    fun confirmTaskDone(taskId: String) {
        viewModelScope.launch {
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            taskRepository.completeTask(taskId, user.uid).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(confirmDialogTaskId = null)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(confirmDialogTaskId = null)
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
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            taskRepository.undoCompleteTask(taskId, user.uid, taskPoints).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(undoDialogTaskId = null)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(undoDialogTaskId = null)
                }
            )
        }
    }

    fun dismissConfirmDialog() { _uiState.value = _uiState.value.copy(confirmDialogTaskId = null) }
    fun dismissUndoDialog() { _uiState.value = _uiState.value.copy(undoDialogTaskId = null) }

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
