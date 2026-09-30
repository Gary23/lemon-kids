package com.lemonkids.shared.repository.impl

import android.util.Log
import com.lemonkids.shared.model.PointRecord
import com.lemonkids.shared.model.Reward
import com.lemonkids.shared.model.RewardRedemption
import com.lemonkids.shared.model.RewardSnapshot
import com.lemonkids.shared.model.User
import com.lemonkids.shared.repository.RewardRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
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

@Serializable
private data class UnavailableRewardId(@SerialName("reward_id") val rewardId: String)

@Singleton
class SupabaseRewardRepository @Inject constructor(
    private val supabase: SupabaseClient
) : RewardRepository {

    private val postgrest get() = supabase.pluginManager.getPlugin(Postgrest)
    private val pointsRefreshEvents = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

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

    override suspend fun getRewardSnapshot(familyId: String, childId: String): Result<RewardSnapshot> {
        var stage = "校验家庭与孩子"
        return runCatching {
            require(familyId.isNotBlank() && childId.isNotBlank()) { "家庭或孩子信息不能为空" }
            stage = "读取孩子信息"
            val user = postgrest.from("users").select {
                filter { eq("uid", childId) }
            }.decodeSingle<User>()
            require(user.uid == childId && user.familyId == familyId) { "当前孩子不属于该家庭" }
            stage = "读取家庭奖励"
            val rewards = postgrest.from("rewards").select {
                filter { eq("family_id", familyId); eq("is_active", true) }
                order("created_at", Order.DESCENDING)
            }.decodeList<Reward>()
            val redemptions = mutableListOf<RewardRedemption>()
            var offset = 0L
            stage = "读取兑换记录"
            do {
                val page = postgrest.from("reward_redemptions").select {
                    filter { eq("child_id", childId) }
                    order("redeemed_at", Order.DESCENDING)
                    order("id", Order.DESCENDING)
                    range(offset, offset + 999)
                }.decodeList<RewardRedemption>()
                redemptions += page
                offset += page.size
            } while (page.size == 1000)
            stage = "读取一次性奖励名额"
            val unavailable = postgrest.rpc(
                "get_unavailable_one_time_rewards", mapOf("p_family_id" to familyId)
            ).decodeList<UnavailableRewardId>().map { it.rewardId }.toSet()
            stage = "读取本月任务收益"
            val monthlyEarned: Int = postgrest.rpc(
                "get_monthly_task_points", mapOf("p_child_id" to childId)
            ).decodeAs()
            val legacy = mutableListOf<PointRecord>()
            offset = 0L
            stage = "读取旧版兑换流水"
            do {
                val page = postgrest.from("point_records").select {
                    filter { eq("child_id", childId); eq("type", "reward_redeem"); filter("related_redemption_id", FilterOperator.IS, "null") }
                    order("timestamp", Order.DESCENDING)
                    order("id", Order.DESCENDING)
                    range(offset, offset + 999)
                }.decodeList<PointRecord>()
                legacy += page
                offset += page.size
            } while (page.size == 1000)
            RewardSnapshot(rewards, redemptions, unavailable, legacy, user.totalPoints, monthlyEarned)
        }.onFailure { Log.e("RewardRepository", "读取奖励快照失败，阶段：$stage", it) }
    }

    override suspend fun redeemReward(rewardId: String, childId: String, requestId: String): Result<Unit> =
        runCatching {
            postgrest.rpc(
                function = "redeem_reward",
                parameters = mapOf(
                    "p_reward_id" to rewardId,
                    "p_child_id" to childId,
                    "p_request_id" to requestId
                )
            )
            requestPointsRefresh()
        }

    override suspend fun cancelRewardRedemption(redemptionId: String, childId: String): Result<Unit> = runCatching {
        postgrest.rpc("cancel_reward_redemption", mapOf("p_redemption_id" to redemptionId, "p_child_id" to childId))
        requestPointsRefresh()
    }

    override suspend fun useRewardRedemption(redemptionId: String, childId: String): Result<Unit> = runCatching {
        postgrest.rpc("use_reward_redemption", mapOf("p_redemption_id" to redemptionId, "p_child_id" to childId))
        requestPointsRefresh()
    }

    override fun requestPointsRefresh() { pointsRefreshEvents.tryEmit(Unit) }

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
                }.decodeSingle<User>()
                trySend(user.totalPoints)
            } catch (_: Exception) {}
        }
        fetch()
        launch { pointsRefreshEvents.collect { fetch() } }
        launch { while (true) { delay(300_000); fetch() } }
        awaitClose { }
    }
}
