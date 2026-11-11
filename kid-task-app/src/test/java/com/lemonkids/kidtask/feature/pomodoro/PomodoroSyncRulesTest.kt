package com.lemonkids.kidtask.feature.pomodoro

import com.lemonkids.shared.model.PomodoroDailyCount
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PomodoroSyncRulesTest {
    private val day = LocalDate.parse("2026-11-11")
    private fun completion(id: String = "one", date: String = day.toString()) =
        LocalPomodoroCompletion(id, 10, 3, 0, 600_000, date)

    @Test fun `accepted records and aggregate replace together without duplicate`() {
        val data = PomodoroStoredData(completions = listOf(completion()), cachedCounts = mapOf(day.toString() to 2))
        val result = PomodoroSyncRules.reconcile(data, listOf(PomodoroDailyCount(day.toString(), 3)), setOf("one"), day, day, 123)
        assertTrue(result.completions.single().synced)
        assertEquals(3, PomodoroRules.mergeCounts(result.cachedCounts, result.completions)[day.toString()])
    }

    @Test fun `unconfirmed upload protects date from remote plus pending double count`() {
        val data = PomodoroStoredData(completions = listOf(completion()), cachedCounts = mapOf(day.toString() to 2))
        val result = PomodoroSyncRules.reconcile(data, listOf(PomodoroDailyCount(day.toString(), 3)), emptySet(), day, day, 123)
        assertFalse(result.completions.single().synced)
        assertEquals(2, result.cachedCounts[day.toString()])
        assertEquals(3, PomodoroRules.mergeCounts(result.cachedCounts, result.completions)[day.toString()])
    }

    @Test fun `partially accepted same date stays pending until authority can replace entire day`() {
        val data = PomodoroStoredData(completions = listOf(completion("one"), completion("two")), cachedCounts = mapOf(day.toString() to 2))
        val first = PomodoroSyncRules.reconcile(data, listOf(PomodoroDailyCount(day.toString(), 3)), setOf("one"), day, day, 123)
        assertTrue(first.completions.none { it.synced })
        assertEquals(4, PomodoroRules.mergeCounts(first.cachedCounts, first.completions)[day.toString()])
        val retry = PomodoroSyncRules.reconcile(first, listOf(PomodoroDailyCount(day.toString(), 4)), setOf("one", "two"), day, day, 124)
        assertTrue(retry.completions.all { it.synced })
        assertEquals(4, PomodoroRules.mergeCounts(retry.cachedCounts, retry.completions)[day.toString()])
    }

    @Test fun `archived completion is marked only when its date has been fetched`() {
        val old = day.minusDays(100)
        val data = PomodoroStoredData(completions = listOf(completion(date = old.toString())))
        val current = PomodoroSyncRules.reconcile(data, emptyList(), setOf("one"), day, day, 123)
        assertFalse(current.completions.single().synced)
        val archived = PomodoroSyncRules.reconcile(current, listOf(PomodoroDailyCount(old.toString(), 1)), setOf("one"), old, old, 124)
        assertTrue(archived.completions.single().synced)
        assertEquals(1, archived.cachedCounts[old.toString()])
    }
}
