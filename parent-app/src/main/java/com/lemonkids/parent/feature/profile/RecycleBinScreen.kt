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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.Task
import com.lemonkids.shared.repository.AuthRepository
import com.lemonkids.shared.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RecycleBinUiState(
    val deletedTasks: List<Task> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecycleBinUiState(isLoading = true))
    val uiState: StateFlow<RecycleBinUiState> = _uiState.asStateFlow()

    init {
        loadDeletedTasks()
    }

    private fun loadDeletedTasks() {
        viewModelScope.launch {
            val user = authRepository.observeCurrentUser().first() ?: return@launch
            val familyId = user.familyId ?: return@launch
            taskRepository.observeDeletedTasks(familyId).collect { tasks ->
                _uiState.value = _uiState.value.copy(isLoading = false, deletedTasks = tasks)
            }
        }
    }

    fun restoreTask(taskId: String) {
        viewModelScope.launch {
            val task = _uiState.value.deletedTasks.find { it.id == taskId } ?: return@launch
            // 乐观更新：立即从本地列表移除
            _uiState.value = _uiState.value.copy(
                deletedTasks = _uiState.value.deletedTasks.filter { it.id != taskId }
            )
            taskRepository.restoreTask(taskId).onFailure {
                // 回滚：恢复原数据
                _uiState.value = _uiState.value.copy(
                    deletedTasks = _uiState.value.deletedTasks + task
                )
            }
        }
    }

    fun permanentlyDelete(taskId: String) {
        viewModelScope.launch {
            val task = _uiState.value.deletedTasks.find { it.id == taskId } ?: return@launch
            // 乐观更新：立即从本地列表移除
            _uiState.value = _uiState.value.copy(
                deletedTasks = _uiState.value.deletedTasks.filter { it.id != taskId }
            )
            taskRepository.permanentlyDeleteTask(taskId).onFailure {
                // 回滚：恢复原数据
                _uiState.value = _uiState.value.copy(
                    deletedTasks = _uiState.value.deletedTasks + task
                )
            }
        }
    }

    fun emptyAll() {
        viewModelScope.launch {
            val backup = _uiState.value.deletedTasks
            _uiState.value = _uiState.value.copy(deletedTasks = emptyList(), isLoading = true)
            val user = authRepository.observeCurrentUser().first()
            val familyId = user?.familyId
            if (familyId == null) {
                _uiState.value = _uiState.value.copy(deletedTasks = backup, isLoading = false)
                return@launch
            }
            taskRepository.emptyRecycleBin(familyId).onFailure {
                _uiState.value = _uiState.value.copy(deletedTasks = backup)
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEmptyConfirm by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("回收站") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.deletedTasks.isNotEmpty()) {
                        TextButton(onClick = { showEmptyConfirm = true }) {
                            Text("清理可删除项", color = Color(0xFFEF5350))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
            )

            if (uiState.isLoading && uiState.deletedTasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(36.dp))
                }
            } else if (uiState.deletedTasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("回收站为空", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.deletedTasks, key = { it.id }) { task ->
                        DeletedTaskCard(
                            task = task,
                            onRestore = { viewModel.restoreTask(task.id) },
                            onDelete = { viewModel.permanentlyDelete(task.id) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }

        // 清空确认弹窗
        if (showEmptyConfirm) {
            AlertDialog(
                onDismissRequest = { showEmptyConfirm = false },
                title = { Text("清空回收站") },
                text = { Text("只会彻底删除今天及之后未完成、且没有积分或完成历史的任务；历史任务会继续保留。") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.emptyAll()
                        showEmptyConfirm = false
                    }) { Text("确定清理", color = Color(0xFFEF5350)) }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyConfirm = false }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun DeletedTaskCard(
    task: Task,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val isRecyclable = task.status.name == "PENDING" && task.completedAt == null && task.verifiedAt == null &&
        runCatching { LocalDate.parse(task.dueDate) >= LocalDate.now() }.getOrDefault(false)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(task.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    val (statusText, statusColor) = when (task.status.name) {
                        "PENDING" -> "待完成" to Color(0xFF1976D2)
                        "DONE", "VERIFIED" -> "已完成" to Color(0xFF4CAF50)
                        "EXPIRED" -> "已过期" to Color(0xFF9E9E9E)
                        "REJECTED" -> "已驳回" to Color(0xFFEF5350)
                        else -> task.status.name to Color.Gray
                    }
                    Text(statusText, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Text("⭐${task.rewardPoints}  ${task.dueDate}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isRecyclable) {
                Row {
                    TextButton(onClick = onRestore) { Text("还原", color = Color(0xFF4CAF50), fontSize = 13.sp) }
                    TextButton(onClick = onDelete) { Text("删除", color = Color(0xFFEF5350), fontSize = 13.sp) }
                }
            } else {
                Text("历史已保留", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
