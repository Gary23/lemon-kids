package com.lemonkids.kidliteracy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lemonkids.kidliteracy.feature.reading.GroupedWordResult
import com.lemonkids.kidliteracy.feature.reading.ReadingTarget
import com.lemonkids.kidliteracy.feature.reading.practiceProgressKey
import com.lemonkids.kidliteracy.ui.theme.LemonLiteracyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode

/** 与 StudyPracticeRowTest 共用 createComposeRule，不启动录音或评测 SDK。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h800dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GroupedWordPracticeRowTest {
    @get:Rule val compose = createComposeRule()

    private val targets = listOf("大树", "树林", "树叶").mapIndexed { index, word ->
        ReadingTarget("tree", "word", word, itemOrder = index)
    }

    @Test fun threeWordsShareOneActionAndKeepIndependentOrderedStars() {
        show(counts = listOf(1, 0, 1))
        targets.forEachIndexed { index, target ->
            compose.onNodeWithText(target.displayText).assertIsDisplayed()
            compose.onNodeWithContentDescription("词语：${target.displayText}")
                .assert(SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription, if (index == 1) "待练习" else "已通过"
                ))
            compose.onNodeWithTag("grouped-word-star-$index").assertTextEquals(if (index == 1) "☆" else "★")
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(1)
        compose.onNodeWithTag("practice-action").assertIsEnabled()
        compose.onNodeWithTag("practice-row").assertIsDisplayed()
        compose.onNodeWithTag("practice-reading", useUnmergedTree = true).assertIsDisplayed()
        val stars = targets.indices.map {
            compose.onNodeWithTag("grouped-word-star-$it").fetchSemanticsNode().boundsInRoot
        }
        assertTrue(stars.zipWithNext().all { (first, next) -> first.bottom <= next.top })
        compose.onNodeWithText("☆ 待练习").assertDoesNotExist()
        compose.onNodeWithText("★ 已通过").assertDoesNotExist()
    }

    @Test fun partiallyCompleteGroupCanStartRemainingWord() {
        var actions = 0
        show(counts = listOf(1, 0, 1), attempted = true, onAction = { actions++ })
        compose.onNodeWithText("开始").assertIsEnabled()
        compose.onNodeWithText("只读『树林』", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("practice-action").performClick()
        assertEquals(1, actions)
    }

    @Test fun completedCountsStillAllowStoppingAnActiveRecording() {
        var actions = 0
        show(counts = listOf(1, 1, 1), state = RecordingState.RECORDING, active = true,
            onAction = { actions++ })
        compose.onNodeWithText("读完").assertIsEnabled()
        compose.onNodeWithTag("practice-action").performClick()
        assertEquals(1, actions)
    }

    @Test fun fullyCompleteGroupHidesAction() {
        show(counts = listOf(1, 1, 1), state = RecordingState.FINISHED)
        compose.onNodeWithTag("practice-action").assertDoesNotExist()
        compose.onNodeWithText("开始").assertDoesNotExist()
        compose.onNodeWithTag("practice-message", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun preparingDisablesActionAndUsesExistingLabel() {
        show(state = RecordingState.PREPARING, active = true)
        compose.onNodeWithText("准备中…").assertIsNotEnabled()
    }

    @Test fun evaluatingDisablesActionAndUsesExistingLabel() {
        show(state = RecordingState.EVALUATING, active = true)
        compose.onNodeWithText("评测中…").assertIsNotEnabled()
    }

    @Test fun errorAllowsRetry() {
        show(state = RecordingState.ERROR)
        compose.onNodeWithText("重试").assertIsEnabled()
    }

    @Test fun anotherRecordingDisablesStarting() {
        show(otherRecordingActive = true)
        compose.onNodeWithText("开始").assertIsNotEnabled()
    }

    @Test fun longPressKeepsItsOwnWordAndCharacterCoordinates() {
        var clicked: Triple<ReadingTarget, Char, Int>? = null
        show(onLongPress = { target, character, index -> clicked = Triple(target, character, index) })
        compose.onNodeWithText("树林").performTouchInput {
            longClick(androidx.compose.ui.geometry.Offset(width * .25f, height * .5f))
        }
        compose.runOnIdle { assertEquals(Triple(targets[1], '树', 0), clicked) }
    }

    @Test fun recordingDisablesLongPressPointReading() {
        var helps = 0
        show(state = RecordingState.RECORDING, active = true, onLongPress = { _, _, _ -> helps++ })
        compose.onNodeWithText("树林").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(0, helps) }
    }

    @Test fun narrowLargeFontWrapsWordsWithoutOverflowOrOverlappingStars() {
        show(width = 320, fontScale = 1.5f)
        val row = compose.onNodeWithTag("practice-row").fetchSemanticsNode().boundsInRoot
        val reading = compose.onNodeWithTag("practice-reading", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val stars = compose.onNodeWithTag("practice-stars", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(stars.right < reading.left)
        val words = targets.indices.map { index ->
            compose.onNodeWithTag("grouped-word-reading-$index", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        words.forEach { word ->
            assertTrue(word.left >= reading.left && word.right <= reading.right)
            assertTrue(word.top >= reading.top && word.bottom <= reading.bottom)
        }
        assertTrue("窄屏大字号下词语应换行", words.last().top > words.first().top)
        targets.forEach { target ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(target.displayText).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { layout ->
                assertFalse(layout.didOverflowWidth)
                assertFalse(layout.didOverflowHeight)
            }
        }
        val action = compose.onNodeWithTag("practice-action").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(action.left >= row.left && action.right <= row.right)
        assertTrue(action.top >= reading.bottom)
    }

    @Test fun wideWordTextStartsAtSentenceLeftInset() = checkSentenceLeftInset(900, 1f)

    @Test fun narrowLargeFontWordTextStartsAtSentenceLeftInset() = checkSentenceLeftInset(460, 1.5f)

    private fun checkSentenceLeftInset(width: Int, fontScale: Float) {
        val sentence = "大树正在成长。"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LemonLiteracyTheme {
                    androidx.compose.foundation.layout.Column(Modifier.width(width.dp)) {
                        com.lemonkids.kidliteracy.ui.components.StudyPracticeRow(
                            completed = false, accent = com.lemonkids.kidliteracy.ui.theme.Sky,
                            contentPressed = false, actionLabel = "开始", actionEnabled = true,
                            onAction = {}, stars = { androidx.compose.material3.Text("☆") }, dots = {},
                            reading = { DialogCharacterText(sentence, "sentence", studyInline = true,
                                onLongPress = { _, _ -> }) }
                        )
                        GroupedWordPracticeRow(
                            items = targets.zip(listOf(0, 0, 0)), state = RecordingState.READY,
                            active = false, otherRecordingActive = false, attempted = false,
                            message = "", results = emptyMap(), highlighted = emptyMap(),
                            onAction = {}, onLongPress = { _, _, _ -> }
                        )
                    }
                }
            }
        }
        val sentenceBounds = compose.onNodeWithText(sentence).fetchSemanticsNode().boundsInRoot
        val firstWord = compose.onNodeWithText(targets.first().displayText).fetchSemanticsNode().boundsInRoot
        assertEquals("首词应与句子文本共用左内边距", sentenceBounds.left, firstWord.left, 1.5f)
    }

    private fun show(
        counts: List<Int> = listOf(0, 0, 0),
        state: RecordingState = RecordingState.READY,
        active: Boolean = false,
        otherRecordingActive: Boolean = false,
        attempted: Boolean = false,
        width: Int = 360,
        fontScale: Float = 1f,
        onAction: () -> Unit = {},
        onLongPress: (ReadingTarget, Char, Int) -> Unit = { _, _, _ -> }
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LemonLiteracyTheme {
                    Box(Modifier.width(width.dp)) {
                        GroupedWordPracticeRow(
                            items = targets.zip(counts), state = state, active = active,
                            otherRecordingActive = otherRecordingActive, attempted = attempted, message = "按顺序朗读",
                            results = mapOf(targets[1].practiceProgressKey() to GroupedWordResult(false, false, setOf(0))),
                            highlighted = emptyMap(), onAction = onAction, onLongPress = onLongPress
                        )
                    }
                }
            }
        }
    }
}
