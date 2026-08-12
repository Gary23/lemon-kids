package com.lemonkids.kidmonitor.monitor

import com.lemonkids.shared.model.AppLimit

data class RemainingUsage(
    val remainingMs: Long,
    val progressPercent: Int,
    val scope: RemainingUsageScope
)

enum class RemainingUsageScope {
    DAILY,
    SESSION
}

object AppLimitUsageDisplay {
    private const val UNLIMITED_DAILY_MINUTES = 999

    fun calculate(limit: AppLimit, state: TrackedAppState, now: Long): RemainingUsage? {
        if (limit.dailyLimitMinutes == 0) {
            return RemainingUsage(
                remainingMs = 0L,
                progressPercent = 100,
                scope = RemainingUsageScope.DAILY
            )
        }

        if (limit.singleSessionMinutes > 0 && limit.cooldownMinutes > 0 && state.sessionStartMs > 0L) {
            val sessionUsedMs = state.sessionAccumulatedMs + (now - state.sessionStartMs).coerceAtLeast(0L)
            val totalMs = limit.singleSessionMinutes * 60_000L
            return RemainingCandidate(
                remainingMs = totalMs - sessionUsedMs,
                totalMs = totalMs,
                scope = RemainingUsageScope.SESSION
            ).toRemainingUsage()
        }

        if (limit.dailyLimitMinutes < UNLIMITED_DAILY_MINUTES) {
            val totalMs = limit.dailyLimitMinutes * 60_000L
            return RemainingCandidate(
                remainingMs = totalMs - state.todayMs,
                totalMs = totalMs,
                scope = RemainingUsageScope.DAILY
            ).toRemainingUsage()
        }

        return null
    }

    fun labelFor(scope: RemainingUsageScope): String {
        return when (scope) {
            RemainingUsageScope.DAILY -> "今日还可用 "
            RemainingUsageScope.SESSION -> "本次还可用 "
        }
    }

    fun formatRemainingTime(remainingMs: Long): String {
        if (remainingMs <= 0L) return "不到1分钟"
        val seconds = remainingMs / 1_000L
        if (seconds > 60L) {
            val minutes = (remainingMs + 59_999L) / 60_000L
            return "${minutes}分钟"
        }
        return when {
            seconds < 60L -> "${seconds.coerceAtLeast(1L)}秒"
            else -> "1分钟"
        }
    }

    private data class RemainingCandidate(
        val remainingMs: Long,
        val totalMs: Long,
        val scope: RemainingUsageScope
    ) {
        fun toRemainingUsage(): RemainingUsage {
            val displayRemainingMs = remainingMs.coerceAtLeast(0L)
            val usedMs = (totalMs - remainingMs).coerceAtLeast(0L)
            val progressPercent = if (totalMs <= 0L) {
                100
            } else {
                ((usedMs * 100L) / totalMs).toInt().coerceIn(0, 100)
            }
            return RemainingUsage(
                remainingMs = displayRemainingMs,
                progressPercent = progressPercent,
                scope = scope
            )
        }
    }
}
