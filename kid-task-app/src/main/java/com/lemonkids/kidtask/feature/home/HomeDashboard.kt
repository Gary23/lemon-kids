package com.lemonkids.kidtask.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemonkids.kidtask.ui.components.StatusBadge
import com.lemonkids.kidtask.ui.components.TaskCard
import com.lemonkids.kidtask.ui.components.TaskUiItem
import com.lemonkids.kidtask.ui.components.assignTaskCategoryAppearances
import com.lemonkids.kidtask.ui.components.stableTaskCategoryAppearance
import com.lemonkids.kidtask.ui.theme.FreshMint
import com.lemonkids.kidtask.ui.theme.FreshMintSoft
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.LemonBorder
import com.lemonkids.kidtask.ui.theme.SlateInk
import com.lemonkids.kidtask.ui.theme.SlateMuted
import com.lemonkids.kidtask.ui.theme.Strawberry
import com.lemonkids.kidtask.ui.theme.KidTaskTheme

@Composable
fun HomeDashboard(
    state: HomeUiState,
    playingTaskId: String?,
    onSpeak: (TaskUiItem) -> Unit,
    onMarkDone: (String) -> Unit,
    onUndo: (String) -> Unit,
    onPendingOrderChanged: (List<String>) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
            .padding(horizontal = 28.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        if (!state.isInitialDataReady || (state.isLoading && state.todayTasks.isEmpty())) {
            Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Lemon)
            }
        } else {
            val layout = buildHomeTaskLayout(state.todayTasks, state.categories.map { it.name }, state.pendingOrderIds)
            ProgressHero(state.todayTasks.size, layout)

            if (state.allTasksDoneToday && state.todayTasks.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(28.dp), color = FreshMintSoft, border = BorderStroke(1.dp, FreshMint)) {
                    Text("🎉 太棒了！今天的任务全部完成啦！", modifier = Modifier.fillMaxWidth().padding(20.dp),
                        color = SlateInk, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            if (state.todayTasks.isEmpty()) {
                EmptyHomeTasks()
            } else {
                val completed = layout.completed
                val pending = layout.pending
                val categoryIds = state.categories.associate { it.name to it.id }
                val categoryAppearances = assignTaskCategoryAppearances(state.todayTasks.map { task ->
                    task.sourceCategoryId ?: categoryIds[task.category] ?: task.category
                })

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    PendingTaskDragColumn(pending, categoryIds, categoryAppearances,
                        playingTaskId, state.syncingTaskIds,
                        scrollState, onSpeak, onMarkDone, onUndo, onPendingOrderChanged,
                        Modifier.weight(1.25f))
                    CompletedTaskColumn(completed, categoryIds, categoryAppearances,
                        playingTaskId, state.syncingTaskIds,
                        onSpeak, onMarkDone, onUndo, Modifier.weight(0.75f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ProgressHero(total: Int, layout: HomeTaskLayout) {
    val completed = layout.completed.size
    val progress = layout.progressPercent / 100f
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = Color(0xFFEFF4FF),
        border = BorderStroke(1.dp, Color(0xFFDCE9FF).copy(alpha = 0.5f)), shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ProgressRing(progress, completed, total, Modifier.size(80.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("今日已完成 $completed/$total 项 (${layout.progressPercent}%)", color = Color(0xFF0D1C2E),
                    fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (total == 0) "今天还没有安排任务" else if (completed == total) "太棒啦，今天的成长任务全部完成！"
                    else "再完成 ${total - completed} 个任务，就能点亮更多星星！", color = Color(0xFF4D4632), fontSize = 15.sp)
            }
            MysteryBox()
        }
    }
}

@Composable
private fun ProgressRing(progress: Float, completed: Int, total: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(64.dp),
            color = FreshMint, trackColor = LemonBorder.copy(alpha = 0.5f), strokeWidth = 6.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(progress * 100).toInt()}%", color = Color(0xFF0D1C2E), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("$completed / $total", color = Color(0xFF4D4632), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MysteryBox() {
    Surface(shape = CircleShape, color = Color.White, border = BorderStroke(1.dp, LemonBorder)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = Strawberry, modifier = Modifier.size(22.dp))
            Column {
                Text("神秘盲盒", color = SlateInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("即将开放", color = Strawberry, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Lemon, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun CompletedTaskColumn(
    tasks: List<TaskUiItem>,
    categoryIds: Map<String, String>,
    categoryAppearances: Map<String, Pair<Color, String>>,
    playingTaskId: String?,
    syncingTaskIds: Set<String>,
    onSpeak: (TaskUiItem) -> Unit,
    onMarkDone: (String) -> Unit,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(12.dp).height(28.dp).background(FreshMint, CircleShape))
            Text("今天已完成", modifier = Modifier.weight(1f), color = SlateInk, fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold)
            StatusBadge("${tasks.size} 项", FreshMintSoft, FreshMint)
        }
        if (tasks.isEmpty()) {
            Surface(shape = RoundedCornerShape(26.dp), color = Color.White,
                border = BorderStroke(1.dp, LemonBorder.copy(alpha = 0.5f))) {
                Text("完成任务后，星星会出现在这里 ✨",
                    modifier = Modifier.fillMaxWidth().padding(25.dp), color = SlateMuted, fontSize = 14.sp)
            }
        }
        tasks.forEach { task ->
            key(task.id) {
                val visualKey = task.sourceCategoryId ?: categoryIds[task.category] ?: task.category
                val (categoryColor, _) = categoryAppearances[visualKey]
                    ?: stableTaskCategoryAppearance(visualKey)
                Column {
                    TaskCard(task, playingTaskId == task.id, categoryColor,
                        categoryColor.copy(alpha = 0.12f),
                        onSpeak = { onSpeak(task) }, onMarkDone = onMarkDone, onUndo = onUndo)
                    if (task.id in syncingTaskIds) {
                        Text("正在同步任务…", color = SlateMuted, fontSize = 12.sp,
                            modifier = Modifier.padding(start = 10.dp, top = 14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeTasks() {
    Surface(shape = RoundedCornerShape(28.dp), color = Color.White, border = BorderStroke(1.dp, LemonBorder)) {
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FreshMint, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(12.dp))
            Text("今天还没有任务～", color = SlateInk, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("新任务出现时会自动显示在这里", color = SlateMuted, fontSize = 14.sp)
        }
    }
}

@Preview(name = "今日任务 · 横屏平板", widthDp = 1024, heightDp = 768, showBackground = true)
@Composable
private fun HomeDashboardTabletPreview() {
    HomeDashboardPreviewContent()
}

@Composable
private fun HomeDashboardPreviewContent() {
    val previewTasks = listOf(
        TaskUiItem("preview-reading", "朗读一篇小故事", "读完后说说最喜欢的角色", "PENDING", "阅读", dueTime = "18:00", rewardPoints = 2, penaltyPoints = 0),
        TaskUiItem("preview-sport", "跳绳十分钟", status = "DONE", category = "运动", dueTime = null, rewardPoints = 1, penaltyPoints = 0)
    )
    KidTaskTheme {
        HomeDashboard(
            state = HomeUiState(todayTasks = previewTasks, points = 18, streakDays = 3, isInitialDataReady = true),
            playingTaskId = null, onSpeak = {}, onMarkDone = {}, onUndo = {}
        )
    }
}
