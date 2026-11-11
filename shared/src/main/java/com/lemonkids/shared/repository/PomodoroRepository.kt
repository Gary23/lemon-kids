package com.lemonkids.shared.repository

import com.lemonkids.shared.model.PomodoroDailyCount

/**
 * 番茄完成记录的受控读写接口。
 *
 * 本地计时与待同步队列由任务端维护；这里只负责把完成记录幂等提交到服务端，
 * 以及读取服务端按上海日期聚合的权威数量。客户端不能传入 child_id。
 */
interface PomodoroRepository {
    suspend fun recordCompletion(
        sessionId: String,
        focusMinutes: Int,
        breakMinutes: Int,
        startedAt: String,
        completedAt: String
    ): Result<Unit>

    suspend fun getDailyCounts(fromDate: String, toDate: String): Result<List<PomodoroDailyCount>>
}
