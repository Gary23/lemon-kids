package com.lemonkids.kidtask.feature.profile

import com.lemonkids.shared.model.BadgeCatalog
import com.lemonkids.shared.model.GrowthBadgeProgress
import com.lemonkids.shared.model.GrowthSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthRulesTest {
    @Test fun levelMilestonesAndCrown() {
        assertEquals(250L, GrowthRules.threshold(2))
        assertEquals(825L, GrowthRules.threshold(4))
        assertEquals(53_825L, GrowthRules.threshold(84))
        assertEquals(54_525L, GrowthRules.threshold(85))
        assertEquals("🌙⭐", GrowthRules.level(1_500).symbol)
        assertEquals("☀️", GrowthRules.level(9_725).symbol)
        assertEquals("👑", GrowthRules.level(54_525).symbol)
        assertNull(GrowthRules.level(99_999).nextThreshold)
        assertEquals(84, GrowthRules.level(54_524).number)
    }

    @Test fun aSixLevelBadgeCountsAsSixIndependentMedalsAtFullLevel() {
        val stages = GrowthRules.obtainedStages(snapshot(listOf(
            GrowthBadgeProgress("recognition", "", 3000, "2026-10-05T00:00:00Z")
        )))
        val recognition = stages.filter { it.key == "recognition" }
        assertEquals(6, recognition.size)
        assertEquals("认字小行家·字海探路", recognition[2].displayTitle)
        assertTrue(recognition.all { it.isObtained })
        assertEquals(1f, recognition.last().progress)
    }

    @Test fun englishReadingFullLevelCountsAsTwentySixMedals() {
        val records = BadgeCatalog.englishGoals.indices.map { index ->
            GrowthBadgeProgress("english_reading", BadgeCatalog.englishLetter(index), BadgeCatalog.englishGoals[index], "2026-10-05T00:00:00Z")
        }
        val english = GrowthRules.obtainedStages(snapshot(records)).filter { it.key == "english_reading" }
        assertEquals(26, english.size)
        assertEquals(26, english.last().level)
        assertEquals("英语阅读·星海阅读家·二阶", english.last().displayTitle)
        assertEquals(1f, english.last().progress)
    }

    @Test fun englishReadingCurrentStageUsesCumulativeProgress() {
        val records = listOf(
            GrowthBadgeProgress("english_reading", "A", 50, "2026-10-05T00:00:00Z"),
            GrowthBadgeProgress("english_reading", "B", 20, "2026-10-05T00:00:00Z")
        )
        val data = snapshot(records)
        val badge = GrowthRules.badges(data).first { it.key == "english_reading" }
        val current = GrowthRules.badgeStages(data, badge)[1]
        assertEquals(50, current.threshold)
        assertEquals(70, current.currentValue)
        assertEquals(100, current.nextThreshold)
        assertEquals(0.4f, current.progress)
    }

    @Test fun currentStageUsesRealRangeProgressAndDialogKeepsLockedStages() {
        val data = snapshot(listOf(GrowthBadgeProgress("recognition", "", 500, "2026-10-05T00:00:00Z")))
        val badge = GrowthRules.badges(data).first { it.key == "recognition" }
        val stages = GrowthRules.badgeStages(data, badge)
        assertEquals(2, stages.count { it.isObtained })
        assertEquals(300, stages[1].threshold)
        assertEquals(750, stages[1].nextThreshold)
        assertEquals(500, stages[1].currentValue)
        assertTrue(stages[1].isCurrent)
        assertEquals(200f / 450f, stages[1].progress)
        assertFalse(stages[2].isObtained)
    }

    @Test fun habitAndManualSourcesStayIndependent() {
        val base = snapshot(listOf(GrowthBadgeProgress("recognition", "", 300, "2026-10-05T00:00:00Z")))
            .copy(checkinDays = 25, dutyDays = 1)
        val badges = GrowthRules.badges(base)
        assertEquals(15, badges.size)
        assertEquals(2, badges.first { it.key == "checkin" }.level)
        assertEquals(1, badges.first { it.key == "task_duty" }.level)
        assertEquals(2, badges.first { it.key == "recognition" }.level)
        assertEquals(5, GrowthRules.obtainedStages(base).size)
    }

    @Test fun currentBadgeUsesLevelThenTimeThenStableKey() {
        val data = snapshot(listOf(
            GrowthBadgeProgress("recognition", "", 300, "2026-10-05T00:00:00Z"),
            GrowthBadgeProgress("dictation", "", 300, "2026-10-06T00:00:00Z")
        ))
        assertEquals("dictation", GrowthRules.currentBadge(data)?.key)
    }

    @Test fun badgeDatesUseShanghaiCalendarDay() {
        assertEquals("2026-10-05", GrowthRules.shanghaiDate("2026-10-05T11:08:59.352628+00:00"))
        assertEquals("2026-10-06", GrowthRules.shanghaiDate("2026-10-05T16:30:00Z"))
        assertEquals("2026-10-06", GrowthRules.shanghaiDate("2026-10-06T00:30:00+08:00"))
        assertEquals("2026-10-05 19:08", GrowthRules.shanghaiTime("2026-10-05T11:08:59.352628+00:00"))
        assertNull(GrowthRules.shanghaiDate("bad-date"))
        assertNull(GrowthRules.shanghaiTime("bad-date"))
    }

    private fun snapshot(progress: List<GrowthBadgeProgress>) = GrowthSnapshot(
        "child", "2026-10-05T00:00:00Z", 0, 0, 0, 0, progress, emptyList()
    )
}
