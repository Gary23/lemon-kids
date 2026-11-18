package com.lemonkids.kidtask.feature.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖分类流失败时的容错语义：异常不逃逸、保留上一次数据、退避重试、取消不吞掉。
 * 这些行为是首页闪退修复的核心，回归时最先失守。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResilientFeedTest {

    @Test
    fun `首次读取超时不会结束收集并在退避后恢复`() = runTest {
        var attempts = 0
        val received = mutableListOf<List<String>>()
        var failures = 0
        var recovered = 0
        val source = flow {
            attempts += 1
            if (attempts == 1) throw IllegalStateException("Request timeout has expired")
            emit(listOf("学习"))
            awaitCancellation()
        }

        backgroundScope.launch {
            source.collectKeepingLastValue(
                onValue = { received += it },
                onRecovered = { recovered += 1 },
                onFailure = { _, count -> failures = count }
            )
        }
        runCurrent()

        assertEquals(1, failures)
        assertEquals(emptyList<List<String>>(), received)

        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(2, attempts)
        assertEquals(listOf(listOf("学习")), received)
        assertEquals(1, recovered)
    }

    @Test
    fun `读取失败时保留上一次成功的数据不被清空`() = runTest {
        var attempts = 0
        val received = mutableListOf<List<String>>()
        var failures = 0
        val source = flow {
            attempts += 1
            if (attempts == 1) {
                emit(listOf("学习"))
                throw IllegalStateException("Request timeout has expired")
            }
            emit(listOf("学习", "运动"))
            awaitCancellation()
        }

        backgroundScope.launch {
            source.collectKeepingLastValue(
                onValue = { received += it },
                onFailure = { _, count -> failures = count }
            )
        }
        runCurrent()

        assertEquals(1, failures)
        assertEquals(listOf(listOf("学习")), received)
        assertTrue("失败不能推出空列表覆盖已有分类", received.none { it.isEmpty() })

        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(listOf(listOf("学习"), listOf("学习", "运动")), received)
        assertEquals(1, failures)
    }

    @Test
    fun `连续失败按十五秒、三十秒、六十秒退避且上限六十秒`() = runTest {
        var attempts = 0
        var failures = 0
        val source = flow<Unit> {
            attempts += 1
            throw IllegalStateException("boom")
        }

        backgroundScope.launch {
            source.collectKeepingLastValue(
                onValue = {},
                onFailure = { _, count -> failures = count }
            )
        }
        runCurrent()
        assertEquals(1, failures)
        assertEquals(1, attempts)

        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(2, failures)
        assertEquals(2, attempts)

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(3, failures)
        assertEquals(3, attempts)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(4, failures)
        assertEquals(4, attempts)

        advanceTimeBy(59_999)
        runCurrent()
        assertEquals(4, failures)
    }

    @Test
    fun `取消异常原样重抛且不计入失败`() = runTest {
        var failures = 0
        val source = flow<Unit> {
            throw CancellationException("cancelled")
        }

        val job = backgroundScope.launch {
            source.collectKeepingLastValue(
                onValue = {},
                onFailure = { _, count -> failures = count }
            )
        }
        runCurrent()

        assertEquals(0, failures)
        assertTrue(job.isCancelled)
        assertFalse(job.isActive)
    }

    @Test
    fun `重试间隔纯函数取值`() {
        assertEquals(15_000L, feedRetryDelayMs(1))
        assertEquals(30_000L, feedRetryDelayMs(2))
        assertEquals(60_000L, feedRetryDelayMs(3))
        assertEquals(60_000L, feedRetryDelayMs(10))
        assertEquals(15_000L, feedRetryDelayMs(0))
    }
}
