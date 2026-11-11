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
    fun `default session starts focus without scheduling break`() {
        val session = PomodoroRules.startSession(25, 5, start, "session-1")

        assertEquals(PomodoroPhase.FOCUS, session.phase)
        assertEquals(start + 25 * 60_000L, session.focusEndsAtMillis)
        assertEquals(0L, session.breakEndsAtMillis)
        assertEquals("25:00", PomodoroRules.formatRemaining(PomodoroRules.remainingMillis(session, start)))
    }

    @Test
    fun `focus end waits for manual break and records exactly one completion`() {
        val session = PomodoroRules.startSession(25, 5, start, "session-2")

        val before = PomodoroRules.advance(session, session.focusEndsAtMillis - 1)
        assertEquals(PomodoroPhase.FOCUS, before.session.phase)
        assertNull(before.completion)

        val ended = PomodoroRules.advance(session, session.focusEndsAtMillis)
        assertEquals(PomodoroPhase.READY_FOR_BREAK, ended.session.phase)
        assertEquals(PomodoroAlert.FOCUS_END, ended.session.alert)
        assertEquals("session-2", ended.completion?.sessionId)
        assertEquals(25, ended.completion?.focusMinutes ?: -1)

        val repeated = PomodoroRules.advance(ended.session, session.focusEndsAtMillis + 1_000)
        assertNull(repeated.completion)
        assertTrue(repeated.session.focusCompletionRecorded)
    }

    @Test
    fun `stopping alert does not start break and manual start grants full duration`() {
        val focusEnded = PomodoroRules.advance(
            PomodoroRules.startSession(10, 3, start, "session-3"),
            start + 10 * 60_000L
        ).session
        val alertStopped = focusEnded.copy(alert = PomodoroAlert.NONE)
        val waited = PomodoroRules.advance(alertStopped, start + 60 * 60_000L)
        assertEquals(PomodoroPhase.READY_FOR_BREAK, waited.session.phase)
        assertNull(waited.completion)
        val breakStart = start + 60 * 60_000L
        val resting = PomodoroRules.startBreak(waited.session, breakStart)
        assertEquals(PomodoroPhase.BREAK, resting.phase)
        assertEquals(3 * 60_000L, PomodoroRules.remainingMillis(resting, breakStart))
        assertEquals(PomodoroAlert.NONE, resting.alert)
        assertEquals(resting, PomodoroRules.startBreak(resting, breakStart + 1000))
        val breakEnded = PomodoroRules.advance(resting, resting.breakEndsAtMillis)
        assertEquals(PomodoroPhase.WAITING, breakEnded.session.phase)
        assertEquals(PomodoroAlert.BREAK_END, breakEnded.session.alert)
        assertNull(breakEnded.completion)
    }

    @Test
    fun `long delayed focus tick still waits for manual break with one completion`() {
        val session = PomodoroRules.startSession(10, 3, start, "session-4")

        val caughtUp = PomodoroRules.advance(session, start + 60 * 60_000L)
        assertEquals(PomodoroPhase.READY_FOR_BREAK, caughtUp.session.phase)
        assertEquals(PomodoroAlert.FOCUS_END, caughtUp.session.alert)
        assertNotNull(caughtUp.completion)

        val again = PomodoroRules.advance(caughtUp.session, start + 120 * 60_000L)
        assertEquals(PomodoroPhase.READY_FOR_BREAK, again.session.phase)
        assertNull(again.completion)
    }

    @Test
    fun `focus pause freezes progress and preserves real completion time`() {
        val session = PomodoroRules.startSession(10, 3, start)
        val pausedAt = start + 2 * 60_000L
        val paused = PomodoroRules.pause(session, pausedAt)
        assertEquals(8 * 60_000L, paused.pausedRemainingMillis)
        assertEquals(paused, PomodoroRules.pause(paused, pausedAt + 1000))
        assertEquals(paused, PomodoroRules.advance(paused, start + 100 * 60_000L).session)
        assertNull(PomodoroRules.advance(paused, start + 100 * 60_000L).completion)
        assertEquals(PomodoroRules.fraction(paused, pausedAt), PomodoroRules.fraction(paused, start + 100 * 60_000L), 0f)
        val resumed = PomodoroRules.resume(paused, start + 7 * 60_000L)
        assertEquals(start + 15 * 60_000L, resumed.focusEndsAtMillis)
        assertEquals(resumed, PomodoroRules.resume(resumed, start + 8 * 60_000L))
        val ended = PomodoroRules.advance(resumed, resumed.focusEndsAtMillis)
        assertEquals(start, ended.completion?.startedAtMillis)
        assertEquals(start + 15 * 60_000L, ended.completion?.completedAtMillis)
        assertEquals(PomodoroPhase.READY_FOR_BREAK, ended.session.phase)
    }

    @Test
    fun `break pause freezes and resumes remaining without another tomato`() {
        val ended = PomodoroRules.advance(PomodoroRules.startSession(10, 3, start), start + 10 * 60_000L).session
        val resting = PomodoroRules.startBreak(ended, start + 20 * 60_000L)
        val paused = PomodoroRules.pause(resting, start + 21 * 60_000L)
        assertEquals(2 * 60_000L, PomodoroRules.remainingMillis(paused, start + 100 * 60_000L))
        val resumed = PomodoroRules.resume(paused, start + 30 * 60_000L)
        assertEquals(start + 32 * 60_000L, resumed.breakEndsAtMillis)
        val finished = PomodoroRules.advance(resumed, resumed.breakEndsAtMillis)
        assertEquals(PomodoroPhase.WAITING, finished.session.phase)
        assertNull(finished.completion)
    }

    @Test
    fun `pause at focus deadline settles completion rather than pausing nonexistent break`() {
        val session = PomodoroRules.startSession(10, 3, start)
        val advanced = PomodoroRules.advance(session, session.focusEndsAtMillis)
        val paused = PomodoroRules.pause(advanced.session, session.focusEndsAtMillis)
        assertNotNull(advanced.completion)
        assertEquals(PomodoroPhase.READY_FOR_BREAK, paused.phase)
        assertNull(paused.pausedRemainingMillis)
        assertEquals(PomodoroBand.ALERT_RED, PomodoroRules.band(paused, 0f))
        assertEquals(0f, PomodoroRules.fraction(paused, session.focusEndsAtMillis), 0f)
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
