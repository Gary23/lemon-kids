package com.lemonkids.kidliteracy.feature.home

import com.lemonkids.shared.model.ChildLiteracyCharacter
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LiteracyHomeTitleTest {
    @Test
    fun pendingTitleUsesSnapshotDate() {
        val snapshot = DailyLiteracyTaskSnapshot(
            date = "2026-10-14",
            characters = listOf(ChildLiteracyCharacter(id = "a", character = "春"))
        )

        val group = snapshot.toLearningGroup().single()
        assertEquals(LocalDate.of(2026, 10, 14), group.taskDate)
        assertEquals("待认识的字", group.homeHeading())
        assertEquals("10月14日", group.homeDateLabel())
    }

    @Test
    fun invalidSnapshotDateDoesNotShowGroupNumber() {
        val group = DailyLiteracyTaskSnapshot(
            date = "invalid",
            characters = listOf(ChildLiteracyCharacter(id = "a", character = "春"))
        ).toLearningGroup().single()

        assertEquals(null, group.taskDate)
        assertEquals("待认识的字", group.homeHeading())
        assertEquals(null, group.homeDateLabel())
    }

    @Test
    fun recognizedTitleKeepsRecordedDates() {
        val group = LiteracyCharacterGroup(
            type = LiteracyGroupType.KNOWN,
            groupNumber = 2,
            recognizedDates = listOf(LocalDate.of(2026, 10, 12)),
            characters = listOf("山")
        )

        assertEquals("已认识的字", group.homeHeading())
        assertEquals("10月12日", group.homeDateLabel())
    }

    @Test
    fun recognizedDatesStayOnOneDateLineAcrossMonths() {
        val group = LiteracyCharacterGroup(
            type = LiteracyGroupType.KNOWN,
            groupNumber = 2,
            recognizedDates = listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 30)),
            characters = listOf("山")
        )

        assertEquals("已认识的字", group.homeHeading())
        assertEquals("10月1日 · 9月30日", group.homeDateLabel())
    }

    @Test
    fun recognizedGroupWithoutDateKeepsGroupFallback() {
        val group = LiteracyCharacterGroup(
            type = LiteracyGroupType.KNOWN,
            groupNumber = 3,
            characters = listOf("山")
        )

        assertEquals("已认识的字", group.homeHeading())
        assertEquals("第3组", group.homeDateLabel())
    }
}
