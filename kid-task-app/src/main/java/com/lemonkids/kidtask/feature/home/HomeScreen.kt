package com.lemonkids.kidtask.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lemonkids.kidtask.di.KidTtsEntryPoint
import com.lemonkids.kidtask.ui.theme.Coral
import com.lemonkids.kidtask.ui.theme.CoralSoft
import com.lemonkids.kidtask.ui.theme.Cream
import com.lemonkids.kidtask.ui.theme.InkBrown
import com.lemonkids.kidtask.ui.theme.Lavender
import com.lemonkids.kidtask.ui.theme.LavenderSoft
import com.lemonkids.kidtask.ui.theme.Mint
import com.lemonkids.kidtask.ui.theme.MintSoft
import com.lemonkids.kidtask.ui.theme.MutedGray
import com.lemonkids.kidtask.ui.theme.Pink
import com.lemonkids.kidtask.ui.theme.PinkSoft
import com.lemonkids.kidtask.ui.theme.Sunny
import com.lemonkids.kidtask.ui.components.TaskCard
import com.lemonkids.kidtask.ui.components.TaskConfirmDialog
import com.lemonkids.kidtask.ui.components.UndoConfirmDialog
import com.lemonkids.kidtask.util.KidTtsManager
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalTime
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                HeaderSection(
                    nickname = "小当家",
                    points = uiState.points,
                    streakDays = uiState.streakDays
                )

                val hasAnyTask = uiState.todayTasks.isNotEmpty()

                if (uiState.isLoading && !hasAnyTask) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Pink, modifier = Modifier.size(40.dp))
                    }
                } else if (!hasAnyTask && !uiState.isLoading) {
                    EmptyTaskView()
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))

                        // 全部完成庆祝卡片
                        if (uiState.allTasksDoneToday && uiState.todayTasks.isNotEmpty()) {
                            CelebrationCard()
                        }

                        // 首页仅按家长端配置的任务分类展示，不再按上午、下午、晚上或日期分段。
                        if (uiState.todayTasks.isNotEmpty()) {
                            CategoryTaskList(
                                tasks = uiState.todayTasks,
                                categoryNames = uiState.categoryNames,
                                playingTaskId = playingTaskId,
                                onSpeak = { task -> ttsManager.speak(task.id, task.title, task.description) },
                                onMarkDone = { viewModel.markTaskDone(it) },
                                onUndo = { viewModel.markTaskUndo(it) }
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            // 积分飞入动画
            if (uiState.showPointsAnimation) {
                PointsFlyAnimation(
                    earnedPoints = uiState.earnedPoints,
                    onFinished = { viewModel.dismissPointsAnimation() }
                )
            }

            // 全部完成庆祝覆盖层
            if (uiState.showCelebration) {
                CelebrationOverlay(onDismiss = { viewModel.dismissCelebration() })
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

            if (uiState.isLoading && hasAnyTask(uiState)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Pink, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryTaskList(
    tasks: List<com.lemonkids.kidtask.ui.components.TaskUiItem>,
    categoryNames: List<String>,
    playingTaskId: String?,
    onSpeak: (com.lemonkids.kidtask.ui.components.TaskUiItem) -> Unit,
    onMarkDone: (String) -> Unit,
    onUndo: (String) -> Unit
) {
    val tasksByCategory = tasks.groupBy { it.category.ifBlank { "默认" } }
    // 先按家长端分类管理页的顺序显示；历史任务中已被删除的分类放在最后，避免任务丢失。
    val orderedCategories = buildList {
        categoryNames.filter { tasksByCategory.containsKey(it) }.forEach(::add)
        tasksByCategory.keys.filterNot { it in this }.sorted().forEach(::add)
    }
    val colors = listOf(Pink, Lavender, Coral, Mint, Sunny)
    val emojis = listOf("🌸", "💜", "🍊", "🌿", "⭐")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("今天的任务", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = InkBrown)
            Spacer(Modifier.width(8.dp))
            Text("还有 ${tasks.count { it.status == "PENDING" }} 个", color = Pink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        orderedCategories.forEachIndexed { index, categoryName ->
            val categoryTasks = tasksByCategory.getValue(categoryName)
            val color = colors[index % colors.size]
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 3.dp) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("${emojis[index % emojis.size]}  $categoryName", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = InkBrown)
                    Spacer(Modifier.height(8.dp))
                    categoryTasks.forEachIndexed { taskIndex, task ->
                            TaskCard(
                                task = task,
                                isPlaying = playingTaskId == task.id,
                                sectionColor = color,
                                softColor = color.copy(alpha = 0.12f),
                                onSpeak = { onSpeak(task) },
                                onMarkDone = onMarkDone,
                                onUndo = onUndo
                            )
                            if (taskIndex != categoryTasks.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

private fun hasAnyTask(state: HomeUiState) =
    state.todayTasks.isNotEmpty()

// ==================== 顶部 Header ====================

@Composable
private fun HeaderSection(nickname: String, points: Int, streakDays: Int) {
    val greeting = when (LocalTime.now().hour) {
        in 6..11 -> "早上好！"
        in 12..17 -> "下午好！"
        else -> "晚上好！"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                Brush.linearGradient(
                    listOf(PinkSoft, Pink)
                )
            )
            .padding(top = 20.dp, bottom = 28.dp, start = 24.dp, end = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    greeting,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    "$nickname，今天也要加油鸭～",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 连续打卡天数
                if (streakDays > 0) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.9f),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = Coral,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "$streakDays",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Coral
                            )
                        }
                    }
                }
                // 积分
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.9f),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = Sunny,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$points",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Pink
                        )
                    }
                }
            }
        }

        // 装饰 emoji
        Text(
            "🌸",
            fontSize = 56.sp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-20).dp).alpha(0.2f)
        )
    }
}

// ==================== 任务分组 ====================

@Composable
private fun TaskSection(
    emoji: String,
    title: String,
    countLabel: String,
    isExpanded: Boolean,
    barColor: Color,
    softColor: Color,
    chipBg: Color,
    chipText: Color,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column {
            // 分组标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(softColor)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = InkBrown)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = chipBg
                    ) {
                        Text(
                            countLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = chipText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    modifier = Modifier.size(24.dp),
                    tint = MutedGray
                )
            }

            // 任务列表
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content()
                }
            }
        }
    }
}

// ==================== 庆祝卡片 ====================

@Composable
private fun CelebrationCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Sunny.copy(alpha = 0.5f), PinkSoft.copy(alpha = 0.5f)))
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = Color.White, shadowElevation = 4.dp, modifier = Modifier.size(80.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Sunny, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("太棒了！所有任务都完成啦！", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = InkBrown)
            Text("你今天超级厉害，奖励自己一颗大星星吧～", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkBrown.copy(alpha = 0.7f))
        }
    }
}

// ==================== 积分飞入动画 ====================

@Composable
private fun PointsFlyAnimation(
    earnedPoints: Int,
    onFinished: () -> Unit
) {
    var animationPhase by remember { mutableStateOf(0) }
    var offsetY by remember { mutableStateOf(0f) }
    var offsetX by remember { mutableStateOf(0f) }

    val scale by animateFloatAsState(
        targetValue = when (animationPhase) {
            0 -> 0.3f
            1 -> 1.3f
            else -> 1.0f
        },
        animationSpec = tween(400)
    )

    val alpha by animateFloatAsState(
        targetValue = when (animationPhase) {
            0 -> 0f
            1 -> 1f
            else -> 0f
        },
        animationSpec = tween(
            durationMillis = when (animationPhase) {
                0 -> 300
                1 -> 600
                else -> 300
            }
        )
    )

    LaunchedEffect(Unit) {
        animationPhase = 0
        kotlinx.coroutines.delay(100)
        animationPhase = 1
        kotlinx.coroutines.delay(600)
        offsetX = 120f
        offsetY = -200f
        animationPhase = 2
        kotlinx.coroutines.delay(400)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .scale(scale)
                .alpha(alpha)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Pink
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎉", fontSize = 28.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "+$earnedPoints",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ==================== 全部完成覆盖层 ====================

@Composable
private fun CelebrationOverlay(onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500)
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (visible) 0.3f else 0f,
        animationSpec = tween(300)
    )

    LaunchedEffect(Unit) { visible = true }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgAlpha)))

    Box(
        modifier = Modifier.fillMaxSize().scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 12.dp,
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("⭐", "🎉", "🌟", "🎊", "⭐").forEach { emoji ->
                        Text(emoji, fontSize = 36.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("太棒了！", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Pink)
                Spacer(Modifier.height(8.dp))
                Text("今天所有任务都完成啦～", fontSize = 16.sp, color = MutedGray)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink)
                ) {
                    Text("😊 好的", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}

// ==================== 空状态 ====================

@Composable
private fun EmptyTaskView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text("今天还没有任务～", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Pink)
            Spacer(Modifier.height(6.dp))
            Text("等妈妈给你布置任务吧", fontSize = 15.sp, color = MutedGray)
        }
    }
}
