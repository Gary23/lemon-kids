package com.lemonkids.parent.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.util.Log
import com.lemonkids.shared.model.Category
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.model.TaskRecurrenceType
import com.lemonkids.shared.model.TaskTemplate
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.CategoryRepository
import com.lemonkids.shared.repository.ChildUserInfo
import com.lemonkids.shared.repository.TaskRepository
import com.lemonkids.shared.repository.TaskTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    /** 列表模式当前正在查看的孩子，用于保持筛选状态和创建后的即时更新。 */
    val selectedChildId: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val viewMode: ViewMode = ViewMode.LIST,
    val selectedDate: LocalDate = LocalDate.now(),
    /** 日历：日期 → 该日所有任务（已按 end_date 展开） */
    val monthTasks: Map<LocalDate, List<Task>> = emptyMap(),
    /** 日历下方选中的日任务列表 */
    val selectedDateTasks: List<TaskUiItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val taskTemplates: List<TaskTemplate> = emptyList(),
    val expandedCategories: Set<String> = emptySet(),
    val isManageMode: Boolean = false,
    val selectedTaskIds: Set<String> = emptySet()
)

enum class ViewMode { LIST, CALENDAR }

private const val TASKS_VIEW_MODEL_TAG = "TasksViewModel"

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
    private val taskTemplateRepository: TaskTemplateRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState(isLoading = true))
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    private var hasLoadedOnce = false
    private var defaultCategoryRequested = false

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
                existing.collect { list ->
                    if (list.none { it.name == "默认" } && !defaultCategoryRequested) {
                        defaultCategoryRequested = true
                        categoryRepository.createCategory(Category(familyId = familyId, name = "默认"))
                    }
                    _uiState.value = _uiState.value.copy(categories = list)
                }
            }
            launch {
                taskTemplateRepository.observeTemplates(familyId).collect { templates ->
                    _uiState.value = _uiState.value.copy(taskTemplates = templates)
                }
            }
        }
    }

    private var currentObservingChildId: String = ""
    private var observeJob: kotlinx.coroutines.Job? = null
    private var observedCompletedTaskIds = emptySet<String>()

    private fun observeTasks(childId: String) {
        observeJob?.cancel()
        currentObservingChildId = childId
        observeJob = viewModelScope.launch {
            taskRepository.observeChildTasks(childId)
                .catch { error ->
                    Log.e(TASKS_VIEW_MODEL_TAG, "任务列表加载失败 childId=$childId", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "任务列表加载失败，请检查网络后重试"
                    )
                }
                .collect { tasks ->
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
                            errorMessage = null,
                            selectedChildId = childId,
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
            _uiState.value = _uiState.value.copy(isLoading = true, selectedChildId = childId, errorMessage = null)
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
        template: TaskTemplate,
        endDate: String,
        dueDate: String,
        childId: String,
        recurrenceType: TaskRecurrenceType,
        recurrenceWeekdays: Set<Int>,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val user = authRepository.observeCurrentUser().first()
                ?: run {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "登录状态已失效，请重新登录")
                    return@launch
                }
            val familyId = user.familyId
                ?: run {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "未获取到家庭信息，请重新登录后重试")
                    return@launch
                }

            // 计算需要创建的日期列表
            val start = try { LocalDate.parse(dueDate) } catch (_: Exception) { null }
                ?: run {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "任务日期无效")
                    return@launch
                }
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
            Log.i(
                TASKS_VIEW_MODEL_TAG,
                "开始创建任务 familyId=$familyId childId=$childId dates=${dates.size} recurrence=$recurrenceType"
            )

            var hasError = false
            val createdTasks = mutableListOf<Task>()
            for (date in dates) {
                val task = Task(
                    familyId = familyId,
                    title = template.title,
                    description = template.description,
                    childId = childId,
                    createdBy = user.uid,
                    category = template.category,
                    rewardPoints = template.rewardPoints,
                    penaltyPoints = template.penaltyPoints,
                    dueDate = date.toString(),
                    endDate = null,
                    dueTime = null,
                    recurrenceSeriesId = seriesId,
                    recurrenceType = recurrenceType,
                    recurrenceWeekdays = recurrenceWeekdays.sorted(),
                    recurrenceEndDate = recurrenceEnd.toString()
                )
                taskRepository.createTask(task)
                    .onSuccess { taskId -> createdTasks += task.copy(id = taskId) }
                    .onFailure { error ->
                        hasError = true
                        Log.e(
                            TASKS_VIEW_MODEL_TAG,
                            "创建任务失败 childId=$childId dueDate=${task.dueDate} recurrence=$recurrenceType",
                            error
                        )
                    }
            }

            if (hasError) {
                Log.w(TASKS_VIEW_MODEL_TAG, "任务创建结束：存在失败项 familyId=$familyId childId=$childId")
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "创建失败")
            } else {
                Log.i(TASKS_VIEW_MODEL_TAG, "任务创建结束：全部成功 familyId=$familyId childId=$childId")
                if (_uiState.value.viewMode == ViewMode.CALENDAR) {
                    loadMonthData(YearMonth.from(_uiState.value.selectedDate))
                    refreshSelectedDateTasks()
                    _uiState.value = _uiState.value.copy(isLoading = false)
                } else {
                    // 不等 60 秒轮询：插入接口已返回每条任务的 ID，直接合并到当前列表。
                    // 后续轮询仍会以服务端数据为准，处理并发修改或排序变更。
                    val current = _uiState.value
                    val updatedTasks = if (childId == currentObservingChildId) {
                        (current.tasks + createdTasks.map { it.toUiItem(getChildName(it.childId)) })
                            .distinctBy { it.id }
                            .sortedBy { it.dueDate }
                    } else {
                        current.tasks
                    }
                    _uiState.value = current.copy(isLoading = false, tasks = updatedTasks)
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

    fun toggleManageMode() {
        _uiState.value = _uiState.value.copy(
            isManageMode = !_uiState.value.isManageMode,
            selectedTaskIds = emptySet()
        )
    }

    fun toggleTaskSelection(taskId: String) {
        val task = _uiState.value.tasks.find { it.id == taskId } ?: return
        if (!task.isCancellableByParent()) return
        val current = _uiState.value.selectedTaskIds.toMutableSet()
        if (current.contains(taskId)) current.remove(taskId) else current.add(taskId)
        _uiState.value = _uiState.value.copy(selectedTaskIds = current)
    }

    fun batchDeleteTasks() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedTaskIds.filter { taskId ->
                _uiState.value.tasks.find { it.id == taskId }?.isCancellableByParent() == true
            }
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

private fun TaskUiItem.isCancellableByParent(): Boolean =
    status == "PENDING" && runCatching { LocalDate.parse(dueDate) >= LocalDate.now() }.getOrDefault(false)
