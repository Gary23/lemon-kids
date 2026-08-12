package com.lemonkids.parent.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.lemonkids.shared.model.Category
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.model.TaskRecurrenceType
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.CategoryRepository
import com.lemonkids.shared.repository.ChildUserInfo
import com.lemonkids.shared.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

data class TasksUiState(
    val tasks: List<TaskUiItem> = emptyList(),
    val editingTask: TaskEditData? = null,
    val childUsers: List<ChildUserInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val viewMode: ViewMode = ViewMode.LIST,
    val selectedDate: LocalDate = LocalDate.now(),
    /** 日历：日期 → 该日所有任务（已按 end_date 展开） */
    val monthTasks: Map<LocalDate, List<Task>> = emptyMap(),
    /** 日历下方选中的日任务列表 */
    val selectedDateTasks: List<TaskUiItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val expandedCategories: Set<String> = emptySet(),
    val isManageMode: Boolean = false,
    val selectedTaskIds: Set<String> = emptySet()
)

enum class ViewMode { LIST, CALENDAR }

data class TaskUiItem(
    val id: String,
    val title: String,
    val description: String = "",
    val status: String,
    val category: String,
    val rewardPoints: Int,
    val penaltyPoints: Int = 2,
    val dueDate: String,
    val dueTime: String? = null,
    val childId: String = "",
    val childName: String = "",
    val categoryName: String = ""
)

data class TaskEditData(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val rewardPoints: Int = 5,
    val penaltyPoints: Int = 2,
    val dueDate: String = LocalDate.now().toString(),
    val endDate: String = LocalDate.now().toString(),
    val dueTime: String? = null,
    val childId: String = "",
    val categoryName: String = "默认",
    val recurrenceType: TaskRecurrenceType = TaskRecurrenceType.NONE,
    val recurrenceWeekdays: Set<Int> = emptySet(),
    val recurrenceEndDate: String? = null,
    val recurrenceSeriesId: String? = null
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState(isLoading = true))
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    private var hasLoadedOnce = false

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            val familyId = user.familyId ?: return@launch

            authRepository.fetchChildUsers(familyId).onSuccess { children ->
                _uiState.value = _uiState.value.copy(childUsers = children)
                if (children.isNotEmpty()) {
                    observeTasks(children.first().uid)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }

            launch {
                // 首次加载时确保"默认"分类存在
                val existing = categoryRepository.observeCategories(familyId)
                // 用一个临时 collect 检查是否存在，不存在则创建
                var hasDefault = false
                existing.collect { list ->
                    hasDefault = list.any { it.name == "默认" }
                    if (!hasDefault && list.isNotEmpty()) {
                        // 有分类但没有"默认"，补建
                        categoryRepository.createCategory(Category(familyId = familyId, name = "默认"))
                    }
                    _uiState.value = _uiState.value.copy(categories = list)
                }
            }
        }
    }

    private var currentObservingChildId: String = ""
    private var observeJob: kotlinx.coroutines.Job? = null
    private var observedCompletedTaskIds = emptySet<String>()

    private fun observeTasks(childId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            currentObservingChildId = childId
            taskRepository.observeChildTasks(childId).collect { tasks ->
                val completedIds = tasks.filter { it.status == com.lemonkids.shared.model.TaskStatus.DONE || it.status == com.lemonkids.shared.model.TaskStatus.VERIFIED }
                    .map { it.id }.toSet()
                if (hasLoadedOnce) {
                    (completedIds - observedCompletedTaskIds).forEach { completedId ->
                        tasks.find { it.id == completedId }?.let { task ->
                            TaskCompletionNotifier.notify(appContext, getChildName(task.childId), task.title, task.id.hashCode())
                        }
                    }
                }
                observedCompletedTaskIds = completedIds
                // 首次加载必更新；后续轮询时如果返回空则跳过（保留旧数据，防止断网闪现空白）
                val shouldUpdate = !hasLoadedOnce || tasks.isNotEmpty()
                if (shouldUpdate) {
                    hasLoadedOnce = true
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        tasks = tasks.map { it.toUiItem(getChildName(it.childId)) }
                    )
                }
            }
        }
    }

    fun refreshTasks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            hasLoadedOnce = false
            observeJob?.cancel()
            val childId = currentObservingChildId.ifEmpty {
                _uiState.value.childUsers.firstOrNull()?.uid ?: return@launch
            }
            observeTasks(childId)
        }
    }

    fun loadTasksForChild(childId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            hasLoadedOnce = false
            observeTasks(childId)
        }
    }

    fun toggleViewMode() {
        val next = if (_uiState.value.viewMode == ViewMode.LIST) ViewMode.CALENDAR else ViewMode.LIST
        _uiState.value = _uiState.value.copy(viewMode = next)
        if (next == ViewMode.CALENDAR) {
            loadMonthTasks()
        }
    }

    fun loadMonthTasks() {
        viewModelScope.launch {
            val ym = YearMonth.from(_uiState.value.selectedDate)
            loadMonthData(ym)
            refreshSelectedDateTasks()
        }
    }

    fun changeMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedDate = yearMonth.atDay(1))
            loadMonthData(yearMonth)
            refreshSelectedDateTasks()
        }
    }

    private suspend fun loadMonthData(ym: YearMonth) {
        val children = _uiState.value.childUsers
        if (children.isEmpty()) return
        val allTasks = mutableMapOf<LocalDate, MutableList<Task>>()
        for (child in children) {
            val tasks = taskRepository.getMonthTasks(child.uid, ym.year, ym.monthValue)
            for (task in tasks) {
                val date = try { LocalDate.parse(task.dueDate) } catch (_: Exception) { null }
                if (date != null && date.year == ym.year && date.monthValue == ym.monthValue) {
                    allTasks.getOrPut(date) { mutableListOf() }.add(task)
                }
            }
        }
        _uiState.value = _uiState.value.copy(monthTasks = allTasks)
    }

    fun onDateClicked(date: LocalDate) {
        val tasks = _uiState.value.monthTasks[date] ?: emptyList()
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            selectedDateTasks = tasks.map { it.toUiItem(getChildName(it.childId)) }
        )
    }

    fun loadTaskForEdit(taskId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            taskRepository.getTaskById(taskId).fold(
                onSuccess = { task ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        editingTask = TaskEditData(
                            id = task.id,
                            title = task.title,
                            description = task.description,
                            rewardPoints = task.rewardPoints,
                            penaltyPoints = task.penaltyPoints,
                            dueDate = task.dueDate,
                            endDate = task.endDate ?: task.dueDate,
                            dueTime = task.dueTime,
                            childId = task.childId,
                            categoryName = task.category,
                            recurrenceType = task.recurrenceType,
                            recurrenceWeekdays = task.recurrenceWeekdays.toSet(),
                            recurrenceEndDate = task.recurrenceEndDate,
                            recurrenceSeriesId = task.recurrenceSeriesId
                        )
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            )
        }
    }

    fun initNewTask() {
        val children = _uiState.value.childUsers
        val defaultChildId = children.firstOrNull()?.uid ?: ""
        val selectedDate = _uiState.value.selectedDate.toString()
        _uiState.value = _uiState.value.copy(
            editingTask = TaskEditData(
                dueDate = selectedDate,
                endDate = selectedDate,
                childId = defaultChildId
            )
        )
    }

    fun clearEditingTask() {
        _uiState.value = _uiState.value.copy(editingTask = null)
    }

    /** 创建单次或重复任务。重复日程展开为独立日任务，以保留逐日完成历史。 */
    fun createTask(
        title: String,
        description: String,
        endDate: String,
        rewardPoints: Int,
        penaltyPoints: Int,
        dueDate: String,
        dueTime: String?,
        childId: String,
        categoryName: String,
        recurrenceType: TaskRecurrenceType,
        recurrenceWeekdays: Set<Int>,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            val familyId = user.familyId ?: return@launch

            // 计算需要创建的日期列表
            val start = try { LocalDate.parse(dueDate) } catch (_: Exception) { null }
                ?: return@launch
            val end = endDate.takeIf { it.isNotEmpty() }
                ?.let { try { LocalDate.parse(it) } catch (_: Exception) { null } }
            val recurrenceEnd = if (recurrenceType == TaskRecurrenceType.NONE) end else (end ?: start.plusMonths(3))
            val dates = if (recurrenceType != TaskRecurrenceType.NONE) {
                generateSequence(start) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(recurrenceEnd) }
                    .filter { date -> shouldCreateOn(date, recurrenceType, recurrenceWeekdays) }
                    .toList()
            } else if (end != null && end != start) {
                generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
            } else {
                listOf(start)
            }
            val seriesId = if (recurrenceType == TaskRecurrenceType.NONE) null else UUID.randomUUID().toString()

            var hasError = false
            for (date in dates) {
                val task = Task(
                    familyId = familyId,
                    title = title,
                    description = description,
                    childId = childId,
                    createdBy = user.uid,
                    category = categoryName,
                    rewardPoints = rewardPoints,
                    penaltyPoints = penaltyPoints,
                    dueDate = date.toString(),
                    endDate = null,
                    dueTime = dueTime,
                    recurrenceSeriesId = seriesId,
                    recurrenceType = recurrenceType,
                    recurrenceWeekdays = recurrenceWeekdays.sorted(),
                    recurrenceEndDate = recurrenceEnd.toString()
                )
                taskRepository.createTask(task).onFailure { hasError = true }
            }

            if (hasError) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "创建失败")
            } else {
                if (_uiState.value.viewMode == ViewMode.CALENDAR) {
                    loadMonthData(YearMonth.from(_uiState.value.selectedDate))
                    refreshSelectedDateTasks()
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                onDone()
            }
        }
    }

    /** 编辑任务：只更新当前这个任务 */
    fun updateTask(
        taskId: String,
        title: String,
        description: String,
        endDate: String,
        rewardPoints: Int,
        penaltyPoints: Int,
        dueDate: String,
        dueTime: String?,
        childId: String,
        categoryName: String,
        recurrenceType: TaskRecurrenceType,
        recurrenceWeekdays: Set<Int>,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            val familyId = user.familyId ?: return@launch

            val task = Task(
                id = taskId,
                familyId = familyId,
                title = title,
                description = description,
                childId = childId,
                createdBy = user.uid,
                category = categoryName,
                rewardPoints = rewardPoints,
                penaltyPoints = penaltyPoints,
                dueDate = dueDate,
                endDate = null,
                dueTime = dueTime,
                recurrenceType = recurrenceType,
                recurrenceWeekdays = recurrenceWeekdays.sorted(),
                recurrenceEndDate = endDate.takeIf { recurrenceType != TaskRecurrenceType.NONE }
            )
            val existing = _uiState.value.editingTask
            val update = if (existing?.recurrenceSeriesId != null) {
                taskRepository.updateFutureTasksInSeries(existing.recurrenceSeriesId, dueDate, task)
            } else {
                taskRepository.updateTask(task)
            }
            update.fold(
                onSuccess = {
                    if (_uiState.value.viewMode == ViewMode.CALENDAR) {
                        loadMonthData(YearMonth.from(_uiState.value.selectedDate))
                        refreshSelectedDateTasks()
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                    onDone()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "更新失败"
                    )
                }
            )
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            taskRepository.deleteTask(taskId).fold(
                onSuccess = {
                    if (_uiState.value.viewMode == ViewMode.CALENDAR) {
                        loadMonthData(YearMonth.from(_uiState.value.selectedDate))
                        refreshSelectedDateTasks()
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                    // LIST 模式：不关 loading，等 observe 流 emit 时关闭
                },
                onFailure = { _uiState.value = _uiState.value.copy(isLoading = false) }
            )
        }
    }

    /** 默认完成即通过；仅允许家长把已完成任务驳回，并自动回退对应积分。 */
    fun rejectTask(taskId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            taskRepository.rejectTask(taskId).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isLoading = false) },
                onFailure = { _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "驳回失败，请稍后重试") }
            )
        }
    }

    fun toggleManageMode() {
        _uiState.value = _uiState.value.copy(
            isManageMode = !_uiState.value.isManageMode,
            selectedTaskIds = emptySet()
        )
    }

    fun toggleTaskSelection(taskId: String) {
        val current = _uiState.value.selectedTaskIds.toMutableSet()
        if (current.contains(taskId)) current.remove(taskId) else current.add(taskId)
        _uiState.value = _uiState.value.copy(selectedTaskIds = current)
    }

    fun batchDeleteTasks() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedTaskIds.toList()
            if (ids.isEmpty()) return@launch
            _uiState.value = _uiState.value.copy(isLoading = true, selectedTaskIds = emptySet())
            ids.forEach { taskRepository.deleteTask(it) }
            // 强制刷新列表（即使结果为空也能正常更新，不会卡 loading）
            hasLoadedOnce = false
            observeJob?.cancel()
            observeTasks(currentObservingChildId)
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            val familyId = user.familyId ?: return@launch
            categoryRepository.createCategory(Category(familyId = familyId, name = name))
        }
    }

    /** 点击分类标题切换展开/折叠 */
    fun toggleCategoryExpand(categoryName: String) {
        val current = _uiState.value.expandedCategories.toMutableSet()
        if (current.contains(categoryName)) current.remove(categoryName)
        else current.add(categoryName)
        _uiState.value = _uiState.value.copy(expandedCategories = current)
    }

    /** 全部展开或全部折叠 */
    fun toggleCollapseAll() {
        val grouped = _uiState.value.tasks.groupBy { it.categoryName }
        val allExpanded = grouped.keys.all { _uiState.value.expandedCategories.contains(it) }
        _uiState.value = _uiState.value.copy(
            expandedCategories = if (allExpanded) emptySet() else grouped.keys.toSet()
        )
    }

    private fun getChildName(childId: String) =
        _uiState.value.childUsers.find { it.uid == childId }?.name ?: ""

    /** 根据当前 monthTasks 刷新 selectedDateTasks */
    private fun refreshSelectedDateTasks() {
        val date = _uiState.value.selectedDate
        val tasks = _uiState.value.monthTasks[date] ?: emptyList()
        _uiState.value = _uiState.value.copy(
            selectedDateTasks = tasks.map { it.toUiItem(getChildName(it.childId)) }
        )
    }

    private fun Task.toUiItem(childName: String) = TaskUiItem(
        id = id, title = title, description = description,
        status = status.name, category = category,
        rewardPoints = rewardPoints, penaltyPoints = penaltyPoints,
        dueDate = dueDate, dueTime = dueTime,
        childId = childId, childName = childName,
        categoryName = category
    )

    private fun shouldCreateOn(
        date: LocalDate,
        recurrenceType: TaskRecurrenceType,
        weekdays: Set<Int>
    ): Boolean = when (recurrenceType) {
        TaskRecurrenceType.DAILY -> true
        TaskRecurrenceType.WEEKDAYS -> date.dayOfWeek.value in 1..5
        TaskRecurrenceType.WEEKLY -> date.dayOfWeek.value in weekdays
        TaskRecurrenceType.NONE -> true
    }
}
