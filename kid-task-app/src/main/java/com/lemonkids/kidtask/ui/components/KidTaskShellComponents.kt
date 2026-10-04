package com.lemonkids.kidtask.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.lemonkids.kidtask.ui.theme.FreshMint
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.LemonBorder
import com.lemonkids.kidtask.ui.theme.LemonShadow
import com.lemonkids.kidtask.ui.theme.SlateInk
import com.lemonkids.kidtask.ui.theme.SlateMuted
import com.lemonkids.kidtask.ui.theme.Strawberry
import com.lemonkids.kidtask.ui.theme.StrawberrySoft

@Composable
fun ChildSummary(userName: String, hasUser: Boolean, points: Int?, pointsUnavailable: Boolean, streakDays: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = Lemon, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("🍋", fontSize = 23.sp) }
            }
            Column(Modifier.weight(1f)) {
                Text(childNameLabel(userName, hasUser), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                    color = SlateInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("今天也要加油呀", fontSize = 12.sp, color = SlateMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryBadge(Icons.Filled.LocalFireDepartment, "连续 $streakDays 天", Strawberry, StrawberrySoft, Modifier.fillMaxWidth())
            val pointsLabel = points?.let { "$it 积分" } ?: if (pointsUnavailable) "积分暂不可用" else "积分读取中"
            SummaryBadge(Icons.Filled.Star, pointsLabel, LemonShadow, LemonBorder, Modifier.fillMaxWidth())
        }
    }
}

fun childNameLabel(userName: String, hasUser: Boolean): String = when {
    !hasUser -> "姓名加载中"
    userName.isBlank() -> "未设置姓名"
    else -> userName.trim()
}

@Composable
fun SummaryBadge(icon: ImageVector, label: String, tint: Color, background: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = background) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateInk, maxLines = 1)
        }
    }
}

@Composable
fun StatusBadge(label: String, background: Color, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = background) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = color,
            fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LemonPressButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(modifier = modifier.height(60.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .offset(y = 4.dp)
                .background(LemonShadow, CircleShape)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .offset(y = if (pressed) 4.dp else 0.dp)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
            shape = CircleShape,
            color = Lemon,
            border = BorderStroke(1.dp, LemonBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(label, color = SlateInk, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
