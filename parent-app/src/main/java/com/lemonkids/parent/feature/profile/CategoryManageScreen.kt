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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryManageUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val operationMessage: String? = null
)

@HiltViewModel
class CategoryManageViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository
) : androidx.lifecycle.ViewModel() {

    private val _uiState = MutableStateFlow(CategoryManageUiState(isLoading = true))
    val uiState: StateFlow<CategoryManageUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            val familyId = user.familyId ?: return@launch
            categoryRepository.observeCategories(familyId).collect { list ->
                _uiState.value = _uiState.value.copy(isLoading = false, categories = list)
            }
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            val familyId = user.familyId ?: return@launch
            // 乐观更新：立即插入本地列表
            val tempId = "temp_${System.currentTimeMillis()}"
            val optimistic = Category(id = tempId, familyId = familyId, name = name)
            _uiState.value = _uiState.value.copy(
                categories = _uiState.value.categories + optimistic
            )
            categoryRepository.createCategory(optimistic).fold(
                onSuccess = { realId ->
                    // 用真实 id 替换临时 id
                    _uiState.value = _uiState.value.copy(
                        categories = _uiState.value.categories.map {
                            if (it.id == tempId) it.copy(id = realId) else it
                        },
                        operationMessage = null
                    )
                },
                onFailure = {
                    // 回滚乐观更新
                    _uiState.value = _uiState.value.copy(
                        categories = _uiState.value.categories.filter { it.id != tempId },
                        operationMessage = "添加失败"
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
                }
            )
            categoryRepository.updateCategory(category.copy(name = newName)).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(operationMessage = null) },
                onFailure = {
                    // 回滚
                    _uiState.value = _uiState.value.copy(
                        categories = _uiState.value.categories.map {
                            if (it.id == category.id) it.copy(name = category.name) else it
                        },
                        operationMessage = "修改失败"
                    )
                }
            )
        }
    }

    fun deleteCategory(category: Category, onBlocked: (String) -> Unit) {
        viewModelScope.launch {
            if (category.name == "默认") {
                onBlocked("「默认」分类不可删除")
                return@launch
            }
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            val familyId = user.familyId ?: return@launch
            val count = categoryRepository.getTaskCountByCategory(familyId, category.name).getOrNull()
            if (count != null && count.first > 0) {
                onBlocked("该分类下有 ${count.first} 个未完成任务，无法删除")
                return@launch
            }
            // 乐观更新：立即从本地列表移除
            _uiState.value = _uiState.value.copy(
                categories = _uiState.value.categories.filter { it.id != category.id }
            )
            categoryRepository.deleteCategory(category.id).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(operationMessage = null) },
                onFailure = {
                    // 回滚
                    _uiState.value = _uiState.value.copy(
                        categories = _uiState.value.categories + category,
                        operationMessage = "删除失败"
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
                    TextButton(onClick = { dialogName = ""; showAddDialog = true }) {
                        Text("＋ 新增")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
            )

            if (uiState.isLoading) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(36.dp))
                }
            } else if (uiState.categories.isEmpty()) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有分类\n点击右上角新增", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.categories, key = { it.id }) { cat ->
                        CategoryItem(
                            category = cat,
                            onEdit = { dialogName = cat.name; showEditDialog = cat },
                            onDelete = { showDeleteConfirm = cat }
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
}

@Composable
private fun CategoryItem(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(category.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Row {
                TextButton(onClick = onEdit) { Text("编辑", fontSize = 13.sp) }
                TextButton(onClick = onDelete) {
                    Text("删除", fontSize = 13.sp, color = Color(0xFFEF5350))
                }
            }
        }
    }
}
