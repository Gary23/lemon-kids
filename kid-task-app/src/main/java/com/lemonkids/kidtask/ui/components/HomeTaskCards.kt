package com.lemonkids.kidtask.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.Strawberry

private val HomeCardBorder = Color(0xFFDCE9FF)
private val HomeDoneBackground = Color(0xFFEFF4FF)
private val HomeSpeechBackground = Color(0xFFDCE9FF)
private val HomeStarBackground = Color(0xFFFFE083)
private val HomeInk = Color(0xFF0D1C2E)
private val HomeMuted = Color(0xFF4D4632)
private val HomeDoneGreen = Color(0xFF6BFF8F)

@Composable
internal fun HomeTaskCard(
    task: TaskUiItem,
    isPlaying: Boolean,
    categoryColor: Color,
    isDone: Boolean,
    isExpired: Boolean,
    onSpeak: () -> Unit,
    onMarkDone: (String) -> Unit,
    onUndo: (String) -> Unit,
    actionsEnabled: Boolean = true
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isDone) HomeDoneBackground else Color.White,
        border = BorderStroke(1.dp, HomeCardBorder.copy(alpha = 0.7f)),
        shadowElevation = if (isDone) 0.dp else 2.dp
    ) {
        if (isDone) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = CircleShape, color = HomeDoneGreen, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF007432), modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(task.title, color = HomeInk.copy(alpha = 0.75f), fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.LineThrough,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("获得 ${task.rewardPoints} 颗星星", color = Color(0xFF006E2F),
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("撤销", modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { onUndo(task.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp), color = HomeMuted.copy(alpha = 0.7f),
                    fontSize = 14.sp)
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                val stackAction = maxWidth < 340.dp
                if (stackAction) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PendingTaskInfo(task, categoryColor, isExpired, isPlaying, onSpeak, Modifier.fillMaxWidth(), actionsEnabled)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            HomeCompleteButton(actionsEnabled) { onMarkDone(task.id) }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PendingTaskInfo(task, categoryColor, isExpired, isPlaying, onSpeak, Modifier.weight(1f), actionsEnabled)
                        HomeCompleteButton(actionsEnabled) { onMarkDone(task.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingTaskInfo(
    task: TaskUiItem,
    categoryColor: Color,
    isExpired: Boolean,
    isPlaying: Boolean,
    onSpeak: () -> Unit,
    modifier: Modifier,
    actionsEnabled: Boolean
) {
    val categoryBackgroundColor = categoryColor
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(8.dp).height(54.dp).clip(CircleShape)
            .background(categoryBackgroundColor))
        Surface(shape = CircleShape, color = HomeSpeechBackground, modifier = Modifier.size(40.dp)) {
            Box(Modifier.clickable(enabled = actionsEnabled, role = Role.Button, onClick = onSpeak), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "朗读任务",
                    tint = if (isPlaying) Strawberry else Color(0xFF735C00), modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = CircleShape, color = categoryBackgroundColor,
                    modifier = Modifier.weight(1f, fill = false)) {
                    Text(task.category.ifBlank { "今日任务" }, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = HomeInk, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(shape = CircleShape, color = HomeStarBackground) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFF735C00), modifier = Modifier.size(12.dp))
                        Text("+${task.rewardPoints} 积分", color = HomeInk, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            Text(task.title, color = HomeInk, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (task.description.isNotBlank()) {
                Text(task.description, color = HomeMuted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (isExpired) {
                Text("已错过 · 仍可补做", color = Strawberry, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HomeCompleteButton(actionsEnabled: Boolean, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Lemon, shadowElevation = 3.dp,
        modifier = Modifier.clickable(enabled = actionsEnabled, role = Role.Button, onClick = onClick)) {
        Text("打卡", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            color = Color(0xFF231B00), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            maxLines = 1)
    }
}
