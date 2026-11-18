package com.lemonkids.kidtask.feature.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * 分类读取失败后的重试间隔：首次 15 秒，第二次 30 秒，之后固定 60 秒；
 * 避免弱网或服务端抖动时持续打满请求。
 */
internal fun feedRetryDelayMs(failureCount: Int): Long = when {
    failureCount <= 1 -> 15_000L
    failureCount == 2 -> 30_000L
    else -> 60_000L
}

/**
 * 持续收集 [this]。单次订阅失败时只回调 [onFailure] 并按 [feedRetryDelayMs] 退避后重新订阅，
 * 不把异常抛给调用方的协程作用域；调用方已经展示的数据保持不变。
 *
 * - 每个元素交给 [onValue]；失败后重新收到数据时先回调一次 [onRecovered]。
 * - [CancellationException] 原样重抛，保持协程取消语义。
 * - 上游正常结束时直接返回。
 */
internal suspend fun <T> Flow<T>.collectKeepingLastValue(
    onValue: suspend (T) -> Unit,
    onRecovered: suspend () -> Unit = {},
    onFailure: suspend (Throwable, Int) -> Unit
) {
    var failureCount = 0
    while (true) {
        try {
            collect { value ->
                onValue(value)
                if (failureCount > 0) {
                    failureCount = 0
                    onRecovered()
                }
            }
            return
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failureCount += 1
            onFailure(error, failureCount)
            delay(feedRetryDelayMs(failureCount))
        }
    }
}
