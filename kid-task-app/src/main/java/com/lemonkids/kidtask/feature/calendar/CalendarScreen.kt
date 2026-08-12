package com.lemonkids.kidtask.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lemonkids.kidtask.ui.components.TaskCard
import com.lemonkids.kidtask.ui.components.TaskConfirmDialog
import com.lemonkids.kidtask.ui.components.UndoConfirmDialog
import com.lemonkids.kidtask.ui.theme.Coral
import com.lemonkids.kidtask.ui.theme.Cream
import com.lemonkids.kidtask.ui.theme.InkBrown
import com.lemonkids.kidtask.ui.theme.Lavender
import com.lemonkids.kidtask.ui.theme.LavenderSoft
import com.lemonkids.kidtask.ui.theme.Mint
import com.lemonkids.kidtask.ui.theme.MutedGray
import com.lemonkids.kidtask.ui.theme.Pink
import com.lemonkids.kidtask.ui.theme.PinkSoft
import com.lemonkids.kidtask.ui.theme.Sunny
import com.lemonkids.kidtask.di.KidTtsEntryPoint
import com.lemonkids.kidtask.util.KidTtsManager
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.YearMonth

private val WEEK_LABELS = listOf("日", "一", "二", "三", "四", "五", "六")

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now().toString()

    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val ttsManager = remember {
        EntryPointAccessors.fromApplication(appContext, KidTtsEntryPoint::class.java).ttsManager()
    }
    var playingTaskId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(ttsManager) {
        ttsManager.onSpeakingChanged = { taskId -> playingTaskId = taskId }
        onDispose { }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部 Header
            CalendarHeader(
                year = uiState.year,
                month = uiState.month,
                onShift = { viewModel.shiftMonth(it) }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // 日历卡片
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 星期头
                        Row(modifier = Modifier.fillMaxWidth()) {
                            WEEK_LABELS.forEach { label ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = LavenderSoft.copy(alpha = 0.4f),
                                    modifier = Modifier.weight(1f).padding(2.dp)
                                ) {
                                    Text(
                                        label,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Lavender,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))

                        // 日期网格
                        val ym = YearMonth.of(uiState.year, uiState.month)
                        val firstWeekday = LocalDate.of(uiState.year, uiState.month, 1).dayOfWeek.value % 7
                        val daysInMonth = ym.lengthOfMonth()

                        val rows = mutableListOf<Int?>()
                        for (i in 0 until firstWeekday) rows.add(null)
                        for (d in 1..daysInMonth) rows.add(d)
                        while (rows.size % 7 != 0) rows.add(null)

                        rows.chunked(7).forEach { week ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                week.forEach { day ->
                                    Box(modifier = Modifier.weight(1f).padding(2.dp)) {
                                        if (day != null) {
                                            val dateStr = String.format(
                                                "%04d-%02d-%02d",
                                                uiState.year, uiState.month, day
                                            )
                                            val tasks = uiState.tasksByDate[dateStr]
                                            val hasTask = tasks != null && tasks.isNotEmpty()
                                            val allDone = hasTask && tasks!!.all {
                                                it.status == "DONE" || it.status == "VERIFIED"
                                            }
                                            val hasPending = hasTask && !allDone
                                            val isToday = dateStr == today
                                            val isSelected = dateStr == uiState.selectedDate

                                            DayCell(
                                                day = day,
                                                isToday = isToday,
                                                isSelected = isSelected,
                                                hasTask = hasTask,
                                                allDone = allDone,
                                                hasPending = hasPending,
                                                onClick = { viewModel.selectDate(dateStr) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 图例
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LegendDot(color = Mint, label = "全部完成")
                            Spacer(Modifier.width(20.dp))
                            LegendDot(color = Coral, label = "有未完成")
                            Spacer(Modifier.width(20.dp))
                            LegendDot(color = Color(0xFFD0CCC8), label = "无任务")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 选中日期任务列表
                val selectedTasks = uiState.tasksByDate[uiState.selectedDate] ?: emptyList()
                val isTodaySelected = uiState.selectedDate == today

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            if (isTodaySelected) "今天的安排" else
                                uiState.selectedDate.removePrefix("${uiState.year}-").replace("-", "月") + "日的安排",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkBrown
                        )
                        Spacer(Modifier.height(12.dp))

                        if (selectedTasks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF5F0EB))
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🌈", fontSize = 36.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text("这一天没有任务，好好玩耍吧！", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MutedGray)
                                }
                            }
                        } else {
                            // 使用与首页一致的任务卡片
                            selectedTasks.forEach { task ->
                                TaskCard(
                                    task = task,
                                    isPlaying = playingTaskId == task.id,
                                    sectionColor = Pink,
                                    softColor = PinkSoft.copy(alpha = 0.15f),
                                    onSpeak = { ttsManager.speak(task.id, task.title, task.description) },
                                    onMarkDone = { viewModel.markTaskDone(it) },
                                    onUndo = { viewModel.markTaskUndo(it) }
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }

        // 确认完成弹窗
        if (uiState.confirmDialogTaskId != null) {
            TaskConfirmDialog(
                onClose = { viewModel.dismissConfirmDialog() },
                onConfirm = { viewModel.confirmTaskDone(uiState.confirmDialogTaskId!!) }
            )
        }

        // 撤销确认弹窗
        if (uiState.undoDialogTaskId != null) {
            UndoConfirmDialog(
                onClose = { viewModel.dismissUndoDialog() },
                onConfirm = { viewModel.confirmTaskUndo(uiState.undoDialogTaskId!!) }
            )
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Pink, modifier = Modifier.size(40.dp))
            }
        }
    }
}

// ==================== 月份导航 Header ====================

@Composable
private fun CalendarHeader(
    year: Int,
    month: Int,
    onShift: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                Brush.linearGradient(listOf(LavenderSoft, Lavender))
            )
            .padding(top = 20.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "我的任务日历 📅",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(44.dp).clickable { onShift(-1) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月", tint = Lavender, modifier = Modifier.size(28.dp))
                    }
                }
                Text(
                    "${year}年${month}月",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(44.dp).clickable { onShift(1) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "下个月", tint = Lavender, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

// ==================== 日期格子 ====================

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasTask: Boolean,
    allDone: Boolean,
    hasPending: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (hasTask) Sunny.copy(alpha = 0.15f) else Color.Transparent
            )
            .then(
                if (isSelected) Modifier.border(2.dp, Pink, RoundedCornerShape(16.dp)) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isToday) Pink else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$day",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isToday) Color.White else InkBrown
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        allDone -> Mint
                        hasPending -> Coral
                        else -> Color.Transparent
                    }
                )
        )
    }
}

// ==================== 图例圆点 ====================

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MutedGray)
    }
}
