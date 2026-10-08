package com.lemonkids.kidtask.feature.reward

import com.lemonkids.shared.model.Reward
import com.lemonkids.shared.model.RewardSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RewardImageUrlsTest {
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
