package com.lemonkids.kidtask.feature.home

import com.lemonkids.kidtask.ui.components.TaskUiItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTaskLayoutTest {
    @Test
    fun groupsRealStatusesAndKeepsConfiguredCategoryOrder() {
        val tasks = listOf(
            task("a", "阅读", "PENDING"),
            task("b", "运动", "VERIFIED"),
            task("c", "运动", "PENDING"),
            task("d", "阅读", "DONE"),
            task("e", "运动", "EXPIRED")
        )

        val layout = buildHomeTaskLayout(tasks, listOf("运动", "阅读"))

        assertEquals(listOf("c", "e", "a"), layout.pending.map { it.id })
        assertEquals(listOf("b", "d"), layout.completed.map { it.id })
        assertEquals(40, layout.progressPercent)
    }

    @Test
    fun emptyTasksHaveZeroProgress() {
        val layout = buildHomeTaskLayout(emptyList(), emptyList())
        assertEquals(0, layout.progressPercent)
        assertEquals(emptyList<TaskUiItem>(), layout.pending)
        assertEquals(emptyList<TaskUiItem>(), layout.completed)
    }

    private fun task(id: String, category: String, status: String) = TaskUiItem(
        id = id,
        title = id,
        status = status,
        category = category,
        dueTime = null,
        rewardPoints = 1,
        penaltyPoints = 0
    )
}
