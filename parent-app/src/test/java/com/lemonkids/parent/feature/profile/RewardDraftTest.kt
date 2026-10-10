package com.lemonkids.parent.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RewardDraftTest {
    private val valid = RewardDraft("读一本书", "15", true, "", false)

    @Test fun acceptsValidDraft() {
        assertNull(validateRewardDraft(valid))
    }

    @Test fun rejectsEmptyTitle() {
        assertEquals("请输入奖励名称", validateRewardDraft(valid.copy(title = "  ")))
    }

    @Test fun rejectsInvalidCost() {
        listOf("0", "-2", "1.5", "abc", "99999999999999999999").forEach { cost ->
            assertEquals("积分价格必须是正整数", validateRewardDraft(valid.copy(cost = cost)))
        }
    }
}
