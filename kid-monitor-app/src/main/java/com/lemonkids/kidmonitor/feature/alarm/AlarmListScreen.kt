package com.lemonkids.kidmonitor.feature.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lemonkids.kidmonitor.alarm.DeviceAlarmEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AlarmListScreen(viewModel: AlarmListViewModel = hiltViewModel()) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("闹钟", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "仅展示这台 Pad 已生效或即将生效的闹钟",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "同步闹钟")
                }
            }
        }
        if (alarms.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text("当前没有生效或即将生效的闹钟", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(alarms, key = { it.alarmId }) { alarm -> AlarmListItem(alarm) }
        }
    }
}

@Composable
private fun AlarmListItem(alarm: DeviceAlarmEntity) {
    val zone = runCatching { ZoneId.of(alarm.timezone) }.getOrDefault(ZoneId.systemDefault())
    val trigger = Instant.ofEpochMilli(alarm.triggerAtMillis).atZone(zone)
    val end = Instant.ofEpochMilli(alarm.endAtMillis).atZone(zone)
    val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日")
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (alarm.state == DeviceAlarmEntity.STATE_RINGING) "正在响铃" else "下次提醒",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Text("${trigger.format(dateFormat)}  ${trigger.format(timeFormat)}", fontWeight = FontWeight.Bold)
            Text(alarm.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (alarm.message.isNotBlank()) {
                Text(alarm.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trigger.toLocalDate() != end.toLocalDate()) {
                Text(
                    "生效至 ${end.format(dateFormat)} ${end.format(timeFormat)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
