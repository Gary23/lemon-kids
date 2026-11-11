package com.lemonkids.kidtask.feature.pomodoro

/** 大窗与小窗共用的纯展示规则，不改变计时或提醒状态。 */
object PomodoroPresentationRules {
    fun fraction(state: PomodoroUiState): Float {
        val total = when (state.phase) {
            PomodoroPhase.FOCUS -> state.focusMinutes * PomodoroRules.MINUTE_MILLIS
            PomodoroPhase.BREAK -> state.breakMinutes * PomodoroRules.MINUTE_MILLIS
            PomodoroPhase.READY_FOR_BREAK, PomodoroPhase.WAITING -> return 0f
            PomodoroPhase.SETUP -> return 1f
        }
        if (total <= 0L) return 0f
        return (state.remainingMillis.toDouble() / total).toFloat().coerceIn(0f, 1f)
    }

    fun band(state: PomodoroUiState): PomodoroBand = when {
        state.phase == PomodoroPhase.READY_FOR_BREAK -> PomodoroBand.ALERT_RED
        state.phase != PomodoroPhase.FOCUS -> PomodoroBand.OCEAN_BLUE
        fraction(state) > 0.5f -> PomodoroBand.CALM_GREEN
        fraction(state) > 0.25f -> PomodoroBand.WARM_GOLD
        else -> PomodoroBand.ALERT_RED
    }

    fun remainingText(state: PomodoroUiState): String = when (state.phase) {
        PomodoroPhase.READY_FOR_BREAK, PomodoroPhase.WAITING -> "00:00"
        else -> PomodoroRules.formatRemaining(state.remainingMillis)
    }

    fun actions(state: PomodoroUiState): List<PomodoroAction> = buildList {
        when (state.phase) {
            PomodoroPhase.SETUP -> add(PomodoroAction.START)
            PomodoroPhase.FOCUS, PomodoroPhase.BREAK -> {
                add(if (state.isPaused) PomodoroAction.RESUME else PomodoroAction.PAUSE)
                add(PomodoroAction.END_CYCLE)
            }
            PomodoroPhase.READY_FOR_BREAK -> {
                add(PomodoroAction.START_BREAK)
                add(PomodoroAction.END_CYCLE)
            }
            PomodoroPhase.WAITING -> add(PomodoroAction.NEXT_ROUND)
        }
        if (state.alert != PomodoroAlert.NONE) add(PomodoroAction.STOP_ALERT)
    }
}

enum class PomodoroAction(val label: String) {
    START("开始专注"), PAUSE("暂停"), RESUME("继续"), START_BREAK("开始休息"),
    END_CYCLE("结束本轮"), NEXT_ROUND("下一轮"), STOP_ALERT("停止响铃")
}
