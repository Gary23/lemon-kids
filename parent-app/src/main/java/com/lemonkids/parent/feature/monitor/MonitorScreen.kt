package com.lemonkids.parent.feature.monitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.lemonkids.shared.model.ParentAlarmStatus
import com.lemonkids.shared.model.RemoteAlarm

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<RemoteAlarm?>(null) }

    // 切换回此 Tab 时自动刷新数据
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("使用情况监控", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.children.forEach { child ->
                    FilterChip(
                        selected = child.uid == uiState.selectedChild?.uid,
                        onClick = { viewModel.selectChild(child) },
                        label = { Text(child.name) }
                    )
                }
            }
        }

        item {
            val dateText = if (uiState.selectedDate == LocalDate.now()) "今天" 
                else uiState.selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("查看日期", fontWeight = FontWeight.Bold)
                TextButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Filled.DateRange, contentDescription = "选择日期", modifier = Modifier.padding(end = 4.dp))
                    Text(dateText)
                }
            }
        }

        if (uiState.selectedChild == null) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("请先添加孩子", textAlign = TextAlign.Center)
                }
            }
            return@LazyColumn
        }

        item {
            RemoteAlarmSection(
                childName = uiState.selectedChild!!.name,
                hasMonitorPad = uiState.monitorDevices.isNotEmpty(),
                alarms = uiState.remoteAlarms,
                error = uiState.remoteAlarmError,
                onAdd = { editingAlarm = null; showAlarmDialog = true },
                onEdit = { editingAlarm = it; showAlarmDialog = true },
                onCancel = viewModel::cancelRemoteAlarm
            )
        }

        item {
            val usageDateText = if (uiState.selectedDate == LocalDate.now()) "今天" 
                else uiState.selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${uiState.selectedChild!!.name} 使用情况", fontWeight = FontWeight.Bold)
                    Text("${usageDateText} ${uiState.todayTotalMinutes} 分钟")
                }
            }
        }

        if (uiState.appUsages.isNotEmpty()) {
            item {
                Text("App 使用排行", fontWeight = FontWeight.Bold)
            }

            items(uiState.appUsages, key = { it.packageName }) { usage ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(usage.appName, fontWeight = FontWeight.Medium)
                            Text(
                                "${usage.minutes} 分钟",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val existing = uiState.appLimits.find { it.packageName == usage.packageName }
                        TextButton(onClick = {
                            viewModel.openLimitDialog(usage.packageName, usage.appName, existing)
                        }) {
                            Text(if (existing != null) "编辑限制" else "设置限制")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("限时管理", fontWeight = FontWeight.Bold)
        }

        if (uiState.appLimits.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无限时规则，点击上方 App 设置", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(uiState.appLimits, key = { it.id }) { limit ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(limit.appName, fontWeight = FontWeight.Medium)
                            val descParts = mutableListOf<String>()
                            if (limit.dailyLimitMinutes == 0) descParts.add("禁止使用")
                            else if (limit.dailyLimitMinutes == 999) descParts.add("不限时")
                            else descParts.add("${limit.dailyLimitMinutes}分钟/天")
                            if (limit.singleSessionMinutes > 0) descParts.add("单次${limit.singleSessionMinutes}分钟")
                            if (limit.cooldownMinutes > 0) descParts.add("间隔${limit.cooldownMinutes}分钟")
                            Text(
                                descParts.joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            viewModel.openLimitDialog(
                                limit.packageName, limit.appName, limit
                            )
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { viewModel.removeLimit(limit.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        if (uiState.appUsages.isEmpty() && uiState.appLimits.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("正在收集数据，明天就能看到报告啦", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (uiState.showLimitDialog) {
        var dailyInput by remember(uiState.limitDialogDailyMinutes) {
            mutableStateOf(uiState.limitDialogDailyMinutes.toString())
        }
        var sessionInput by remember(uiState.limitDialogSessionMinutes) {
            mutableStateOf(uiState.limitDialogSessionMinutes.toString())
        }
        var cooldownInput by remember(uiState.limitDialogCooldownMinutes) {
            mutableStateOf(uiState.limitDialogCooldownMinutes.toString())
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissLimitDialog() },
            title = { Text("${uiState.limitDialogAppName} 使用限制") },
            text = {
                Column {
                    Text("每日可用时长（分钟）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("999 = 无限制，0 = 禁止使用", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = dailyInput,
                        onValueChange = { newVal ->
                            dailyInput = newVal.filter { it.isDigit() }
                            dailyInput.toIntOrNull()?.let { viewModel.updateLimitDailyMinutes(it) }
                        },
                        label = { Text("分钟/天") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("单次使用时长（分钟）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("0 = 不限制", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = sessionInput,
                        onValueChange = { newVal ->
                            sessionInput = newVal.filter { it.isDigit() }
                            sessionInput.toIntOrNull()?.let { viewModel.updateLimitSessionMinutes(it) }
                        },
                        label = { Text("分钟/次") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("使用间隔时间（分钟）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("0 = 不限制", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = cooldownInput,
                        onValueChange = { newVal ->
                            cooldownInput = newVal.filter { it.isDigit() }
                            cooldownInput.toIntOrNull()?.let { viewModel.updateLimitCooldownMinutes(it) }
                        },
                        label = { Text("间隔分钟") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    uiState.limitDialogError?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            message,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.saveLimit() },
                    enabled = !uiState.isSavingLimit
                ) {
                    Text(if (uiState.isSavingLimit) "保存中" else "保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLimitDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.selectDate(date)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showAlarmDialog) {
        RemoteAlarmEditDialog(
            existing = editingAlarm,
            isSaving = uiState.isSavingRemoteAlarm,
            onDismiss = { showAlarmDialog = false },
            onSave = { triggerAt, title, message, requiresConfirmation ->
                viewModel.saveRemoteAlarm(editingAlarm, triggerAt, title, message, requiresConfirmation)
                showAlarmDialog = false
            }
        )
    }
}

@Composable
private fun RemoteAlarmSection(
    childName: String,
    hasMonitorPad: Boolean,
    alarms: List<ParentAlarmStatus>,
    error: String?,
    onAdd: () -> Unit,
    onEdit: (RemoteAlarm) -> Unit,
    onCancel: (RemoteAlarm) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("远程闹钟", fontWeight = FontWeight.Bold)
                    Text("由柠檬闹钟管家在 $childName 的 Pad 上执行", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onAdd, enabled = hasMonitorPad) { Text("新建") }
            }
            if (!hasMonitorPad) {
                Text("尚未发现已绑定的监控 Pad，请先在目标 Pad 完成监控端绑定。", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
            error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            if (alarms.isEmpty() && hasMonitorPad) {
                Text("还没有远程闹钟", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            alarms.forEach { item ->
                val alarm = item.alarm
                val time = runCatching { Instant.parse(alarm.triggerAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) }.getOrDefault("时间格式无效")
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(alarm.title, fontWeight = FontWeight.Medium)
                        Text("$time · ${if (alarm.enabled) deliveryLabel(item) else "已取消"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (alarm.enabled) {
                        TextButton(onClick = { onEdit(alarm) }) { Text("编辑") }
                        TextButton(onClick = { onCancel(alarm) }) { Text("取消") }
                    }
                }
            }
        }
    }
}

private fun deliveryLabel(item: ParentAlarmStatus): String = when (item.delivery?.status) {
    "pending" -> "等待 Pad 确认"
    "deployed" -> "Pad 已部署"
    "exact_alarm_denied" -> "Pad 未授予精确闹钟权限"
    "notification_denied" -> "Pad 未授予通知权限"
    "full_screen_denied" -> "Pad 未开启全屏提醒"
    "ringing" -> "正在响铃"
    "dismissed" -> "已关闭"
    "missed" -> "未执行"
    else -> "等待下发"
}

@Composable
private fun RemoteAlarmEditDialog(
    existing: RemoteAlarm?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Instant, String, String, Boolean) -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    var timeText by remember(existing?.id) {
        mutableStateOf(existing?.triggerAt?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).format(formatter) }.getOrNull() }
            ?: java.time.LocalDateTime.now().plusMinutes(5).format(formatter))
    }
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "起床提醒") }
    var message by remember(existing?.id) { mutableStateOf(existing?.message ?: "") }
    var requiresConfirmation by remember(existing?.id) { mutableStateOf(existing?.requiresConfirmation ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建远程闹钟" else "编辑远程闹钟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("时间使用当前手机时区；Pad 离线时也会在已部署的时间响铃。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(timeText, { timeText = it }, label = { Text("时间（yyyy-MM-dd HH:mm）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(message, { message = it }, label = { Text("提醒内容（可选）") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("需要手动确认", fontSize = 14.sp); Text("一期固定为手动关闭，后续可扩展答题等条件", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(checked = requiresConfirmation, onCheckedChange = { requiresConfirmation = it })
                }
                error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !isSaving, onClick = {
                val trigger = runCatching { LocalDateTime.parse(timeText.trim(), formatter).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
                if (trigger == null) error = "时间格式不正确" else onSave(trigger, title, message, requiresConfirmation)
            }) { Text(if (isSaving) "保存中" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
