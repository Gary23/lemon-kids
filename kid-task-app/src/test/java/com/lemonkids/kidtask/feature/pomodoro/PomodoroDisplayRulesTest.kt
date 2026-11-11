package com.lemonkids.kidtask.feature.pomodoro

import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroDisplayRulesTest {
    @Test
    fun `running and paused rounds close to mini`() {
        listOf(PomodoroPhase.FOCUS, PomodoroPhase.BREAK, PomodoroPhase.READY_FOR_BREAK).forEach { phase ->
            listOf(false, true).forEach { paused ->
                assertEquals(PomodoroDisplay.MINI, PomodoroDisplayRules.afterClose(PomodoroUiState(phase = phase, isPaused = paused)))
            }
        }
    }

    @Test
    fun `setup and completed round without reminder close to hidden`() {
        listOf(PomodoroPhase.SETUP, PomodoroPhase.WAITING).forEach { phase ->
            assertEquals(PomodoroDisplay.HIDDEN, PomodoroDisplayRules.afterClose(PomodoroUiState(phase = phase)))
        }
    }

    @Test
    fun `completed round with reminder keeps mini access`() {
        assertEquals(PomodoroDisplay.MINI, PomodoroDisplayRules.afterClose(
            PomodoroUiState(phase = PomodoroPhase.WAITING, alert = PomodoroAlert.BREAK_END)
        ))
    }
}
