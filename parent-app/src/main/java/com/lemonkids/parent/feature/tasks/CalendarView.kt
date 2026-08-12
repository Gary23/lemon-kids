package com.lemonkids.parent.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lemonkids.shared.model.TaskStatus
import java.time.LocalDate
import java.time.YearMonth

private enum class Density { NONE, LIGHT, MEDIUM, HEAVY }

@Composable
fun CalendarView(
    onEditTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onCreateTask: (LocalDate) -> Unit,
    viewModel: TasksViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val yearMonth = remember(uiState.selectedDate) { YearMonth.from(uiState.selectedDate) }
    val today = remember { LocalDate.now() }

    Column(modifier = Modifier.fillMaxSize()) {
        // ===== 日历网格（可滚动） =====
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
                .padding(horizontal = 12.dp)
        ) {
            item { MonthHeader(yearMonth, { viewModel.changeMonth(yearMonth.minusMonths(1)) }, { viewModel.changeMonth(yearMonth.plusMonths(1)) }); Spacer(Modifier.height(6.dp)) }
            item { WeekHeaderRow(); Spacer(Modifier.height(2.dp)) }

            val daysInMonth = yearMonth.lengthOfMonth()
            val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value
            val startOffset = firstDayOfWeek % 7
            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (col in 0..6) {
                            val cellIndex = row * 7 + col
                            val dayNum = cellIndex - startOffset + 1
                            if (dayNum in 1..daysInMonth) {
                                val date = yearMonth.atDay(dayNum)
                                val tasks = uiState.monthTasks[date]
                                val density = when {
                                    tasks == null || tasks.isEmpty() -> Density.NONE
                                    tasks.size <= 2 -> Density.LIGHT
                                    tasks.size <= 4 -> Density.MEDIUM
                                    else -> Density.HEAVY
                                }
                                val allCompleted = tasks != null && tasks.isNotEmpty() &&
                                    tasks.all { it.status == TaskStatus.DONE || it.status == TaskStatus.VERIFIED }
                                DayCell(
                                    day = dayNum, isToday = date == today,
                                    isSelected = date == uiState.selectedDate,
                                    density = density,
                                    allCompleted = allCompleted,
                                    onClick = { viewModel.onDateClicked(date) },
                                    onLongClick = { onCreateTask(date) }
                                )
                            } else {
                                Box(Modifier.weight(1f).height(44.dp))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        HorizontalDivider()

        // ===== 选中日任务列表（固定区域） =====
        val selTasks = uiState.selectedDateTasks
        val selDate = uiState.selectedDate
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("${selDate.monthValue}月${selDate.dayOfMonth}日 任务", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))

            if (selTasks.isEmpty()) {
                Text("这一天没有任务", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp))
            } else {
                selTasks.forEach { task ->
                    CalendarTaskRow(task = task, onEdit = { onEditTask(task.id) }, onDelete = { onDeleteTask(task.id) })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(yearMonth: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Text("◀", fontSize = 18.sp) }
        Text("${yearMonth.year}年 ${yearMonth.monthValue}月", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = onNext) { Text("▶", fontSize = 18.sp) }
    }
}

@Composable
private fun WeekHeaderRow() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("日", "一", "二", "三", "四", "五", "六").forEach {
            Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowScope.DayCell(day: Int, isToday: Boolean, isSelected: Boolean, density: Density, allCompleted: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surface
    }
    val dotColor = when (density) {
        Density.NONE -> null
        Density.LIGHT -> if (allCompleted) Color(0xFF4CAF50) else Color(0xFFFF8A65)
        Density.MEDIUM -> if (allCompleted) Color(0xFF4CAF50) else Color(0xFFFF8A65)
        Density.HEAVY -> if (allCompleted) Color(0xFF4CAF50) else Color(0xFFFF8A65)
    }
    Box(
        modifier = Modifier.weight(1f).height(42.dp).padding(2.dp)
            .clip(RoundedCornerShape(8.dp)).background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.toString(), fontSize = 14.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            dotColor?.let { c -> Spacer(Modifier.height(2.dp)); Box(Modifier.size(5.dp).clip(CircleShape).background(c)) }
        }
    }
}

@Composable
private fun CalendarTaskRow(task: TaskUiItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    val statusColor = when (task.status) {
        "DONE", "VERIFIED" -> Color(0xFF4CAF50)
        "EXPIRED" -> Color(0xFF9E9E9E)
        "REJECTED" -> Color(0xFFEF5350)
        else -> MaterialTheme.colorScheme.primary
    }
    val statusText = when (task.status) {
        "PENDING" -> "待完成"
        "DONE" -> "已完成"
        "VERIFIED" -> "已验证"
        "EXPIRED" -> "已过期"
        "REJECTED" -> "已驳回"
        else -> task.status
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(statusText, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) { Text("✏️", fontSize = 14.sp) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) { Text("🗑", fontSize = 14.sp) }
                }
            }
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(task.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⭐${task.rewardPoints}", fontSize = 12.sp, color = Color(0xFFFF9800))
                if (task.dueTime != null) { Spacer(Modifier.width(8.dp)); Text("🕐${task.dueTime}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (task.childName.isNotEmpty()) { Spacer(Modifier.width(8.dp)); Text("👦${task.childName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
