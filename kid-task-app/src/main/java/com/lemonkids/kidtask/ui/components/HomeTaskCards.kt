package com.lemonkids.kidtask.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemonkids.kidtask.ui.theme.FreshMint
import com.lemonkids.kidtask.ui.theme.FreshMintSoft
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.LemonBorder
import com.lemonkids.kidtask.ui.theme.SkyBlueSoft
import com.lemonkids.kidtask.ui.theme.SlateInk
import com.lemonkids.kidtask.ui.theme.SlateMuted
import com.lemonkids.kidtask.ui.theme.Strawberry
import com.lemonkids.kidtask.ui.theme.TaskPanel

@Composable
internal fun HomeTaskCard(
    task: TaskUiItem,
    isPlaying: Boolean,
    categoryColor: Color,
    isDone: Boolean,
    isExpired: Boolean,
    onSpeak: () -> Unit,
    onMarkDone: (String) -> Unit,
    onUndo: (String) -> Unit
) {
    val cardColor = if (isDone) TaskPanel else Color.White
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = cardColor,
        border = BorderStroke(1.dp, if (isDone) FreshMintSoft else LemonBorder.copy(alpha = 0.65f)),
        shadowElevation = if (isDone) 0.dp else 3.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (isDone) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = FreshMint, modifier = Modifier.size(56.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(task.title, color = SlateInk, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("已完成 · 获得 ${task.rewardPoints} 颗星星 ⭐", color = FreshMint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Surface(
                        modifier = Modifier.size(width = 64.dp, height = 56.dp).clickable { onUndo(task.id) },
                        shape = CircleShape,
                        color = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("撤销", color = SlateMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PendingTaskInfo(task, categoryColor, isExpired, isPlaying, onSpeak, Modifier.weight(1f))
                    val actionLabel = if (isExpired) "补做任务" else "完成任务"
                    Surface(
                        modifier = Modifier.size(52.dp)
                            .semantics { contentDescription = actionLabel }
                            .clickable(role = Role.Button) { onMarkDone(task.id) },
                        shape = CircleShape,
                        color = Lemon,
                        border = BorderStroke(1.dp, LemonBorder),
                        shadowElevation = 3.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = SlateInk,
                                modifier = Modifier.size(26.dp))
                        }
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
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(6.dp).height(68.dp).background(categoryColor, CircleShape))
        Surface(shape = CircleShape, color = SkyBlueSoft, modifier = Modifier.size(56.dp)) {
            Box(modifier = Modifier.clickable(onClick = onSpeak), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "朗读任务", modifier = Modifier.size(23.dp),
                    tint = if (isPlaying) Strawberry else SlateInk)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(task.category.ifBlank { "今日任务" }, categoryColor.copy(alpha = 0.18f), SlateInk,
                    Modifier.weight(1f, fill = false))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Lemon, modifier = Modifier.size(16.dp))
                    Text("+${task.rewardPoints} 积分", color = SlateInk, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                }
                if (isExpired) StatusBadge("已错过", com.lemonkids.kidtask.ui.theme.StrawberrySoft, Strawberry)
            }
            Text(task.title, color = SlateInk, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (task.description.isNotBlank()) {
                Text(task.description, color = SlateMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
