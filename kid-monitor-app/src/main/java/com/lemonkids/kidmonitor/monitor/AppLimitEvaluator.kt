package com.lemonkids.kidmonitor.monitor

import com.lemonkids.shared.model.AppLimit

enum class LimitBlockReason {
    FORBIDDEN,
    DAILY_LIMIT_REACHED,
    SESSION_LIMIT_REACHED,
    COOLING_DOWN
}

sealed interface LimitDecision {
    data object Allowed : LimitDecision

    data class Blocked(
        val reason: LimitBlockReason,
        val title: String,
        val message: String,
        val cooldownUntilMs: Long? = null
    ) : LimitDecision
}

data class TrackedAppState(
    val packageName: String,
    val date: String,
    val todayMs: Long = 0L,
    val sessionStartMs: Long = 0L,
    val cooldownUntilMs: Long = 0L,
    val sessionAccumulatedMs: Long = 0L,
    val lastExitMs: Long = 0L
)

object AppLimitEvaluator {
    private const val UNLIMITED_DAILY_MINUTES = 999

    fun normalizeForDate(state: TrackedAppState, today: String): TrackedAppState {
        return if (state.date == today) state else {
            state.copy(
                date = today,
                todayMs = 0L,
                sessionStartMs = 0L,
                cooldownUntilMs = 0L,
                sessionAccumulatedMs = 0L,
                lastExitMs = 0L
            )
        }
    }

    fun evaluate(
        packageName: String,
        nowMs: Long,
        today: String,
        state: TrackedAppState?,
        limit: AppLimit
    ): LimitDecision {
        val currentState = normalizeForDate(
            state ?: TrackedAppState(packageName = packageName, date = today),
            today
        )

        if (limit.dailyLimitMinutes == 0) {
            return LimitDecision.Blocked(
                reason = LimitBlockReason.FORBIDDEN,
                title = "这个应用先不玩啦",
                message = "爸爸妈妈帮你设置了休息时间，我们去做点别的吧"
            )
        }

        if (currentState.cooldownUntilMs > nowMs) {
            val remainingMinutes = ((currentState.cooldownUntilMs - nowMs) / 60_000L + 1L)
                .coerceAtLeast(1L)
            return LimitDecision.Blocked(
                reason = LimitBlockReason.COOLING_DOWN,
                title = "还要等一小会儿",
                message = "再休息约 ${remainingMinutes} 分钟，就可以继续打开啦",
                cooldownUntilMs = currentState.cooldownUntilMs
            )
        }

        if (limit.dailyLimitMinutes < UNLIMITED_DAILY_MINUTES &&
            currentState.todayMs >= limit.dailyLimitMinutes * 60_000L
        ) {
            return LimitDecision.Blocked(
                reason = LimitBlockReason.DAILY_LIMIT_REACHED,
                title = "今天先到这里啦",
                message = "这个应用今天可以用 ${limit.dailyLimitMinutes} 分钟，时间已经用完啦"
            )
        }

        if (limit.singleSessionMinutes > 0 &&
            limit.cooldownMinutes > 0 &&
            currentState.sessionStartMs > 0L
        ) {
            val sessionMs = currentState.sessionAccumulatedMs + (nowMs - currentState.sessionStartMs)
            if (sessionMs >= limit.singleSessionMinutes * 60_000L) {
                val cooldownUntilMs = nowMs + limit.cooldownMinutes * 60_000L
                return LimitDecision.Blocked(
                    reason = LimitBlockReason.SESSION_LIMIT_REACHED,
                    title = "该休息一下啦",
                    message = "已经用了 ${limit.singleSessionMinutes} 分钟，先放下平板休息一会儿吧",
                    cooldownUntilMs = cooldownUntilMs
                )
            }
        }

        return LimitDecision.Allowed
    }
}
