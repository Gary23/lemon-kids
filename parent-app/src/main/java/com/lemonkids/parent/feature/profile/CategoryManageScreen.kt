package com.lemonkids.parent.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.Category
import com.lemonkids.shared.model.CategoryTaskTemplate
import com.lemonkids.shared.model.TaskTemplate
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.CategoryRepository
import com.lemonkids.shared.repository.TaskTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryManageUiState(
    val categories: List<Category> = emptyList(),
    val templates: List<TaskTemplate> = emptyList(),
    val categoryTaskTemplates: List<CategoryTaskTemplate> = emptyList(),
    val isLoading: Boolean = false,
    val templatesLoading: Boolean = true,
    val assignmentsLoading: Boolean = true,
    val categoriesError: String? = null,
    val templatesError: String? = null,
    val assignmentsError: String? = null,
    /** 乐观更新仍需给出请求进行中的反馈，避免用户重复提交。 */
    val isOperating: Boolean = false,
    val operationMessage: String? = null
) {
    val canConfigureTasks: Boolean get() = !isLoading && !templatesLoading && !assignmentsLoading &&
        categoriesError == null && templatesError == null && assignmentsError == null
}

@HiltViewModel
class CategoryManageViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val authRepository: AuthRepository
) : androidx.lifecycle.ViewModel() {

    private val _uiState = MutableStateFlow(CategoryManageUiState(isLoading = true))
    val uiState: StateFlow<CategoryManageUiState> = _uiState.asStateFlow()

    private val loadJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    init { loadCategories() }

    fun retryLoads() = loadCategories()

    private fun loadCategories() {
        loadSource("分类", { _uiState.value = _uiState.value.copy(isLoading = true, categoriesError = null) },
            { _uiState.value = _uiState.value.copy(isLoading = false, categoriesError = it) }) { familyId ->
            categoryRepository.observeCategories(familyId).collect {
                _uiState.value = _uiState.value.copy(categories = it, isLoading = false, categoriesError = null)
            }
        }
        loadSource("任务库", { _uiState.value = _uiState.value.copy(templatesLoading = true, templatesError = null) },
            { _uiState.value = _uiState.value.copy(templatesLoading = false, templatesError = it) }) { familyId ->
            taskTemplateRepository.observeTemplates(familyId).collect {
                _uiState.value = _uiState.value.copy(templates = it, templatesLoading = false, templatesError = null)
            }
        }
        loadSource("分类任务关联", { _uiState.value = _uiState.value.copy(assignmentsLoading = true, assignmentsError = null) },
            { _uiState.value = _uiState.value.copy(assignmentsLoading = false, assignmentsError = it) }) { familyId ->
            categoryRepository.observeCategoryTaskTemplates(familyId).collect {
                _uiState.value = _uiState.value.copy(categoryTaskTemplates = it, assignmentsLoading = false, assignmentsError = null)
            }
        }
    }

    private fun loadSource(
        name: String, onStart: () -> Unit, onError: (String) -> Unit,
        collect: suspend (String) -> Unit
    ) {
        loadJobs.remove(name)?.cancel()
        onStart()
        loadJobs[name] = viewModelScope.launchProfileLoad(onError = { onError("${name}加载失败，请检查网络和服务后重试") }) {
            val familyId = authRepository.observeCurrentUser().first()?.familyId
            if (familyId.isNullOrBlank()) onError("未找到家庭信息，请重新登录后重试")
            else collect(familyId)
        }
    }

    private fun dependenciesReady(): Boolean = _uiState.value.let {
        !it.isLoading && !it.templatesLoading && !it.assignmentsLoading &&
            it.categoriesError == null && it.templatesError == null && it.assignmentsError == null
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            val familyId = user.familyId ?: return@launch
            // 乐观更新：立即插入本地列表
            val tempId = "temp_${System.currentTimeMillis()}"
            val optimistic = Category(id = tempId, familyId = familyId, name = name)
            _uiState.value = _uiState.value.copy(
                categories = _uiState.value.categories + optimistic,
                isOperating = true
            )
            categoryRepository.createCategory(optimistic).fold(
                onSuccess = { realId ->
                    // 用真实 id 替换临时 id
                    _uiState.value = _uiState.value.copy(
                        categories = _uiState.value.categories.map {
                            if (it.id == tempId) it.copy(id = realId) else it
                        },
                        isOperating = false,
                        operationMessage = null
                    )
                },
                onFailure = {
                    // 回滚乐观更新
                    _uiState.value = _uiState.value.copy(
                        categories = _uiState.value.categories.filter { it.id != tempId },
                        isOperating = false,
                        operationMessage = "添加失败，请检查网络或分类名称是否重复"
                    )
                }
            )
        }
    }

    fun updateCategory(category: Category, newName: String) {
        viewModelScope.launch {
            // 乐观更新：立即修改本地列表
            _uiState.value = _uiState.value.copy(
                categories = _uiState.value.categories.map {
                    if (it.id == category.id) it.copy(name = newName) else it
                },
                isOperating = true
            )
            categoryRepository.updateCategory(category.copy(name = newName)).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isOperating = false, operationMessage = null) },
                onFailure = {
                    // 回滚
                    _uiState.value = _uiState.value.copy(
                        categories = _uiState.value.categories.map {
                            if (it.id == category.id) it.copy(name = category.name) else it
                        },
                        isOperating = false,
                        operationMessage = "修改失败"
                    )
                }
            )
        }
    }

    fun deleteCategory(category: Category, onBlocked: (String) -> Unit) {
        if (!dependenciesReady() || _uiState.value.isOperating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val pendingCount = categoryRepository.getPendingTaskCountByCategory(category.id).getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                _uiState.value = _uiState.value.copy(isOperating = false, operationMessage = "无法核对待完成任务，请稍后重试")
                return@launch
            }
            if (pendingCount > 0) {
                _uiState.value = _uiState.value.copy(isOperating = false)
                onBlocked("该分类仍有 ${pendingCount} 个待完成任务，请先取消或完成这些任务")
                return@launch
            }
            // 乐观更新：立即从本地列表移除
            _uiState.value = _uiState.value.copy(
                categories = _uiState.value.categories.filter { it.id != category.id },
                isOperating = true
            )
            categoryRepository.deleteCategory(category.id).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isOperating = false, operationMessage = null) },
                onFailure = {
                    // 回滚
                    _uiState.value = _uiState.value.copy(
                        categories = _uiState.value.categories + category,
                        isOperating = false,
                        operationMessage = "删除失败"
                    )
                }
            )
        }
    }

    fun saveCategoryTasks(category: Category, templateIds: List<String>) {
        if (!dependenciesReady() || _uiState.value.isOperating) {
            _uiState.value = _uiState.value.copy(operationMessage = "请等待分类、任务库和关联加载成功后重试")
            return
        }
        viewModelScope.launch {
            val orderedTemplateIds = templateIds.distinct()
            _uiState.value = _uiState.value.copy(isOperating = true)
            categoryRepository.replaceCategoryTaskTemplates(category.id, orderedTemplateIds).fold(
                onSuccess = {
                    // 关联表的观察流是定时轮询。保存成功后立即写回本地状态，避免弹层关闭后
                    // 仍显示“已配置 0 个任务”，同时下一次服务端刷新仍会以数据库结果为准。
                    val savedAssignments = orderedTemplateIds.mapIndexed { index, templateId ->
                        CategoryTaskTemplate(
                            categoryId = category.id,
                            templateId = templateId,
                            familyId = category.familyId,
                            sortOrder = index
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        categoryTaskTemplates = _uiState.value.categoryTaskTemplates
                            .filterNot { it.categoryId == category.id } + savedAssignments,
                        isOperating = false,
                        operationMessage = null
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isOperating = false,
                        operationMessage = "保存分类任务失败，请稍后重试"
                    )
                }
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(operationMessage = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageScreen(
    onBack: () -> Unit,
    viewModel: CategoryManageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Category?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Category?>(null) }
    var configuringTasks by remember { mutableStateOf<Category?>(null) }
    var dialogName by remember { mutableStateOf("") }
    var blockMessage by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("分类管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.isOperating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    TextButton(
                        enabled = !uiState.isOperating && !uiState.isLoading && uiState.categoriesError == null,
                        onClick = { dialogName = ""; showAddDialog = true }
                    ) {
                        Text("＋ 新增")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
            )

            val loadErrors = listOfNotNull(uiState.categoriesError, uiState.templatesError, uiState.assignmentsError)
            if (loadErrors.isNotEmpty()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(loadErrors.joinToString("\n"), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::retryLoads) { Text("重试") }
                }
            }
            if (uiState.isLoading && uiState.categories.isEmpty()) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(36.dp))
                }
            } else if (uiState.categories.isEmpty() && uiState.categoriesError == null) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有分类\n点击右上角新增", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(uiState.categories, key = { it.id }) { cat ->
                        CategoryItem(
                            category = cat,
                            taskCount = if (uiState.assignmentsLoading || uiState.assignmentsError != null) null
                                else uiState.categoryTaskTemplates.count { it.categoryId == cat.id },
                            canConfigure = uiState.canConfigureTasks && !uiState.isOperating,
                            onConfigureTasks = { configuringTasks = cat },
                            onEdit = { dialogName = cat.name; showEditDialog = cat },
                            onDelete = { if (uiState.canConfigureTasks) showDeleteConfirm = cat }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }

            // 操作消息
            uiState.operationMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp))
            }
            blockMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp))
            }
        }
    }

    // 新增对话框
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增分类") },
            text = {
                OutlinedTextField(
                    value = dialogName,
                    onValueChange = { dialogName = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dialogName.isNotBlank()) {
                        viewModel.addCategory(dialogName.trim())
                        showAddDialog = false
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }

    // 编辑对话框
    showEditDialog?.let { cat ->
        AlertDialog(
            onDismissRequest = { showEditDialog = null },
            title = { Text("修改分类") },
            text = {
                OutlinedTextField(
                    value = dialogName,
                    onValueChange = { dialogName = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dialogName.isNotBlank() && dialogName != cat.name) {
                        viewModel.updateCategory(cat, dialogName.trim())
                    }
                    showEditDialog = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = null }) { Text("取消") } }
        )
    }

    // 删除确认
    showDeleteConfirm?.let { cat ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null; blockMessage = null },
            title = { Text("删除分类") },
            text = { Text("确定要删除「${cat.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(cat) { msg -> blockMessage = msg }
                    showDeleteConfirm = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null; blockMessage = null }) { Text("取消") }
            }
        )
    }

    configuringTasks?.takeIf { uiState.canConfigureTasks }?.let { category ->
        val selectedIds = uiState.categoryTaskTemplates
            .filter { it.categoryId == category.id }
            .map { it.templateId }
            .toSet()
        CategoryTaskPickerDialog(
            category = category,
            templates = uiState.templates,
            selectedIds = selectedIds,
            onDismiss = { configuringTasks = null },
            onSave = { ids ->
                // 按任务库展示顺序保存，确保分类预览和服务端生成顺序稳定。
                viewModel.saveCategoryTasks(category, uiState.templates.filter { it.id in ids }.map { it.id })
                configuringTasks = null
            }
        )
    }
}

@Composable
private fun CategoryItem(
    category: Category,
    taskCount: Int?,
    canConfigure: Boolean,
    onConfigureTasks: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(category.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(if (taskCount == null) "任务关联暂不可用" else "已配置 $taskCount 个任务", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                TextButton(modifier = Modifier.height(36.dp), enabled = canConfigure, onClick = onConfigureTasks) { Text("配置任务", fontSize = 13.sp) }
                TextButton(modifier = Modifier.height(36.dp), onClick = onEdit) { Text("编辑", fontSize = 13.sp) }
                TextButton(modifier = Modifier.height(36.dp), enabled = canConfigure, onClick = onDelete) {
                    Text("删除", fontSize = 13.sp, color = Color(0xFFEF5350))
                }
            }
        }
    }
}

@Composable
private fun CategoryTaskPickerDialog(
    category: Category,
    templates: List<TaskTemplate>,
    selectedIds: Set<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var selected by remember(category.id, selectedIds) { mutableStateOf(selectedIds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置「${category.name}」的任务") },
        text = {
            if (templates.isEmpty()) {
                Text("任务库还没有任务，请先创建任务。")
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("可多选；同一个任务可以加入多个分类。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    templates.forEach { template ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(
                                checked = template.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + template.id else selected - template.id
                                }
                            )
                            Column {
                                Text(template.title, fontWeight = FontWeight.Medium)
                                Text("⭐${template.rewardPoints}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.toList()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
