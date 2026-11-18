package com.lemonkids.kidliteracy.feature.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpedCharactersGroupingTest {
    private fun content(
        id: String,
        targetText: String,
        requestedCharacter: String? = null,
        characterIndex: Int? = null,
        requestedAt: String = "2026-11-12T03:00:00+00:00",
        targetType: String = "word"
    ) = HelpedContent(
        id = id,
        targetText = targetText,
        targetType = targetType,
        requestedCharacter = requestedCharacter,
        characterIndex = characterIndex,
        requestedAt = requestedAt
    )

    @Test
    fun locatesCharacterByValidIndex() {
        assertEquals("苹", content("1", "苹果", "苹", 0).highlightedTargetCharacter())
        assertEquals("果", content("2", "苹果", "果", 1).highlightedTargetCharacter())
        assertEquals("天", content("3", "天天", "天", 1).highlightedTargetCharacter())
    }

    @Test
    fun fallsBackToUniqueCharacterForLegacyIndex() {
        assertEquals("苹", content("1", "苹果", "苹", 5).highlightedTargetCharacter())
        assertEquals("苹", content("2", "苹果", "苹").highlightedTargetCharacter())
        assertEquals("苹", content("3", " 苹果 ", "苹").highlightedTargetCharacter())
    }

    @Test
    fun rejectsAmbiguousRepeatedCharacterWithoutValidIndex() {
        assertNull(content("1", "天天", "天").highlightedTargetCharacter())
        assertNull(content("2", "天天", "天", 2).highlightedTargetCharacter())
        assertEquals("天", content("3", "天天", "天", 1).highlightedTargetCharacter())
    }

    @Test
    fun rejectsMissingOrInvalidRequestedCharacter() {
        assertNull(content("1", "苹果", null, 0).highlightedTargetCharacter())
        assertNull(content("2", "苹果", "苹果", 0).highlightedTargetCharacter())
        assertNull(content("3", "apple", "a", 0).highlightedTargetCharacter())
        assertNull(content("4", "苹果", " ", 0).highlightedTargetCharacter())
        assertNull(content("5", "苹果", "桃", 0).highlightedTargetCharacter())
    }

    @Test
    fun groupsSameCharacterAcrossWordAndSentenceRecords() {
        val groups = listOf(
            content("1", "苹果", "苹", 0, "2026-11-10T03:00:00+00:00", "word"),
            content("2", "苹果很甜", "苹", 0, "2026-11-12T03:00:00+00:00", "sentence"),
            content("3", "桃子", "桃", 0, "2026-11-11T03:00:00+00:00", "word")
        ).toHelpedCharacterGroups()

        assertEquals(listOf("苹", "桃"), groups.map { it.character })
        val apple = groups.first()
        assertEquals("2026-11-12T03:00:00+00:00", apple.requestedAt)
        assertEquals(listOf("1", "2"), apple.recordIds)
        assertEquals(listOf("3"), groups[1].recordIds)
    }

    @Test
    fun keepsLatestDateWhenRecordsArriveOutOfOrder() {
        val groups = listOf(
            content("1", "苹果", "苹", 0, "2026-11-12T03:00:00+00:00"),
            content("2", "苹果", "苹", 0, "2026-11-10T03:00:00+00:00")
        ).toHelpedCharacterGroups()

        assertEquals(1, groups.size)
        assertEquals("2026-11-12T03:00:00+00:00", groups.single().requestedAt)
    }

    @Test
    fun keepsUnknownRecordsAsSeparatePlaceholders() {
        val groups = listOf(
            content("u1", "苹果", null, 0, "2026-11-09T03:00:00+00:00"),
            content("u2", "河流河流", "河", null, "2026-11-12T03:00:00+00:00")
        ).toHelpedCharacterGroups()

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.isUnknownTarget })
        assertEquals(listOf("unknown:u2", "unknown:u1"), groups.map { it.key })
        assertEquals("2026-11-12T03:00:00+00:00", groups.first().requestedAt)
        assertEquals(listOf("u2"), groups.first().recordIds)
    }

    @Test
    fun doesNotMergeUnknownRecordsWithSameCharacter() {
        val groups = listOf(
            content("u1", "苹果", null, 0),
            content("u2", "苹果", null, 0)
        ).toHelpedCharacterGroups()

        assertEquals(2, groups.size)
        assertEquals(listOf("unknown:u1", "unknown:u2"), groups.map { it.key })
    }

    @Test
    fun deduplicatesRecordIdsWithinCharacterGroup() {
        val record = content("1", "苹果", "苹", 0)
        val groups = listOf(record, record).toHelpedCharacterGroups()

        assertEquals(1, groups.size)
        assertEquals(listOf("1"), groups.single().recordIds)
    }

    @Test
    fun exposesOnlyTargetCharacterForKnownRecords() {
        val group = listOf(content("1", "苹果很甜", "苹", 0, targetType = "sentence"))
            .toHelpedCharacterGroups()
            .single()

        assertEquals("苹", group.character)
    }
}
