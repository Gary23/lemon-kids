package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.PointRecord
import com.lemonkids.shared.model.Reward
import com.lemonkids.shared.repository.RewardRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class RewardCreate(
    @SerialName("family_id") val familyId: String,
    val title: String,
    val cost: Int,
    val repeatable: Boolean,
    @SerialName("is_active") val isActive: Boolean,
    val description: String?,
    @SerialName("cover_key") val coverKey: String,
    @SerialName("is_featured") val isFeatured: Boolean
)

@Serializable
private data class RewardUpdate(
    val title: String,
    val cost: Int,
    val repeatable: Boolean,
    val description: String?,
    @SerialName("cover_key") val coverKey: String,
    @SerialName("is_featured") val isFeatured: Boolean
)

@Singleton
class SupabaseRewardRepository @Inject constructor(
    private val supabase: SupabaseClient
) : RewardRepository {

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)

    override fun observeRewards(familyId: String): Flow<List<Reward>> = callbackFlow {
        suspend fun fetch() {
            try {
                val rewards = postgrest.from("rewards").select {
                    filter { eq("family_id", familyId); eq("is_active", true) }
                }.decodeList<Reward>()
                trySend(rewards)
            } catch (_: Exception) {}
        }
        fetch()
        while (true) { delay(300_000); fetch() }
    }

    override suspend fun getAllRewards(familyId: String): Result<List<Reward>> = runCatching {
        require(familyId.isNotBlank()) { "家庭信息不能为空" }
        postgrest.from("rewards").select {
            filter { eq("family_id", familyId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<Reward>()
    }

    override suspend fun createReward(reward: Reward): Result<String> = runCatching {
        require(reward.familyId.isNotBlank()) { "家庭信息不能为空" }
        postgrest.from("rewards").insert(
            RewardCreate(
                familyId = reward.familyId,
                title = reward.title.trim(),
                cost = reward.cost,
                repeatable = reward.repeatable,
                isActive = reward.isActive,
                description = reward.description?.trim()?.takeIf { it.isNotEmpty() },
                coverKey = reward.coverKey,
                isFeatured = reward.isFeatured
            )
        ) { select() }.decodeSingle<Reward>().id
    }

    override suspend fun updateReward(reward: Reward): Result<Unit> = runCatching {
        require(reward.id.isNotBlank() && reward.familyId.isNotBlank()) { "奖励或家庭信息不能为空" }
        postgrest.from("rewards").update(
            RewardUpdate(
                title = reward.title.trim(),
                cost = reward.cost,
                repeatable = reward.repeatable,
                description = reward.description?.trim()?.takeIf { it.isNotEmpty() },
                coverKey = reward.coverKey,
                isFeatured = reward.isFeatured
            )
        ) {
            filter { eq("id", reward.id); eq("family_id", reward.familyId) }
            select()
        }.decodeSingle<Reward>()
        Unit
    }

    override suspend fun setRewardActive(rewardId: String, familyId: String, active: Boolean): Result<Unit> = runCatching {
        require(rewardId.isNotBlank() && familyId.isNotBlank()) { "奖励或家庭信息不能为空" }
        postgrest.from("rewards").update(mapOf("is_active" to active)) {
            filter { eq("id", rewardId); eq("family_id", familyId) }
            select()
        }.decodeSingle<Reward>()
        Unit
    }

    override suspend fun deleteReward(rewardId: String): Result<Unit> = runCatching {
        postgrest.from("rewards").update(mapOf("is_active" to false)) {
            filter { eq("id", rewardId) }
        }
    }

    override suspend fun redeemReward(rewardId: String, childId: String): Result<Unit> =
        runCatching {
            postgrest.rpc(
                function = "redeem_reward",
                parameters = mapOf(
                    "p_reward_id" to rewardId,
                    "p_child_id" to childId
                )
            )
        }

    override fun observePointRecords(childId: String): Flow<List<PointRecord>> = callbackFlow {
        suspend fun fetch() {
            try {
                val records = postgrest.from("point_records").select {
                    filter { eq("child_id", childId) }
                    order("timestamp", Order.DESCENDING)
                    limit(50)
                }.decodeList<PointRecord>()
                trySend(records)
            } catch (_: Exception) {}
        }
        fetch()
        while (true) { delay(300_000); fetch() }
    }

    override fun getCurrentPoints(childId: String): Flow<Int> = callbackFlow {
        suspend fun fetch() {
            try {
                val user = postgrest.from("users").select {
                    filter { eq("uid", childId) }
                }.decodeSingleOrNull<com.lemonkids.shared.model.User>()
                trySend(user?.totalPoints ?: 0)
            } catch (_: Exception) {}
        }
        fetch()
        while (true) { delay(300_000); fetch() }
    }
}
