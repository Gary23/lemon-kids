package com.lemonkids.kidtask.feature.pomodoro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.SlateInk
import com.lemonkids.kidtask.ui.theme.SlateMuted
import com.lemonkids.kidtask.ui.theme.Strawberry
import com.lemonkids.kidtask.ui.theme.StrawberrySoft

private val CalmGreen = Color(0xFF20B271)
private val WarmGold = Color(0xFFD9A400)
private val AlertRed = Color(0xFFF2564F)
private val OceanBlue = Color(0xFF1A8FD1)
private val RingTrack = Color(0xFFEAF0F6)
private val PanelBackground = Color(0xFFFFFDF7)

@Composable
fun PomodoroOverlay(
    state: PomodoroUiState,
    onClose: () -> Unit,
    onStart: () -> Unit,
    onNextRound: () -> Unit,
    onStopAlert: () -> Unit,
    onEndCycle: () -> Unit,
    onSaveSettings: (focusMinutes: Int, breakMinutes: Int) -> Unit,
    onRefresh: () -> Unit
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showEndConfirm by rememberSaveable { mutableStateOf(false) }
    val canOpenSettings = state.phase == PomodoroPhase.SETUP || state.phase == PomodoroPhase.WAITING
    val requestClose = {
        if (state.alert != PomodoroAlert.NONE) onStopAlert()
        onClose()
    }

    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0x660E1B2A))
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color.White,
                shadowElevation = 20.dp
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
                    PomodoroHeader(
                        state = state,
                        canOpenSettings = canOpenSettings,
                        onOpenSettings = { showSettings = true },
                        onClose = requestClose,
                        onRefresh = onRefresh
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        PomodoroBody(
                            state = state,
                            onStart = onStart,
                            onNextRound = onNextRound,
                            onStopAlert = onStopAlert,
                            onEndCycle = { showEndConfirm = true }
                        )
                        if (showSettings) {
                            DurationSettingsPanel(
                                initialFocus = state.focusMinutes,
                                initialBreak = state.breakMinutes,
                                onDone = { focus, rest ->
                                    onSaveSettings(focus, rest)
                                    showSettings = false
                                },
                                onCancel = { showSettings = false }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("结束这一轮？") },
            text = { Text("结束后会停止计时和提醒，未完成的专注不会计入番茄数。已完成的番茄会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    showEndConfirm = false
                    onEndCycle()
                }) { Text("结束本轮", color = AlertRed) }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) { Text("继续计时") }
            }
        )
    }
}

@Composable
private fun PomodoroHeader(
    state: PomodoroUiState,
    canOpenSettings: Boolean,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = StrawberrySoft) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Text("🍅", fontSize = 26.sp)
            }
        }
        Column {
            Text("番茄钟", color = SlateInk, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text("一次专注，一点成长", color = SlateMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        Surface(shape = CircleShape, color = StrawberrySoft) {
            Text(
                "今日收获 🍅 ${state.todayCount?.let { "$it 个" } ?: "暂无"}",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color(0xFF9F1239),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        SyncLabel(state, onRefresh)
        OutlinedButton(
            onClick = onOpenSettings,
            enabled = canOpenSettings,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFDCE6F2))
        ) {
            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("时长设置", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "关闭番茄钟，计时继续", tint = SlateInk)
        }
    }
}

@Composable
private fun SyncLabel(state: PomodoroUiState, onRefresh: () -> Unit) {
    val text = when {
        state.isRefreshingCounts -> "同步中…"
        state.syncError != null -> state.syncError
        state.pendingCount > 0 -> "含 ${state.pendingCount} 个待同步"
        state.countsFetched -> "已同步"
        else -> "读取中…"
    }
    val color = when {
        state.syncError != null -> AlertRed
        state.pendingCount > 0 -> Color(0xFFA16207)
        else -> SlateMuted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(enabled = !state.isRefreshingCounts, role = Role.Button, onClick = onRefresh)
            .semantics { contentDescription = "番茄记录同步状态：$text，点击刷新" }
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 190.dp))
        if (state.syncError != null) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun PomodoroBody(
    state: PomodoroUiState,
    onStart: () -> Unit,
    onNextRound: () -> Unit,
    onStopAlert: () -> Unit,
    onEndCycle: () -> Unit
) {
    val fraction = fractionFor(state)
    val band = bandFor(state, fraction)
    val targetColor = when (band) {
        PomodoroBand.CALM_GREEN -> CalmGreen
        PomodoroBand.WARM_GOLD -> WarmGold
        PomodoroBand.ALERT_RED -> AlertRed
        PomodoroBand.OCEAN_BLUE -> OceanBlue
    }
    val ringColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(250))
    val phaseLabel = when (state.phase) {
        PomodoroPhase.SETUP -> "准备开始"
        PomodoroPhase.FOCUS -> "任务时间"
        PomodoroPhase.BREAK -> "休息时间"
        PomodoroPhase.WAITING -> "休息完成"
    }
    val description = when (state.phase) {
        PomodoroPhase.SETUP -> "准备好后开始一段 ${state.focusMinutes} 分钟专注，结束后自动进入 ${state.breakMinutes} 分钟休息。"
        PomodoroPhase.FOCUS -> focusHint(band)
        PomodoroPhase.BREAK -> "休息一下，结束后需要你手动开始下一轮。"
        PomodoroPhase.WAITING -> "准备好再开始下一轮，番茄数已经记下啦。"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val ringSize: Dp = minOf(maxWidth * 0.46f, maxHeight * 0.92f).coerceIn(190.dp, 430.dp)
            key(state.sessionId, state.phase) {
                val animatedFraction by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(durationMillis = 450, easing = LinearEasing)
                )
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(ringSize)) {
                        val strokeWidth = size.minDimension * 0.062f
                        drawArc(
                            color = RingTrack,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - strokeWidth,
                                size.height - strokeWidth
                            ),
                            style = Stroke(width = strokeWidth)
                        )
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedFraction.coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - strokeWidth,
                                size.height - strokeWidth
                            ),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            phaseLabel,
                            color = SlateMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                        Text(
                            PomodoroRules.formatRemaining(state.remainingMillis),
                            color = ringColor,
                            fontSize = (ringSize.value * 0.18f).sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "专注 ${state.focusMinutes} 分 · 休息 ${state.breakMinutes} 分",
                            color = SlateMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        Text(description, color = SlateMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (state.storageError != null) {
            Text(state.storageError, color = AlertRed, fontSize = 12.sp)
        }
        if (state.alert != PomodoroAlert.NONE) {
            AlertBar(state.alert, onStopAlert)
        }
        ActionRow(state, onStart, onNextRound, onEndCycle)
    }
}

@Composable
private fun AlertBar(alert: PomodoroAlert, onStop: () -> Unit) {
    val text = when (alert) {
        PomodoroAlert.FOCUS_END -> "提醒中 · 专注完成，已自动开始休息"
        PomodoroAlert.BREAK_END -> "提醒中 · 休息结束，准备好再出发"
        PomodoroAlert.NONE -> ""
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFF1F2),
        border = BorderStroke(1.dp, Color(0xFFFECDD3))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🔔", fontSize = 22.sp)
                Text(text, modifier = Modifier.weight(1f), color = Color(0xFF9F1239), fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold)
            }
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE84A5F))
            ) {
                Text("停止响铃和震动", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ActionRow(
    state: PomodoroUiState,
    onStart: () -> Unit,
    onNextRound: () -> Unit,
    onEndCycle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state.phase) {
            PomodoroPhase.SETUP -> Button(
                onClick = onStart,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lemon)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = SlateInk)
                Spacer(Modifier.width(8.dp))
                Text("开始专注", color = SlateInk, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }

            PomodoroPhase.FOCUS, PomodoroPhase.BREAK -> OutlinedButton(
                onClick = onEndCycle,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Text("结束本轮", color = SlateMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            PomodoroPhase.WAITING -> Button(
                onClick = onNextRound,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lemon)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = SlateInk)
                Spacer(Modifier.width(8.dp))
                Text("下一轮", color = SlateInk, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun DurationSettingsPanel(
    initialFocus: Int,
    initialBreak: Int,
    onDone: (Int, Int) -> Unit,
    onCancel: () -> Unit
) {
    var focus by remember(initialFocus) { mutableStateOf(initialFocus) }
    var rest by remember(initialBreak) { mutableStateOf(initialBreak) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = PanelBackground,
        border = BorderStroke(1.dp, Color(0xFFF1E7CE)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("时长设置", color = SlateInk, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text("下一轮开始前都可以调整，当前轮不会被修改。", color = SlateMuted, fontSize = 12.sp)
                }
                TextButton(onClick = onCancel) { Text("收起") }
            }
            DurationGroup("专注时间", PomodoroRules.FOCUS_MINUTES, focus) { focus = it }
            DurationGroup("休息时间", PomodoroRules.BREAK_MINUTES, rest) { rest = it }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { onDone(focus, rest) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Lemon)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = SlateInk)
                    Spacer(Modifier.width(6.dp))
                    Text("完成设置", color = SlateInk, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun DurationGroup(
    title: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = SlateInk, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        options.chunked(4).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                rowOptions.forEach { minutes ->
                    DurationChip(minutes, minutes == selected) { onSelect(minutes) }
                }
            }
        }
    }
}

@Composable
private fun DurationChip(minutes: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = "$minutes 分钟${if (selected) "，已选中" else ""}"
            },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Lemon else Color.White,
        border = BorderStroke(1.dp, if (selected) Color(0xFFEAB308) else Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = SlateInk,
                modifier = Modifier.size(15.dp))
            Text("$minutes 分钟", color = SlateInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun fractionFor(state: PomodoroUiState): Float {
    val total = when (state.phase) {
        PomodoroPhase.FOCUS -> state.focusMinutes * PomodoroRules.MINUTE_MILLIS
        PomodoroPhase.BREAK -> state.breakMinutes * PomodoroRules.MINUTE_MILLIS
        PomodoroPhase.WAITING -> return 0f
        else -> return 1f
    }
    if (total <= 0L) return 1f
    return (state.remainingMillis.toFloat() / total).coerceIn(0f, 1f)
}

private fun bandFor(state: PomodoroUiState, fraction: Float): PomodoroBand = when {
    state.phase != PomodoroPhase.FOCUS -> PomodoroBand.OCEAN_BLUE
    fraction > 0.5f -> PomodoroBand.CALM_GREEN
    fraction > 0.25f -> PomodoroBand.WARM_GOLD
    else -> PomodoroBand.ALERT_RED
}

private fun focusHint(band: PomodoroBand): String = when (band) {
    PomodoroBand.CALM_GREEN -> "保持舒缓的节奏，专注当下这件事。"
    PomodoroBand.WARM_GOLD -> "时间过半啦，检查一下进度，继续加油。"
    PomodoroBand.ALERT_RED -> "快完成啦，坚持一下！"
    PomodoroBand.OCEAN_BLUE -> "专注进行中。"
}
