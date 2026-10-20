package com.lemonkids.kidliteracy.ui.components

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lemonkids.kidliteracy.DialogCharacterText
import com.lemonkids.kidliteracy.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h800dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudyPracticeRowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun standardStateTransitionsKeepAllBoundsAndScrollOffset() = checkTransitions(900, 1f)
    @Test fun narrowLargeFontTransitionsKeepAllBoundsAndScrollOffset() = checkTransitions(460, 1.5f)

    private fun checkTransitions(width: Int, scale: Float) {
        var phase by mutableIntStateOf(0)
        lateinit var list: LazyListState
        var actions = 0
        var minimumGapPx = 0f
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                minimumGapPx = with(LocalDensity.current) { 8.dp.toPx() }
                LemonLiteracyTheme {
                    list = rememberLazyListState()
                    LazyColumn(state = list, modifier = Modifier.width(width.dp).height(520.dp)) {
                        item { Spacer(Modifier.height(240.dp)) }
                        item(key = "practice") {
                            StudyPracticeRow(completed = phase == 5,
                                accent = when (phase) { 2 -> Coral; 5 -> Leaf; else -> Sky },
                                contentPressed = false, actionLabel = if (phase == 2) "读完" else if (phase == 4) "重试" else "开始",
                                actionEnabled = phase != 1 && phase != 3,
                                message = when (phase) { 1 -> "正在准备朗读…"; 2 -> "正在听你读…"; 3 -> "正在生成评测结果…"; 4 -> "网络连接暂时中断，请检查网络后重试。这是一条较长的提示。"; else -> null },
                                isError = phase == 4, onAction = { actions++ },
                                stars = { Column { repeat(2) { Text(if (phase == 5) "★" else "☆", fontSize = 24.sp, lineHeight = 24.sp) } } },
                                reading = { DialogCharacterText("今天的花园里开满了美丽的小花，我们一起认真读一读。", "sentence",
                                    wrongCharacterIndexes = if (phase == 4) setOf(2, 5) else emptySet(), onLongPress = { _, _ -> }) },
                                dots = { Text("• • • • •") })
                        }
                        item { Spacer(Modifier.height(400.dp)) }
                    }
                }
            }
        }
        compose.onNodeWithTag("practice-row").performScrollTo()
        compose.waitForIdle()
        val tags = listOf("practice-content", "practice-stars", "practice-reading", "practice-controls", "practice-action-slot")
        compose.onNodeWithTag("practice-message", useUnmergedTree = true).assertDoesNotExist()
        val bounds = tags.associateWith { compose.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
        assertTrue("星星框与文字框应留有间距", bounds.getValue("practice-reading").left - bounds.getValue("practice-stars").right >= minimumGapPx)
        val index = list.firstVisibleItemIndex
        val offset = list.firstVisibleItemScrollOffset
        compose.onNodeWithTag("practice-action").performClick()
        assertEquals(1, actions)
        for (next in 1..5) {
            compose.runOnIdle { phase = next }
            compose.waitForIdle()
            tags.forEach { tag ->
                assertEquals("phase=$next tag=$tag", bounds.getValue(tag), compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
            }
            val stars = compose.onNodeWithTag("practice-stars", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val reading = compose.onNodeWithTag("practice-reading", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("phase=$next 星星框与文字框应留有间距", reading.left - stars.right >= minimumGapPx)
            if (next == 5) compose.onNodeWithTag("practice-message", useUnmergedTree = true).assertDoesNotExist()
            else compose.onNodeWithTag("practice-message", useUnmergedTree = true).assertExists()
            assertEquals("phase=$next list index", index, list.firstVisibleItemIndex)
            assertEquals("phase=$next list offset", offset, list.firstVisibleItemScrollOffset)
            if (next == 1 || next == 3) {
                compose.onNodeWithText("开始").assertIsNotEnabled()
                compose.onNodeWithTag("practice-action").performClick()
                assertEquals(1, actions)
            }
        }
        compose.onNodeWithTag("practice-action").assertDoesNotExist()
        compose.onNodeWithText("开始").assertDoesNotExist()
        assertEquals(1, actions)
    }
}
