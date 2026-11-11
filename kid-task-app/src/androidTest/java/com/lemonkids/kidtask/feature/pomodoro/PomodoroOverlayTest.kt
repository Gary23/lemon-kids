package com.lemonkids.kidtask.feature.pomodoro

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PomodoroOverlayTest {
    @get:Rule val rule = createComposeRule()

    @Test fun closingWindowDoesNotStopActiveAlert() {
        var closes = 0
        var stops = 0
        rule.setContent {
            MaterialTheme {
                PomodoroOverlay(
                    state = PomodoroUiState(phase = PomodoroPhase.READY_FOR_BREAK, alert = PomodoroAlert.FOCUS_END),
                    onClose = { closes++ }, onStart = {}, onPauseResume = {}, onStartBreak = {},
                    onNextRound = {}, onStopAlert = { stops++ }, onEndCycle = {},
                    onSaveSettings = { _, _ -> }, onRefresh = {}
                )
            }
        }
        rule.onNodeWithContentDescription("关闭番茄钟，计时继续").performClick()
        rule.runOnIdle {
            assertEquals(1, closes)
            assertEquals(0, stops)
        }
    }

    @Test fun timerActionsAndStopAlertRemainOnSameRow() {
        var pauses = 0
        var stops = 0
        rule.setContent {
            MaterialTheme {
                PomodoroOverlay(
                    state = PomodoroUiState(phase = PomodoroPhase.FOCUS, alert = PomodoroAlert.FOCUS_END),
                    onClose = {}, onStart = {}, onPauseResume = { pauses++ }, onStartBreak = {},
                    onNextRound = {}, onStopAlert = { stops++ }, onEndCycle = {},
                    onSaveSettings = { _, _ -> }, onRefresh = {}
                )
            }
        }
        val pause = rule.onNodeWithText("暂停")
        val end = rule.onNodeWithText("结束本轮")
        val stop = rule.onNodeWithText("停止响铃")
        val y = pause.fetchSemanticsNode().boundsInRoot.center.y
        assertEquals(y, end.fetchSemanticsNode().boundsInRoot.center.y, 1f)
        assertEquals(y, stop.fetchSemanticsNode().boundsInRoot.center.y, 1f)
        pause.performScrollTo().performClick()
        stop.performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(1, pauses)
            assertEquals(1, stops)
        }
        rule.onNodeWithText("停止响铃和震动").assertDoesNotExist()
    }

    @Test fun miniOverlayHasOnlyOneClickTargetThatOpensWindow() {
        var opens = 0
        rule.setContent {
            MaterialTheme {
                PomodoroMiniOverlay(
                    state = PomodoroUiState(phase = PomodoroPhase.READY_FOR_BREAK, alert = PomodoroAlert.FOCUS_END),
                    onOpen = { opens++ }
                )
            }
        }
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
        rule.onNodeWithContentDescription("番茄钟，剩余 00:00，点击打开大窗").performClick()
        rule.runOnIdle { assertEquals(1, opens) }
        rule.onNodeWithText("停止响铃").assertDoesNotExist()
        rule.onNodeWithText("开始休息").assertDoesNotExist()
        rule.onNodeWithText("专注完成").assertDoesNotExist()
    }
}
