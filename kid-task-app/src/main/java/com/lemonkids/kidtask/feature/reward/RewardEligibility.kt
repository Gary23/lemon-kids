package com.lemonkids.kidtask.feature.reward

import com.lemonkids.shared.model.Reward

internal fun Reward.redemptionBlockReason(balance: Int?, unavailableOneTimeIds: Set<String>): String? = when {
    balance == null -> "余额读取中"
    !isActive -> "奖励已停用"
    !repeatable && id in unavailableOneTimeIds -> "一次性奖励已兑换"
    balance < cost -> "还差 ${cost - balance} 颗星星"
    else -> null
}

internal fun rewardFailureMessage(message: String?): String {
    val detail = message.orEmpty().lowercase()
    return when {
        "insufficient points" in detail -> "星星不足，请刷新余额后再试"
        "one-time reward already redeemed" in detail -> "这件一次性奖励已被家人兑换，请刷新奖励"
        "reward is inactive" in detail -> "家长已停用这件奖励，请刷新奖励"
        "used reward cannot be cancelled" in detail -> "奖励已使用，无法取消退星"
        "cancelled reward cannot be used" in detail -> "兑换已取消，无法标记使用"
        "outside current family" in detail || "only the child" in detail -> "当前账号无权操作这件奖励"
        else -> "操作失败或网络中断，请刷新后重试"
    }
}
