package com.lemonkids.shared.repository

import com.lemonkids.shared.model.PointRecord
import com.lemonkids.shared.model.Reward
import kotlinx.coroutines.flow.Flow

interface RewardRepository {
    fun observeRewards(familyId: String): Flow<List<Reward>>
    suspend fun createReward(reward: Reward): Result<String>
    suspend fun updateReward(reward: Reward): Result<Unit>
    suspend fun deleteReward(rewardId: String): Result<Unit>
    suspend fun redeemReward(rewardId: String, childId: String): Result<Unit>
    fun observePointRecords(childId: String): Flow<List<PointRecord>>
    fun getCurrentPoints(childId: String): Flow<Int>
}
