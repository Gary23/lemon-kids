package com.lemonkids.kidliteracy.feature.home

import com.lemonkids.shared.model.ChildLiteracyCharacter
import com.lemonkids.shared.model.RecognizedCharacter
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LiteracyHomeTitleTest {
    @Test
    fun recognizedReadingRequirementsFollowDisplayGroups() {
        val characters = (10 downTo 5).flatMap { day ->
            List(3) { index ->
                RecognizedCharacter(id = "$day-$index", character = "山",
                    recognizedAt = LocalDate.of(2026, 11, day).toString())
            }
        }

        val groups = characters.toKnownGroups()
        assertEquals(listOf(1, 2, 3), groups.map { it.groupNumber })
        assertEquals(listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 9)), groups.first().recognizedDates)
        assertEquals(listOf(2, 2, 1), groups.map { it.recognizedCharacterRequiredReadings })
    }

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
