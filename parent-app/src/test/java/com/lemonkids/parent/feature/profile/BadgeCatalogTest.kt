package com.lemonkids.parent.feature.profile

import com.lemonkids.shared.model.BadgeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeCatalogTest {
    @Test fun approvedCapsAndSemesterBoundaries() {
        assertEquals(13, BadgeCatalog.manual.size)
        assertEquals(3000, BadgeCatalog.byKey.getValue("recognition").limit)
        assertEquals(437, BadgeCatalog.byKey.getValue("dino_english").limit)
        assertEquals(211, BadgeCatalog.byKey.getValue("thinking_seed").limit)
        val writing = BadgeCatalog.byKey.getValue("writing")
        assertEquals(4, writing.level(159))
        assertEquals("三年级上学期·习作启航", writing.stageName(160))
        assertFalse(writing.isComplete(300))
        assertTrue(writing.isComplete(320))
    }

    @Test fun englishReadingKeepsLaterEntriesButReturnsToFirstIncompleteLevel() {
        assertEquals(2049, BadgeCatalog.englishGoals.sum())
        assertEquals("星海阅读家·二阶", BadgeCatalog.englishName(25))
        val values = BadgeCatalog.englishGoals.indices.associate { BadgeCatalog.englishLetter(it) to BadgeCatalog.englishGoals[it] }.toMutableMap()
        assertEquals(26, BadgeCatalog.englishLevel(values))
        values["B"] = 49
        assertEquals(2, BadgeCatalog.englishLevel(values))
        assertEquals(99, BadgeCatalog.englishCountedTotal(values))
        assertEquals(62, values["Z"])
        assertFalse(BadgeCatalog.validate("english_reading", "B", 51))
        assertFalse(BadgeCatalog.validate("recognition", "", 3001))
    }
}
