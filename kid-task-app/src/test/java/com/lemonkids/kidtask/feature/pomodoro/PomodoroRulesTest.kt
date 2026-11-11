package com.lemonkids.kidtask.feature.pomodoro

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroRulesTest {

    private val start = 1_800_000_000_000L

    @Test
    fun `null session must not be treated as an active alert`() {
        assertFalse(PomodoroRules.hasActiveAlert(null))

        val running = PomodoroRules.startSession(10, 3, start, "session-null-alert")
        assertFalse(PomodoroRules.hasActiveAlert(running))
        assertTrue(PomodoroRules.hasActiveAlert(running.copy(alert = PomodoroAlert.FOCUS_END)))
        assertTrue(PomodoroRules.hasActiveAlert(running.copy(alert = PomodoroAlert.BREAK_END)))
    }

    @Test
    fun `in-progress session is never persisted`() {
        val data = PomodoroStoredData(
            session = PomodoroRules.startSession(10, 3, start, "session-persist")
        )
        val encoded = Json.encodeToString(PomodoroStoredData.serializer(), data)
        assertFalse(encoded.contains("session"))

        val legacy = Json { ignoreUnknownKeys = true }
            .decodeFromString<PomodoroStoredData>("""{"session":{"sessionId":"legacy"}}""")
        assertNull(legacy.session)
    }

    @Test
    fun `default session runs focus then break back to back`() {
        val session = PomodoroRules.startSession(25, 5, start, "session-1")

        assertEquals(PomodoroPhase.FOCUS, session.phase)
        assertEquals(start + 25 * 60_000L, session.focusEndsAtMillis)
        assertEquals(start + 30 * 60_000L, session.breakEndsAtMillis)
        assertEquals("25:00", PomodoroRules.formatRemaining(PomodoroRules.remainingMillis(session, start)))
    }

    @Test
    fun `focus end switches to break and records exactly one completion`() {
        val session = PomodoroRules.startSession(25, 5, start, "session-2")

        val before = PomodoroRules.advance(session, session.focusEndsAtMillis - 1)
        assertEquals(PomodoroPhase.FOCUS, before.session.phase)
        assertNull(before.completion)

        val ended = PomodoroRules.advance(session, session.focusEndsAtMillis)
        assertEquals(PomodoroPhase.BREAK, ended.session.phase)
        assertEquals(PomodoroAlert.FOCUS_END, ended.session.alert)
        assertEquals("session-2", ended.completion?.sessionId)
        assertEquals(25, ended.completion?.focusMinutes ?: -1)

        val repeated = PomodoroRules.advance(ended.session, session.focusEndsAtMillis + 1_000)
        assertNull(repeated.completion)
        assertTrue(repeated.session.focusCompletionRecorded)
    }

    @Test
    fun `stopping the alert does not change phase or stop the break`() {
        val focusEnded = PomodoroRules.advance(
            PomodoroRules.startSession(10, 3, start, "session-3"),
            start + 10 * 60_000L
        ).session
        val alertStopped = focusEnded.copy(alert = PomodoroAlert.NONE)

        val breakEnded = PomodoroRules.advance(alertStopped, start + 13 * 60_000L)
        assertEquals(PomodoroPhase.WAITING, breakEnded.session.phase)
        assertEquals(PomodoroAlert.BREAK_END, breakEnded.session.alert)
        assertNull(breakEnded.completion)
    }

    @Test
    fun `waiting after both deadlines keeps only break end alert and one completion`() {
        val session = PomodoroRules.startSession(10, 3, start, "session-4")

        val caughtUp = PomodoroRules.advance(session, start + 60 * 60_000L)
        assertEquals(PomodoroPhase.WAITING, caughtUp.session.phase)
        assertEquals(PomodoroAlert.BREAK_END, caughtUp.session.alert)
        assertNotNull(caughtUp.completion)

        val again = PomodoroRules.advance(caughtUp.session, start + 120 * 60_000L)
        assertEquals(PomodoroPhase.WAITING, again.session.phase)
        assertNull(again.completion)
    }

    @Test
    fun `color bands use inclusive boundaries`() {
        val session = PomodoroRules.startSession(20, 5, start, "session-5")
        fun bandAtRemainingRatio(ratio: Double) = PomodoroRules.band(
            session,
            ratio.toFloat()
        )

        assertEquals(PomodoroBand.CALM_GREEN, bandAtRemainingRatio(1.0))
        assertEquals(PomodoroBand.CALM_GREEN, bandAtRemainingRatio(0.5001))
        assertEquals(PomodoroBand.WARM_GOLD, bandAtRemainingRatio(0.5))
        assertEquals(PomodoroBand.WARM_GOLD, bandAtRemainingRatio(0.2501))
        assertEquals(PomodoroBand.ALERT_RED, bandAtRemainingRatio(0.25))
        assertEquals(PomodoroBand.OCEAN_BLUE, PomodoroRules.band(session.copy(phase = PomodoroPhase.BREAK), 0.1f))
        assertEquals(PomodoroBand.OCEAN_BLUE, PomodoroRules.band(null, 1.0f))
    }

    @Test
    fun `countdown format rounds up and uses two digit seconds`() {
        assertEquals("25:00", PomodoroRules.formatRemaining(25 * 60_000L))
        assertEquals("24:59", PomodoroRules.formatRemaining(24 * 60_000L + 59_000L))
        assertEquals("00:01", PomodoroRules.formatRemaining(1L))
        assertEquals("00:00", PomodoroRules.formatRemaining(0L))
        assertEquals("00:00", PomodoroRules.formatRemaining(-50L))
    }

    @Test
    fun `completion date is attributed in Shanghai across midnight`() {
        assertEquals("2026-03-02", PomodoroRules.completionDate(1_772_382_000_000L))
        assertEquals("2026-03-01", PomodoroRules.completionDate(1_772_382_000_000L - 25 * 60_000L))
    }

    @Test
    fun `pending completions are added once and synced ones are not`() {
        val pending = LocalPomodoroCompletion(
            sessionId = "pending",
            focusMinutes = 25,
            breakMinutes = 5,
            startedAtMillis = start,
            completedAtMillis = start + 25 * 60_000L,
            completedDate = "2026-03-02",
            synced = false
        )
        val synced = pending.copy(sessionId = "synced", synced = true)

        val counts = PomodoroRules.mergeCounts(
            cached = mapOf("2026-03-02" to 2, "2026-03-03" to 0),
            completions = listOf(pending, synced)
        )
        assertEquals(3, counts["2026-03-02"] ?: -1)
        assertEquals(0, counts["2026-03-03"] ?: -1)
    }

    @Test
    fun `invalid settings fall back and invalid presets are rejected`() {
        assertEquals(
            PomodoroSettings(25, 5),
            PomodoroRules.sanitizeSettings(7, 4)
        )
        assertThrows(IllegalArgumentException::class.java) {
            PomodoroRules.startSession(7, 5, start)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PomodoroRules.startSession(25, 4, start)
        }
        PomodoroRules.FOCUS_MINUTES.forEach { focus ->
            PomodoroRules.BREAK_MINUTES.forEach { rest ->
                val session = PomodoroRules.startSession(focus, rest, start)
                assertEquals(focus, session.focusMinutes)
                assertEquals(rest, session.breakMinutes)
            }
        }
    }

    @Test
    fun `fraction drains monotonically during focus`() {
        val session = PomodoroRules.startSession(10, 3, start, "session-6")
        val first = PomodoroRules.fraction(session, start + 60_000L)
        val second = PomodoroRules.fraction(session, start + 5 * 60_000L)
        assertTrue(first > second)
        assertFalse(first > 1f)
        assertEquals(0f, PomodoroRules.fraction(session, session.focusEndsAtMillis + 10_000L), 0.0001f)
    }
}
