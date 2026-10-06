package com.lemonkids.kidtask.feature.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import com.lemonkids.kidtask.ui.components.TaskUiItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PendingTaskDragColumnTest {
    @get:Rule val rule = createComposeRule()

    private fun task(id: String) = TaskUiItem(id, "任务 $id", status = "PENDING",
        category = "阅读", dueTime = null, rewardPoints = 1, penaltyPoints = 0)

    private fun visualY(id: String): Float = rule.onNodeWithTag("pending-task-$id")
        .fetchSemanticsNode().boundsInRoot.center.y

    private fun drag(from: String, to: String) {
        val fromY = visualY(from)
        val toY = visualY(to)
        rule.onNodeWithTag("pending-task-$from").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, toY - fromY + if (toY > fromY) 12f else -12f))
            advanceEventTime(250)
            up()
        }
        rule.waitForIdle()
    }

    @Test fun repeatedSwapThenStationaryHoldKeepsEveryCardInItsSlot() {
        var order by mutableStateOf(listOf("a", "b", "c"))
        val commits = mutableListOf<List<String>>()
        rule.setContent {
            val scrollState = rememberScrollState()
            Column(Modifier.verticalScroll(scrollState)) {
                PendingTaskDragColumn(order.map(::task), emptyMap(), null, emptySet(), scrollState,
                    onSpeak = {}, onMarkDone = {}, onUndo = {}, onOrderChanged = {
                        commits += it
                        order = it
                    })
            }
        }
        rule.waitForIdle()
        drag("a", "b")
        rule.waitUntil(3000) { commits.size == 1 }
        assertEquals(listOf("b", "a", "c"), order)
        drag("b", "a")
        rule.waitUntil(3000) { commits.size == 2 }
        assertEquals(listOf("a", "b", "c"), order)

        val thirdBeforeDrag = visualY("c")
        val firstBeforeDrag = visualY("a")
        val secondBeforeDrag = visualY("b")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("pending-task-a").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, secondBeforeDrag - firstBeforeDrag + 12f))
            advanceEventTime(250)
        }
        rule.mainClock.advanceTimeBy(250)
        rule.mainClock.advanceTimeBy(250)
        assertTrue(kotlin.math.abs(visualY("c") - thirdBeforeDrag) < 2f)
        assertTrue("b=${visualY("b")}, expected=$firstBeforeDrag, a=${visualY("a")}",
            kotlin.math.abs(visualY("b") - firstBeforeDrag) < 4f)
        rule.onNodeWithTag("pending-task-a").performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitUntil(3000) { commits.size == 3 }
        assertEquals(listOf("b", "a", "c"), order)

        val firstY = visualY("b")
        val secondY = visualY("a")
        val thirdY = visualY("c")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("pending-task-b").performTouchInput {
            down(center)
            advanceEventTime(700)
        }
        rule.mainClock.advanceTimeBy(200)
        assertTrue(kotlin.math.abs(visualY("b") - firstY) < 2f)
        assertTrue(kotlin.math.abs(visualY("a") - secondY) < 2f)
        assertTrue(kotlin.math.abs(visualY("c") - thirdY) < 2f)
        rule.onNodeWithTag("pending-task-b").performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(3, commits.size)
    }
}
