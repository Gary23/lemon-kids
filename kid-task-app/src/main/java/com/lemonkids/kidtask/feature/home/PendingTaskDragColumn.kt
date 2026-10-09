package com.lemonkids.kidtask.feature.home

import android.animation.ValueAnimator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lemonkids.kidtask.ui.components.StatusBadge
import com.lemonkids.kidtask.ui.components.TaskCard
import com.lemonkids.kidtask.ui.components.TaskUiItem
import com.lemonkids.kidtask.ui.components.stableTaskCategoryAppearance
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.LemonBorder
import com.lemonkids.kidtask.ui.theme.SlateInk
import com.lemonkids.kidtask.ui.theme.SlateMuted
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragPhase { Held, Dragging, Settling, AwaitingModel }

private data class DragSession(
    val id: Long,
    val draggedId: String,
    val originalOrder: List<String>,
    val slots: Map<String, DragSlot>,
    val startWindowY: Float,
    val pointerWindowY: Float,
    val fingerDistance: Float = 0f,
    val scrollDistance: Float = 0f,
    val previewOrder: List<String> = originalOrder,
    val phase: DragPhase = DragPhase.Held
) {
    val totalDistance: Float get() = fingerDistance + scrollDistance
}

@Composable
internal fun PendingTaskDragColumn(
    tasks: List<TaskUiItem>,
    categoryIds: Map<String, String>,
    categoryAppearances: Map<String, Pair<Color, String>>,
    playingTaskId: String?,
    syncingTaskIds: Set<String>,
    scrollState: androidx.compose.foundation.ScrollState,
    onSpeak: (TaskUiItem) -> Unit,
    onMarkDone: (String) -> Unit,
    onUndo: (String) -> Unit,
    onOrderChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val inputOrder = tasks.map { it.id }
    val taskById = tasks.associateBy { it.id }
    val measuredSlots = remember { mutableStateMapOf<String, DragSlot>() }
    val coordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    var nextSessionId by remember { mutableStateOf(0L) }
    var session by remember { mutableStateOf<DragSession?>(null) }
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val density = LocalDensity.current
    val spacingPx = with(density) { 14.dp.toPx() }
    val activationPx = with(density) { 8.dp.toPx() }
    val edgePx = with(density) { 80.dp.toPx() }

    // Keep the old fixed slots until the ViewModel publishes the committed order.
    val active = session?.takeUnless {
        it.phase == DragPhase.AwaitingModel && inputOrder == it.previewOrder
    }
    val renderOrder = active?.originalOrder ?: inputOrder

    LaunchedEffect(inputOrder, session?.phase) {
        measuredSlots.keys.filter { it !in inputOrder }.forEach { measuredSlots.remove(it) }
        coordinates.keys.removeAll { it !in inputOrder }
        val current = session ?: return@LaunchedEffect
        if (current.phase == DragPhase.AwaitingModel && inputOrder == current.previewOrder) {
            session = null
        } else if (current.phase != DragPhase.AwaitingModel && inputOrder != current.originalOrder) {
            session = null
        }
    }

    LaunchedEffect(session?.id, session?.phase) {
        val current = session ?: return@LaunchedEffect
        if (current.phase == DragPhase.Settling) {
            if (animationsEnabled) delay(210)
            if (session?.id != current.id || session?.phase != DragPhase.Settling) return@LaunchedEffect
            if (current.previewOrder == current.originalOrder) {
                session = null
            } else {
                session = current.copy(phase = DragPhase.AwaitingModel)
                onOrderChanged(current.previewOrder)
            }
        }
    }

    LaunchedEffect(session?.id, session?.phase) {
        while (true) {
            val current = session ?: break
            if (current.phase != DragPhase.Dragging) break
            val viewLocation = IntArray(2)
            view.getLocationInWindow(viewLocation)
            val delta = dragEdgeScrollDelta(true, current.pointerWindowY,
                viewLocation[1].toFloat(), viewLocation[1] + view.height.toFloat(), edgePx, 18f)
            if (delta != 0f) {
                val moved = scrollState.scrollBy(delta)
                val latest = session
                if (moved != 0f && latest?.id == current.id && latest.phase == DragPhase.Dragging) {
                    val updated = latest.copy(scrollDistance = latest.scrollDistance + moved)
                    val target = dragTargetIndex(updated.originalOrder, updated.draggedId,
                        updated.totalDistance, updated.slots)
                    session = updated.copy(previewOrder = dragPreviewOrder(updated.originalOrder,
                        updated.draggedId, target))
                }
            }
            delay(16)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(12.dp).height(28.dp).background(Lemon, CircleShape))
            Text("待完成任务", modifier = Modifier.weight(1f), color = SlateInk, fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold)
            StatusBadge("还有 ${tasks.size} 个", LemonBorder, SlateInk)
        }
        if (tasks.isEmpty()) {
            androidx.compose.material3.Surface(shape = RoundedCornerShape(26.dp), color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, LemonBorder.copy(alpha = 0.5f))) {
                Text("待办清单空空的，真棒！", modifier = Modifier.fillMaxWidth().padding(25.dp),
                    color = SlateMuted, fontSize = 14.sp)
            }
        }
        renderOrder.forEach { id ->
            val task = taskById[id] ?: return@forEach
            key(task.id) {
                val current = active
                val isDragged = current?.draggedId == task.id
                val offsets = current?.let {
                    dragSlotOffsets(it.originalOrder, it.previewOrder, it.slots, spacingPx)
                }.orEmpty()
                val targetOffset = offsets[task.id] ?: 0f
                val neighbourOffset = remember(current?.id, task.id) { Animatable(0f) }
                LaunchedEffect(current?.id, targetOffset) {
                    if (!isDragged) neighbourOffset.animateTo(targetOffset,
                        tween(if (animationsEnabled) 180 else 0))
                }
                val settleOffset = remember(current?.id, current?.phase, task.id) {
                    Animatable(if (current?.phase == DragPhase.Settling) current.totalDistance else targetOffset)
                }
                LaunchedEffect(current?.id, current?.phase, targetOffset) {
                    if (isDragged && current?.phase == DragPhase.Settling) {
                        settleOffset.animateTo(targetOffset, tween(if (animationsEnabled) 180 else 0))
                    }
                }
                val visualOffset = when {
                    !isDragged -> neighbourOffset.value
                    current?.phase == DragPhase.Settling -> settleOffset.value
                    current?.phase == DragPhase.AwaitingModel -> targetOffset
                    current?.phase == DragPhase.Dragging -> current.totalDistance
                    else -> 0f
                }
                val lift by animateFloatAsState(if (isDragged && animationsEnabled) 1.04f else 1f,
                    animationSpec = tween(170), label = "任务卡抬起")
                val outline by animateColorAsState(if (isDragged) Lemon else Color.Transparent,
                    animationSpec = tween(140), label = "任务卡描边")
                val wobble = remember(task.id, current?.id) { Animatable(0f) }
                LaunchedEffect(isDragged, animationsEnabled, current?.id) {
                    if (isDragged && animationsEnabled && current?.phase != DragPhase.AwaitingModel) {
                        while (true) {
                            wobble.animateTo(-1.3f, tween(180))
                            wobble.animateTo(1.3f, tween(180))
                        }
                    } else wobble.snapTo(0f)
                }
                val visualKey = task.sourceCategoryId ?: categoryIds[task.category] ?: task.category
                val (categoryColor, _) = categoryAppearances[visualKey]
                    ?: stableTaskCategoryAppearance(visualKey)
                Box(Modifier.fillMaxWidth().zIndex(if (isDragged) 1f else 0f)
                    .onGloballyPositioned { slot ->
                        coordinates[task.id] = slot
                        measuredSlots[task.id] = DragSlot(slot.positionInParent().y, slot.size.height.toFloat())
                    }
                    .semantics { contentDescription = "长按拖动排序：${task.title}" }
                    .pointerInput(task.id, inputOrder) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                val previous = session
                                if ((previous != null && !(previous.phase == DragPhase.AwaitingModel &&
                                        inputOrder == previous.previewOrder)) ||
                                    measuredSlots.keys.toSet() != inputOrder.toSet()) return@detectDragGesturesAfterLongPress
                                val slot = coordinates[task.id]?.takeIf { it.isAttached }
                                    ?: return@detectDragGesturesAfterLongPress
                                val startY = slot.localToWindow(position).y
                                nextSessionId += 1
                                session = DragSession(nextSessionId, task.id, inputOrder,
                                    inputOrder.associateWith { measuredSlots.getValue(it) }, startY, startY)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragCancel = { if (session?.draggedId == task.id) session = null },
                            onDragEnd = {
                                val currentSession = session
                                if (currentSession?.draggedId == task.id) {
                                    session = if (currentSession.phase == DragPhase.Dragging)
                                        currentSession.copy(phase = DragPhase.Settling) else null
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val currentSession = session
                                if (currentSession?.draggedId == task.id &&
                                    currentSession.phase != DragPhase.Settling &&
                                    currentSession.phase != DragPhase.AwaitingModel) {
                                    val windowY = coordinates[task.id]?.takeIf { it.isAttached }
                                        ?.localToWindow(change.position)?.y ?: currentSession.pointerWindowY
                                    val finger = windowY - currentSession.startWindowY
                                    val phase = if (currentSession.phase == DragPhase.Dragging || abs(finger) >= activationPx)
                                        DragPhase.Dragging else DragPhase.Held
                                    val updated = currentSession.copy(pointerWindowY = windowY,
                                        fingerDistance = finger, phase = phase)
                                    val target = if (phase == DragPhase.Dragging)
                                        dragTargetIndex(updated.originalOrder, task.id,
                                            updated.totalDistance, updated.slots)
                                    else updated.originalOrder.indexOf(task.id)
                                    session = updated.copy(previewOrder = dragPreviewOrder(
                                        updated.originalOrder, task.id, target))
                                }
                            }
                        )
                    }) {
                    Column(Modifier.fillMaxWidth()
                        .graphicsLayer {
                            translationY = visualOffset
                            scaleX = lift
                            scaleY = lift
                            rotationZ = wobble.value
                            shadowElevation = if (isDragged) 16.dp.toPx() else 0f
                            shape = RoundedCornerShape(20.dp)
                        }
                        .testTag("pending-task-${task.id}")
                        .border(2.dp, outline, RoundedCornerShape(20.dp))) {
                        TaskCard(task, playingTaskId == task.id, categoryColor,
                            categoryColor.copy(alpha = 0.12f), actionsEnabled = !isDragged,
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
}
