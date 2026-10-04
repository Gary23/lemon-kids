package com.lemonkids.kidtask.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lemonkids.kidtask.ui.theme.Lemon
import com.lemonkids.kidtask.ui.theme.LemonBorder
import com.lemonkids.kidtask.ui.theme.SlateInk
import com.lemonkids.kidtask.ui.theme.Strawberry

/** 今日任务与日历任务共用的完成入口；过期任务仅改变读屏名称。 */
@Composable
internal fun TaskActionIconButton(
    density: TaskCardDensity,
    isExpired: Boolean,
    onClick: () -> Unit
) {
    val isCompact = density == TaskCardDensity.Compact
    val touchSize = if (isCompact) 48.dp else 52.dp
    val faceSize = if (isCompact) 40.dp else 52.dp
    val label = if (isExpired) "补做任务" else "完成任务"
    Box(
        modifier = Modifier.size(touchSize)
            .then(if (isCompact) Modifier else Modifier.shadow(3.dp, CircleShape))
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(faceSize),
            shape = CircleShape,
            color = Lemon,
            border = BorderStroke(1.dp, LemonBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = SlateInk,
                    modifier = Modifier.size(if (isCompact) 28.dp else 26.dp)
                )
            }
        }
    }
}

/** 回转箭头按设计预览绘制为真正的矢量路径，不依赖字体字符。 */
private val UndoArrow = ImageVector.Builder(
    name = "TaskUndoArrow",
    defaultWidth = 40.dp,
    defaultHeight = 40.dp,
    viewportWidth = 40f,
    viewportHeight = 40f
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = 4f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(11f, 5f)
        lineTo(2f, 14f)
        lineTo(11f, 23f)
        moveTo(3f, 14f)
        lineTo(27f, 14f)
        curveTo(34f, 14f, 38f, 18f, 38f, 25f)
        curveTo(38f, 32f, 33f, 36f, 26f, 36f)
        lineTo(17f, 36f)
    }
}.build()

@Composable
internal fun TaskUndoIconButton(
    density: TaskCardDensity,
    onClick: () -> Unit
) {
    val isCompact = density == TaskCardDensity.Compact
    val touchSize = if (isCompact) 48.dp else 52.dp
    val faceSize = if (isCompact) 40.dp else 52.dp
    Box(
        modifier = Modifier.size(touchSize)
            .clip(CircleShape)
            .semantics { contentDescription = "撤销完成" }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(faceSize),
            shape = CircleShape,
            color = Strawberry
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    UndoArrow,
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(if (isCompact) 22.dp else 26.dp)
                )
            }
        }
    }
}
