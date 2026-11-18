package com.lemonkids.kidliteracy.feature.reading

import com.lemonkids.kidliteracy.feature.parentpass.ParentPassStarSnapshot
import com.lemonkids.kidliteracy.feature.parentpass.ParentPassStarState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ParentPassHistoryTest {
    @Test fun historicalReadingsAreSeparateFromDisplayedStars() {
        val state = ParentPassStarState("花朵", earned = 1, required = 1, storedEarned = 2)
        val restored = Json.decodeFromString<ParentPassStarState>(Json.encodeToString(state))
        assertEquals(1, restored.earned)
        assertEquals(1, restored.required)
        assertEquals(2, restored.progressReadings())
    }

    @Test fun oldSnapshotsStillRestoreEarnedReadings() {
        val old = Json.decodeFromString<ParentPassStarSnapshot>(
            """{"character":{"text":"花","earned":2,"required":3},"words":[{"text":"花朵","earned":1,"required":2}]}"""
        )
        assertEquals(2, old.character.progressReadings())
        assertEquals(1, old.words.single().progressReadings())
    }

    @Test fun recognizedCharacterRetainsThreeAtThresholdTwo() {
        val state = ParentPassStarState("花", earned = 2, required = 2, storedEarned = 3)
        assertEquals(3, state.progressReadings())
        assertEquals(2, state.earned)
    }
}
