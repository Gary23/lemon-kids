package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.BadgeCatalog
import com.lemonkids.shared.model.BadgeProgress
import com.lemonkids.shared.repository.BadgeProgressRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

class BadgeProgressConflict(val current: BadgeProgress?) : Exception("进度已在其他地方更新")
class BadgeProgressSaveUnconfirmed : Exception("保存结果暂时无法确认")
class BadgeProgressSaveRejected : Exception("保存失败")

internal sealed interface BadgeWriteResolution {
    data class Saved(val record: BadgeProgress) : BadgeWriteResolution
    data class Conflict(val current: BadgeProgress?) : BadgeWriteResolution
    data object Rejected : BadgeWriteResolution
}

internal fun resolveBadgeWrite(
    current: BadgeProgress?,
    expectedVersion: Int,
    requestedValue: Int
): BadgeWriteResolution = when {
    current?.value == requestedValue -> BadgeWriteResolution.Saved(current)
    (current?.version ?: 0) != expectedVersion -> BadgeWriteResolution.Conflict(current)
    else -> BadgeWriteResolution.Rejected
}

@Serializable
private data class SetBadgeProgressParams(
    @SerialName("p_child_id") val childId: String,
    @SerialName("p_badge_key") val badgeKey: String,
    @SerialName("p_segment") val segment: String,
    @SerialName("p_value") val value: Int,
    @SerialName("p_expected_version") val expectedVersion: Int
)

@Singleton
class SupabaseBadgeProgressRepository @Inject constructor(private val supabase: SupabaseClient) : BadgeProgressRepository {
    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override suspend fun getForChild(childId: String): Result<List<BadgeProgress>> = runCatching {
        require(childId.isNotBlank())
        postgrest.from("badge_progress").select { filter { eq("child_id", childId) } }.decodeList<BadgeProgress>()
    }

    override suspend fun setAbsolute(childId: String, key: String, segment: String, value: Int, expectedVersion: Int): Result<BadgeProgress> {
        if (!BadgeCatalog.validate(key, segment, value) || expectedVersion < 0) {
            return Result.failure(IllegalArgumentException("进度参数无效"))
        }
        // RPC 可能已经提交，而响应体解码或网络返回失败。以再次读取的服务端值为准。
        val writeFailed = runCatching {
            postgrest.rpc("set_badge_progress", SetBadgeProgressParams(childId, key, segment, value, expectedVersion))
        }.isFailure
        val snapshot = getForChild(childId).getOrNull() ?: return Result.failure(BadgeProgressSaveUnconfirmed())
        val current = snapshot.firstOrNull { it.badgeKey == key && it.segment == segment }
        return when (val resolution = resolveBadgeWrite(current, expectedVersion, value)) {
            is BadgeWriteResolution.Saved -> Result.success(resolution.record)
            is BadgeWriteResolution.Conflict -> Result.failure(BadgeProgressConflict(resolution.current))
            BadgeWriteResolution.Rejected -> Result.failure(if (writeFailed) BadgeProgressSaveRejected() else BadgeProgressSaveUnconfirmed())
        }
    }
}
