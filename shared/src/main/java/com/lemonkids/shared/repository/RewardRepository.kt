package com.lemonkids.shared.repository

import com.lemonkids.shared.model.PointRecord
import com.lemonkids.shared.model.Reward
import com.lemonkids.shared.model.RewardSnapshot
import kotlinx.coroutines.flow.Flow

interface RewardRepository {
    fun observeRewards(familyId: String): Flow<List<Reward>>
    suspend fun getAllRewards(familyId: String): Result<List<Reward>>
    suspend fun createReward(reward: Reward): Result<String>
    suspend fun updateReward(reward: Reward): Result<Unit>
    suspend fun uploadRewardImage(familyId: String, jpegBytes: ByteArray): Result<String>
    suspend fun deleteRewardImage(familyId: String, imagePath: String): Result<Unit>
    suspend fun createRewardImageUrl(familyId: String, imagePath: String): Result<String>
    suspend fun setRewardActive(rewardId: String, familyId: String, active: Boolean): Result<Unit>
    suspend fun deleteReward(rewardId: String): Result<Unit>
    suspend fun getRewardSnapshot(familyId: String, childId: String): Result<RewardSnapshot>
    suspend fun redeemReward(rewardId: String, childId: String, requestId: String): Result<Unit>
    suspend fun cancelRewardRedemption(redemptionId: String, childId: String): Result<Unit>
    suspend fun useRewardRedemption(redemptionId: String, childId: String): Result<Unit>
    fun requestPointsRefresh()
    fun observePointRecords(childId: String): Flow<List<PointRecord>>
    fun getCurrentPoints(childId: String): Flow<Int>
}
