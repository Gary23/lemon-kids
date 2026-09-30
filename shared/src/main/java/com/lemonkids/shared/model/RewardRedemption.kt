package com.lemonkids.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RewardRedemption(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("child_id") val childId: String,
    @SerialName("reward_id") val rewardId: String,
    @SerialName("title_snapshot") val title: String,
    @SerialName("cost_snapshot") val cost: Int,
    @SerialName("repeatable_snapshot") val repeatable: Boolean,
    val status: RewardRedemptionStatus,
    @SerialName("redeemed_at") val redeemedAt: String,
    @SerialName("used_at") val usedAt: String? = null,
    @SerialName("cancelled_at") val cancelledAt: String? = null
)

@Serializable
enum class RewardRedemptionStatus {
    @SerialName("held") HELD,
    @SerialName("used") USED,
    @SerialName("cancelled") CANCELLED
}

data class RewardSnapshot(
    val rewards: List<Reward>,
    val redemptions: List<RewardRedemption>,
    val unavailableOneTimeIds: Set<String>,
    val legacyRedemptions: List<PointRecord>,
    val balance: Int,
    val monthlyEarned: Int
)
