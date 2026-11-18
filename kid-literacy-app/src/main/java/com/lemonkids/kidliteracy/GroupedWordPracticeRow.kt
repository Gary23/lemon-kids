package com.lemonkids.kidliteracy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemonkids.kidliteracy.feature.reading.GroupedWordResult
import com.lemonkids.kidliteracy.feature.reading.ReadingTarget
import com.lemonkids.kidliteracy.feature.reading.practiceProgressKey
import com.lemonkids.kidliteracy.feature.reading.requiredCorrectReadings
import com.lemonkids.kidliteracy.ui.components.StudyPracticeRow
import com.lemonkids.kidliteracy.ui.theme.Coral
import com.lemonkids.kidliteracy.ui.theme.Leaf
import com.lemonkids.kidliteracy.ui.theme.Sky

/** 只映射已有练习状态；录音、逐词计分和补读选择仍由调用方负责。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupedWordPracticeRow(
    items: List<Pair<ReadingTarget, Int>>,
    state: RecordingState,
    active: Boolean,
    otherRecordingActive: Boolean,
    attempted: Boolean,
    message: String,
    results: Map<String, GroupedWordResult>,
    highlighted: Map<String, Int>,
    onAction: () -> Unit,
    onLongPress: (ReadingTarget, Char, Int) -> Unit
) {
    if (items.isEmpty()) return
    val pending = items.filter { (target, count) -> count < target.requiredCorrectReadings() }
    val recording = active && state == RecordingState.RECORDING
    val busy = active && state in listOf(
        RecordingState.PREPARING, RecordingState.RECORDING, RecordingState.EVALUATING
    )
    // 即使计数在录音中已同步满，也必须保留停止入口。
    val completed = pending.isEmpty() && state != RecordingState.RECORDING
    val pointReadEnabled = !busy && !otherRecordingActive
    var pressedKey by remember(items.map { it.first.practiceProgressKey() }) { mutableStateOf<String?>(null) }
    val partialHint = pending.firstOrNull()?.takeIf {
        attempted || pending.size < items.size
    }?.let { "只读『${it.first.displayText}』" }
    val displayMessage = when {
        completed -> null
        partialHint != null && !busy -> listOf(partialHint, message).filter { it.isNotBlank() }.joinToString("；")
        state == RecordingState.READY && !busy -> null
        else -> message
    }
    StudyPracticeRow(
        completed = completed,
        accent = when {
            completed -> Leaf
            recording -> Coral
            else -> Sky
        },
        contentPressed = pointReadEnabled && pressedKey != null,
        actionLabel = when {
            recording -> "读完"
            active && state == RecordingState.PREPARING -> "准备中…"
            active && state == RecordingState.EVALUATING -> "评测中…"
            state == RecordingState.ERROR -> "重试"
            else -> "开始"
        },
        actionEnabled = recording || (pending.isNotEmpty() && (!otherRecordingActive || active) && !busy),
        message = displayMessage,
        isError = state == RecordingState.ERROR,
        onAction = onAction,
        stars = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                items.forEachIndexed { index, (target, count) ->
                    val required = target.requiredCorrectReadings()
                    val complete = count >= required
                    Column(
                        Modifier.testTag("grouped-word-star-$index").semantics(mergeDescendants = true) {
                            contentDescription = "词语：${target.displayText}"
                            stateDescription = if (complete) "已通过" else "待练习"
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy((-2).dp)
                    ) {
                        // 与 StudyStarRail 保持相同的空星/金星、字号及行距。
                        repeat(required) { star ->
                            Text(
                                if (star < count) "★" else "☆",
                                color = if (star < count) Color(0xFFFFC83D) else Color(0xFF86A7D5),
                                fontSize = 21.sp,
                                lineHeight = 21.sp
                            )
                        }
                    }
                }
            }
        },
        reading = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Match DialogCharacterText's sentence inset: 5dp padding plus a centered
                // 92%-wide text block. Each word already contributes its own 5dp padding.
                val sentenceInset = ((maxWidth - 10.dp) * .04f).coerceAtLeast(0.dp)
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = sentenceInset, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items.forEachIndexed { index, (target, _) ->
                        val key = target.practiceProgressKey()
                        // DialogReadingContent 自带 fillMaxWidth；独立本征宽度才能横排而非每词占满一行。
                        Column(Modifier.width(IntrinsicSize.Max).testTag("grouped-word-reading-$index")) {
                            DialogReadingContent(
                                target = target,
                                enabled = pointReadEnabled,
                                studyInline = true,
                                wrongCharacterIndexes = results[key]?.wrongCharacterIndexes.orEmpty(),
                                pointReadCharacterIndex = highlighted[key],
                                onPressChanged = { pressed ->
                                    if (pressed) pressedKey = key
                                    else if (pressedKey == key) pressedKey = null
                                },
                                onLongPress = { character, characterIndex ->
                                    if (pointReadEnabled) onLongPress(target, character, characterIndex)
                                }
                            )
                        }
                    }
                }
            }
        },
        dots = {}
    )
}
