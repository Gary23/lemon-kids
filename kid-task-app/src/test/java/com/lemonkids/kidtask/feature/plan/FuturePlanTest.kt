package com.lemonkids.kidtask.feature.plan

import com.lemonkids.shared.model.Task
import com.lemonkids.shared.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FuturePlanTest {
    @Test
    fun includesAllFutureDatesAndStatusesInDateOrder() {
        val today = LocalDate.of(2026, 9, 29)
        val groups = buildFutureDateGroups(
            listOf(
                task("far", "2027-01-01", TaskStatus.DONE),
                task("near", "2026-10-01", TaskStatus.PENDING),
                task("verified", "2026-10-01", TaskStatus.VERIFIED),
                task("today", "2026-09-29", TaskStatus.PENDING),
                task("invalid", "tomorrow", TaskStatus.PENDING)
            ), today
        )
        assertEquals(listOf("2026-10-01", "2027-01-01"), groups.map { it.date })
        assertEquals(setOf(TaskStatus.PENDING, TaskStatus.VERIFIED), groups[0].tasks.map { it.status }.toSet())
        assertEquals(TaskStatus.DONE, groups[1].tasks.single().status)
    }

    private fun task(id: String, date: String, status: TaskStatus) = Task(
        id = id, title = id, dueDate = date, status = status
    )
}
