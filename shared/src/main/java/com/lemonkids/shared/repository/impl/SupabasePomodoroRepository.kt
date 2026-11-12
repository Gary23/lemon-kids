package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.PomodoroDailyCount
import com.lemonkids.shared.auth.TaskSessionGuard
import com.lemonkids.shared.repository.PomodoroRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Serializable
internal data class RecordedPomodoroSession(
    @SerialName("session_id") val sessionId: String
)

@Singleton
class SupabasePomodoroRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val sessionGuard: TaskSessionGuard
) : PomodoroRepository {

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override suspend fun recordCompletion(
        expectedChildId: String,
        sessionId: String,
        focusMinutes: Int,
        breakMinutes: Int,
        startedAt: String,
        completedAt: String
    ): Result<Unit> = runCatching {
        val parameters = completionParameters(sessionId, focusMinutes, breakMinutes, startedAt, completedAt)
        sessionGuard.execute(expectedChildId) {
            postgrest.rpc(function = "record_pomodoro_completion", parameters = parameters)
            Unit
        }
    }.onFailure { error ->
        // 账户切换/释放运行时的取消必须向上传播，不能继续处理旧账户待上传队列。
        if (error is CancellationException) throw error
    }

    override suspend fun getDailyCounts(expectedChildId: String, fromDate: String, toDate: String): Result<List<PomodoroDailyCount>> =
        runCatching {
            val parameters = dailyCountParameters(fromDate, toDate)
            sessionGuard.execute(expectedChildId) {
                postgrest.rpc(function = "get_pomodoro_daily_counts", parameters = parameters)
                    .decodeList<PomodoroDailyCount>()
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }

    override suspend fun getRecordedSessionIds(expectedChildId: String, sessionIds: List<String>): Result<Set<String>> =
        runCatching {
            queryRecordedSessionIds(sessionIds) { batch ->
                // 原迁移已授予 authenticated SELECT，RLS仅允许本孩子/本家庭记录。
                sessionGuard.execute(expectedChildId) {
                    postgrest.from("pomodoro_completions").select(columns = Columns.list("session_id")) {
                        filter { isIn("session_id", batch) }
                    }.decodeList<RecordedPomodoroSession>()
                }
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }

    companion object {
        // UUID每项36字符，分批限制URL长度，同时低于PostgREST默认行数上限。
        internal const val SESSION_QUERY_BATCH_SIZE = 40

        internal suspend fun queryRecordedSessionIds(
            sessionIds: List<String>,
            fetch: suspend (List<String>) -> List<RecordedPomodoroSession>
        ): Set<String> {
            // 先完整验证再发请求，防止后续批次才发现非法参数。
            val requestedByUuid = sessionIds.groupBy { id ->
                val uuid = try {
                    UUID.fromString(id)
                } catch (_: IllegalArgumentException) {
                    throw IllegalArgumentException("番茄轮次 ID 无效")
                }
                require(uuid.toString().equals(id, ignoreCase = true)) { "番茄轮次 ID 无效" }
                uuid.toString()
            }
            val recorded = mutableSetOf<String>()
            for (batch in requestedByUuid.keys.toList().chunked(SESSION_QUERY_BATCH_SIZE)) {
                coroutineContext.ensureActive()
                val rows = fetch(batch)
                coroutineContext.ensureActive()
                for (row in rows) {
                    // 只接受本批次所查ID；返回调用者的原ID形式以便直接归并本地队列。
                    val normalized = UUID.fromString(row.sessionId).toString()
                    require(normalized in batch) { "服务端返回了未请求的番茄轮次" }
                    recorded.addAll(requestedByUuid.getValue(normalized))
                }
            }
            return recorded
        }

        val FOCUS_MINUTES = setOf(10, 15, 20, 25, 30, 35, 40)
        val BREAK_MINUTES = setOf(3, 5, 10, 15)

        /** 显式构造 JSON，避免混合 String/Int 的 Map 推断为 Any 后无法序列化。 */
        internal fun completionParameters(
            sessionId: String,
            focusMinutes: Int,
            breakMinutes: Int,
            startedAt: String,
            completedAt: String,
            now: Instant = Instant.now()
        ): JsonObject {
            require(runCatching { UUID.fromString(sessionId) }.isSuccess) { "番茄轮次 ID 无效" }
            require(focusMinutes in FOCUS_MINUTES) { "专注时长不在允许范围内" }
            require(breakMinutes in BREAK_MINUTES) { "休息时长不在允许范围内" }
            val started = parseInstant(startedAt)
            val completed = parseInstant(completedAt)
            // 暂停只延长实际经过时间，不修改真实起止时间，也不允许提前完成。
            require(!completed.isBefore(started.plus(Duration.ofMinutes(focusMinutes.toLong())))) {
                "番茄起止时间短于专注时长"
            }
            require(!completed.isAfter(now.plus(Duration.ofMinutes(5)))) {
                "不能提交未来的番茄完成记录"
            }
            return buildJsonObject {
                put("p_session_id", sessionId)
                put("p_focus_minutes", focusMinutes)
                put("p_break_minutes", breakMinutes)
                put("p_started_at", started.toString())
                put("p_completed_at", completed.toString())
            }
        }

        internal fun dailyCountParameters(fromDate: String, toDate: String): JsonObject {
            val from = parseDate(fromDate)
            val to = parseDate(toDate)
            require(!to.isBefore(from)) { "日期范围无效" }
            require(Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() <= 62) {
                "单次最多查询 62 天"
            }
            return buildJsonObject {
                put("p_from_date", from.toString())
                put("p_to_date", to.toString())
            }
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
    }
}
