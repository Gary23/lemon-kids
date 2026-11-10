package com.lemonkids.kidtask.feature.reward

import com.lemonkids.shared.model.Reward
import com.lemonkids.shared.model.RewardRedemption
import com.lemonkids.shared.model.RewardRedemptionStatus
import com.lemonkids.shared.model.RewardSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardDisplayTest {
    private fun reward(
        id: String,
        featured: Boolean = false,
        repeatable: Boolean = true
    ) = Reward(id = id, title = id, cost = 10, repeatable = repeatable, isFeatured = featured)

    private fun redemption(
        rewardId: String,
        status: RewardRedemptionStatus,
        id: String = "redemption-$rewardId-${status.name}"
    ) = RewardRedemption(
        id = id,
        familyId = "family",
        childId = "child",
        rewardId = rewardId,
        title = rewardId,
        cost = 10,
        repeatable = true,
        status = status,
        redeemedAt = "2026-11-10T08:00:00Z"
    )

    private fun snapshot(
        rewards: List<Reward>,
        redemptions: List<RewardRedemption> = emptyList(),
        unavailableOneTimeIds: Set<String> = emptySet()
    ) = RewardSnapshot(
        rewards = rewards,
        redemptions = redemptions,
        unavailableOneTimeIds = unavailableOneTimeIds,
        legacyRedemptions = emptyList(),
        balance = 100,
        monthlyEarned = 0
    )

    @Test fun unredeemedFeaturedWishStaysVisible() {
        val display = snapshot(listOf(reward("big", featured = true), reward("small"))).rewardDisplay()

        assertEquals("big", display.featured?.id)
        assertEquals(listOf("small"), display.smallWishes.map { it.id })
    }

    @Test fun heldFeaturedWishIsRemovedFromRedemptionArea() {
        val display = snapshot(
            listOf(reward("big", featured = true), reward("small")),
            redemptions = listOf(redemption("big", RewardRedemptionStatus.HELD))
        ).rewardDisplay()

        assertNull(display.featured)
        assertEquals(listOf("small"), display.smallWishes.map { it.id })
    }

    @Test fun usedFeaturedWishIsRemovedFromRedemptionArea() {
        val display = snapshot(
            listOf(reward("big", featured = true), reward("small")),
            redemptions = listOf(redemption("big", RewardRedemptionStatus.USED))
        ).rewardDisplay()

        assertNull(display.featured)
    }

    @Test fun cancelledOnlyClaimRestoresFeaturedWish() {
        val display = snapshot(
            listOf(reward("big", featured = true)),
            redemptions = listOf(redemption("big", RewardRedemptionStatus.CANCELLED))
        ).rewardDisplay()

        assertEquals("big", display.featured?.id)
    }

    @Test fun familyOneTimeClaimHidesNonRepeatableFeaturedWish() {
        val display = snapshot(
            listOf(reward("big", featured = true, repeatable = false)),
            unavailableOneTimeIds = setOf("big")
        ).rewardDisplay()

        assertNull(display.featured)
    }

    @Test fun repeatableFeaturedWishHidesAfterOwnClaim() {
        val display = snapshot(
            listOf(reward("big", featured = true, repeatable = true)),
            redemptions = listOf(redemption("big", RewardRedemptionStatus.HELD))
        ).rewardDisplay()

        assertNull(display.featured)
    }

    @Test fun hiddenFeaturedWishNeverFallsIntoSmallWishList() {
        val display = snapshot(
            listOf(reward("big", featured = true), reward("small-1"), reward("small-2")),
            redemptions = listOf(redemption("big", RewardRedemptionStatus.USED))
        ).rewardDisplay()

        assertNull(display.featured)
        assertEquals(listOf("small-1", "small-2"), display.smallWishes.map { it.id })
        assertFalse(display.smallWishes.any { it.isFeatured })
    }

    @Test fun repeatableSmallWishStaysAfterRedemption() {
        val display = snapshot(
            listOf(reward("small")),
            redemptions = listOf(redemption("small", RewardRedemptionStatus.HELD))
        ).rewardDisplay()

        assertEquals(listOf("small"), display.smallWishes.map { it.id })
        assertTrue(display.smallWishes.single().repeatable)
    }

    @Test fun noFeaturedWishLeavesSectionEmpty() {
        val display = snapshot(listOf(reward("small-1"), reward("small-2"))).rewardDisplay()

        assertNull(display.featured)
        assertEquals(listOf("small-1", "small-2"), display.smallWishes.map { it.id })
    }

    @Test fun hiddenFeaturedWishFallsBackToNextVisibleFeaturedWish() {
        val display = snapshot(
            listOf(
                reward("big-1", featured = true),
                reward("big-2", featured = true),
                reward("small")
            ),
            redemptions = listOf(redemption("big-1", RewardRedemptionStatus.HELD))
        ).rewardDisplay()

        assertEquals("big-2", display.featured?.id)
        assertEquals(listOf("small"), display.smallWishes.map { it.id })
    }

    @Test fun mixedHistoryKeepsWishHiddenWhileAnyEffectiveClaimRemains() {
        val display = snapshot(
            listOf(reward("big", featured = true)),
            redemptions = listOf(
                redemption("big", RewardRedemptionStatus.CANCELLED, id = "cancelled"),
                redemption("big", RewardRedemptionStatus.USED, id = "used")
            )
        ).rewardDisplay()

        assertNull(display.featured)
    }

    @Test fun nonFeaturedRewardIsAlwaysVisibleForRedemption() {
        val reward = reward("small")
        val snapshot = snapshot(listOf(reward), unavailableOneTimeIds = setOf("small"))

        assertTrue(snapshot.isVisibleForRedemption(reward))
    }
}
