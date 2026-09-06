package com.lemonkids.parent.feature.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.lemonkids.shared.model.ParentAlarmStatus
import com.lemonkids.shared.model.RemoteAlarm
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmScreen(viewModel: AlarmViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAlarmDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<RemoteAlarm?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
                Text("远程闹钟", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        }
        item {
            Text(
                "为孩子 Pad 上的柠檬闹钟管家建立提醒。已部署的闹钟即使 Pad 临时离线，也会按时执行。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (uiState.children.isNotEmpty()) {
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
        }
        if (uiState.selectedChild == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "请先添加孩子", textAlign = TextAlign.Center)
                }
            }
        } else {
            item {
                AlarmListSection(
                    childName = uiState.selectedChild!!.name,
                    hasMonitorPad = uiState.monitorDevices.isNotEmpty(),
                    alarms = uiState.remoteAlarms,
                    error = uiState.error,
                    onAdd = { editingAlarm = null; showAlarmDialog = true },
                    onEdit = { editingAlarm = it; showAlarmDialog = true },
                    onCancel = viewModel::cancelRemoteAlarm
                )
            }
        }
    }

    if (showAlarmDialog) {
        RemoteAlarmEditDialog(
            existing = editingAlarm,
            isSaving = uiState.isSaving,
            onDismiss = { showAlarmDialog = false },
            onSave = { triggerAt, title, message, requiresConfirmation ->
                viewModel.saveRemoteAlarm(editingAlarm, triggerAt, title, message, requiresConfirmation)
                showAlarmDialog = false
            }
        )
    }
}

@Composable
private fun AlarmListSection(
    childName: String,
    hasMonitorPad: Boolean,
    alarms: List<ParentAlarmStatus>,
    error: String?,
    onAdd: () -> Unit,
    onEdit: (RemoteAlarm) -> Unit,
    onCancel: (RemoteAlarm) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${childName} 的闹钟", fontWeight = FontWeight.Bold)
                    Text("由柠檬闹钟管家在目标 Pad 上执行", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                val time = runCatching {
                    Instant.parse(alarm.triggerAt).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
                }.getOrDefault("时间格式无效")
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(alarm.title, fontWeight = FontWeight.Medium)
                        Text(
                            "$time · ${if (alarm.enabled) deliveryLabel(item) else "已取消"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
        mutableStateOf(
            existing?.triggerAt?.let {
                runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).format(formatter) }.getOrNull()
            } ?: LocalDateTime.now().plusMinutes(5).format(formatter)
        )
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
                    Column {
                        Text("需要手动确认", fontSize = 14.sp)
                        Text("一期固定为手动关闭，后续可扩展答题等条件", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = requiresConfirmation, onCheckedChange = { requiresConfirmation = it })
                }
                error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !isSaving, onClick = {
                val trigger = runCatching {
                    LocalDateTime.parse(timeText.trim(), formatter).atZone(ZoneId.systemDefault()).toInstant()
                }.getOrNull()
                if (trigger == null) error = "时间格式不正确" else onSave(trigger, title, message, requiresConfirmation)
            }) { Text(if (isSaving) "保存中" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
