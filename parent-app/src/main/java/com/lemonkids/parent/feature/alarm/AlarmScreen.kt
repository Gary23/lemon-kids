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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import com.lemonkids.shared.model.MonitorDevice
import com.lemonkids.shared.model.ParentAlarmStatus
import com.lemonkids.shared.model.RemoteAlarm
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
                    monitorDevices = uiState.monitorDevices,
                    hasMonitorPad = uiState.monitorDevices.isNotEmpty(),
                    alarms = uiState.remoteAlarms,
                    error = uiState.error,
                    isSaving = uiState.isSaving,
                    isRefreshingMonitorDevices = uiState.isRefreshingMonitorDevices,
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
            monitorDevices = uiState.monitorDevices,
            isSaving = uiState.isSaving,
            onDismiss = { showAlarmDialog = false },
            onSave = { targetDeviceId, triggerAt, endAt, title, message, requiresConfirmation ->
                viewModel.saveRemoteAlarm(editingAlarm, targetDeviceId, triggerAt, endAt, title, message, requiresConfirmation) {
                    showAlarmDialog = false
                }
            }
        )
    }
}

@Composable
private fun AlarmListSection(
    childName: String,
    monitorDevices: List<MonitorDevice>,
    hasMonitorPad: Boolean,
    alarms: List<ParentAlarmStatus>,
    error: String?,
    isSaving: Boolean,
    isRefreshingMonitorDevices: Boolean,
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
                if (isSaving || isRefreshingMonitorDevices) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
                }
                TextButton(onClick = onAdd, enabled = hasMonitorPad && !isRefreshingMonitorDevices) { Text("新建") }
            }
            if (!hasMonitorPad && !isRefreshingMonitorDevices) {
                Text("尚未发现已绑定的监控 Pad，请先在目标 Pad 完成监控端绑定。", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
            error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            if (alarms.isEmpty() && hasMonitorPad) {
                Text("还没有远程闹钟", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            alarms.forEach { item ->
                val alarm = item.alarm
                val time = alarmTimeRangeLabel(alarm)
                val targetLabel = monitorDevices.indexOfFirst { it.deviceId == alarm.targetDeviceId }
                    .takeIf { it >= 0 }
                    ?.let(::monitorDeviceLabel)
                    ?: "原目标 Pad 已失效"
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(alarm.title, fontWeight = FontWeight.Medium)
                        Text(
                            "$time · ${if (alarm.enabled) deliveryLabel(item) else "已取消"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            targetLabel,
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

private fun alarmTimeRangeLabel(alarm: RemoteAlarm): String = runCatching {
    val zone = runCatching { ZoneId.of(alarm.timezone) }.getOrDefault(ZoneId.systemDefault())
    val start = Instant.parse(alarm.triggerAt).atZone(zone)
    val end = Instant.parse(alarm.endAt).atZone(zone)
    val dateFormatter = DateTimeFormatter.ofPattern("M月d日")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    if (start.toLocalDate() == end.toLocalDate()) {
        "${start.format(dateFormatter)} ${start.format(timeFormatter)}"
    } else {
        "${start.format(dateFormatter)} 至 ${end.format(dateFormatter)}，每日 ${start.format(timeFormatter)}"
    }
}.getOrElse {
    runCatching {
        Instant.parse(alarm.triggerAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    }.getOrDefault("时间格式无效")
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RemoteAlarmEditDialog(
    existing: RemoteAlarm?,
    monitorDevices: List<MonitorDevice>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Instant, Instant, String, String, Boolean) -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val defaultDateTime = remember { LocalDateTime.now().plusMinutes(5).withSecond(0).withNano(0) }
    var startDate by remember(existing?.id) {
        mutableStateOf(
            existing?.triggerAt?.let {
                runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
            } ?: defaultDateTime.toLocalDate()
        )
    }
    var endDate by remember(existing?.id) {
        mutableStateOf(
            existing?.endAt?.takeIf { it.isNotBlank() }?.let {
                runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
            } ?: defaultDateTime.toLocalDate()
        )
    }
    var alarmTime by remember(existing?.id) {
        mutableStateOf(
            existing?.triggerAt?.let {
                runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalTime() }.getOrNull()
            } ?: defaultDateTime.toLocalTime()
        )
    }
    var selectingStartDate by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "起床提醒") }
    var message by remember(existing?.id) { mutableStateOf(existing?.message ?: "") }
    var requiresConfirmation by remember(existing?.id) { mutableStateOf(existing?.requiresConfirmation ?: true) }
    // 新建时必须主动选择；编辑时仅回填仍有效的原目标设备。
    var selectedDeviceId by remember(existing?.id, monitorDevices) {
        mutableStateOf(existing?.targetDeviceId?.takeIf { selectedId ->
            monitorDevices.any { it.deviceId == selectedId }
        }.orEmpty())
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建远程闹钟" else "编辑远程闹钟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("在生效日期范围内，Pad 会每天在指定时间响铃；开始日和结束日均包含。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("目标监控 Pad", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    monitorDevices.forEachIndexed { index, device ->
                        FilterChip(
                            selected = selectedDeviceId == device.deviceId,
                            onClick = {
                                selectedDeviceId = device.deviceId
                                error = null
                            },
                            label = { Text(monitorDeviceLabel(index)) }
                        )
                    }
                }
                monitorDevices.firstOrNull { it.deviceId == selectedDeviceId }?.let { device ->
                    Text(
                        "${maskedDeviceId(device.deviceId)}；请按该 Pad 的设备尾号确认",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: Text(
                    if (existing == null) "请选择要接收此闹钟的 Pad" else "原目标 Pad 已失效，请重新选择",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
                DateTimeRangeField(
                    label = "开始日期",
                    value = startDate.format(dateFormatter),
                    onClick = { selectingStartDate = true; showDatePicker = true }
                )
                DateTimeRangeField(
                    label = "结束日期",
                    value = endDate.format(dateFormatter),
                    onClick = { selectingStartDate = false; showDatePicker = true }
                )
                DateTimeRangeField(
                    label = "每日提醒时间",
                    value = alarmTime.format(timeFormatter),
                    onClick = { showTimePicker = true }
                )
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(message, { message = it }, label = { Text("提醒内容（可选）") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("需要手动确认", fontSize = 14.sp)
                        Text("开启后由孩子手动关闭本次提醒；最长响铃 60 分钟", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = requiresConfirmation, onCheckedChange = { requiresConfirmation = it })
                }
                error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !isSaving, onClick = {
                val trigger = LocalDateTime.of(startDate, alarmTime).atZone(ZoneId.systemDefault()).toInstant()
                val end = LocalDateTime.of(endDate, alarmTime).atZone(ZoneId.systemDefault()).toInstant()
                when {
                    selectedDeviceId.isBlank() -> error = "请选择要响铃的监控 Pad"
                    end.isBefore(trigger) -> error = "结束日期不能早于开始日期"
                    else -> onSave(selectedDeviceId, trigger, end, title, message, requiresConfirmation)
                }
            }) { Text(if (isSaving) "保存中" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showDatePicker) {
        val selected = if (selectingStartDate) startDate else endDate
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (selectingStartDate) startDate = date else endDate = date
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(alarmTime.hour, alarmTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择每日提醒时间") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    alarmTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } }
        )
    }
}

private fun monitorDeviceLabel(index: Int): String = "监控 Pad ${index + 1}"

private fun maskedDeviceId(deviceId: String): String = when {
    deviceId.length >= 4 -> "设备尾号 ${deviceId.takeLast(4).uppercase()}"
    deviceId.isNotBlank() -> "设备尾号 $deviceId"
    else -> "设备标识不可用"
}

@Composable
private fun DateTimeRangeField(label: String, value: String, onClick: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { TextButton(onClick = onClick) { Text("选择") } }
    )
}
