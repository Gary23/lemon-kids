package com.lemonkids.kidtask.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lemonkids.kidtask.di.KidTtsEntryPoint
import com.lemonkids.kidtask.ui.components.TaskConfirmDialog
import com.lemonkids.kidtask.ui.components.TaskUiItem
import com.lemonkids.kidtask.ui.components.UndoConfirmDialog
import com.lemonkids.kidtask.ui.theme.Butter
import com.lemonkids.kidtask.ui.theme.Canvas
import com.lemonkids.kidtask.ui.theme.FreshMint
import com.lemonkids.kidtask.ui.theme.FreshMintSoft
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.SkyBlueSoft
import com.lemonkids.kidtask.ui.theme.SlateInk
import com.lemonkids.kidtask.ui.theme.SlateMuted
import com.lemonkids.kidtask.ui.theme.Strawberry
import com.lemonkids.kidtask.ui.theme.StrawberrySoft
import com.lemonkids.kidtask.util.KidTtsManager
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.YearMonth

private val WeekLabels = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
private val CalendarTile = Color(0xFFEDF4FF)
private val CalendarBorder = Color(0xFFE7EDF6)
private val PendingTile = Color(0xFFFFFBEB)
private val PendingBorder = Color(0xFFFCD34D)
private val DoneTile = Color(0xFFECFDF5)
private val DoneBorder = Color(0xFFA7F3D0)
private val NeutralTile = Color(0xFFF5F7FA)

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val ttsManager = remember {
        EntryPointAccessors.fromApplication(appContext, KidTtsEntryPoint::class.java).ttsManager()
    }
    var playingTaskId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(ttsManager) {
        ttsManager.onSpeakingChanged = { taskId -> playingTaskId = taskId }
        onDispose { ttsManager.onSpeakingChanged = null }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 780.dp
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CalendarHeader(uiState.year, uiState.month, viewModel::shiftMonth, viewModel::goToToday)
                    if (wide) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(7f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                CalendarMonthPanel(uiState, today, viewModel::selectDate)
                                DemoStatistics(CalendarDemoUiState())
                                DemoFootprints(CalendarDemoUiState())
                            }
                            Column(Modifier.weight(5f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SelectedDayHeader(uiState)
                                CalendarTaskList(uiState.tasksByDate[uiState.selectedDate].orEmpty(), playingTaskId,
                                    ttsManager, viewModel::markTaskDone, viewModel::markTaskUndo)
                                DemoEncouragement(CalendarDemoUiState())
                            }
                        }
                    } else {
                        CalendarMonthPanel(uiState, today, viewModel::selectDate)
                        DemoStatistics(CalendarDemoUiState())
                        SelectedDayHeader(uiState)
                        CalendarTaskList(uiState.tasksByDate[uiState.selectedDate].orEmpty(), playingTaskId,
                            ttsManager, viewModel::markTaskDone, viewModel::markTaskUndo)
                        DemoEncouragement(CalendarDemoUiState())
                        DemoFootprints(CalendarDemoUiState())
                    }
                }
            }
            uiState.confirmDialogTaskId?.let { taskId ->
                TaskConfirmDialog(onClose = viewModel::dismissConfirmDialog, onConfirm = { viewModel.confirmTaskDone(taskId) })
            }
            uiState.undoDialogTaskId?.let { taskId ->
                UndoConfirmDialog(onClose = viewModel::dismissUndoDialog, onConfirm = { viewModel.confirmTaskUndo(taskId) })
            }
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Lemon, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
private fun CalendarHeader(year: Int, month: Int, onShift: (Int) -> Unit, onToday: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White,
        border = BorderStroke(1.dp, CalendarBorder), shadowElevation = 2.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(14.dp)) {
            val narrow = maxWidth < 690.dp
            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MonthNavigation(year, month, onShift, onToday)
                    StatusLegend()
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    MonthNavigation(year, month, onShift, onToday)
                    StatusLegend()
                }
            }
        }
    }
}

@Composable
private fun MonthNavigation(year: Int, month: Int, onShift: (Int) -> Unit, onToday: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MonthButton("上个月", Icons.Filled.ChevronLeft) { onShift(-1) }
        Surface(shape = CircleShape, color = CalendarTile) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.DateRange, contentDescription = null, tint = SlateInk, modifier = Modifier.size(17.dp))
                Text("${year} 年 ${month} 月", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
            }
        }
        MonthButton("下个月", Icons.Filled.ChevronRight) { onShift(1) }
        Surface(shape = CircleShape, color = Lemon, modifier = Modifier.clickable(role = Role.Button, onClick = onToday)) {
            Text("回到今天", Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                color = SlateInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MonthButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = CalendarTile,
        modifier = Modifier.size(38.dp).clickable(role = Role.Button, onClick = onClick)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = SlateInk, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun StatusLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendPill(FreshMint, "已全部达成")
        LegendPill(Lemon, "部分打卡")
        LegendPill(Color(0xFFCBD5E1), "未来 / 休息")
    }
}

@Composable
private fun LegendPill(color: Color, label: String) {
    Surface(shape = CircleShape, color = CalendarTile) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color))
            Text(label, fontSize = 10.sp, color = SlateMuted, maxLines = 1)
        }
    }
}

@Composable
private fun CalendarMonthPanel(state: CalendarUiState, today: LocalDate, onSelect: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White,
        border = BorderStroke(1.dp, CalendarBorder), shadowElevation = 2.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                WeekLabels.forEachIndexed { index, label ->
                    Text(label, Modifier.weight(1f), textAlign = TextAlign.Center,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (index == 0 || index == 6) Strawberry else SlateInk)
                }
            }
            val month = YearMonth.of(state.year, state.month)
            val offset = month.atDay(1).dayOfWeek.value % 7
            val first = month.atDay(1).minusDays(offset.toLong())
            val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
            (0 until count).map { first.plusDays(it.toLong()) }.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    week.forEach { date ->
                        val inMonth = YearMonth.from(date) == month
                        val summary = dayTaskSummary(state.tasksByDate[date.toString()].orEmpty())
                        DayCell(date, summary, today, date.toString() == state.selectedDate, inMonth,
                            Modifier.weight(1f)) { onSelect(date.toString()) }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = CalendarTile, modifier = Modifier.fillMaxWidth()) {
                Text("点击日期查看真实任务记录和未来安排", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 11.sp, color = SlateMuted)
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, summary: DayTaskSummary, today: LocalDate, isSelected: Boolean,
    inMonth: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val status = dayDisplayStatus(date, today, summary)
    val dotColor = when (status) {
        DayTaskStatus.COMPLETE -> FreshMint
        DayTaskStatus.PENDING -> Lemon
        DayTaskStatus.EMPTY -> Color(0xFFCBD5E1)
    }
    val label = when (status) {
        DayTaskStatus.COMPLETE -> "已全部达成"
        DayTaskStatus.PENDING -> "部分打卡"
        DayTaskStatus.EMPTY -> "未来或休息"
    }
    val stars = if (summary.completed > 0) "+${summary.earnedStars}⭐" else "--"
    val base = modifier.height(65.dp).clip(RoundedCornerShape(13.dp))
        .background(if (isSelected) Lemon else CalendarTile)
        .then(if (isSelected) Modifier.border(1.dp, Color(0xFFEAB308), RoundedCornerShape(13.dp)) else Modifier)
        .semantics { contentDescription = "${date.monthValue}月${date.dayOfMonth}日，$label，得星${if (summary.completed > 0) summary.earnedStars else 0}，${if (date == today) "今天，" else ""}${if (isSelected) "已选中" else "未选中"}" }
    Column(
        modifier = if (inMonth) base.clickable(role = Role.Button, onClick = onClick).padding(7.dp)
            else base.padding(7.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top) {
            Text("${date.dayOfMonth}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                color = if (!inMonth) SlateMuted.copy(alpha = 0.45f) else if (date == today && !isSelected) Strawberry else SlateInk)
            if (inMonth) Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        }
        if (inMonth) Text(stars,
            Modifier.fillMaxWidth(), textAlign = TextAlign.End, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, color = if (isSelected) SlateInk else SlateMuted,
            maxLines = 1)
    }
}

@Composable
private fun SelectedDayHeader(state: CalendarUiState) {
    val selected = LocalDate.parse(state.selectedDate)
    val summary = state.selectedSummary
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White,
        border = BorderStroke(1.dp, CalendarBorder), shadowElevation = 2.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(8.dp).height(32.dp).clip(CircleShape).background(Lemon))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${selected.monthValue}月${selected.dayOfMonth}日 · ${WeekLabels[selected.dayOfWeek.value % 7]}",
                        fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
                    Text("今日打卡计划与成长记录", fontSize = 11.sp, color = SlateMuted)
                }
                Surface(shape = CircleShape, color = FreshMintSoft) {
                    Text("已完成 ${summary.completed} / ${summary.total}", Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        fontSize = 12.sp, color = Color(0xFF166534), fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(CalendarTile)) {
                if (summary.total > 0) Box(Modifier.fillMaxWidth(summary.completed.toFloat() / summary.total)
                    .height(10.dp).clip(CircleShape).background(FreshMint))
            }
        }
    }
}

@Composable
private fun CalendarTaskList(tasks: List<TaskUiItem>, playingTaskId: String?, ttsManager: KidTtsManager,
    onMarkDone: (String) -> Unit, onUndo: (String) -> Unit) {
    if (tasks.isEmpty()) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = CalendarTile) {
            Text("这一天没有任务，好好玩耍吧！", Modifier.padding(20.dp), color = SlateMuted,
                textAlign = TextAlign.Center)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            tasks.forEach { task ->
                CalendarTaskCard(task, playingTaskId == task.id,
                    onSpeak = { ttsManager.speak(task.id, task.title, task.description) },
                    onMarkDone = onMarkDone, onUndo = onUndo)
            }
        }
    }
}

@Composable
private fun CalendarTaskCard(task: TaskUiItem, isPlaying: Boolean, onSpeak: () -> Unit,
    onMarkDone: (String) -> Unit, onUndo: (String) -> Unit) {
    val done = task.status == "DONE" || task.status == "VERIFIED"
    val expired = task.status == "EXPIRED" || task.status == "REJECTED"
    val background = if (done) DoneTile else if (expired) NeutralTile else PendingTile
    val border = if (done) DoneBorder else if (expired) CalendarBorder else PendingBorder
    val accent = if (done) Color(0xFF166534) else if (expired) SlateMuted else Color(0xFF92400E)
    val detail = when {
        done -> "已完成 · 获得 ${task.rewardPoints} 颗星"
        task.status == "REJECTED" -> "已驳回 · 可重新打卡"
        expired -> "已过期 · 可补做"
        else -> "待打卡 · 可获得 ${task.rewardPoints} 颗星"
    }
    val extra = listOf(task.description.takeIf { it.isNotBlank() },
        task.dueTime?.takeIf { it.isNotBlank() }?.let { "时间 $it" }).filterNotNull().joinToString(" · ")
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = background,
        border = BorderStroke(if (done || expired) 1.dp else 2.dp, border)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = CircleShape, color = if (done) Color(0xFFA7F3D0) else if (expired) CalendarTile else Color(0xFFFDE68A)) {
                        Text(task.category.ifBlank { "任务" }, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
                    }
                    Text(task.title, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = SlateInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (extra.isBlank()) detail else "$detail · $extra", fontSize = 11.sp,
                    color = accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(32.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSpeak)
                .semantics { contentDescription = "朗读任务" }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null,
                    tint = if (isPlaying) Strawberry else accent, modifier = Modifier.size(18.dp))
            }
            CalendarTaskActionButton(done) {
                if (done) onUndo(task.id) else onMarkDone(task.id)
            }
        }
    }
}

@Composable
private fun CalendarTaskActionButton(done: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        shape = CircleShape,
        color = if (done) Color.White else Lemon,
        border = if (done) BorderStroke(1.dp, Color(0xFF6EE7B7)) else null,
        shadowElevation = if (done) 1.dp else 3.dp
    ) {
        Text(
            if (done) "撤销" else "打卡",
            Modifier.padding(horizontal = 16.dp, vertical = if (done) 7.dp else 8.dp),
            color = if (done) Color(0xFF047857) else Color(0xFF6C5700),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun DemoStatistics(demo: CalendarDemoUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DemoStat("累计打卡天数", demo.checkInDays, "●", StrawberrySoft, Strawberry, Modifier.weight(1f))
            DemoStat("本月收获小星星", demo.stars, "★", Butter, Color(0xFFEAB308), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DemoStat("全勤满分天数", demo.perfectDays, "✓", FreshMintSoft, FreshMint, Modifier.weight(1f))
            DemoStat("打卡达成率", demo.completionRate, "◯", SkyBlueSoft, FreshMint, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DemoStat(title: String, value: String, symbol: String, iconBg: Color, accent: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Color.White,
        border = BorderStroke(1.dp, CalendarBorder), shadowElevation = 2.dp) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                Text(symbol, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("$title · 演示", color = SlateMuted, fontSize = 10.sp, maxLines = 1)
                Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun DemoEncouragement(demo: CalendarDemoUiState) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = StrawberrySoft.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, Strawberry.copy(alpha = 0.2f))) {
        Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(35.dp).clip(RoundedCornerShape(11.dp)).background(StrawberrySoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Strawberry, modifier = Modifier.size(19.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("今日鼓励寄语 · 演示", fontWeight = FontWeight.ExtraBold, color = SlateInk, fontSize = 13.sp)
                Text(demo.encouragement, color = SlateMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DemoFootprints(demo: CalendarDemoUiState) {
    Surface(shape = RoundedCornerShape(20.dp), color = FreshMintSoft, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("成长足迹 · 演示", fontWeight = FontWeight.ExtraBold, color = SlateInk, fontSize = 13.sp)
            Text(demo.footprints.joinToString("  ·  "), color = SlateMuted, fontSize = 11.sp)
        }
    }
}
