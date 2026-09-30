package com.lemonkids.kidtask.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthDemoUiStateTest {
    @Test fun filtersOnlyRequestedMedalCategoryAndCanRestoreAll() {
        val state = GrowthDemoUiState()
        val discipline = state.visibleMedals(MedalCategory.DISCIPLINE)
        assertTrue(discipline.isNotEmpty())
        assertTrue(discipline.all { it.category == MedalCategory.DISCIPLINE })
        assertEquals(state.medals, state.visibleMedals(MedalCategory.ALL))
    }

    @Test fun everyFilterHasVisibleExample() {
        val state = GrowthDemoUiState()
        MedalCategory.entries.filter { it != MedalCategory.ALL }.forEach {
            assertTrue("No example for $it", state.visibleMedals(it).isNotEmpty())
        }
    }
}
