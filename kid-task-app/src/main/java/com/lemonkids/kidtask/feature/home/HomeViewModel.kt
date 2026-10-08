package com.lemonkids.kidtask.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.Category
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    /** 今日任务 */
    val todayTasks: List<TaskUiItem> = emptyList(),
    /** 家长端配置的任务分类，保持配置创建顺序及稳定的分类标识。 */
    val categories: List<Category> = emptyList(),
    /** 昨天及以前的未完成任务（PENDING），日期降序 */
    val overdueTasks: List<TaskUiItem> = emptyList(),
    /** 明天及以后的任务，日期升序 */
    val upcomingTasks: List<TaskUiItem> = emptyList(),
    val points: Int = 0,
    /** 首次真实积分流结果到达前，不能把默认 0 解释为账户余额。 */
    val isPointsLoaded: Boolean = false,
    val isPointsLoadTimedOut: Boolean = false,
    val previousPoints: Int = 0,
    val earnedPoints: Int = 0,
    val showPointsAnimation: Boolean = false,
    val streakDays: Int = 0,
    val allTasksDoneToday: Boolean = false,
    val showCelebration: Boolean = false,
    val isLoading: Boolean = false,
    /** 首屏任务和分类均已取得首次结果（或加载超时），才能渲染分类列表。 */
    val isInitialDataReady: Boolean = false,
    /** 已本地反馈、正在同步到服务端的任务；不再用全屏加载遮挡列表。 */
    val syncingTaskIds: Set<String> = emptySet(),
    val confirmDialogTaskId: String? = null,
    val undoDialogTaskId: String? = null,
    val actionError: String? = null,
    val orderError: String? = null,
    /** null 表示尚未手动排序，继续使用默认分类顺序。 */
    val pendingOrderIds: List<String>? = null,
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
    /** 首屏需同时等待任务与分类，避免两个独立请求先后返回导致分类重排。 */
    private var initialTasksLoaded = false
    private var initialCategoriesLoaded = false
    private var initialLoadTimedOut = false
    /** 等待仓库返回新快照期间保留本地状态，避免旧的轮询结果把卡片改回去。 */
    private val optimisticTaskStatuses = mutableMapOf<String, TaskStatus>()
    private val orderStore = HomeTaskOrderStore(appContext)
    private val orderWriteMutex = Mutex()
    private var orderWriteRevision = 0L
    private var orderChildId: String? = null
    private var orderDate: String? = null

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            loadOrderFor(userId, LocalDate.now().toString())

            user.familyId?.takeIf { it.isNotBlank() }?.let { familyId ->
                launch {
                    categoryRepository.observeCategories(familyId).collect { categories ->
                        initialCategoriesLoaded = true
                        val isInitialDataReady = initialTasksLoaded || initialLoadTimedOut
                        _uiState.value = _uiState.value.copy(
                            categories = categories.filter { it.name.isNotBlank() },
                            isLoading = !isInitialDataReady,
                            isInitialDataReady = isInitialDataReady
                        )
                    }
                }
            } ?: run {
                // 未加入家庭的账号没有分类数据源，不应让首屏一直等待。
                initialCategoriesLoaded = true
            }

            launch {
                // 观察所有任务（非删除），在内存中分类过滤
                taskRepository.observeChildTasks(userId).collect { tasks ->
                    TaskReminderScheduler.schedule(appContext, tasks)
                    val today = LocalDate.now()
                    val todayStr = today.toString()
                    loadOrderFor(userId, todayStr)
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

                    initialTasksLoaded = true
                    val isInitialDataReady = initialCategoriesLoaded || initialLoadTimedOut

                    val pendingIds = todayTasks.filterNot { it.status == "DONE" || it.status == "VERIFIED" }
                        .map { it.id }.toSet()
                    _uiState.value.pendingOrderIds?.let { saved ->
                        val pruned = saved.filter { it in pendingIds }
                        if (pruned != saved) {
                            _uiState.value = _uiState.value.copy(pendingOrderIds = pruned)
                            val revision = ++orderWriteRevision
                            viewModelScope.launch {
                                if (!writeOrder(userId, todayStr, pruned, revision) &&
                                    revision == orderWriteRevision) {
                                    _uiState.value = _uiState.value.copy(orderError = "排序保存失败，请重试")
                                }
                            }
                        }
                    }

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
                        isLoading = !isInitialDataReady,
                        isInitialDataReady = isInitialDataReady,
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
                if (!initialTasksLoaded || !initialCategoriesLoaded) {
                    initialLoadTimedOut = true
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isInitialDataReady = true
                    )
                }
            }

            launch {
                rewardRepository.getCurrentPoints(userId).collect { points ->
                    _uiState.value = _uiState.value.copy(
                        points = points,
                        isPointsLoaded = true,
                        isPointsLoadTimedOut = false
                    )
                }
            }

            launch {
                kotlinx.coroutines.delay(8000)
                if (!_uiState.value.isPointsLoaded) {
                    _uiState.value = _uiState.value.copy(isPointsLoadTimedOut = true)
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
                    removeCompletedFromOrder(taskId)
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
            if (_uiState.value.isPointsLoaded && oldPoints < taskPoints) {
                _uiState.value = _uiState.value.copy(
                    undoDialogTaskId = null,
                    actionError = "积分已用于兑换，当前余额不足以撤销这项任务"
                )
                rewardRepository.requestPointsRefresh()
                return@launch
            }
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
                    appendUndoneToOrder(taskId)
                    _uiState.value = _uiState.value.copy(
                        syncingTaskIds = _uiState.value.syncingTaskIds - taskId
                    )
                },
                onFailure = { error ->
                    optimisticTaskStatuses.remove(taskId)
                    updateTaskStatusInUi(taskId, TaskStatus.VERIFIED)
                    _uiState.value = _uiState.value.copy(
                        points = oldPoints,
                        syncingTaskIds = _uiState.value.syncingTaskIds - taskId,
                        actionError = error.message ?: "撤销失败，请刷新后重试"
                    )
                    rewardRepository.requestPointsRefresh()
                }
            )
        }
    }

    fun dismissConfirmDialog() { _uiState.value = _uiState.value.copy(confirmDialogTaskId = null) }
    fun dismissActionError() { _uiState.value = _uiState.value.copy(actionError = null) }
    fun dismissOrderError() { _uiState.value = _uiState.value.copy(orderError = null) }
    fun dismissUndoDialog() { _uiState.value = _uiState.value.copy(undoDialogTaskId = null) }
    fun dismissCelebration() { _uiState.value = _uiState.value.copy(showCelebration = false) }
    fun dismissPointsAnimation() { _uiState.value = _uiState.value.copy(showPointsAnimation = false) }

    /** 前台恢复时重新建立有效会话并刷新任务；无论请求结果如何都不能无限显示加载动画。 */
    fun refreshAfterForeground() {
        viewModelScope.launch {
            if (_uiState.value.todayTasks.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            taskRepository.refreshTasks()
            rewardRepository.requestPointsRefresh()
            kotlinx.coroutines.delay(8_000)
            if (_uiState.value.isLoading) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun toggleTodayExpand() { _uiState.value = _uiState.value.copy(todayExpanded = !_uiState.value.todayExpanded) }
    fun toggleOverdueExpand() { _uiState.value = _uiState.value.copy(overdueExpanded = !_uiState.value.overdueExpanded) }
    fun toggleUpcomingExpand() { _uiState.value = _uiState.value.copy(upcomingExpanded = !_uiState.value.upcomingExpanded) }

    private fun loadOrderFor(childId: String, date: String) {
        if (orderChildId == childId && orderDate == date) return
        orderChildId = childId
        orderDate = date
        _uiState.value = _uiState.value.copy(pendingOrderIds = orderStore.read(childId, date))
    }

    private suspend fun writeOrder(childId: String, date: String, ids: List<String>, revision: Long): Boolean =
        orderWriteMutex.withLock {
            if (revision != orderWriteRevision) return@withLock true
            runCatching { withContext(Dispatchers.IO) { orderStore.write(childId, date, ids) } }
                .getOrDefault(false)
        }

    fun savePendingOrder(ids: List<String>) {
        val childId = orderChildId ?: return
        val date = orderDate ?: return
        val pendingIds = _uiState.value.todayTasks.filterNot {
            it.status == "DONE" || it.status == "VERIFIED"
        }.map { it.id }.toSet()
        val cleanIds = ids.filter { it in pendingIds }.distinct()
        val previous = _uiState.value.pendingOrderIds
        _uiState.value = _uiState.value.copy(pendingOrderIds = cleanIds, orderError = null)
        val revision = ++orderWriteRevision
        viewModelScope.launch {
            if (!writeOrder(childId, date, cleanIds, revision) && orderChildId == childId &&
                orderDate == date && revision == orderWriteRevision) {
                _uiState.value = _uiState.value.copy(
                    pendingOrderIds = previous,
                    orderError = "排序保存失败，请重试"
                )
            }
        }
    }

    private fun removeCompletedFromOrder(taskId: String) {
        val current = _uiState.value.pendingOrderIds ?: return
        if (taskId !in current) return
        savePendingOrder(current - taskId)
    }

    private fun appendUndoneToOrder(taskId: String) {
        val current = _uiState.value.pendingOrderIds ?: buildHomeTaskLayout(
            _uiState.value.todayTasks.filterNot { it.id == taskId },
            _uiState.value.categories.map { it.name }
        ).pending.map { it.id }
        savePendingOrder((current - taskId) + taskId)
    }

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
        dueDate = dueDate, dueTime = dueTime, rewardPoints = rewardPoints, penaltyPoints = penaltyPoints,
        sourceCategoryId = sourceCategoryId
    )
}

private val taskSort = compareBy<TaskUiItem> {
    when (it.status) { "PENDING" -> 0; "DONE", "VERIFIED" -> 1; else -> 2 }
}
