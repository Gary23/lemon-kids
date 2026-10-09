package com.lemonkids.kidtask.feature.reward

import com.lemonkids.shared.model.Reward
import com.lemonkids.shared.model.RewardSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RewardImageUrlsTest {
    @Test fun keepsOnlyMatchingUnexpiredImageUrls() {
        val previous = mapOf(
            "same" to RewardImageEntry("family-a", "family-a/same.jpg", "same-url", 1_000),
            "changed" to RewardImageEntry("family-a", "family-a/old.jpg", "old-url", 1_000),
            "removed" to RewardImageEntry("family-a", "family-a/removed.jpg", "removed-url", 1_000),
            "expired" to RewardImageEntry("family-a", "family-a/expired.jpg", "expired-url", -600_000),
            "other" to RewardImageEntry("family-b", "family-b/other.jpg", "other-url", 1_000)
        )
        val snapshot = RewardSnapshot(listOf(
            Reward(id = "same", familyId = "family-a", imagePath = "family-a/same.jpg"),
            Reward(id = "changed", familyId = "family-a", imagePath = "family-a/new.jpg"),
            Reward(id = "removed", familyId = "family-a"),
            Reward(id = "expired", familyId = "family-a", imagePath = "family-a/expired.jpg"),
            Reward(id = "other", familyId = "family-a", imagePath = "family-a/other.jpg")
        ), emptyList(), emptySet(), emptyList(), 10, 10)

        assertEquals(mapOf("same" to previous.getValue("same")),
            retainRewardImageEntries("family-a", snapshot, previous, 2_000))
        assertEquals(emptyMap<String, RewardImageEntry>(),
            retainRewardImageEntries("family-b", snapshot, previous, 2_000))
        assertEquals(emptyMap<String, RewardImageEntry>(),
            retainRewardImageEntries("family-a", snapshot, previous, 601_000))
    }

    @Test fun signsOnlyCurrentFamilyImagesAndIsolatesFailures() = runBlocking {
        val rewards = listOf(
            Reward(id = "featured", familyId = "family-a", imagePath = "family-a/featured.jpg"),
            Reward(id = "broken", familyId = "family-a", imagePath = "family-a/broken.jpg"),
            Reward(id = "thrown", familyId = "family-a", imagePath = "family-a/thrown.jpg"),
            Reward(id = "missing", familyId = "family-a"),
            Reward(id = "other", familyId = "family-b", imagePath = "family-b/other.jpg")
        )
        val snapshot = RewardSnapshot(rewards, emptyList(), emptySet(), emptyList(), 10, 10)
        val signedPaths = mutableListOf<String>()

        val urls = resolveRewardImageUrls("family-a", snapshot) { family, path ->
            assertEquals("family-a", family)
            signedPaths += path
            if (path.endsWith("thrown.jpg")) throw IllegalStateException("unavailable")
            if (path.endsWith("broken.jpg")) Result.failure(IllegalStateException("unavailable"))
            else Result.success("signed:$path")
        }

        assertEquals(mapOf("featured" to "signed:family-a/featured.jpg"), urls)
        assertEquals(setOf("family-a/featured.jpg", "family-a/broken.jpg", "family-a/thrown.jpg"), signedPaths.toSet())
    }
}
