package com.lemonkids.kidliteracy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemonkids.kidliteracy.ui.theme.*

/** 未开始和 SDK 会话共用的几何结构；状态仅改变文字、色彩和语义。 */
@Composable
internal fun StudyPracticeRow(
    completed: Boolean, accent: Color, actionAccent: Color = accent, contentPressed: Boolean,
    actionLabel: String, actionEnabled: Boolean, message: String? = null, isError: Boolean = false,
    onAction: () -> Unit, stars: @Composable () -> Unit, reading: @Composable () -> Unit,
    dots: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scale = density.fontScale.coerceAtLeast(1f)
    val actionWidth = 82.dp * scale
    val controlsWidth = 172.dp * scale
    val actionHeight = 52.dp * scale
    val messageHeight = with(density) { 20.sp.toDp() * 2 }.coerceAtLeast(48.dp)
    var showDetails by remember { mutableStateOf(false) }
    // 两种调用路径共享同一边框厚度、padding、正文容器及提示高度。
    Surface(Modifier.fillMaxWidth().testTag("practice-row"), shape = RoundedCornerShape(22.dp), color = Background,
        border = BorderStroke(2.dp, accent.copy(alpha = .72f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth.value / scale < 540f
                val contentPanel: @Composable (Modifier) -> Unit = { modifier ->
                    Surface(modifier.testTag("practice-content"), shape = RoundedCornerShape(16.dp),
                        color = when { contentPressed -> SkyLight; completed -> LeafLight; else -> Color.White },
                        border = BorderStroke(2.dp, if (contentPressed) Sky else accent.copy(alpha = .5f))) {
                        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(32.dp * scale).testTag("practice-stars"), contentAlignment = Alignment.Center) { stars() }
                            Box(Modifier.weight(1f).testTag("practice-reading")) { reading() }
                        }
                    }
                }
                val controls: @Composable () -> Unit = {
                    Row(Modifier.width(controlsWidth).height(actionHeight).testTag("practice-controls"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                        Box(Modifier.width(80.dp * scale), contentAlignment = Alignment.Center) { if (!completed) dots() }
                        Box(Modifier.width(actionWidth).height(actionHeight).testTag("practice-action-slot")) {
                            if (!completed) GardenActionButton(onAction, modifier = Modifier.fillMaxSize().testTag("practice-action"),
                                enabled = actionEnabled, colors = ButtonDefaults.buttonColors(containerColor = actionAccent),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)) {
                                Text(actionLabel, maxLines = 1)
                            }
                        }
                    }
                }
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    contentPanel(Modifier.fillMaxWidth())
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { controls() }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    contentPanel(Modifier.weight(1f))
                    controls()
                }
            }
            Row(Modifier.fillMaxWidth().height(messageHeight).testTag("practice-message"), verticalAlignment = Alignment.CenterVertically) {
                Text(message.orEmpty(), color = if (isError) EvaluationErrorRed else Color(0xFF687D9F),
                    fontSize = 16.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(Modifier.size(48.dp)) {
                    if (!message.isNullOrBlank() && isError) IconButton(onClick = { showDetails = true }) {
                        Icon(Icons.Default.Info, "查看完整提示", tint = EvaluationErrorRed)
                    }
                }
            }
        }
    }
    if (showDetails) GardenDialog(onDismissRequest = { showDetails = false }) {
        GardenPanel {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message.orEmpty(), color = Ink)
                GardenActionButton(onClick = { showDetails = false }) { Text("知道了") }
            }
        }
    }
}
