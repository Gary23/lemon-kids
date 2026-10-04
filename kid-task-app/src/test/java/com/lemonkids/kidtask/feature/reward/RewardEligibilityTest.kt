package com.lemonkids.kidtask.feature.reward

import com.lemonkids.shared.model.Reward
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RewardEligibilityTest {
    @Test fun unknownBalanceCannotRedeem() {
        assertEquals("余额读取中", Reward(id = "r", cost = 20).redemptionBlockReason(null, emptySet()))
    }

    @Test fun oneTimeRewardIsBlockedAfterFamilyClaimEvenWithEnoughPoints() {
        val reward = Reward(id = "r", cost = 20, repeatable = false)
        assertEquals("一次性奖励已兑换", reward.redemptionBlockReason(100, setOf("r")))
        assertNull(reward.redemptionBlockReason(100, emptySet()))
    }

    @Test fun repeatableRewardCanBeRedeemedAgainAndInsufficientBalanceShowsDifference() {
        val reward = Reward(id = "r", cost = 20, repeatable = true)
        assertNull(reward.redemptionBlockReason(20, setOf("r")))
        assertEquals("还差 7 颗星星", reward.redemptionBlockReason(13, setOf("r")))
    }

    @Test fun serviceErrorsAreShownAsActionableChildMessages() {
        assertEquals("星星不足，请刷新余额后再试", rewardFailureMessage("Insufficient points"))
        assertEquals("奖励已使用，无法取消退星", rewardFailureMessage("Used reward cannot be cancelled"))
    }
}
