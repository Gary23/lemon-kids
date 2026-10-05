package com.lemonkids.parent.feature.profile

import com.lemonkids.shared.model.BadgeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgeLevelDetailsTest {
    @Test fun everyManualBadgeHasItsFullApprovedLevelList() {
        assertEquals(13, BadgeCatalog.manual.size)
        BadgeCatalog.manual.forEach { spec ->
            val expected = when (spec.key) {
                "english_reading" -> 26
                "reading", "writing", "calculation", "math_explaining", "life_skills" -> 12
                else -> 6
            }
            assertEquals(spec.title, expected, badgeLevelDetailRows(spec).size)
        }
    }

    @Test fun characterBadgeShowsPerLevelAndCumulativeExperience() {
        val spec = BadgeCatalog.byKey.getValue("recognition")
        val rows = badgeLevelDetailRows(spec)
        assertEquals("字", spec.unit)
        assertEquals(BadgeLevelDetailRow(1, "字形初识", 1, 1), rows.first())
        assertEquals(BadgeLevelDetailRow(3, "字海探路", 450, 750), rows[2])
        assertEquals(BadgeLevelDetailRow(6, "识字领航", 900, 3000), rows.last())
    }

    @Test fun semesterBadgeKeepsGraduationGoalSeparateFromLevels() {
        val spec = BadgeCatalog.byKey.getValue("writing")
        val rows = badgeLevelDetailRows(spec)
        assertEquals("三年级上学期·习作启航", rows[4].name)
        assertEquals(40, rows[4].levelExperience)
        assertEquals(160, rows[4].cumulativeExperience)
        assertEquals(300, rows.last().cumulativeExperience)
        assertEquals(320, spec.graduationGoal)
        assertNull(rows.last().segment)
    }

    @Test fun englishReadingShowsEachLetterGoalAndCompletedTotal() {
        val rows = badgeLevelDetailRows(BadgeCatalog.byKey.getValue("english_reading"))
        assertEquals(BadgeLevelDetailRow(1, "阅读小芽·一阶", 50, 50, "A"), rows.first())
        assertEquals(BadgeLevelDetailRow(2, "阅读小芽·二阶", 50, 100, "B"), rows[1])
        assertEquals(BadgeLevelDetailRow(26, "星海阅读家·二阶", 62, 2049, "Z"), rows.last())
    }
}
