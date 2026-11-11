package com.lemonkids.shared.repository

import com.lemonkids.shared.model.PomodoroDailyCount

/**
 * 番茄完成记录的受控读写接口。
 *
 * 本地计时与待同步队列由任务端维护；这里只负责把完成记录幂等提交到服务端，
 * 以及读取服务端按上海日期聚合的权威数量。客户端不能传入 child_id。
 * 起止时间必须是真实时间，完成不早于开始加有效专注时长；暂停可延长真实经过时间。
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

    /** 只读确认当前身份已接纳的轮次，用于上传响应丢失后的对账；不允许指定 child_id。 */
    suspend fun getRecordedSessionIds(sessionIds: List<String>): Result<Set<String>>
}
