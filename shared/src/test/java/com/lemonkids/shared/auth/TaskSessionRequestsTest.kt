package com.lemonkids.shared.auth

import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TaskSessionRequestsTest {
    private val jwtError get() = RestException("JWT expired", null, 401, "test")
    private class Fixture {
        var clock = 1_000_000L
        var session: TaskSessionSnapshot? = TaskSessionSnapshot("child", "old", clock + 3_600_000)
        var refreshes = 0
        var prompts = 0
        var initialized = 0
        var refreshAction: suspend () -> Unit = { session = session?.copy(token = "new", expiresAtMillis = clock + 3_600_000) }
        val guard = TaskSessionRequests(
            initialize = { initialized++ }, snapshot = { session },
            refresh = { refreshes++; refreshAction() }, requireRecovery = { prompts++ }, now = { clock }
        )
    }

    @Test fun actualSdkJwtMessageLayoutIsRecognizedWithoutNullableDetails() {
        assertTrue(TaskSessionRequests.isJwtRejection(RestException("JWT expired", null, 401, "test")))
        assertTrue(TaskSessionRequests.isJwtRejection(RestException("JWSError JWSInvalidSignature", null, 401, "test")))
        assertFalse(TaskSessionRequests.isJwtRejection(RestException("permission denied", null, 401, "test")))
        assertFalse(TaskSessionRequests.isJwtRejection(RestException("JWT expired", null, 403, "test")))
    }

    @Test fun validSessionDoesNotRefresh() = runBlocking {
        val f = Fixture()
        assertEquals(42, f.guard.execute("child") { 42 })
        assertEquals(0, f.refreshes)
        assertEquals(0, f.prompts)
        assertEquals(1, f.initialized)
    }

    @Test fun longRunningSessionRefreshesBeforeNextRequest() = runBlocking {
        val f = Fixture()
        f.clock += 3_600_001
        var tokenUsed: String? = null
        f.guard.execute("child") { tokenUsed = f.session!!.token }
        assertEquals("new", tokenUsed)
        assertEquals(1, f.refreshes)
    }

    @Test fun nearExpiryAlsoRefreshes() = runBlocking {
        val f = Fixture()
        f.session = f.session!!.copy(expiresAtMillis = f.clock + 10_000)
        f.guard.ensureReady()
        assertEquals(1, f.refreshes)
    }

    @Test fun missingSessionPromptsWithoutSendingBusinessRequest() = runBlocking {
        val f = Fixture()
        f.session = null
        var calls = 0
        try { f.guard.execute("child") { calls++ }; fail() } catch (_: TaskSessionUnavailableException) { }
        assertEquals(0, calls)
        assertEquals(1, f.prompts)
    }

    @Test fun explicitlyRejectedJwtIsRefreshedAndRetriedOnlyOnce() = runBlocking {
        val f = Fixture()
        var calls = 0
        val result = f.guard.execute("child") { if (++calls == 1) throw jwtError else "ok" }
        assertEquals("ok", result)
        assertEquals(2, calls)
        assertEquals(1, f.refreshes)
    }

    @Test fun repeatedJwtRejectionShowsRecoveryInsteadOfLooping() = runBlocking {
        val f = Fixture()
        var calls = 0
        try { f.guard.execute("child") { calls++; throw jwtError }; fail() } catch (_: TaskSessionUnavailableException) { }
        assertEquals(2, calls)
        assertEquals(1, f.refreshes)
        assertEquals(1, f.prompts)
    }

    @Test fun unknownWriteResponseAndPermissionErrorsAreNeverReplayed() = runBlocking {
        for (error in listOf(IOException("response lost"), RestException("42501", "permission denied", 403, "test"), RestException("business", "no", 401, "test"))) {
            val f = Fixture()
            var calls = 0
            try { f.guard.execute("child") { calls++; throw error }; fail() } catch (caught: Exception) { assertSame(error, caught) }
            assertEquals(1, calls)
            assertEquals(0, f.refreshes)
            assertEquals(0, f.prompts)
        }
    }

    @Test fun sdkClearingSessionDuringRequestShowsRecovery() = runBlocking {
        val f = Fixture()
        try { f.guard.execute("child") { f.session = null }; fail() } catch (_: TaskSessionUnavailableException) { }
        assertEquals(1, f.prompts)
    }

    @Test fun parallelExpiredRequestsShareOneApplicationRefresh() = runBlocking {
        val f = Fixture()
        f.clock += 3_600_001
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.refreshAction = { started.complete(Unit); release.await(); f.session = f.session!!.copy(token = "new", expiresAtMillis = f.clock + 3_600_000) }
        val jobs = (1..20).map { async { f.guard.execute("child") { "ok" } } }
        started.await()
        release.complete(Unit)
        assertEquals(List(20) { "ok" }, jobs.awaitAll())
        assertEquals(1, f.refreshes)
    }

    @Test fun failedRefreshIsCoalescedAndManualRetryCanSucceed() = runBlocking {
        val f = Fixture()
        f.clock += 3_600_001
        f.refreshAction = { throw IOException("offline") }
        repeat(3) {
            try { f.guard.ensureReady(); fail() } catch (_: TaskSessionUnavailableException) { }
        }
        assertEquals(1, f.refreshes)
        f.clock += 2_001
        f.refreshAction = { f.session = f.session!!.copy(token = "new", expiresAtMillis = f.clock + 3_600_000) }
        f.guard.ensureReady()
        assertEquals(2, f.refreshes)
    }

    @Test fun staleJwtRejectionDoesNotRefreshAnAlreadyReplacedToken() = runBlocking {
        val f = Fixture()
        var calls = 0
        f.guard.execute("child") {
            if (++calls == 1) {
                f.session = f.session!!.copy(token = "sdk-new")
                throw jwtError
            }
        }
        assertEquals(2, calls)
        assertEquals(0, f.refreshes)
    }

    @Test fun accountSwitchNeverReplaysOldChildRequest() = runBlocking {
        val f = Fixture()
        var calls = 0
        try {
            f.guard.execute("child") { calls++; f.session = f.session!!.copy(childId = "other"); throw jwtError }
            fail()
        } catch (_: CancellationException) { }
        assertEquals(1, calls)
        assertEquals(0, f.refreshes)
        assertEquals(0, f.prompts)
    }

    @Test fun wrongChildIsRejectedBeforeRequest() = runBlocking {
        val f = Fixture()
        var calls = 0
        try { f.guard.execute("other") { calls++ }; fail() } catch (_: CancellationException) { }
        assertEquals(0, calls)
        assertEquals(0, f.prompts)
    }

    @Test fun cancellationIsNotConvertedToRecovery() = runBlocking {
        val f = Fixture()
        val cancellation = CancellationException("cancel")
        try { f.guard.execute("child") { throw cancellation }; fail() } catch (caught: CancellationException) { assertSame(cancellation, caught) }
        assertEquals(0, f.prompts)
    }

    @Test fun cancellingRefreshReleasesMutexAndDoesNotPrompt() = runBlocking {
        val f = Fixture()
        f.clock += 3_600_001
        val started = CompletableDeferred<Unit>()
        f.refreshAction = { started.complete(Unit); CompletableDeferred<Unit>().await() }
        val job = launch { f.guard.ensureReady() }
        started.await()
        job.cancelAndJoin()
        assertEquals(0, f.prompts)
        f.refreshAction = { f.session = f.session!!.copy(token = "new", expiresAtMillis = f.clock + 3_600_000) }
        f.guard.ensureReady()
        assertEquals(2, f.refreshes)
    }

    @Test fun cancellationWhileWaitingForMutexDoesNotPrompt() = runBlocking {
        val f = Fixture()
        f.clock += 3_600_001
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.refreshAction = { started.complete(Unit); release.await(); f.session = f.session!!.copy(token = "new", expiresAtMillis = f.clock + 3_600_000) }
        val first = launch { f.guard.ensureReady() }
        started.await()
        val waiting = launch { f.guard.ensureReady() }
        yield()
        waiting.cancelAndJoin()
        release.complete(Unit)
        first.join()
        assertEquals(0, f.prompts)
        assertEquals(1, f.refreshes)
    }

    @Test fun ownTimeoutShowsRecoveryButParentCancellationDoesNot() = runBlocking {
        var prompts = 0
        val never = CompletableDeferred<Unit>()
        val guard = TaskSessionRequests(
            initialize = { never.await() }, snapshot = { null }, refresh = {},
            requireRecovery = { prompts++ }, recoveryTimeoutMillis = 20
        )
        try { guard.ensureReady(); fail() } catch (_: TaskSessionUnavailableException) { }
        assertEquals(1, prompts)
        val job = launch { guard.ensureReady() }
        yield()
        job.cancelAndJoin()
        assertEquals(1, prompts)
    }
}
