package com.lemonkids.kidtask.feature.reward

import com.lemonkids.shared.model.Reward
import com.lemonkids.shared.model.RewardRedemptionStatus
import com.lemonkids.shared.model.RewardSnapshot

/** 兑换区的展示结果：大心愿至多一个，小心愿按目录顺序展示。 */
internal data class RewardDisplay(
    val featured: Reward?,
    val smallWishes: List<Reward>
)

/** 当前孩子对该奖励是否存在待使用或已使用的有效兑换；已取消不计入。 */
internal fun RewardSnapshot.hasEffectiveClaim(rewardId: String): Boolean =
    redemptions.any { it.rewardId == rewardId && it.status != RewardRedemptionStatus.CANCELLED }

/**
 * 大心愿是否仍需在兑换区展示。
 *
 * 本人已有有效兑换时隐藏；配置为一次性的奖励在家庭名额被占用时隐藏。
 * 该规则与 repeatable 独立，避免可重复配置的大心愿兑换后持续出现。
 */
internal fun RewardSnapshot.isVisibleForRedemption(reward: Reward): Boolean {
    if (!reward.isFeatured) return true
    if (hasEffectiveClaim(reward.id)) return false
    return reward.repeatable || reward.id !in unavailableOneTimeIds
}

/**
 * 计算兑换区展示内容。
 *
 * 已被隐藏的大心愿不会降级进入小心愿区；小心愿列表只包含非 isFeatured 奖励。
 */
internal fun RewardSnapshot.rewardDisplay(): RewardDisplay {
    val featured = rewards.firstOrNull { it.isFeatured && isVisibleForRedemption(it) }
    val smallWishes = rewards.filter { !it.isFeatured }
    return RewardDisplay(featured, smallWishes)
}
