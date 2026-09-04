package com.lemonkids.parent.feature.tasks

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lemonkids.shared.model.Category
import com.lemonkids.shared.model.TaskRecurrenceType
import com.lemonkids.shared.model.TaskTemplate
import com.lemonkids.shared.repository.ChildUserInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    taskId: String?,
    onBack: () -> Unit,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val isNew = taskId == null || taskId == "new"
    val uiState by viewModel.uiState.collectAsState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategoryName by remember { mutableStateOf("默认") }
    var pointsText by remember { mutableStateOf("5") }
    var dueDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var endDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var selectedChildId by remember { mutableStateOf("") }
    var selectedTemplateId by remember { mutableStateOf("") }
    var recurrenceType by remember { mutableStateOf(TaskRecurrenceType.NONE) }
    var recurrenceWeekdays by remember { mutableStateOf(emptySet<Int>()) }

    var pickingDateField by remember { mutableStateOf<String?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    LaunchedEffect(taskId) {
        if (!isNew) {
            viewModel.loadTaskForEdit(taskId)
        } else {
            viewModel.initNewTask()
            if (uiState.childUsers.isNotEmpty() && selectedChildId.isEmpty()) {
                selectedChildId = uiState.childUsers.first().uid
            }
        }
    }

    LaunchedEffect(uiState.editingTask) {
        uiState.editingTask?.let { data ->
            title = data.title
            description = data.description
            selectedCategoryName = data.categoryName
            pointsText = data.rewardPoints.toString()
            dueDate = data.dueDate.ifEmpty { LocalDate.now().toString() }
            endDate = data.endDate.ifEmpty { data.dueDate.ifEmpty { LocalDate.now().toString() } }
            selectedChildId = data.childId
            recurrenceType = data.recurrenceType
            recurrenceWeekdays = data.recurrenceWeekdays
        }
    }

    LaunchedEffect(uiState.childUsers) {
        if (isNew && selectedChildId.isEmpty() && uiState.childUsers.isNotEmpty()) {
            selectedChildId = uiState.childUsers.first().uid
        }
    }

    val points = pointsText.toIntOrNull() ?: 0

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(if (isNew) "新建任务" else "编辑任务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (isNew) {
                    TaskTemplateSelector(
                        templates = uiState.taskTemplates,
                        selectedTemplateId = selectedTemplateId,
                        onSelected = { template ->
                            selectedTemplateId = template.id
                            title = template.title
                            description = template.description
                            selectedCategoryName = template.category
                            pointsText = template.rewardPoints.toString()
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                } else {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("任务标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("任务描述（可选）") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                    Spacer(Modifier.height(16.dp))
                }

                ChildSelector(
                    children = uiState.childUsers,
                    selectedChildId = selectedChildId,
                    onChildSelected = { selectedChildId = it }
                )
                Spacer(Modifier.height(12.dp))

                if (!isNew) {
                    CategorySelector(categories = uiState.categories, selectedName = selectedCategoryName, onSelected = { selectedCategoryName = it }, onAddNew = { showAddCategoryDialog = true })
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = pointsText, onValueChange = { newVal -> if (newVal.isEmpty() || newVal.all { it.isDigit() }) pointsText = newVal }, label = { Text("⭐ 完成可得积分") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                }

                DateField(label = if (isNew) "开始日期" else "任务日期", dateStr = dueDate, onClick = { pickingDateField = "start" })
                Spacer(Modifier.height(12.dp))

                // 单次任务可一次创建多个独立日期；重复任务则在日期范围内生成日程。
                if (isNew) {
                    DateField(
                        label = if (recurrenceType == TaskRecurrenceType.NONE) "结束日期（可选）" else "重复至",
                        dateStr = endDate,
                        onClick = { pickingDateField = "end" }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (isNew) {
                    RecurrenceSelector(
                        recurrenceType = recurrenceType,
                        weekdays = recurrenceWeekdays,
                        onTypeChanged = { type ->
                            recurrenceType = type
                            if (type != TaskRecurrenceType.WEEKLY) recurrenceWeekdays = emptySet()
                            if (type != TaskRecurrenceType.NONE && endDate == dueDate) {
                                endDate = runCatching { LocalDate.parse(dueDate).plusMonths(3).toString() }.getOrDefault(endDate)
                            }
                        },
                        onWeekdaysChanged = { recurrenceWeekdays = it }
                    )
                    Spacer(Modifier.height(12.dp))
                } else if (recurrenceType != TaskRecurrenceType.NONE) {
                    Text("保存后将更新该重复日程中尚未完成的未来任务", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                }

                uiState.errorMessage?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if ((if (isNew) selectedTemplateId.isNotBlank() else title.isNotBlank()) && selectedChildId.isNotBlank() && points > 0) {
                            if (isNew) {
                                val template = uiState.taskTemplates.find { it.id == selectedTemplateId } ?: return@Button
                                viewModel.createTask(
                                    template = template,
                                    endDate = endDate,
                                    dueDate = dueDate,
                                    childId = selectedChildId,
                                    recurrenceType = recurrenceType,
                                    recurrenceWeekdays = recurrenceWeekdays,
                                    onDone = { onBack() }
                                )
                            } else {
                                viewModel.updateTask(
                                    taskId = taskId!!,
                                    title = title.trim(),
                                    description = description.trim(),
                                    endDate = endDate,
                                    rewardPoints = points,
                                    penaltyPoints = 2,
                                    dueDate = dueDate,
                                    dueTime = null,
                                    childId = selectedChildId,
                                    categoryName = selectedCategoryName,
                                    recurrenceType = recurrenceType,
                                    recurrenceWeekdays = recurrenceWeekdays,
                                    onDone = { onBack() }
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = (if (isNew) selectedTemplateId.isNotBlank() else title.isNotBlank()) && selectedChildId.isNotBlank() && points > 0 &&
                        (recurrenceType != TaskRecurrenceType.WEEKLY || recurrenceWeekdays.isNotEmpty()) && !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("保存任务")
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        // DatePicker 对话框
        if (pickingDateField != null) {
            val targetDate = if (pickingDateField == "start") dueDate else endDate
            DatePickerDialog(
                onDismissRequest = { pickingDateField = null },
                confirmButton = { TextButton(onClick = { pickingDateField = null }) { Text("确定") } },
                dismissButton = { TextButton(onClick = { pickingDateField = null }) { Text("取消") } }
            ) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = try {
                        LocalDate.parse(targetDate)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    } catch (_: Exception) { null }
                )
                DatePicker(state = datePickerState)
                LaunchedEffect(datePickerState.selectedDateMillis) {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                        if (pickingDateField == "start") {
                            dueDate = picked
                            val end = try { LocalDate.parse(endDate) } catch (_: Exception) { null }
                            val start = LocalDate.parse(picked)
                            if (end == null || end.isBefore(start)) endDate = picked
                        } else {
                            endDate = picked
                        }
                    }
                }
            }
        }

        // 添加分类对话框
        if (showAddCategoryDialog) {
            AlertDialog(
                onDismissRequest = { showAddCategoryDialog = false },
                title = { Text("添加分类") },
                text = {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("分类名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.addCategory(newCategoryName.trim())
                            selectedCategoryName = newCategoryName.trim()
                            newCategoryName = ""
                            showAddCategoryDialog = false
                        }
                    })
                    { Text("添加") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategoryDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun RecurrenceSelector(
    recurrenceType: TaskRecurrenceType,
    weekdays: Set<Int>,
    onTypeChanged: (TaskRecurrenceType) -> Unit,
    onWeekdaysChanged: (Set<Int>) -> Unit
) {
    Column {
        Text("重复", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf(
                TaskRecurrenceType.NONE to "不重复",
                TaskRecurrenceType.DAILY to "每天",
                TaskRecurrenceType.WEEKDAYS to "工作日",
                TaskRecurrenceType.WEEKLY to "每周"
            ).forEach { (type, label) ->
                FilterChip(selected = recurrenceType == type, onClick = { onTypeChanged(type) }, label = { Text(label) })
            }
        }
        if (recurrenceType == TaskRecurrenceType.WEEKLY) {
            Spacer(Modifier.height(8.dp))
            Text("选择星期", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                    val weekday = index + 1
                    FilterChip(
                        selected = weekday in weekdays,
                        onClick = {
                            onWeekdaysChanged(if (weekday in weekdays) weekdays - weekday else weekdays + weekday)
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskTemplateSelector(
    templates: List<TaskTemplate>,
    selectedTemplateId: String,
    onSelected: (TaskTemplate) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = templates.find { it.id == selectedTemplateId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected?.title ?: if (templates.isEmpty()) "请先到「我的 > 任务管理」创建任务" else "请选择任务",
            onValueChange = {}, readOnly = true, label = { Text("任务") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = templates.isNotEmpty()).fillMaxWidth(),
            enabled = templates.isNotEmpty()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            templates.forEach { template ->
                DropdownMenuItem(
                    text = { Text("${template.title} · ${template.category} · ⭐${template.rewardPoints}") },
                    onClick = { onSelected(template); expanded = false }
                )
            }
        }
    }
}

/** 动态分类选择器：从已有分类列表中选择，或点击"添加"创建新分类 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(
    categories: List<Category>,
    selectedName: String,
    onSelected: (String) -> Unit,
    onAddNew: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("分类") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    onClick = { onSelected(cat.name); expanded = false }
                )
            }
            // 分隔
            DropdownMenuItem(
                text = { Text("＋ 添加新分类", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                onClick = { expanded = false; onAddNew() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildSelector(
    children: List<ChildUserInfo>,
    selectedChildId: String,
    onChildSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedChild = children.find { it.uid == selectedChildId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedChild?.name ?: "请选择孩子",
            onValueChange = {},
            readOnly = true,
            label = { Text("分配给") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = children.isNotEmpty())
                .fillMaxWidth(),
            enabled = children.isNotEmpty()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            children.forEach { child ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👦")
                            Spacer(Modifier.width(8.dp))
                            Text(child.name, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text("⭐${child.totalPoints}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onChildSelected(child.uid); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun DateField(label: String, dateStr: String, onClick: () -> Unit) {
    val displayDate = try {
        LocalDate.parse(dateStr).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
    } catch (_: Exception) { dateStr }

    OutlinedTextField(
        value = displayDate,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = onClick) { Text("📅", fontSize = 20.sp) }
        }
    )
}
