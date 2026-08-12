package com.lemonkids.parent.feature.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onCreateTask: () -> Unit,
    onEditTask: (String) -> Unit,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("任务管理") },
                actions = {
                    if (uiState.isManageMode) {
                        // 管理模式：显示删除和退出按钮
                        TextButton(
                            onClick = { viewModel.batchDeleteTasks() },
                            enabled = uiState.selectedTaskIds.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "删除${if (uiState.selectedTaskIds.isNotEmpty()) "(${uiState.selectedTaskIds.size})" else ""}",
                                color = if (uiState.selectedTaskIds.isNotEmpty()) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.toggleManageMode() }) {
                            Text("退出")
                        }
                    } else {
                        // 普通模式
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                imageVector = if (uiState.viewMode == ViewMode.LIST) Icons.Filled.CalendarMonth
                                else Icons.AutoMirrored.Filled.List,
                                contentDescription = if (uiState.viewMode == ViewMode.LIST) "日历视图" else "列表视图"
                            )
                        }
                        // 管理按钮仅在列表视图下显示
                        if (uiState.viewMode == ViewMode.LIST) {
                            TextButton(onClick = { viewModel.toggleManageMode() }) {
                                Text("管理")
                            }
                        }
                        IconButton(onClick = onCreateTask) {
                            Icon(Icons.Filled.Add, contentDescription = "新建任务")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
            )

            if (uiState.childUsers.size > 1) {
                ChildFilterRow(
                    children = uiState.childUsers,
                    selectedId = uiState.childUsers.firstOrNull()?.uid ?: "",
                    onChildSelected = { viewModel.loadTasksForChild(it) }
                )
                Spacer(Modifier.height(8.dp))
            }

            when {
                uiState.viewMode == ViewMode.CALENDAR -> {
                    CalendarView(
                        onEditTask = onEditTask,
                        onDeleteTask = { viewModel.deleteTask(it) },
                        onCreateTask = { date ->
                            viewModel.onDateClicked(date)
                            onCreateTask()
                        },
                        viewModel = viewModel
                    )
                }
                uiState.tasks.isEmpty() && !uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("\uD83D\uDCCB", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("还没有创建过任务\n点击右上角 + 添加第一个任务吧", fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
                else -> {
                    Box(Modifier.fillMaxSize()) {
                        val grouped = uiState.tasks.groupBy { it.categoryName }
                            // 按 categories 列表的创建顺序排序，保证折叠项顺序稳定
                            val catNames = uiState.categories.map { it.name }
                            val orderedKeys = catNames.filter { it in grouped.keys } +
                                grouped.keys.filter { it !in catNames }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                val allExpanded = grouped.keys.all { uiState.expandedCategories.contains(it) }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { viewModel.toggleCollapseAll() }) {
                                        Text(
                                            if (allExpanded) "全部折叠" else "全部展开",
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            for (categoryName in orderedKeys) {
                                val tasks = grouped[categoryName] ?: continue
                                val displayName = when (categoryName) {
                                    "other", "" -> "默认"
                                    else -> categoryName
                                }
                                val isExpanded = uiState.expandedCategories.contains(categoryName)
                                item(key = "cat_$categoryName") {
                                    CategoryHeader(
                                        name = displayName,
                                        count = tasks.size,
                                        isExpanded = isExpanded,
                                        onClick = { viewModel.toggleCategoryExpand(categoryName) }
                                    )
                                }
                                if (isExpanded) {
                                    items(tasks, key = { "${it.id}_${it.dueDate}" }) { task ->
                                        TaskRow(
                                            task = task,
                                            isManageMode = uiState.isManageMode,
                                            isSelected = uiState.selectedTaskIds.contains(task.id),
                                            onToggleSelect = { viewModel.toggleTaskSelection(task.id) },
                                            onEdit = { onEditTask(task.id) },
                                            onDelete = { viewModel.deleteTask(task.id) },
                                            onReject = { viewModel.rejectTask(task.id) }
                                        )
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }

                        if (uiState.isLoading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(36.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    name: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("$count 项", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ChildFilterRow(
    children: List<com.lemonkids.shared.repository.ChildUserInfo>,
    selectedId: String,
    onChildSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        children.forEach { child ->
            FilterChip(
                selected = child.uid == selectedId,
                onClick = { onChildSelected(child.uid) },
                label = { Text(child.name, fontSize = 13.sp) }
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskUiItem,
    isManageMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReject: () -> Unit
) {
    val statusColor = when (task.status) {
        "DONE", "VERIFIED" -> Color(0xFF4CAF50)
        "EXPIRED" -> Color(0xFF9E9E9E)
        "REJECTED" -> Color(0xFFEF5350)
        else -> MaterialTheme.colorScheme.primary
    }
    val statusText = when (task.status) {
        "PENDING" -> "待完成"
        "DONE" -> "已完成"
        "VERIFIED" -> "已通过"
        "EXPIRED" -> "已过期"
        "REJECTED" -> "已驳回"
        else -> task.status
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isManageMode) 0.dp else 12.dp)
            .clickable(enabled = isManageMode) { onToggleSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = if (isManageMode) 4.dp else 14.dp, end = 8.dp, top = if (isManageMode) 6.dp else 14.dp, bottom = if (isManageMode) 6.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 管理模式下的选择框
            if (isManageMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(40.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        task.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 非管理模式才显示编辑和删除按钮
                    if (!isManageMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(statusText, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                                Text("✏️", fontSize = 16.sp)
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                                Text("🗑", fontSize = 16.sp)
                            }
                            if (task.status == "DONE" || task.status == "VERIFIED") {
                                TextButton(onClick = onReject, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                                    Text("驳回", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    } else {
                        Text(statusText, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Medium)
                    }
                }

                if (task.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(task.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⭐${task.rewardPoints}", fontSize = 13.sp, color = Color(0xFFFF9800))
                    Text(task.dueDate, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (task.dueTime != null) {
                        Text("🕐${task.dueTime}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (task.childName.isNotEmpty()) {
                        Text("👦${task.childName}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
