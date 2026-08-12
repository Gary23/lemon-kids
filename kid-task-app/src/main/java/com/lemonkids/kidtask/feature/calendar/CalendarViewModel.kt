package com.lemonkids.kidtask.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.model.TaskStatus
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
import javax.inject.Inject

data class CalendarUiState(
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue,
    val selectedDate: String = LocalDate.now().toString(),
    val tasksByDate: Map<String, List<TaskUiItem>> = emptyMap(),
    val isLoading: Boolean = false,
    val confirmDialogTaskId: String? = null,
    val undoDialogTaskId: String? = null
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
                        list.map { it.toUiItem() }
                            .sortedWith(taskSort)
                    }
                _uiState.value = _uiState.value.copy(
                    tasksByDate = grouped,
                    isLoading = false
                )
            }
        }
    }

    fun selectDate(date: String) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }

    fun shiftMonth(delta: Int) {
        val current = _uiState.value
        val newMonth = current.month + delta
        val newYear = if (newMonth < 1) {
            current.year - 1
        } else if (newMonth > 12) {
            current.year + 1
        } else {
            current.year
        }
        val finalMonth = if (newMonth < 1) 12 else if (newMonth > 12) 1 else newMonth
        _uiState.value = current.copy(year = newYear, month = finalMonth)
    }

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
        dueDate = dueDate, dueTime = dueTime, rewardPoints = rewardPoints, penaltyPoints = penaltyPoints
    )
}

private val taskSort = compareBy<TaskUiItem> {
    when (it.status) { "PENDING" -> 0; "DONE", "VERIFIED" -> 1; else -> 2 }
}
