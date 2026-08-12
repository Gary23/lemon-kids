package com.lemonkids.parent.feature.profile

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lemonkids.shared.model.DeviceStatusEventType
import com.lemonkids.shared.model.DeviceStatusLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceStatusLogScreen(
    onBack: () -> Unit,
    viewModel: DeviceStatusLogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text("设备日志", style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
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

        uiState.errorMessage?.let { message ->
            item {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
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

        if (uiState.logs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无设备上报日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(uiState.logs, key = { it.id.ifBlank { "${it.createdAt}_${it.eventType}" } }) { log ->
                DeviceStatusLogCard(log)
            }
        }
    }
}

@Composable
private fun DeviceStatusLogCard(log: DeviceStatusLog) {
    val isWarning = !log.accessibilityEnabled || !log.limitServiceRunning
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isWarning) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(eventTitle(log.eventType), fontWeight = FontWeight.Bold)
                Text(
                    formatTime(log.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(log.message.ifBlank { "无说明" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "无障碍：${statusText(log.accessibilityEnabled)} · 限制服务：${statusText(log.limitServiceRunning)} · 电池优化：${batteryText(log.batteryIgnoringOptimizations)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun eventTitle(eventType: String): String = when (eventType) {
    DeviceStatusEventType.APP_START.value -> "应用启动"
    DeviceStatusEventType.BOOT.value -> "开机恢复"
    DeviceStatusEventType.USER_PRESENT.value -> "解锁上报"
    DeviceStatusEventType.SERVICE_RECOVERED.value -> "服务恢复"
    DeviceStatusEventType.ACCESSIBILITY_DISABLED.value -> "无障碍关闭"
    else -> "心跳上报"
}

private fun statusText(enabled: Boolean): String = if (enabled) "正常" else "异常"

private fun batteryText(ignoring: Boolean): String = if (ignoring) "已忽略" else "未忽略"

private fun formatTime(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        Instant.parse(value)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    }.getOrDefault(value)
}
