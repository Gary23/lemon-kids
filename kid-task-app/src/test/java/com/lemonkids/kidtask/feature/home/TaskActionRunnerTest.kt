package com.lemonkids.kidtask.feature.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskActionRunnerTest {
    @Test
    fun `confirm claim blocks duplicate completion and undo for same task`() = runBlocking {
        val runner = TaskActionRunner()
        val response = CompletableDeferred<Unit>()
        var calls = 0
        var successes = 0
        var finished = false
        assertTrue(runner.tryStart("task-a"))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runner.execute("task-a", request = {
                calls++
                response.await()
                Result.success(Unit)
            }, onSuccess = { successes++ }, onFailure = { error("unexpected failure") },
                onFinished = { finished = true })
        }
        assertFalse(runner.tryStart("task-a"))
        assertEquals(0, successes)
        assertFalse(finished)
        response.complete(Unit)
        job.join()
        assertEquals(1, calls)
        assertEquals(1, successes)
        assertTrue(finished)
        assertTrue(runner.tryStart("task-a"))
    }

    @Test
    fun `failed write has no success callback and is not automatically retried`() = runBlocking {
        val runner = TaskActionRunner()
        var calls = 0
        var successes = 0
        var failures = 0
        var finished = false
        assertTrue(runner.tryStart("a"))
        runner.execute("a", request = {
            calls++
            Result.failure(IllegalStateException("private backend response"))
        }, onSuccess = { successes++ }, onFailure = { failures++ }, onFinished = { finished = true })
        assertEquals(1, calls)
        assertEquals(0, successes)
        assertEquals(1, failures)
        assertTrue(finished)
        assertTrue(runner.tryStart("a"))
    }

    @Test
    fun `direct request exception becomes one visible failure and releases claim`() = runBlocking {
        val runner = TaskActionRunner()
        var failures = 0
        assertTrue(runner.tryStart("a"))
        runner.execute("a", request = { throw IllegalStateException("network") },
            onSuccess = { error("unexpected success") }, onFailure = { failures++ }, onFinished = {})
        assertEquals(1, failures)
        assertTrue(runner.tryStart("a"))
    }

    @Test
    fun `cancellation wrapped in Result propagates without failure callback`() = runBlocking {
        val runner = TaskActionRunner()
        val cancelled = CancellationException("cancelled")
        var caught = false
        var cancellations = 0
        var finished = false
        assertTrue(runner.tryStart("a"))
        try {
            runner.execute("a", request = { Result.failure(cancelled) },
                onSuccess = { error("unexpected success") }, onFailure = { error("must not show failure") },
                onCancelled = { cancellations++ }, onFinished = { finished = true })
        } catch (error: CancellationException) {
            caught = error === cancelled
        }
        assertTrue(caught)
        assertEquals(1, cancellations)
        assertTrue(finished)
        assertTrue(runner.tryStart("a"))
    }

    @Test
    fun `cancellation during request clears sync marker and permits future retry`() = runBlocking {
        val runner = TaskActionRunner()
        val response = CompletableDeferred<Unit>()
        var cancelled = false
        var finished = false
        assertTrue(runner.tryStart("a"))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runner.execute("a", request = { response.await(); Result.success(Unit) },
                onSuccess = { error("unexpected success") }, onFailure = { error("must not show failure") },
                onCancelled = { cancelled = true }, onFinished = { finished = true })
        }
        job.cancelAndJoin()
        assertTrue(cancelled)
        assertTrue(finished)
        assertTrue(runner.tryStart("a"))
    }

    @Test
    fun `concurrent unrelated tasks complete independently and only success triggers animation`() = runBlocking {
        val runner = TaskActionRunner()
        val failedResponse = CompletableDeferred<Unit>()
        val successfulResponse = CompletableDeferred<Unit>()
        var refreshes = 0
        var animations = 0
        assertTrue(runner.tryStart("failed"))
        assertTrue(runner.tryStart("successful"))
        val failed = launch(start = CoroutineStart.UNDISPATCHED) {
            runner.execute("failed", request = {
                failedResponse.await()
                Result.failure(IllegalStateException("offline"))
            }, onSuccess = { animations++ }, onFailure = { refreshes++ }, onFinished = {})
        }
        val successful = launch(start = CoroutineStart.UNDISPATCHED) {
            runner.execute("successful", request = {
                successfulResponse.await()
                Result.success(Unit)
            }, onSuccess = { animations++; refreshes++ }, onFailure = { error("unexpected failure") }, onFinished = {})
        }
        assertEquals(0, animations)
        successfulResponse.complete(Unit)
        successful.join()
        failedResponse.complete(Unit)
        failed.join()
        assertEquals(1, animations)
        assertEquals(2, refreshes)
        assertTrue(runner.tryStart("failed"))
        assertTrue(runner.tryStart("successful"))
    }

    @Test
    fun `callback exception still clears claim and completion marker`() = runBlocking {
        val runner = TaskActionRunner()
        var finished = false
        assertTrue(runner.tryStart("a"))
        try {
            runner.execute("a", request = { Result.success(Unit) },
                onSuccess = { throw IllegalStateException("render") }, onFailure = { error("not request failure") },
                onFinished = { finished = true })
        } catch (_: IllegalStateException) { }
        assertTrue(finished)
        assertTrue(runner.tryStart("a"))
    }

    @Test
    fun `friendly action feedback never includes backend details`() {
        val complete = taskActionFailureMessage(completing = true)
        val undo = taskActionFailureMessage(completing = false)
        assertTrue(complete.contains("刷新"))
        assertTrue(undo.contains("撤销"))
        assertFalse(complete.contains("private backend response"))
        assertFalse(undo.contains("private backend response"))
    }
}
