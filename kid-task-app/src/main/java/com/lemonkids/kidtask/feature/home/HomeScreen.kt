package com.lemonkids.kidtask.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lemonkids.kidtask.di.KidTtsEntryPoint
import com.lemonkids.kidtask.ui.components.TaskConfirmDialog
import com.lemonkids.kidtask.ui.components.UndoConfirmDialog
import com.lemonkids.kidtask.ui.theme.Canvas
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.SlateInk
import com.lemonkids.kidtask.ui.theme.SlateMuted
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val ttsManager = remember {
        EntryPointAccessors.fromApplication(appContext, KidTtsEntryPoint::class.java).ttsManager()
    }
    var playingTaskId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAfterForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(ttsManager) {
        ttsManager.onSpeakingChanged = { taskId -> playingTaskId = taskId }
        onDispose { }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeDashboard(
                state = uiState,
                playingTaskId = playingTaskId,
                onSpeak = { task -> ttsManager.speak(task.id, task.title, task.description) },
                onMarkDone = viewModel::markTaskDone,
                onUndo = viewModel::markTaskUndo
            )

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

        }
    }
}

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
                color = Lemon
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
                        color = SlateInk
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
                Text("太棒了！", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = SlateInk)
                Spacer(Modifier.height(8.dp))
                Text("今天所有任务都完成啦～", fontSize = 16.sp, color = SlateMuted)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Lemon)
                ) {
                    Text("😊 好的", color = SlateInk, fontSize = 18.sp)
                }
            }
        }
    }
}
