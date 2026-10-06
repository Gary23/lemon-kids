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

    @Test
    fun savedPendingOrderCanCrossCategoriesWithoutChangingCompletedOrder() {
        val tasks = listOf(
            task("a", "阅读", "PENDING"),
            task("done-1", "运动", "DONE"),
            task("b", "运动", "PENDING"),
            task("done-2", "阅读", "VERIFIED"),
            task("c", "阅读", "PENDING")
        )

        val layout = buildHomeTaskLayout(tasks, listOf("运动", "阅读"), listOf("c", "b", "a"))

        assertEquals(listOf("c", "b", "a"), layout.pending.map { it.id })
        assertEquals(listOf("done-1", "done-2"), layout.completed.map { it.id })
    }

    @Test
    fun newAndUndoneTasksAppendAndDeletedTasksDisappear() {
        val tasks = listOf(
            task("new", "运动", "PENDING"),
            task("a", "阅读", "PENDING"),
            task("undone", "运动", "PENDING"),
            task("b", "阅读", "PENDING")
        )

        val layout = buildHomeTaskLayout(tasks, listOf("运动", "阅读"), listOf("b", "deleted", "a"))

        assertEquals(listOf("b", "a", "new", "undone"), layout.pending.map { it.id })
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
