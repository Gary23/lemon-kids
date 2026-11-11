package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.PomodoroDailyCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SupabasePomodoroRepositoryTest {
    private val session = "80000000-0000-0000-0000-000000000001"
    private val started = "2026-03-01T15:55:00Z"
    private val now = Instant.parse("2026-03-02T12:00:00Z")

    private fun payload(
        id: String = session,
        focus: Int = 25,
        rest: Int = 5,
        start: String = started,
        end: String = "2026-03-01T16:20:00Z"
    ) = SupabasePomodoroRepository.completionParameters(id, focus, rest, start, end, now)

    private fun rejected(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java, block)
    }

    @Test fun legacyMixedMapCannotBeSerialized() {
        // 原实现的混合 Map 在发出 HTTP 前就失败；不能靠重试或重新部署 RPC 修复。
        assertThrows(SerializationException::class.java) {
            Json.encodeToString(mapOf("p_session_id" to session, "p_focus_minutes" to 25))
        }
    }

    @Test fun legacyUnpausedCompletionRetainsRealTimesAndNumericDurations() {
        val data = payload()
        assertEquals(started, data["p_started_at"]!!.jsonPrimitive.content)
        assertEquals("2026-03-01T16:20:00Z", data["p_completed_at"]!!.jsonPrimitive.content)
        assertEquals(25, data["p_focus_minutes"]!!.jsonPrimitive.int)
        assertFalse(data["p_focus_minutes"]!!.jsonPrimitive.isString)
        assertEquals(data, Json.decodeFromString<JsonObject>(Json.encodeToString(data)))
    }

    @Test fun pausedCompletionKeepsOriginalStartAndActualCompletion() {
        val data = payload(end = "2026-03-01T17:20:00Z")
        assertEquals(started, data["p_started_at"]!!.jsonPrimitive.content)
        assertEquals("2026-03-01T17:20:00Z", data["p_completed_at"]!!.jsonPrimitive.content)
        assertEquals(session, data["p_session_id"]!!.jsonPrimitive.content)
        assertEquals(data, payload(end = "2026-03-01T17:20:00Z"))
    }

    @Test fun completionEvenOneMillisecondShortIsRejected() {
        rejected { payload(end = "2026-03-01T16:19:59.999Z") }
        rejected { payload(end = "2026-03-01T15:54:00Z") }
    }

    @Test fun futureLimitRemainsFiveMinutes() {
        payload(end = "2026-03-02T12:05:00Z")
        rejected { payload(end = "2026-03-02T12:05:00.001Z") }
    }

    @Test fun invalidIdentityDurationAndTimesAreRejected() {
        rejected { payload(id = "bad-id") }
        rejected { payload(focus = 7) }
        rejected { payload(rest = 7) }
        rejected { payload(start = "bad-time") }
        rejected { payload(end = "bad-time") }
    }

    @Test fun allSupportedDurationsAreAccepted() {
        for (focus in SupabasePomodoroRepository.FOCUS_MINUTES) {
            for (rest in SupabasePomodoroRepository.BREAK_MINUTES) {
                payload(focus = focus, rest = rest, end = "2026-03-01T18:00:00Z")
            }
        }
    }

    @Test fun dailyCountsAcceptSameDayAndExactly62DayDifference() {
        val sameDay = SupabasePomodoroRepository.dailyCountParameters("2026-03-01", "2026-03-01")
        assertEquals("2026-03-01", sameDay["p_from_date"]!!.jsonPrimitive.content)
        val range = SupabasePomodoroRepository.dailyCountParameters("2026-03-01", "2026-05-02")
        assertEquals("2026-05-02", range["p_to_date"]!!.jsonPrimitive.content)
        assertEquals(range, Json.decodeFromString<JsonObject>(Json.encodeToString(range)))
    }

    @Test fun invalidDateRangesAreRejected() {
        rejected { SupabasePomodoroRepository.dailyCountParameters("bad", "2026-03-01") }
        rejected { SupabasePomodoroRepository.dailyCountParameters("2026-03-02", "2026-03-01") }
        rejected { SupabasePomodoroRepository.dailyCountParameters("2026-03-01", "2026-05-03") }
    }

    @Test fun emptySessionConfirmationDoesNotQuery() = runBlocking {
        val recorded = SupabasePomodoroRepository.queryRecordedSessionIds(emptyList()) {
            fail("Empty IDs must not query PostgREST")
            emptyList()
        }
        assertEquals(emptySet<String>(), recorded)
    }

    @Test fun sessionConfirmationValidatesAllIdsBeforeQuerying() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                SupabasePomodoroRepository.queryRecordedSessionIds(listOf(session, "bad-id")) {
                    fail("Validation must finish before the first query")
                    emptyList()
                }
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                SupabasePomodoroRepository.queryRecordedSessionIds(listOf("1-1-1-1-1")) { emptyList() }
            }
        }
    }

    @Test fun sessionConfirmationChunksAndDeduplicatesBeforeQuerying() = runBlocking {
        val ids = (1..85).map { "80000000-0000-0000-0000-%012d".format(it) }
        val batches = mutableListOf<List<String>>()
        val recorded = SupabasePomodoroRepository.queryRecordedSessionIds(ids + ids.take(3)) { batch ->
            batches += batch
            batch.map(::RecordedPomodoroSession)
        }
        assertEquals(listOf(40, 40, 5), batches.map { it.size })
        assertEquals(ids, batches.flatten())
        assertEquals(ids.toSet(), recorded)
    }

    @Test fun sessionConfirmationReturnsOnlyPresentIdsAndPreservesCallerCase() = runBlocking {
        val lower = "abcdef00-0000-0000-0000-000000000001"
        val upper = lower.uppercase()
        val recorded = SupabasePomodoroRepository.queryRecordedSessionIds(listOf(upper, session)) { batch ->
            assertEquals(listOf(lower, session), batch)
            listOf(RecordedPomodoroSession(lower))
        }
        assertEquals(setOf(upper), recorded)
    }

    @Test fun confirmationDtoDecodesOnlySessionIdAndRejectsUnrequestedRows() {
        val rows = Json.decodeFromString<List<RecordedPomodoroSession>>("""[{"session_id":"$session"}]""")
        assertEquals(listOf(RecordedPomodoroSession(session)), rows)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                SupabasePomodoroRepository.queryRecordedSessionIds(listOf(session)) {
                    listOf(RecordedPomodoroSession("80000000-0000-0000-0000-000000000002"))
                }
            }
        }
    }

    @Test fun laterConfirmationBatchFailureDoesNotReturnPartialSuccess() {
        var calls = 0
        val failure = IllegalStateException("network unavailable")
        val ids = (1..41).map { "80000000-0000-0000-0000-%012d".format(it) }
        val caught = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                SupabasePomodoroRepository.queryRecordedSessionIds(ids) { batch ->
                    if (++calls == 2) throw failure
                    batch.map(::RecordedPomodoroSession)
                }
            }
        }
        assertSame(failure, caught)
        assertEquals(2, calls)
    }

    @Test fun confirmationCancellationPropagatesWithoutAnotherBatch() {
        var calls = 0
        val cancelled = CancellationException("account changed")
        val ids = (1..41).map { "80000000-0000-0000-0000-%012d".format(it) }
        val caught = assertThrows(CancellationException::class.java) {
            runBlocking {
                SupabasePomodoroRepository.queryRecordedSessionIds(ids) {
                    calls++
                    throw cancelled
                }
            }
        }
        assertSame(cancelled, caught)
        assertEquals(1, calls)
    }

    @Test fun deployedDailyCountResponseDecodesWithShanghaiDate() {
        val counts = Json.decodeFromString<List<PomodoroDailyCount>>("""[{"completed_date":"2026-03-02","count":2}]""")
        assertEquals(listOf(PomodoroDailyCount("2026-03-02", 2)), counts)
        assertEquals(emptyList<PomodoroDailyCount>(), Json.decodeFromString<List<PomodoroDailyCount>>("[]"))
    }
}
