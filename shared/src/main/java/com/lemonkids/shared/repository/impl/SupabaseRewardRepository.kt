package com.lemonkids.shared.repository.impl

import com.lemonkids.shared.model.PointRecord
import com.lemonkids.shared.model.Reward
import com.lemonkids.shared.repository.RewardRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

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
        while (true) { delay(60_000); fetch() }
    }

    override suspend fun createReward(reward: Reward): Result<String> = runCatching {
        postgrest.from("rewards").insert(reward) { select() }.decodeSingle<Reward>().id
    }

    override suspend fun updateReward(reward: Reward): Result<Unit> = runCatching {
        postgrest.from("rewards").update(reward) { filter { eq("id", reward.id) } }
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
        while (true) { delay(60_000); fetch() }
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
        while (true) { delay(60_000); fetch() }
    }
}
