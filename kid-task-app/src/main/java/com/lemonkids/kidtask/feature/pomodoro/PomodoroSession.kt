package com.lemonkids.kidtask.feature.pomodoro

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil

enum class PomodoroPhase { SETUP, FOCUS, BREAK, WAITING }

/** 到点提醒与计时阶段相互独立：停止提醒不会改变阶段，也不会启动下一轮。 */
enum class PomodoroAlert { NONE, FOCUS_END, BREAK_END }

/** 专注剩余时间对应的视觉区间；休息、等待和准备态固定为海洋蓝。 */
enum class PomodoroBand { CALM_GREEN, WARM_GOLD, ALERT_RED, OCEAN_BLUE }

@Serializable
data class PomodoroSession(
    val sessionId: String,
    val phase: PomodoroPhase,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val focusStartedAtMillis: Long,
    val focusEndsAtMillis: Long,
    val breakEndsAtMillis: Long,
    val alert: PomodoroAlert = PomodoroAlert.NONE,
    val focusCompletionRecorded: Boolean = false
)

data class FocusCompletion(
    val sessionId: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val startedAtMillis: Long,
    val completedAtMillis: Long
)

data class AdvanceResult(
    val session: PomodoroSession,
    val completion: FocusCompletion? = null
)

@Serializable
data class PomodoroSettings(
    val focusMinutes: Int = PomodoroRules.DEFAULT_FOCUS_MINUTES,
    val breakMinutes: Int = PomodoroRules.DEFAULT_BREAK_MINUTES
)

@Serializable
data class LocalPomodoroCompletion(
    val sessionId: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
    val completedDate: String,
    val synced: Boolean = false
)

@Serializable
data class PomodoroStoredData(
    val settings: PomodoroSettings = PomodoroSettings(),
    /**
     * 仅存在于内存的当前轮快照。`@Transient` 保证它不会被序列化；
     * 旧版本已落盘的 `session` 字段在读取时也会被忽略。
     */
    @Transient
    val session: PomodoroSession? = null,
    val completions: List<LocalPomodoroCompletion> = emptyList(),
    val cachedCounts: Map<String, Int> = emptyMap(),
    val countsFetchedAtMillis: Long = 0L
)

/**
 * 番茄钟的纯计算规则，不依赖 Android，便于用虚拟时钟验证到点、阶段推进和跨日归属。
 *
 * 计时以 UTC 截止时间为事实，每 200ms 的 tick 只负责刷新展示和触发一次阶段推进。
 * 进行中的快照不会跨进程保存；App 关闭后重新打开不会恢复或补算。
 */
object PomodoroRules {
    val FOCUS_MINUTES = listOf(10, 15, 20, 25, 30, 35, 40)
    val BREAK_MINUTES = listOf(3, 5, 10, 15)
    const val DEFAULT_FOCUS_MINUTES = 25
    const val DEFAULT_BREAK_MINUTES = 5
    const val MINUTE_MILLIS = 60_000L
    val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")

    fun startSession(
        focusMinutes: Int,
        breakMinutes: Int,
        nowMillis: Long,
        sessionId: String = UUID.randomUUID().toString()
    ): PomodoroSession {
        require(focusMinutes in FOCUS_MINUTES) { "专注时长不在允许范围内" }
        require(breakMinutes in BREAK_MINUTES) { "休息时长不在允许范围内" }
        val focusEnds = nowMillis + focusMinutes * MINUTE_MILLIS
        return PomodoroSession(
            sessionId = sessionId,
            phase = PomodoroPhase.FOCUS,
            focusMinutes = focusMinutes,
            breakMinutes = breakMinutes,
            focusStartedAtMillis = nowMillis,
            focusEndsAtMillis = focusEnds,
            breakEndsAtMillis = focusEnds + breakMinutes * MINUTE_MILLIS,
            alert = PomodoroAlert.NONE,
            focusCompletionRecorded = false
        )
    }

    /**
     * 推进到 `nowMillis`。跨过专注终点时最多产生一次完成事件；若一次跨过专注和休息
     * 两个终点，直接停在等待下一轮，并只保留休息结束提醒，不补跑循环。
     */
    fun advance(session: PomodoroSession, nowMillis: Long): AdvanceResult {
        var current = session
        var completion: FocusCompletion? = null
        if (current.phase == PomodoroPhase.FOCUS && nowMillis >= current.focusEndsAtMillis) {
            if (!current.focusCompletionRecorded) {
                completion = FocusCompletion(
                    sessionId = current.sessionId,
                    focusMinutes = current.focusMinutes,
                    breakMinutes = current.breakMinutes,
                    startedAtMillis = current.focusStartedAtMillis,
                    completedAtMillis = current.focusEndsAtMillis
                )
            }
            current = current.copy(
                phase = PomodoroPhase.BREAK,
                alert = PomodoroAlert.FOCUS_END,
                focusCompletionRecorded = true
            )
        }
        if (current.phase == PomodoroPhase.BREAK && nowMillis >= current.breakEndsAtMillis) {
            current = current.copy(phase = PomodoroPhase.WAITING, alert = PomodoroAlert.BREAK_END)
        }
        return AdvanceResult(current, completion)
    }

    fun remainingMillis(session: PomodoroSession, nowMillis: Long): Long = when (session.phase) {
        PomodoroPhase.FOCUS -> (session.focusEndsAtMillis - nowMillis).coerceAtLeast(0L)
        PomodoroPhase.BREAK -> (session.breakEndsAtMillis - nowMillis).coerceAtLeast(0L)
        else -> 0L
    }

    fun totalMillis(session: PomodoroSession): Long = when (session.phase) {
        PomodoroPhase.FOCUS -> session.focusMinutes * MINUTE_MILLIS
        PomodoroPhase.BREAK -> session.breakMinutes * MINUTE_MILLIS
        else -> 0L
    }

    fun fraction(session: PomodoroSession, nowMillis: Long): Float {
        val total = totalMillis(session)
        if (total <= 0L) return 1f
        return (remainingMillis(session, nowMillis).toDouble() / total).toFloat().coerceIn(0f, 1f)
    }

    /** 专注先按剩余比例取色；休息、等待和准备态固定海洋蓝。 */
    fun band(session: PomodoroSession?, fraction: Float): PomodoroBand = when {
        session == null || session.phase != PomodoroPhase.FOCUS -> PomodoroBand.OCEAN_BLUE
        fraction > 0.5f -> PomodoroBand.CALM_GREEN
        fraction > 0.25f -> PomodoroBand.WARM_GOLD
        else -> PomodoroBand.ALERT_RED
    }

    /** 显示用 mm:ss；向上取整避免开始瞬间显示少一秒。 */
    fun formatRemaining(millis: Long): String {
        val totalSeconds = ceil(millis.coerceAtLeast(0L) / 1000.0).toLong()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /** 空会话不能视为“有提醒”；避免 `null != NONE` 被 Kotlin 判为 true。 */
    fun hasActiveAlert(session: PomodoroSession?): Boolean =
        session != null && session.alert != PomodoroAlert.NONE

    fun completionDate(completedAtMillis: Long): String =
        Instant.ofEpochMilli(completedAtMillis).atZone(SHANGHAI).toLocalDate().toString()

    /** 展示数量 = 远端已同步聚合 + 本机未同步记录；已同步记录不在本地重复累加。 */
    fun mergeCounts(
        cached: Map<String, Int>,
        completions: List<LocalPomodoroCompletion>
    ): Map<String, Int> {
        val counts = cached.toMutableMap()
        completions.filterNot { it.synced }.forEach { completion ->
            counts[completion.completedDate] = (counts[completion.completedDate] ?: 0) + 1
        }
        return counts
    }

    fun sanitizeSettings(focusMinutes: Int, breakMinutes: Int): PomodoroSettings =
        PomodoroSettings(
            focusMinutes = focusMinutes.takeIf { it in FOCUS_MINUTES } ?: DEFAULT_FOCUS_MINUTES,
            breakMinutes = breakMinutes.takeIf { it in BREAK_MINUTES } ?: DEFAULT_BREAK_MINUTES
        )
}
