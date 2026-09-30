package com.lemonkids.kidtask.feature.reward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardDemoUiStateTest {
    @Test fun requestingDemoWishOnlyChangesLocalDemoState() {
        val before = RewardDemoUiState()
        val after = before.requestWish("book")

        assertEquals(before.wishes, after.wishes)
        assertEquals(before.examples, after.examples)
        assertEquals(setOf("book"), after.appliedWishIds)
        assertTrue(after.feedback.orEmpty().contains("未通知家长，也未扣除星星"))
        assertTrue(after.feedback.orEmpty().contains("挑选一本喜欢的漫画/故事书"))
        assertEquals(null, before.feedback)
        assertTrue(before.appliedWishIds.isEmpty())
    }

    @Test fun unknownWishCannotCreateApplication() {
        val before = RewardDemoUiState()
        assertSame(before, before.requestWish("missing"))
    }
}
