package com.lemonkids.kidtask.feature.pomodoro

import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroPresentationRulesTest {
    private val focus = PomodoroUiState(
        phase = PomodoroPhase.FOCUS, focusMinutes = 10, breakMinutes = 5,
        remainingMillis = 600_000L
    )

    @Test fun `focus colors use exact half and quarter boundaries`() {
        listOf(
            600_000L to PomodoroBand.CALM_GREEN,
            300_001L to PomodoroBand.CALM_GREEN,
            300_000L to PomodoroBand.WARM_GOLD,
            150_001L to PomodoroBand.WARM_GOLD,
            150_000L to PomodoroBand.ALERT_RED,
            0L to PomodoroBand.ALERT_RED
        ).forEach { (remaining, band) ->
            assertEquals(band, PomodoroPresentationRules.band(focus.copy(remainingMillis = remaining)))
        }
    }

    @Test fun `ready for break is empty red and waiting is empty blue`() {
        listOf(
            PomodoroPhase.READY_FOR_BREAK to PomodoroBand.ALERT_RED,
            PomodoroPhase.WAITING to PomodoroBand.OCEAN_BLUE
        ).forEach { (phase, band) ->
            val state = focus.copy(phase = phase)
            assertEquals(0f, PomodoroPresentationRules.fraction(state), 0f)
            assertEquals("00:00", PomodoroPresentationRules.remainingText(state))
            assertEquals(band, PomodoroPresentationRules.band(state))
        }
    }

    @Test fun `break uses its own duration and blue band`() {
        val state = focus.copy(phase = PomodoroPhase.BREAK, remainingMillis = 150_000L)
        assertEquals(0.5f, PomodoroPresentationRules.fraction(state), 0f)
        assertEquals(PomodoroBand.OCEAN_BLUE, PomodoroPresentationRules.band(state))
        assertEquals("02:30", PomodoroPresentationRules.remainingText(state))
    }

    @Test fun `paused display retains fraction time and color in both running phases`() {
        listOf(PomodoroPhase.FOCUS, PomodoroPhase.BREAK).forEach { phase ->
            val state = focus.copy(phase = phase, remainingMillis = 149_999L)
            val paused = state.copy(isPaused = true)
            assertEquals(PomodoroPresentationRules.fraction(state), PomodoroPresentationRules.fraction(paused), 0f)
            assertEquals(PomodoroPresentationRules.band(state), PomodoroPresentationRules.band(paused))
            assertEquals(PomodoroPresentationRules.remainingText(state), PomodoroPresentationRules.remainingText(paused))
            assertEquals(listOf(PomodoroAction.PAUSE, PomodoroAction.END_CYCLE), PomodoroPresentationRules.actions(state))
            assertEquals(listOf(PomodoroAction.RESUME, PomodoroAction.END_CYCLE), PomodoroPresentationRules.actions(paused))
        }
    }

    @Test fun `phase actions append stop alert without replacing timer controls`() {
        val expected = mapOf(
            PomodoroPhase.SETUP to listOf(PomodoroAction.START),
            PomodoroPhase.FOCUS to listOf(PomodoroAction.PAUSE, PomodoroAction.END_CYCLE),
            PomodoroPhase.READY_FOR_BREAK to listOf(PomodoroAction.START_BREAK, PomodoroAction.END_CYCLE),
            PomodoroPhase.BREAK to listOf(PomodoroAction.PAUSE, PomodoroAction.END_CYCLE),
            PomodoroPhase.WAITING to listOf(PomodoroAction.NEXT_ROUND)
        )
        expected.forEach { (phase, actions) ->
            val state = focus.copy(phase = phase)
            assertEquals(actions, PomodoroPresentationRules.actions(state))
            assertEquals(actions + PomodoroAction.STOP_ALERT,
                PomodoroPresentationRules.actions(state.copy(alert = PomodoroAlert.FOCUS_END)))
        }
    }

    @Test fun `progress clamps to ring bounds`() {
        assertEquals(1f, PomodoroPresentationRules.fraction(focus.copy(remainingMillis = 900_000L)), 0f)
        assertEquals(0f, PomodoroPresentationRules.fraction(focus.copy(remainingMillis = -1L)), 0f)
    }
}
