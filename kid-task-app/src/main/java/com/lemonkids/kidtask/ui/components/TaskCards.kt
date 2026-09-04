package com.lemonkids.kidtask.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.lemonkids.kidtask.ui.theme.Coral
import com.lemonkids.kidtask.ui.theme.CompletedTaskBackground
import com.lemonkids.kidtask.ui.theme.CompletedTaskBorder
import com.lemonkids.kidtask.ui.theme.InkBrown
import com.lemonkids.kidtask.ui.theme.Mint
import com.lemonkids.kidtask.ui.theme.MutedGray
import com.lemonkids.kidtask.ui.theme.Pink
import com.lemonkids.kidtask.ui.theme.PinkSoft
import com.lemonkids.kidtask.ui.theme.Sunny

/** 任务 UI 数据模型，首页和日历页共用 */
data class TaskUiItem(
    val id: String,
    val title: String,
    val description: String = "",
    val status: String,
    val category: String,
    val dueDate: String = "",
    val dueTime: String?,
    val rewardPoints: Int,
    val penaltyPoints: Int
)

/**
 * 任务卡片分发器 — 根据状态自动选择 Pending / Done / Expired 卡片
 * @param sectionColor 分组主色（Pink / Coral / Lavender）
 * @param softColor 分组淡色背景
 */
@Composable
fun TaskCard(
    task: TaskUiItem,
    isPlaying: Boolean,
    sectionColor: Color,
    softColor: Color,
    onSpeak: () -> Unit,
    onMarkDone: (String) -> Unit,
    onUndo: (String) -> Unit
) {
    val isDone = task.status == "DONE" || task.status == "VERIFIED"
    val isExpired = task.status == "EXPIRED" || task.status == "REJECTED"

    when {
        isDone -> DoneTaskCard(task = task, onUndo = onUndo)
        isExpired -> ExpiredTaskCard(task = task, sectionColor = sectionColor, onMarkDone = onMarkDone)
        else -> PendingTaskCard(
            task = task,
            isPlaying = isPlaying,
            sectionColor = sectionColor,
            softColor = softColor,
            onSpeak = onSpeak,
            onMarkDone = onMarkDone
        )
    }
}

/** 待完成任务卡片 — 喇叭朗读 + 标题/描述/积分/截止时间 + 我做完啦按钮 */
@Composable
fun PendingTaskCard(
    task: TaskUiItem,
    isPlaying: Boolean,
    sectionColor: Color,
    softColor: Color,
    onSpeak: () -> Unit,
    onMarkDone: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = softColor,
        border = BorderStroke(1.dp, sectionColor.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧装饰条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(sectionColor, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            // 喇叭按钮
            Surface(
                shape = CircleShape,
                color = softColor,
                modifier = Modifier.size(44.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clickable(onClick = onSpeak)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "朗读任务",
                        tint = if (isPlaying) Coral else sectionColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // 任务信息
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = InkBrown)
                if (!task.description.isNullOrBlank()) {
                    Text(
                        task.description,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MutedGray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Sunny, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${task.rewardPoints} 积分", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Pink)
                    if (!task.dueTime.isNullOrEmpty()) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = MutedGray, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(task.dueTime, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MutedGray)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // 完成按钮
            Button(
                onClick = { onMarkDone(task.id) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = sectionColor),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("我做完啦！", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

/** 已完成任务卡片 — 绿色对勾 + 标题 + 积分 + 撤销 */
@Composable
fun DoneTaskCard(task: TaskUiItem, onUndo: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CompletedTaskBackground,
        border = BorderStroke(1.dp, CompletedTaskBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Mint,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = InkBrown)
                Text("✅ 已完成 · 得到 ${task.rewardPoints} 颗星星", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Mint)
            }
            Text(
                "撤销",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MutedGray,
                modifier = Modifier.clickable { onUndo(task.id) }
            )
        }
    }
}

/** 过期任务卡片 — 灰色时钟 + 标题 + 截止时间 + 补做啦 */
@Composable
fun ExpiredTaskCard(task: TaskUiItem, sectionColor: Color, onMarkDone: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF5F0EB)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MutedGray.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = MutedGray, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MutedGray)
                Text("⏰ 已错过 · 截止 ${task.dueTime ?: ""}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MutedGray)
            }
            Button(
                onClick = { onMarkDone(task.id) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral.copy(alpha = 0.9f)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("补做啦", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

/** 完成确认弹窗 */
@Composable
fun TaskConfirmDialog(
    onClose: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = PinkSoft.copy(alpha = 0.3f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🎉", fontSize = 36.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "完成啦？",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            Text(
                "做完了才能得到小星星哦～\n没做完的话先不要着急点",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onClose,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F0EB)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("还没有", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MutedGray)
                }
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("完成啦！", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    )
}

/** 撤销确认弹窗 */
@Composable
fun UndoConfirmDialog(
    onClose: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = {
            Text(
                "🔄 确定要撤销吗？",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                "撤销后任务会恢复为未完成状态，\n已获得的积分也会退回哦",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text("取消", color = MutedGray, fontSize = 18.sp)
                }
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("确认撤销", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    )
}
