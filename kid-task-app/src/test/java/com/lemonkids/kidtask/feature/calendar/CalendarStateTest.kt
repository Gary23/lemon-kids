package com.lemonkids.kidtask.feature.calendar

import com.lemonkids.kidtask.ui.components.TaskUiItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarStateTest {
    @Test
    fun monthSelectionHandlesYearBoundaryAndReturnsToToday() {
        val today = LocalDate.of(2026, 12, 31)
        assertEquals(LocalDate.of(2027, 1, 1), selectedDateForMonth(YearMonth.of(2027, 1), today))
        assertEquals(today, selectedDateForMonth(YearMonth.of(2026, 12), today))
    }

    @Test
    fun selectingDateSynchronizesDisplayedMonth() {
        val state = CalendarUiState(year = 2026, month = 12, selectedDate = "2026-12-31")
            .withSelectedDate(LocalDate.of(2027, 2, 1))
        assertEquals(2027, state.year)
        assertEquals(2, state.month)
        assertEquals("2027-02-01", state.selectedDate)
    }

    @Test
    fun summaryUsesOnlyRealTaskStatusesAndEmptyDayIsEmpty() {
        assertEquals(DayTaskStatus.EMPTY, dayTaskSummary(emptyList()).status)
        val tasks = listOf(task("DONE"), task("VERIFIED"), task("PENDING"))
        assertEquals(DayTaskSummary(3, 2, 2), dayTaskSummary(tasks))
        assertEquals(DayTaskStatus.PENDING, dayTaskSummary(tasks).status)
        assertEquals(DayTaskStatus.COMPLETE, dayTaskSummary(tasks.take(2)).status)
    }

    @Test
    fun earnedStarsOnlyIncludeCompletedAndVerifiedRewards() {
        val tasks = listOf(task("DONE", 3), task("VERIFIED", 2), task("PENDING", 9), task("REJECTED", 7))
        assertEquals(DayTaskSummary(4, 2, 5), dayTaskSummary(tasks))
        assertEquals(DayTaskSummary(1, 1, 0), dayTaskSummary(listOf(task("DONE", 0))))
        assertEquals(DayTaskSummary(1, 0, 0), dayTaskSummary(listOf(task("PENDING", 4))))
    }

    @Test
    fun calendarDotUsesFutureAndEmptyDaysAsNeutral() {
        val today = LocalDate.of(2026, 9, 29)
        val partial = dayTaskSummary(listOf(task("DONE"), task("PENDING")))
        assertEquals(DayTaskStatus.PENDING, dayDisplayStatus(today, today, partial))
        assertEquals(DayTaskStatus.PENDING, dayDisplayStatus(today.minusDays(1), today, partial))
        assertEquals(DayTaskStatus.EMPTY, dayDisplayStatus(today.plusDays(1), today, partial))
        assertEquals(DayTaskStatus.EMPTY, dayDisplayStatus(today, today, dayTaskSummary(emptyList())))
        assertEquals(DayTaskStatus.COMPLETE, dayDisplayStatus(today, today, dayTaskSummary(listOf(task("DONE")))))
    }

    @Test
    fun taskOrderUsesDueTimeWithoutMovingCompletedToEnd() {
        val tasks = listOf(task("DONE").copy(dueTime = "08:00"),
            task("PENDING").copy(dueTime = null), task("VERIFIED").copy(dueTime = "09:00"),
            task("REJECTED").copy(dueTime = null))
        assertEquals(listOf(tasks[0], tasks[2], tasks[1], tasks[3]), sortCalendarTasks(tasks))
        assertEquals(listOf(tasks[0], tasks[2], tasks[1], tasks[3]),
            sortCalendarTasks(listOf(tasks[2], tasks[1], tasks[0], tasks[3])))
    }

    private fun task(status: String, points: Int = 1) = TaskUiItem(
        id = status, title = status, status = status, category = "默认",
        dueTime = null, rewardPoints = points, penaltyPoints = 0
    )
}
