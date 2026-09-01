package com.lemonkids.parent.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

data class TaskTemplateManageUiState(
    val templates: List<TaskTemplate> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class TaskTemplateManageViewModel @Inject constructor(
    private val templateRepository: TaskTemplateRepository,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TaskTemplateManageUiState())
    val uiState: StateFlow<TaskTemplateManageUiState> = _uiState.asStateFlow()
    private var defaultCategoryRequested = false

    init {
        viewModelScope.launch {
            val familyId = authRepository.observeCurrentUser().first()?.familyId ?: return@launch
            launch {
                templateRepository.observeTemplates(familyId).collect { templates ->
                    _uiState.value = _uiState.value.copy(templates = templates, isLoading = false)
                }
            }
            launch {
                categoryRepository.observeCategories(familyId).collect { categories ->
                    val missingDefault = categories.none { it.name == "默认" }
                    if (missingDefault && !defaultCategoryRequested) {
                        defaultCategoryRequested = true
                        categoryRepository.createCategory(Category(familyId = familyId, name = "默认"))
                    }
                    _uiState.value = _uiState.value.copy(
                        categories = if (missingDefault) categories + Category(familyId = familyId, name = "默认") else categories
                    )
                }
            }
        }
    }

    fun save(template: TaskTemplate) = viewModelScope.launch {
        val familyId = authRepository.observeCurrentUser().first()?.familyId ?: return@launch
        val result = if (template.id.isBlank()) {
            templateRepository.createTemplate(template.copy(familyId = familyId))
        } else {
            templateRepository.updateTemplate(template)
        }
        result.onFailure { _uiState.value = _uiState.value.copy(errorMessage = "保存失败，请稍后重试") }
    }

    fun delete(template: TaskTemplate) = viewModelScope.launch {
        templateRepository.deleteTemplate(template.id).onFailure {
            _uiState.value = _uiState.value.copy(errorMessage = "删除失败，请稍后重试")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTemplateManageScreen(
    onBack: () -> Unit,
    viewModel: TaskTemplateManageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<TaskTemplate?>(null) }
    var deleting by remember { mutableStateOf<TaskTemplate?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("任务管理") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { TextButton(onClick = { editing = TaskTemplate() }) { Text("＋ 新增") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
            )
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(36.dp)) }
                uiState.templates.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有任务\n点击右上角新增", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.templates, key = { it.id }) { template ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(template.title, fontWeight = FontWeight.Bold)
                                    Text("⭐${template.rewardPoints}", color = MaterialTheme.colorScheme.primary)
                                }
                                Text(template.category, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (template.description.isNotBlank()) Text(template.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.align(Alignment.End)) {
                                    TextButton(onClick = { editing = template }) { Text("编辑") }
                                    TextButton(onClick = { deleting = template }) { Text("删除", color = Color(0xFFEF5350)) }
                                }
                            }
                        }
                    }
                }
            }
            uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        }
    }
    editing?.let { template ->
        TaskTemplateEditDialog(template, uiState.categories, onDismiss = { editing = null }, onSave = { viewModel.save(it); editing = null })
    }
    deleting?.let { template ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("删除任务") },
            text = { Text("删除「${template.title}」后，已安排的任务不会受影响。") },
            confirmButton = { TextButton(onClick = { viewModel.delete(template); deleting = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskTemplateEditDialog(template: TaskTemplate, categories: List<Category>, onDismiss: () -> Unit, onSave: (TaskTemplate) -> Unit) {
    var title by remember(template.id) { mutableStateOf(template.title) }
    var description by remember(template.id) { mutableStateOf(template.description) }
    var category by remember(template.id) { mutableStateOf(template.category) }
    var points by remember(template.id) { mutableStateOf(template.rewardPoints.toString()) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template.id.isBlank()) "新建任务" else "编辑任务") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("任务标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(description, { description = it }, label = { Text("任务描述（可选）") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                    OutlinedTextField(category, {}, readOnly = true, label = { Text("分类") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        categories.forEach { item -> DropdownMenuItem({ Text(item.name) }, { category = item.name; expanded = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(points, { if (it.isEmpty() || it.all(Char::isDigit)) points = it }, label = { Text("⭐ 完成可得积分") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { val value = points.toIntOrNull() ?: 0; if (title.isNotBlank() && category.isNotBlank() && value > 0) onSave(template.copy(title = title.trim(), description = description.trim(), category = category, rewardPoints = value)) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
