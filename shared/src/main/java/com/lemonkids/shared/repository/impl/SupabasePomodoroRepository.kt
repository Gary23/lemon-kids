package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.PomodoroDailyCount
import com.lemonkids.shared.repository.PomodoroRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabasePomodoroRepository @Inject constructor(
    private val supabase: SupabaseClient
) : PomodoroRepository {

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override suspend fun recordCompletion(
        sessionId: String,
        focusMinutes: Int,
        breakMinutes: Int,
        startedAt: String,
        completedAt: String
    ): Result<Unit> = runCatching {
        require(runCatching { UUID.fromString(sessionId) }.isSuccess) { "番茄轮次 ID 无效" }
        require(focusMinutes in FOCUS_MINUTES) { "专注时长不在允许范围内" }
        require(breakMinutes in BREAK_MINUTES) { "休息时长不在允许范围内" }
        val started = parseInstant(startedAt)
        val completed = parseInstant(completedAt)
        require(completed == started.plus(Duration.ofMinutes(focusMinutes.toLong()))) {
            "番茄起止时间与专注时长不一致"
        }
        require(!completed.isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
            "不能提交未来的番茄完成记录"
        }
        postgrest.rpc(
            function = "record_pomodoro_completion",
            parameters = mapOf(
                "p_session_id" to sessionId,
                "p_focus_minutes" to focusMinutes,
                "p_break_minutes" to breakMinutes,
                "p_started_at" to started.toString(),
                "p_completed_at" to completed.toString()
            )
        )
        Unit
    }

    override suspend fun getDailyCounts(fromDate: String, toDate: String): Result<List<PomodoroDailyCount>> =
        runCatching {
            val from = parseDate(fromDate)
            val to = parseDate(toDate)
            require(!to.isBefore(from)) { "日期范围无效" }
            require(Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() <= 62) {
                "单次最多查询 62 天"
            }
            postgrest.rpc(
                function = "get_pomodoro_daily_counts",
                parameters = mapOf("p_from_date" to from.toString(), "p_to_date" to to.toString())
            ).decodeList<PomodoroDailyCount>()
        }

    private fun parseInstant(value: String): Instant = try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException("时间格式无效")
    }

    private fun parseDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException("日期格式无效")
    }

    companion object {
        val FOCUS_MINUTES = setOf(10, 15, 20, 25, 30, 35, 40)
        val BREAK_MINUTES = setOf(3, 5, 10, 15)
    }
}
