package com.lemonkids.kidliteracy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemonkids.kidliteracy.ui.theme.*

internal val StudyErrorText = Color(0xFFFF492A)
internal val StudyErrorBorder = Color(0xFFFF6271)
private val StudyMessageText = Color(0xFF687D9F)

/** 未开始和 SDK 会话共用的几何结构；状态仅改变文字、色彩和语义。 */
@Composable
internal fun StudyPracticeRow(
    completed: Boolean, accent: Color, contentPressed: Boolean,
    actionLabel: String, actionEnabled: Boolean, message: String? = null, isError: Boolean = false,
    showHintBelowDots: Boolean = false,
    readingWidth: androidx.compose.ui.unit.Dp? = null,
    onAction: () -> Unit, stars: @Composable () -> Unit, reading: @Composable () -> Unit,
    dots: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scale = density.fontScale.coerceAtLeast(1f)
    val actionWidth = 92.dp * scale
    val dotsWidth = 152.dp
    val actionHeight = 48.dp * scale
    val messageHeight = with(density) { 18.sp.toDp() * 2 }.coerceAtLeast(48.dp)
    var showDetails by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().testTag("practice-row"), shape = RoundedCornerShape(20.dp),
        color = if (isError) Color(0xFFFFF8F6) else Color(0xFFF4F9FF),
        border = BorderStroke(1.5.dp, if (isError) StudyErrorBorder else accent.copy(alpha = .3f))) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth.value / scale < 520f
                val rowWidth = maxWidth
                val contentPanel: @Composable (Modifier) -> Unit = { modifier ->
                    Row(modifier.testTag("practice-content"), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFFAFCFF),
                            border = BorderStroke(1.dp, Sky.copy(alpha = .16f))) {
                            Box(Modifier.width(48.dp * scale).heightIn(min = if (readingWidth != null) 62.dp * scale else 48.dp * scale).testTag("practice-stars"),
                                contentAlignment = Alignment.Center) { stars() }
                        }
                        Box(Modifier.weight(1f).testTag("practice-reading"), contentAlignment = Alignment.Center) {
                            Surface(Modifier.then(if (readingWidth != null) Modifier.widthIn(max = readingWidth * scale) else Modifier.fillMaxWidth()),
                                shape = RoundedCornerShape(14.dp),
                                color = when { contentPressed -> SkyLight; completed -> LeafLight; else -> Color(0xFFFFFDF7) },
                                border = BorderStroke(1.dp, if (contentPressed) Sky else Color(0xFFF2E8D9))) {
                                reading()
                            }
                        }
                    }
                }
                val action: @Composable () -> Unit = {
                    Box(Modifier.width(actionWidth).height(actionHeight).testTag("practice-action-slot"),
                        contentAlignment = Alignment.Center) {
                        if (!completed) {
                            val redAction = actionLabel == "读完" || actionLabel == "重试"
                            val shape = RoundedCornerShape(50)
                            val fill = if (redAction) {
                                Brush.verticalGradient(listOf(Color(0xFFFF8B7E), Color(0xFFFF615C)))
                            } else {
                                Brush.verticalGradient(listOf(Color(0xFF52A2FE), Color(0xFF248AFF)))
                            }
                            val outline = if (redAction) Color(0xFFFF3D40) else Color(0xFF0878FF)
                            Box(Modifier.fillMaxSize()
                                .alpha(if (actionEnabled) 1f else .55f)
                                .background(fill, shape)
                                .border(1.dp, outline, shape)
                                .clickable(enabled = actionEnabled, role = Role.Button, onClick = onAction)
                                .testTag("practice-action"), contentAlignment = Alignment.Center) {
                            Text(actionLabel, color = Color.White,
                                maxLines = 1, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    contentPanel(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().testTag("practice-controls"),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(dotsWidth), contentAlignment = Alignment.Center) { if (!completed) dots() }
                        action()
                    }
                    if (readingWidth != null && !message.isNullOrBlank()) Row(Modifier.fillMaxWidth().height(messageHeight).testTag("practice-message"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (showHintBelowDots) Arrangement.End else Arrangement.Start) {
                        StudyPracticeMessage(message, isError, { showDetails = true }, Modifier.fillMaxWidth(), maxLines = 2)
                    }
                } else Box(Modifier.fillMaxWidth().heightIn(min = (if (readingWidth != null) 82.dp else 58.dp) * scale).testTag("practice-content")) {
                    val starWidth = 48.dp * scale
                    val readingStart = starWidth + 10.dp
                    val readingSlotWidth = minOf(
                        if (readingWidth == null) rowWidth * .44f else readingWidth * scale,
                        (rowWidth * .57f - 10.dp - readingStart).coerceAtLeast(0.dp)
                    )
                    Box(Modifier.align(Alignment.CenterStart)
                        .width(starWidth).heightIn(min = if (readingWidth != null) 62.dp * scale else 48.dp * scale).testTag("practice-stars"),
                        contentAlignment = Alignment.Center) {
                        Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFFAFCFF),
                            border = BorderStroke(1.dp, Sky.copy(alpha = .16f))) {
                            Box(Modifier.fillMaxWidth().heightIn(min = if (readingWidth != null) 62.dp * scale else 48.dp * scale), contentAlignment = Alignment.Center) { stars() }
                        }
                    }
                    Box(Modifier.align(Alignment.CenterStart).offset(x = readingStart)
                        .width(readingSlotWidth).testTag("practice-reading"), contentAlignment = Alignment.Center) {
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            color = when { contentPressed -> SkyLight; completed -> LeafLight; else -> Color(0xFFFFFDF7) },
                            border = BorderStroke(1.dp, if (contentPressed) Sky else Color(0xFFF2E8D9))) { reading() }
                    }
                    Box(Modifier.align(Alignment.CenterStart).offset(x = rowWidth * .57f,
                        y = if (showHintBelowDots) (-9).dp else 0.dp)
                        .width(dotsWidth).height(28.dp).testTag("practice-controls"),
                        contentAlignment = Alignment.Center) { if (!completed) dots() }
                    Box(Modifier.align(Alignment.CenterStart).offset(x = rowWidth * .86f)) { action() }
                    if (showHintBelowDots && !message.isNullOrBlank()) Box(
                        Modifier.align(Alignment.CenterStart).offset(x = rowWidth * .57f, y = 18.dp)
                            .width(dotsWidth).height(20.dp).testTag("practice-message"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(message, color = StudyMessageText, fontSize = 12.sp, lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (readingWidth != null && isError && !message.isNullOrBlank()) Box(
                        Modifier.align(Alignment.BottomStart).offset(x = rowWidth * .26f)
                            .width(rowWidth * .58f).height(22.dp).testTag("practice-message"),
                        contentAlignment = Alignment.CenterStart
                    ) { StudyPracticeMessage(message, true, { showDetails = true }, Modifier.fillMaxWidth(), maxLines = 1) }
                }
            }
            if (readingWidth == null && !message.isNullOrBlank()) Row(Modifier.fillMaxWidth().height(messageHeight).testTag("practice-message"), verticalAlignment = Alignment.CenterVertically) {
                StudyPracticeMessage(message, isError, { showDetails = true }, Modifier.fillMaxWidth(), maxLines = 2)
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

@Composable
private fun StudyPracticeMessage(message: String?, isError: Boolean, onDetails: () -> Unit,
    modifier: Modifier, maxLines: Int) {
    Row(modifier.then(if (isError && !message.isNullOrBlank()) Modifier.clickable(role = Role.Button, onClick = onDetails) else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isError && !message.isNullOrBlank()) Box(Modifier.size(17.dp).background(StudyErrorText, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center) {
            Text("!", color = Color.White, fontSize = 12.sp, lineHeight = 14.sp)
        }
        Text(message.orEmpty(), color = StudyMessageText,
            fontSize = if (maxLines == 1) 12.sp else 14.sp, lineHeight = 17.sp,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
